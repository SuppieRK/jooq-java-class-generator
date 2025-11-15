/*
 * MIT License
 *
 * Copyright (c) 2024 Roman Khlebnov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to do so, subject to the following conditions:
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
import java.util.Objects;
import javax.annotation.Nonnull;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;

/**
 * Root configuration entry point for the temporary Testcontainers-backed databases used during jOOQ
 * code generation. Exposed to build scripts as the {@code jooqCodegen} extension.
 *
 * @see DatabaseExtension
 */
public class GeneratorExtension {
  /** Extension registration name. */
  public static final String EXTENSION_NAME = "jooqCodegen";

  private final NamedDomainObjectContainer<DatabaseExtension> databases;
  private final Logger logger;

  /**
   * Creates a new instance backed by the given {@link ObjectFactory}.
   *
   * @param objects Gradle object factory used to instantiate properties
   * @param logger to use for logging
   */
  public GeneratorExtension(@Nonnull ObjectFactory objects, @Nonnull Logger logger) {
    this.logger = Objects.requireNonNull(logger, "logger");
    this.databases =
        objects.domainObjectContainer(
            DatabaseExtension.class, name -> new DatabaseExtension(name, objects, this.logger));
  }

  /**
   * Returns all registered database definitions in registration order. Build scripts can iterate
   * over this container to inspect or further configure the declared databases.
   *
   * @return container of database specifications
   */
  public @Nonnull NamedDomainObjectContainer<DatabaseExtension> getDatabases() {
    return databases;
  }

  /**
   * Provides access to the database container for DSL-style configuration ({@code databases {
   * create(...) }}).
   *
   * @param action configuration block
   * @see #database(String, Action)
   */
  public void databases(
      @Nonnull Action<? super NamedDomainObjectContainer<DatabaseExtension>> action) {
    action.execute(databases);
  }

  /**
   * Groovy-friendly variant accepting a {@link Closure}.
   *
   * @param closure configuration block
   */
  public void databases(@Nonnull Closure<?> closure) {
    configureWithClosure(closure, databases);
  }

  /**
   * Declares (or configures) a logical database using container semantics. Repeated invocations
   * merge configuration rather than replace it.
   *
   * @param name unique database name
   * @param action configuration block
   * @see DatabaseExtension
   */
  public void database(@Nonnull String name, @Nonnull Action<? super DatabaseExtension> action) {
    action.execute(obtainDatabase(name));
  }

  /**
   * Groovy-friendly alias that delegates to {@link #database(String, Action)}.
   *
   * @param name unique database name
   * @param closure configuration block
   * @see DatabaseExtension
   */
  public void database(@Nonnull String name, @Nonnull Closure<?> closure) {
    configureWithClosure(closure, obtainDatabase(name));
  }

  /**
   * Retrieves an existing database extension or creates a new one when absent.
   *
   * @param name database name requested by the DSL
   * @return existing or newly created {@link DatabaseExtension}
   */
  private @Nonnull DatabaseExtension obtainDatabase(@Nonnull String name) {
    final var existing = databases.findByName(name);
    if (existing != null) {
      logger.warn(
          "Database '{}' was configured multiple times in jooqCodegen; merge configuration blocks where possible.",
          name);
      return existing;
    }
    return databases.create(name);
  }
}
