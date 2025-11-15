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

package io.github.suppierk.codegen.config;

/**
 * Provides default Flyway configuration values used by the plugin when users do not override the
 * corresponding properties in {@link FlywayConfig}. Mirrors Flyway's own defaults so that the
 * plugin behaviour matches the CLI.
 *
 * @see FlywayConfig
 */
public final class DefaultFlywaySettings {

  /** Default versioned SQL migration prefix used by Flyway. */
  public static final String SQL_MIGRATION_PREFIX = "V";

  /** Default repeatable SQL migration prefix used by Flyway. */
  public static final String REPEATABLE_SQL_MIGRATION_PREFIX = "R";

  /** Default Flyway schema history table name. */
  public static final String SCHEMA_HISTORY_TABLE = "flyway_schema_history";

  /** Default constructor. */
  private DefaultFlywaySettings() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }
}
