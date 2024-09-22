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
import org.testcontainers.containers.PostgreSQLContainer;

/** Defines database container with PostgreSQL database. */
public class PostgreSQL implements DatabaseContainer {
  private static final String DRIVER_CLASS_NAME = "org.postgresql.Driver";

  private final PostgreSQLContainer<?> container;

  /**
   * Constructor.
   *
   * @param dockerImageName to use as a base
   */
  public PostgreSQL(String dockerImageName) {
    this.container = new PostgreSQLContainer<>(dockerImageName);
    this.container.start();
  }

  /**
   * <b>RECOMMENDED TO BE CREATED ACROSS ALL OTHER {@link DatabaseContainer} IMPLEMENTATIONS</b>
   *
   * @param driverClassName used in Flyway / jOOQ Gradle configurations
   * @return {@code true} if current implementation supports given driver, {@code false} otherwise
   */
  public static boolean supportsDriverClassName(String driverClassName) {
    return DRIVER_CLASS_NAME.equals(driverClassName);
  }

  /** {@inheritDoc} */
  @Override
  public @Nonnull String getDriverClassName() {
    return container.getDriverClassName();
  }

  /** {@inheritDoc} */
  @Override
  public @Nonnull String getJdbcUrl() {
    return container.getJdbcUrl();
  }

  /** {@inheritDoc} */
  @Override
  public @Nonnull String getUsername() {
    return container.getUsername();
  }

  /** {@inheritDoc} */
  @Override
  public @Nonnull String getPassword() {
    return container.getPassword();
  }

  /** {@inheritDoc} */
  @Override
  public void close() throws Exception {
    container.stop();
  }
}
