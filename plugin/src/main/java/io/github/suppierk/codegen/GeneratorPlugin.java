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

import io.github.suppierk.codegen.config.FlywayConfig;
import io.github.suppierk.codegen.extensions.DatabaseExtension;
import io.github.suppierk.codegen.extensions.GeneratorExtension;
import io.github.suppierk.codegen.extensions.SchemaExtension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import nu.studer.gradle.jooq.JooqConfig;
import nu.studer.gradle.jooq.JooqExtension;
import nu.studer.gradle.jooq.JooqGenerate;
import nu.studer.gradle.jooq.JooqPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Registers the Gradle plugin that coordinates Flyway migrations, Testcontainers databases and jOOQ
 * generation.
 *
 * <p>The plugin wires a custom DSL via {@link GeneratorExtension}, validates the declared schemas
 * and jOOQ configurations, and replaces the default {@link JooqGenerate} tasks with {@link
 * GeneratorTask} / {@link FlywayMigrateTask} pairs.
 *
 * @see #apply(Project)
 * @see GeneratorTask
 * @see FlywayMigrateTask
 */
@SuppressWarnings("unused")
public class GeneratorPlugin implements Plugin<Project> {
  private static final String COMPILE_JAVA_TASK_NAME = "compileJava";
  private static final String SOURCES_JAR_TASK_NAME = "sourcesJar";
  private static final String AGGREGATE_TASK_NAME = "generateDatabaseClasses";

  /** Default constructor */
  public GeneratorPlugin() {
    // No operations required
  }

  /**
   * Entry point that applies prerequisite plugins, exposes the {@code jooqCodegen} DSL, registers
   * the aggregate tasks and ensures every relevant Java / Kotlin task depends on the generated
   * sources.
   *
   * @param currentProject target Gradle project
   * @see GeneratorExtension
   */
  @Override
  public void apply(@Nonnull Project currentProject) {
    // Registering external plugin dependencies
    currentProject.getPluginManager().apply(JavaPlugin.class);
    currentProject.getPluginManager().apply(JooqPlugin.class);

    // Extensions
    final GeneratorExtension codegenExtension =
        currentProject
            .getExtensions()
            .create(
                GeneratorExtension.EXTENSION_NAME,
                GeneratorExtension.class,
                currentProject.getObjects(),
                currentProject.getLogger());
    final var jooqExtension =
        Objects.requireNonNull(
            currentProject.getExtensions().getByType(JooqExtension.class),
            "Cannot retrieve jOOQ extension");

    final TaskProvider<Task> aggregateTask =
        currentProject
            .getTasks()
            .register(
                AGGREGATE_TASK_NAME,
                task -> {
                  task.setGroup("codegen");
                  task.setDescription(
                      "Executes all configured database code generation tasks provided by the plugin.");
                  task.setDependsOn(Collections.emptyList());
                });

    registerDslBackedTasks(currentProject, codegenExtension, jooqExtension, aggregateTask);
  }

  /**
   * Processes the configured DSL databases and registers the corresponding Gradle tasks. Tracks the
   * relationship between schemas and jOOQ configurations so that tasks are only materialised when
   * both sides are available.
   *
   * @see #handleSchemaConfigurationUpdate(SchemaExtension, DatabaseExtension, String, List, Set,
   *     Map, Map, Set, Map, Map, java.util.function.Consumer)
   */
  private void registerDslBackedTasks(
      @Nonnull Project project,
      @Nonnull GeneratorExtension codegenExtension,
      @Nonnull JooqExtension jooqExtension,
      @Nonnull TaskProvider<Task> aggregateTask) {
    final Map<String, SchemaContext> schemaContextsByConfig = new LinkedHashMap<>();
    final Map<String, JooqConfig> jooqConfigsByName = new LinkedHashMap<>();
    final Map<SchemaExtension, Set<String>> schemaConfigAssignments = new IdentityHashMap<>();
    final Map<SchemaExtension, String> schemaDatabaseNames = new IdentityHashMap<>();
    final Set<String> registeredConfigurations = new LinkedHashSet<>();
    final Map<String, TaskProvider<GeneratorTask>> generatorTasksByConfig = new LinkedHashMap<>();
    final Map<String, TaskProvider<FlywayMigrateTask>> flywayTasksByConfig = new LinkedHashMap<>();

    final TaskProvider<Task> validationTask =
        registerConfigurationValidationTask(
            project,
            schemaContextsByConfig,
            jooqConfigsByName,
            schemaConfigAssignments,
            schemaDatabaseNames,
            registeredConfigurations);

    aggregateTask.configure(task -> task.dependsOn(validationTask));
    getTaskByNameSafely(project, COMPILE_JAVA_TASK_NAME)
        .ifPresent(task -> task.dependsOn(validationTask));
    getTaskByNameSafely(project, SOURCES_JAR_TASK_NAME)
        .ifPresent(task -> task.dependsOn(validationTask));

    project
        .getTasks()
        .matching(this::isKotlinCompileTask)
        .configureEach(task -> task.dependsOn(validationTask));
    project
        .getTasks()
        .matching(task -> isKotlinSourcesJarTask(task.getName()))
        .configureEach(task -> task.dependsOn(validationTask));

    final java.util.function.Consumer<String> attemptRegistration =
        configName -> {
          final SchemaContext schemaContext = schemaContextsByConfig.get(configName);
          final JooqConfig jooqConfig = jooqConfigsByName.get(configName);
          if (schemaContext == null || jooqConfig == null) {
            return;
          }
          if (!registeredConfigurations.add(configName)) {
            return;
          }
          registerDslTaskSet(
              project,
              aggregateTask,
              validationTask,
              generatorTasksByConfig,
              flywayTasksByConfig,
              schemaContext.databaseExtension(),
              jooqConfig,
              schemaContext);
        };

    codegenExtension
        .getDatabases()
        .configureEach(
            databaseExtension -> {
              final var databaseName = databaseExtension.getName();

              databaseExtension
                  .getSchemas()
                  .configureEach(
                      schemaExtension -> {
                        schemaDatabaseNames.put(schemaExtension, databaseName);
                        final Set<String> trackedConfigs =
                            schemaConfigAssignments.computeIfAbsent(
                                schemaExtension, ignored -> new LinkedHashSet<>());

                        schemaExtension.whenJooqConfigurationsUpdated(
                            configurationNames ->
                                handleSchemaConfigurationUpdate(
                                    schemaExtension,
                                    databaseExtension,
                                    databaseName,
                                    configurationNames,
                                    trackedConfigs,
                                    schemaContextsByConfig,
                                    schemaConfigAssignments,
                                    registeredConfigurations,
                                    generatorTasksByConfig,
                                    flywayTasksByConfig,
                                    attemptRegistration));
                      });
            });

    jooqExtension
        .getConfigurations()
        .configureEach(
            jooqConfig -> {
              final String configName = GeneratorTask.getJooqConfigName(jooqConfig);
              jooqConfigsByName.put(configName, jooqConfig);
              attemptRegistration.accept(configName);
            });
  }

  /**
   * Creates a task that fails the build when the DSL references jOOQ configurations that do not
   * exist or when schemas omit jOOQ configuration references entirely. The task is added as a
   * dependency to all downstream code generation tasks so that failures surface early.
   *
   * @return task provider for the validation task
   * @see #registerDslBackedTasks(Project, GeneratorExtension, JooqExtension, TaskProvider)
   */
  private @Nonnull TaskProvider<Task> registerConfigurationValidationTask(
      @Nonnull Project project,
      @Nonnull Map<String, SchemaContext> schemaContextsByConfig,
      @Nonnull Map<String, JooqConfig> jooqConfigsByName,
      @Nonnull Map<SchemaExtension, Set<String>> schemaConfigAssignments,
      @Nonnull Map<SchemaExtension, String> schemaDatabaseNames,
      @Nonnull Set<String> registeredConfigurations) {
    return project
        .getTasks()
        .register(
            "verifyJooqCodegenConfiguration",
            task -> {
              task.setGroup("codegen");
              task.setDescription(
                  "Validates that the jooqCodegen DSL references existing jOOQ configurations.");
              task.onlyIf(
                  t -> {
                    if (schemaConfigAssignments.isEmpty()) {
                      return true;
                    }
                    if (registeredConfigurations.isEmpty()) {
                      return true;
                    }
                    final boolean hasSchemaWarnings =
                        schemaConfigAssignments.values().stream().anyMatch(Set::isEmpty);
                    if (hasSchemaWarnings) {
                      return true;
                    }
                    final var unresolved = new LinkedHashSet<>(schemaContextsByConfig.keySet());
                    unresolved.removeAll(jooqConfigsByName.keySet());
                    return !unresolved.isEmpty();
                  });
              task.doLast(
                  unused -> {
                    if (schemaConfigAssignments.isEmpty()) {
                      throw new IllegalStateException(
                          "The jooqCodegen DSL must declare at least one database. Configure the extension before applying the plugin tasks.");
                    }

                    schemaConfigAssignments.forEach(
                        (schema, configs) -> {
                          if (!configs.isEmpty()) {
                            return;
                          }
                          final String databaseName =
                              schemaDatabaseNames.getOrDefault(schema, "<unknown>");
                          throw new IllegalStateException(
                              "Schema '"
                                  + schema.getName()
                                  + "' in temporary database '"
                                  + databaseName
                                  + "' does not reference any jOOQ configurations.");
                        });

                    final var unresolved = new LinkedHashSet<>(schemaContextsByConfig.keySet());
                    unresolved.removeAll(jooqConfigsByName.keySet());

                    if (!unresolved.isEmpty()) {
                      final String missing = unresolved.getFirst();
                      final SchemaContext context = schemaContextsByConfig.get(missing);
                      final String message = getMissingSchemaContextMessage(context, missing);
                      throw new IllegalStateException(message);
                    }

                    if (registeredConfigurations.isEmpty()) {
                      throw new IllegalStateException(
                          "No jOOQ configurations matched the jooqCodegen DSL entries. Ensure each schema references an existing jOOQ configuration and declares a driver.");
                    }
                  });
            });
  }

  private static @NotNull String getMissingSchemaContextMessage(
      SchemaContext context, String missing) {
    final String message;
    if (context != null) {
      message =
          "Schema '"
              + context.schemaName()
              + "' in temporary database '"
              + context.databaseName()
              + "' references missing jOOQ configuration '"
              + missing
              + "'.";
    } else {
      message = "jOOQ configuration '" + missing + "' was not found.";
    }
    return message;
  }

  /**
   * Handles updates to a schema's {@code jooqConfigurations} list. When new names appear, the
   * method records the schema context and attempts task registration. When names disappear, the
   * associated tasks are disabled to keep task graphs valid.
   *
   * @see SchemaExtension#whenJooqConfigurationsUpdated(org.gradle.api.Action)
   */
  private void handleSchemaConfigurationUpdate(
      @Nonnull SchemaExtension schemaExtension,
      @Nonnull DatabaseExtension databaseExtension,
      @Nonnull String databaseName,
      @Nonnull List<String> configurationNames,
      @Nonnull Set<String> trackedConfigs,
      @Nonnull Map<String, SchemaContext> schemaContextsByConfig,
      @Nonnull Map<SchemaExtension, Set<String>> schemaConfigAssignments,
      @Nonnull Set<String> registeredConfigurations,
      @Nonnull Map<String, TaskProvider<GeneratorTask>> generatorTasksByConfig,
      @Nonnull Map<String, TaskProvider<FlywayMigrateTask>> flywayTasksByConfig,
      @Nonnull java.util.function.Consumer<String> attemptRegistration) {
    final Set<String> updatedConfigs = new LinkedHashSet<>(configurationNames);

    final List<String> orphaned = new ArrayList<>();
    for (String configName : trackedConfigs) {
      if (!updatedConfigs.contains(configName)) {
        schemaContextsByConfig.remove(configName);
        registeredConfigurations.remove(configName);
        disableTask(generatorTasksByConfig.remove(configName));
        disableTask(flywayTasksByConfig.remove(configName));
        orphaned.add(configName);
      }
    }
    orphaned.forEach(trackedConfigs::remove);

    if (updatedConfigs.isEmpty()) {
      schemaConfigAssignments.put(schemaExtension, trackedConfigs);
      return;
    }

    schemaConfigAssignments.put(schemaExtension, trackedConfigs);

    for (String configName : updatedConfigs) {
      if (trackedConfigs.contains(configName)) {
        continue;
      }
      if (schemaContextsByConfig.containsKey(configName)) {
        throw new IllegalStateException(
            "jOOQ configuration '"
                + configName
                + "' is referenced by multiple schemas in the jooqCodegen DSL.");
      }

      schemaContextsByConfig.put(
          configName,
          new SchemaContext(
              databaseName,
              schemaExtension.getName(),
              null,
              schemaExtension.getFlyway(),
              databaseExtension));
      trackedConfigs.add(configName);
      attemptRegistration.accept(configName);
    }
  }

  /**
   * Wires the custom Flyway / jOOQ tasks for a single schema. The method replaces the original
   * {@link JooqGenerate} task with a disabled stub and registers {@link GeneratorTask} / {@link
   * FlywayMigrateTask} instances that reuse the same configuration.
   *
   * @see GeneratorTask
   * @see FlywayMigrateTask
   */
  private void registerDslTaskSet(
      @Nonnull Project project,
      @Nonnull TaskProvider<Task> aggregateTask,
      @Nonnull TaskProvider<Task> validationTask,
      @Nonnull Map<String, TaskProvider<GeneratorTask>> generatorTasksByConfig,
      @Nonnull Map<String, TaskProvider<FlywayMigrateTask>> flywayTasksByConfig,
      @Nonnull DatabaseExtension databaseExtension,
      @Nonnull JooqConfig jooqConfig,
      @Nonnull SchemaContext schemaContext) {
    final var jooqGenerateTaskName = GeneratorTask.assumeJooqGenerateTaskName(jooqConfig);
    final TaskProvider<JooqGenerate> jooqGenerateTaskProvider =
        project.getTasks().named(jooqGenerateTaskName, JooqGenerate.class);
    jooqGenerateTaskProvider.configure(task -> task.setEnabled(false));

    final var generateDatabaseClassesTask =
        project
            .getTasks()
            .register(
                GeneratorTask.createTaskName(jooqConfig),
                GeneratorTask.class,
                databaseExtension,
                schemaContext.flywayOverrides(),
                jooqConfig,
                jooqGenerateTaskProvider);

    generateDatabaseClassesTask.configure(
        task -> {
          task.dependsOn(validationTask);
          getTaskByNameSafely(project, "processResources").ifPresent(task::dependsOn);
          task.applyDslOverrides(
              schemaContext.databaseName(),
              schemaContext.schemaName(),
              schemaContext.driverClassName());
          task.configureCaching();
        });

    final var flywayMigrateTask =
        project
            .getTasks()
            .register(
                FlywayMigrateTask.createTaskName(jooqConfig),
                FlywayMigrateTask.class,
                databaseExtension,
                schemaContext.flywayOverrides(),
                jooqConfig);

    flywayMigrateTask.configure(
        task -> {
          task.dependsOn(validationTask);
          task.setDescription(
              "Runs Flyway migrations against the temporary database used by jOOQ.");
          task.applyDslOverrides(
              schemaContext.databaseName(),
              schemaContext.schemaName(),
              schemaContext.driverClassName());
        });

    aggregateTask.configure(task -> task.dependsOn(generateDatabaseClassesTask));

    final String configName = GeneratorTask.getJooqConfigName(jooqConfig);
    generatorTasksByConfig.put(configName, generateDatabaseClassesTask);
    flywayTasksByConfig.put(configName, flywayMigrateTask);

    getTaskByNameSafely(project, COMPILE_JAVA_TASK_NAME)
        .ifPresent(task -> task.dependsOn(generateDatabaseClassesTask));
    getTaskByNameSafely(project, SOURCES_JAR_TASK_NAME)
        .ifPresent(task -> task.dependsOn(generateDatabaseClassesTask));

    wireKotlinTasks(project, generateDatabaseClassesTask);
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
    } catch (UnknownTaskException e) {
      return Optional.empty();
    }
  }

  /**
   * Adds the generator task as a dependency to Kotlin compile- and sources-oriented tasks so that
   * Kotlin consumers see the generated sources without extra manual wiring.
   *
   * @see #isKotlinCompileTask(Task)
   * @see #isKotlinSourcesJarTask(String)
   */
  private void wireKotlinTasks(
      @Nonnull Project project, @Nonnull TaskProvider<GeneratorTask> generatorTask) {
    project
        .getTasks()
        .matching(this::isKotlinCompileTask)
        .configureEach(task -> task.dependsOn(generatorTask));

    project
        .getTasks()
        .matching(task -> isKotlinSourcesJarTask(task.getName()))
        .configureEach(task -> task.dependsOn(generatorTask));
  }

  /** Returns {@code true} when the task appears to be a Kotlin compile task. */
  private boolean isKotlinCompileTask(@Nonnull Task task) {
    final String className = task.getClass().getName();
    return className.startsWith("org.jetbrains.kotlin.gradle.tasks.")
        && className.contains("KotlinCompile");
  }

  /** Returns {@code true} for Kotlin sources jar tasks that should include generated code. */
  private boolean isKotlinSourcesJarTask(@Nonnull String taskName) {
    final String normalized = taskName.toLowerCase(Locale.ROOT);
    return normalized.contains("kotlinsourcesjar");
  }

  /**
   * Disables the supplied task provider if it is non-null. Used when schema configuration updates
   * break the association between a schema and a jOOQ configuration.
   *
   * @param taskProvider provider pointing to the task to disable
   * @see #handleSchemaConfigurationUpdate(SchemaExtension, DatabaseExtension, String, List, Set,
   *     Map, Map, Set, Map, Map, java.util.function.Consumer)
   */
  private void disableTask(@Nullable TaskProvider<? extends Task> taskProvider) {
    if (taskProvider == null) {
      return;
    }
    taskProvider.configure(task -> task.setEnabled(false));
  }

  /** Bundles schema-specific context required when configuring tasks. */
  private record SchemaContext(
      @Nonnull String databaseName,
      @Nonnull String schemaName,
      @Nullable String driverClassName,
      @Nonnull FlywayConfig flywayOverrides,
      @Nonnull DatabaseExtension databaseExtension) {}
}
