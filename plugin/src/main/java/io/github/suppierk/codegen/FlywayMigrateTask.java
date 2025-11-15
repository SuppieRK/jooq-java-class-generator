/*
 * MIT License
 *
 * Copyright (c) 2024 Roman Khlebnov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to do so, subject to the
 * following conditions:
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

import io.github.suppierk.codegen.config.FlywayConfig;
import io.github.suppierk.codegen.extensions.DatabaseExtension;
import io.github.suppierk.codegen.util.Strings;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import nu.studer.gradle.jooq.JooqConfig;
import org.gradle.api.tasks.TaskAction;

/**
 * Task that runs Flyway migrations inside a temporary Testcontainers database without invoking
 * jOOQ. Useful for developers who want to validate migrations without generating sources.
 *
 * @see GeneratorTask
 * @see
 *     AbstractDatabaseTask#runFlywayMigrations(io.github.suppierk.codegen.docker.AbstractDatabaseContainer,
 *     String, java.net.URLClassLoader)
 */
public class FlywayMigrateTask extends AbstractDatabaseTask {
  private static final String GROUP_NAME = "codegen";
  private static final String TASK_NAME_TEMPLATE = "migrate%sDatabaseSchema";
  private static final String JOOQ_GENERATOR_MAIN_TASK_NAME = "main";

  /**
   * Creates a new Flyway-only task bound to the provided jOOQ configuration.
   *
   * @param databaseExtension database specification from the DSL
   * @param flywayConfig schema-specific Flyway configuration
   * @param jooqConfig associated jOOQ configuration
   */
  @Inject
  public FlywayMigrateTask(
      @Nonnull DatabaseExtension databaseExtension,
      @Nonnull FlywayConfig flywayConfig,
      @Nonnull JooqConfig jooqConfig) {
    super(databaseExtension, flywayConfig, jooqConfig);

    setGroup(GROUP_NAME);
  }

  /**
   * Derives a predictable task name from the supplied jOOQ configuration.
   *
   * @param jooqConfig configuration to inspect
   * @return Gradle task name exposing Flyway migrations for the configuration
   */
  public static @Nonnull String createTaskName(@Nonnull JooqConfig jooqConfig) {
    final var name = GeneratorTask.getJooqConfigName(jooqConfig);
    return JOOQ_GENERATOR_MAIN_TASK_NAME.equalsIgnoreCase(name)
        ? String.format(TASK_NAME_TEMPLATE, "")
        : String.format(TASK_NAME_TEMPLATE, Strings.capitalizeFirstLetter(name));
  }

  /**
   * Runs Flyway migrations against the configured container using the same driver resolution logic
   * as {@link GeneratorTask}, but without invoking jOOQ.
   *
   * @see GeneratorTask#run()
   */
  @TaskAction
  public void run() {
    final var jdbcConfiguration = jooqConfig.getJooqConfiguration().getJdbc();
    final var generatorDatabase = jooqConfig.getJooqConfiguration().getGenerator().getDatabase();

    final var dbDriverClassName = resolveDriver(jdbcConfiguration.getDriver());
    if (dbDriverClassName.isBlank()) {
      throw new IllegalStateException(
          "Database driver class must be provided to run Flyway migrations");
    }

    final var dbSchema = resolveSchema(generatorDatabase.getInputSchema());

    withDatabase(
        dbDriverClassName,
        (database, classLoader) -> runFlywayMigrations(database, dbSchema, classLoader));
  }
}
