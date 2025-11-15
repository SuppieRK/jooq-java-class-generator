/*
 * MIT License
 *
 * Copyright (c) 2024 Roman Khlebnov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.github.suppierk.codegen.extensions;

import static io.github.suppierk.codegen.util.Extensions.configureWithClosure;

import groovy.lang.Closure;
import io.github.suppierk.codegen.docker.AbstractDatabaseContainer;
import io.github.suppierk.codegen.docker.MySQL;
import io.github.suppierk.codegen.docker.PostgreSQL;
import io.github.suppierk.codegen.util.Strings;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

/**
 * Describes configuration for a logical temporary database instance. Each database can host
 * multiple schemas, and each schema references one or more jOOQ configurations.
 *
 * @see GeneratorExtension
 * @see SchemaExtension
 */
public class DatabaseExtension implements Named {
  private static final String DEFAULT_POSTGRES_IMAGE = "postgres:17-alpine";
  private static final String DEFAULT_MYSQL_IMAGE = "mysql:8.4";

  private final String name;
  private final Property<String> driver;
  private final NamedDomainObjectContainer<SchemaExtension> schemas;
  private final Logger logger;
  private final ContainerExtension container;

  /**
   * Default constructor.
   *
   * @param name unique database name
   * @param objects Gradle object factory used to instantiate properties
   * @param logger to use for logging
   */
  public DatabaseExtension(
      @Nonnull String name, @Nonnull ObjectFactory objects, @Nonnull Logger logger) {
    this.name = Objects.requireNonNull(name, "name");
    this.driver = objects.property(String.class);
    this.logger = logger;
    this.schemas =
        objects.domainObjectContainer(
            SchemaExtension.class, schemaName -> new SchemaExtension(schemaName, objects));
    this.container = new ContainerExtension(objects);
  }

  /**
   * Driver class name used to select the Testcontainers implementation. Build scripts typically set
   * this to either {@code org.postgresql.Driver} or {@code com.mysql.cj.jdbc.Driver}.
   *
   * @return Gradle property backing the configured driver
   */
  public @Nonnull Property<String> getDriver() {
    return driver;
  }

  /**
   * Sets the driver class name to use for the current database.
   *
   * @param value JDBC driver class name
   */
  public void setDriver(@Nonnull String value) {
    driver.set(Objects.requireNonNull(value, "driver"));
  }

  /**
   * Groovy-friendly alias for {@link #setDriver(String)}.
   *
   * @param value JDBC driver class name
   */
  public void driver(@Nonnull String value) {
    setDriver(value);
  }

  /**
   * Configures container-specific overrides (currently only the Docker image) for the current
   * database.
   *
   * @param action configuration block applied to the container specification
   * @see ContainerExtension
   */
  public void container(@Nonnull Action<? super ContainerExtension> action) {
    action.execute(container);
  }

  /**
   * Groovy-friendly variant accepting a {@link Closure}.
   *
   * @param closure configuration block applied to the container specification
   */
  public void container(@Nonnull Closure<?> closure) {
    configureWithClosure(closure, container);
  }

  /**
   * Returns container overrides associated with this database.
   *
   * @return container override specification
   */
  public @Nonnull ContainerExtension getContainer() {
    return container;
  }

  /** {@inheritDoc} */
  @Override
  public @Nonnull String getName() {
    return name;
  }

  /**
   * Declares (or configures) a schema using container semantics.
   *
   * @param name unique schema name
   * @param action configuration block
   * @see SchemaExtension
   */
  public void schema(@Nonnull String name, @Nonnull Action<? super SchemaExtension> action) {
    action.execute(obtainSchema(name));
  }

  /**
   * Groovy-friendly variant accepting a {@link Closure}.
   *
   * @param name unique schema name
   * @param closure configuration block
   */
  public void schema(@Nonnull String name, @Nonnull Closure<?> closure) {
    configureWithClosure(closure, obtainSchema(name));
  }

  /**
   * Provides access to the schema container for DSL-style configuration ({@code schemas {
   * create(...) }}).
   *
   * @param action configuration block
   */
  public void schemas(@Nonnull Action<? super NamedDomainObjectContainer<SchemaExtension>> action) {
    action.execute(schemas);
  }

  /**
   * Groovy-friendly variant accepting a {@link Closure}.
   *
   * @param closure configuration block
   */
  public void schemas(@Nonnull Closure<?> closure) {
    configureWithClosure(closure, schemas);
  }

  /**
   * Returns registered schemas for the current database.
   *
   * @return immutable view of schema specifications
   */
  public @Nonnull NamedDomainObjectContainer<SchemaExtension> getSchemas() {
    return schemas;
  }

  /**
   * Retrieves an existing schema extension or creates a new entry when absent.
   *
   * @param name schema identifier requested by the DSL
   * @return existing or newly created {@link SchemaExtension}
   */
  private @Nonnull SchemaExtension obtainSchema(@Nonnull String name) {
    final var existing = schemas.findByName(name);
    if (existing != null) {
      logger.warn(
          "Schema '{}' in database '{}' was configured multiple times; merge configuration blocks where possible.",
          name,
          this.name);
      return existing;
    }
    return schemas.create(name);
  }

  /**
   * Creates a Testcontainers database container instance for the resolved driver. Invoked by {@code
   * AbstractDatabaseTask} whenever a schema runs migrations or generates code.
   *
   * @param driverClassName driver requested by the DSL / Flyway configuration
   * @param classLoader to use to find the JDBC driver
   * @return database container instance
   */
  @Nonnull
  public AbstractDatabaseContainer createDatabaseContainer(
      @Nonnull String driverClassName, @Nonnull ClassLoader classLoader) {
    if (PostgreSQL.supportsDriverClassName(driverClassName)) {
      return new PostgreSQL(resolvePostgresImage());
    }
    if (MySQL.supportsDriverClassName(driverClassName)) {
      return new MySQL(resolveMySqlImage(), classLoader);
    }
    throw new UnsupportedOperationException(
        "[INTERNAL PLUGIN ERROR] Driver '%s' is not yet supported".formatted(driverClassName));
  }

  /**
   * Resolves the Docker image that will back the requested JDBC driver. The method honours
   * container-level overrides and falls back to sensible defaults when none are provided.
   *
   * @param driverClassName database driver to inspect
   * @return Docker image name that will be used when launching the container
   */
  public @Nonnull String determineContainerImage(@Nonnull String driverClassName) {
    if (PostgreSQL.supportsDriverClassName(driverClassName)) {
      return resolvePostgresImage();
    }
    if (MySQL.supportsDriverClassName(driverClassName)) {
      return resolveMySqlImage();
    }
    throw new UnsupportedOperationException(
        "[INTERNAL PLUGIN ERROR] Driver '%s' is not yet supported".formatted(driverClassName));
  }

  /**
   * Determines the Docker image to use for PostgreSQL-based containers.
   *
   * @return Docker image tag
   */
  private @Nonnull String resolvePostgresImage() {
    return Strings.firstNonBlank(container.image().getOrNull(), DEFAULT_POSTGRES_IMAGE);
  }

  /**
   * Determines the Docker image to use for MySQL-based containers.
   *
   * @return Docker image tag
   */
  private @Nonnull String resolveMySqlImage() {
    return Strings.firstNonBlank(container.image().getOrNull(), DEFAULT_MYSQL_IMAGE);
  }
}
