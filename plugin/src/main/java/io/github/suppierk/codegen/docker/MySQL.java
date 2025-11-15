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

package io.github.suppierk.codegen.docker;

import javax.annotation.Nonnull;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;

/**
 * Testcontainers-backed MySQL database used when schemas declare either the modern or legacy MySQL
 * driver. Provides a specialised container implementation that can resolve the JDBC driver from a
 * supplied class loader.
 *
 * @see PostgreSQL
 */
public class MySQL extends AbstractDatabaseContainer {
  private static final String DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";
  private static final String LEGACY_DRIVER_CLASS_NAME = "com.mysql.jdbc.Driver";
  private static final String ROOT_USERNAME = "root";
  private static final String ROOT_PASSWORD = "test";
  private static final String ROOT_HOST_WILDCARD = "%";

  /**
   * Creates a MySQL Testcontainer using supplied Docker image and default class loader.
   *
   * @param dockerImageName Docker tag to launch
   */
  public MySQL(@Nonnull String dockerImageName) {
    this(dockerImageName, MySQL.class.getClassLoader());
  }

  /**
   * Creates a MySQL Testcontainer using supplied Docker image and explicit driver loader.
   *
   * @param dockerImageName Docker tag to launch
   * @param driverClassLoader {@link ClassLoader} capable of resolving the JDBC driver
   */
  public MySQL(@Nonnull String dockerImageName, @Nonnull ClassLoader driverClassLoader) {
    super(new DriverAwareMySQLContainer(dockerImageName, driverClassLoader));
    getDatabaseContainer().start();
  }

  /**
   * Verifies whether provided driver class name is supported by this container.
   *
   * @param driverClassName used in Flyway / jOOQ Gradle configurations
   * @return {@code true} if current implementation supports given driver, {@code false} otherwise
   */
  public static boolean supportsDriverClassName(@Nonnull String driverClassName) {
    return DRIVER_CLASS_NAME.equals(driverClassName)
        || LEGACY_DRIVER_CLASS_NAME.equals(driverClassName);
  }

  @Override
  public @Nonnull String getDriverClassName() {
    return DRIVER_CLASS_NAME;
  }

  /** MySQL container variant capable of loading the JDBC driver from a provided class loader. */
  private static final class DriverAwareMySQLContainer
      extends MySQLContainer<DriverAwareMySQLContainer> {
    private final ClassLoader driverClassLoader;

    /** Creates a container instance bound to the requested image and driver class loader. */
    DriverAwareMySQLContainer(
        @Nonnull String dockerImageName, @Nonnull ClassLoader driverClassLoader) {
      super(dockerImageName);
      this.driverClassLoader = driverClassLoader;
      withEnv("MYSQL_ROOT_PASSWORD", ROOT_PASSWORD);
      withEnv("MYSQL_ROOT_HOST", ROOT_HOST_WILDCARD);
      withUsername(ROOT_USERNAME);
      withPassword(ROOT_PASSWORD);
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull String getDriverClassName() {
      return DRIVER_CLASS_NAME;
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull java.sql.Driver getJdbcDriverInstance() {
      try {
        final Class<?> driverClass = Class.forName(getDriverClassName(), true, driverClassLoader);
        return (java.sql.Driver) driverClass.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        throw new JdbcDatabaseContainer.NoDriverFoundException("Could not load MySQL driver", e);
      }
    }
  }
}
