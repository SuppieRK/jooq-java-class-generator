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

package io.github.suppierk.codegen;

import io.github.suppierk.codegen.docker.DatabaseContainer;
import io.github.suppierk.codegen.docker.PostgreSQL;
import io.github.suppierk.codegen.util.Strings;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Defines properties for the plugin.
 *
 * <p>The only properties we require are Docker image tags in case if user wants to override jOOQ
 * code generator version.
 *
 * @see <a
 *     href="https://github.com/etiennestuder/gradle-jooq-plugin/tree/main?tab=readme-ov-file#synchronizing-the-jooq-version-between-the-spring-boot-gradle-plugin-and-the-jooq-gradle-plugin">jOOQ
 *     version override consideration</a>
 */
public class GeneratorExtension {
  /** Default extension name. */
  public static final String EXTENSION_NAME = "jooqDockerImages";

  /**
   * Default PostgreSQL Docker image tag.
   *
   * @see <a href="https://hub.docker.com/_/postgres">Available tags on Docker hub</a>
   * @see <a href="https://www.jooq.org/download/support-matrix#PostgreSQL">jOOQ support matrix</a>
   */
  private static final String DEFAULT_POSGTRES_IMAGE = "postgres:17-alpine";

  /** User-defined PostgreSQL Docker image tag. */
  @Nullable public String postgres;

  /** Default constructor. */
  public GeneratorExtension() {
    // No actions required
  }

  /**
   * Factory method to create the appropriate Docker image container to execute Flyway and jOOQ
   * against.
   *
   * @param driverClassName of the database
   * @return {@link DatabaseContainer} to use during execution
   */
  public @Nonnull DatabaseContainer getDatabaseContainer(String driverClassName) {
    if (PostgreSQL.supportsDriverClassName(driverClassName)) {
      return new PostgreSQL(getPostgreSQLDockerImage());
    } else {
      throw new UnsupportedOperationException(
          "[INTERNAL PLUGIN ERROR] Driver '%s' is not yet supported".formatted(driverClassName));
    }
  }

  /**
   * Provides a stable Docker image tag for operations
   *
   * @return null-safe Docker image tag for PostgreSQL database
   */
  public @Nonnull String getPostgreSQLDockerImage() {
    return Strings.firstNonBlank(postgres, DEFAULT_POSGTRES_IMAGE);
  }
}
