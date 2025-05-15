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

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import nu.studer.gradle.jooq.JooqExtension;
import nu.studer.gradle.jooq.JooqPlugin;
import org.flywaydb.gradle.FlywayExtension;
import org.flywaydb.gradle.FlywayPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaBasePlugin;

/**
 * Registers all necessary dependencies and configurations.
 *
 * <p>An entry point to the plugin functionality.
 */
@SuppressWarnings("unused")
public class GeneratorPlugin implements Plugin<Project> {
  /**
   * @see nu.studer.gradle.jooq.JooqPlugin - name parts source
   */
  private static final String JOOQ_CONFIG_NAME_PREFIX = "generate";

  private static final String JOOQ_CONFIG_NAME_SUFFIX = "Jooq";

  /**
   * @see org.flywaydb.gradle.FlywayPlugin - name source
   */
  private static final String FLYWAY_MIGRATE_TASK_NAME = "flywayMigrate";

  private static final String COMPILE_JAVA_TASK_NAME = "compileJava";
  private static final String SOURCES_JAR_TASK_NAME = "sourcesJar";

  /** Default constructor */
  public GeneratorPlugin() {
    // No operations required
  }

  /**
   * Plugin entry point
   *
   * @param currentProject we are working with
   */
  @Override
  public void apply(@Nonnull Project currentProject) {
    // Registering external plugin dependencies
    currentProject.getPluginManager().apply(JavaBasePlugin.class);
    currentProject.getPluginManager().apply(FlywayPlugin.class);
    currentProject.getPluginManager().apply(JooqPlugin.class);

    // Extensions
    final var pluginExtension =
        currentProject
            .getExtensions()
            .create(GeneratorExtension.EXTENSION_NAME, GeneratorExtension.class);
    final var flywayExtension =
        Objects.requireNonNull(
            currentProject.getExtensions().getByType(FlywayExtension.class),
            "Cannot retrieve Flyway extension");
    final var jooqExtension =
        Objects.requireNonNull(
            currentProject.getExtensions().getByType(JooqExtension.class),
            "Cannot retrieve jOOQ extension");

    // Tasks
    final var flywayMigrateTask =
        getTaskByNameSafely(currentProject, FLYWAY_MIGRATE_TASK_NAME)
            .orElseThrow(() -> new IllegalStateException(FLYWAY_MIGRATE_TASK_NAME + " not found"));

    // Registering current plugin tasks
    currentProject.afterEvaluate(
        project ->
            jooqExtension
                .getConfigurations()
                .forEach(
                    jooqConfig -> {
                      final var jooqGenerateTaskName =
                          GeneratorTask.assumeJooqGenerateTaskName(jooqConfig);
                      final var jooqGenerateTask =
                          getTaskByNameSafely(project, jooqGenerateTaskName)
                              .orElseThrow(
                                  () ->
                                      new IllegalStateException(
                                          jooqGenerateTaskName + " not found"));

                      final var generateDatabaseClassesTask =
                          project
                              .getTasks()
                              .register(
                                  GeneratorTask.createTaskName(jooqConfig),
                                  GeneratorTask.class,
                                  pluginExtension,
                                  flywayExtension,
                                  jooqConfig,
                                  flywayMigrateTask,
                                  jooqGenerateTask);

                      // Registering sensible task dependencies
                      getTaskByNameSafely(project, COMPILE_JAVA_TASK_NAME)
                          .ifPresent(task -> task.dependsOn(generateDatabaseClassesTask));
                      getTaskByNameSafely(project, SOURCES_JAR_TASK_NAME)
                          .ifPresent(task -> task.dependsOn(generateDatabaseClassesTask));
                    }));
  }

  /**
   * Returns a requested task safely as {@link Optional}, suppressing potential {@link
   * org.gradle.api.UnknownTaskException}.
   *
   * @param project to search for the task in
   * @param name of the task to search for
   * @return {@link Optional} with the task, if it is present
   */
  private @Nonnull Optional<Task> getTaskByNameSafely(
      @Nonnull Project project, @Nonnull String name) {
    try {
      return Optional.of(project.getTasks().getByName(name));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
