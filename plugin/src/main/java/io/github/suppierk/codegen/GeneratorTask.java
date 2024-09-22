package io.github.suppierk.codegen;

import io.github.suppierk.codegen.docker.DatabaseContainer;
import io.github.suppierk.codegen.util.Strings;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import nu.studer.gradle.jooq.JooqConfig;
import nu.studer.gradle.jooq.JooqGenerate;
import org.flywaydb.gradle.FlywayExtension;
import org.flywaydb.gradle.task.FlywayMigrateTask;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

/**
 * Represents the general workflow of the plugin, invoking functionality from specific extensions as
 * required.
 */
public class GeneratorTask extends DefaultTask {
  private static final String GENERATOR_GROUP_NAME = "codegen";

  private static final String GENERATOR_TASK_NAME_TEMPLATE = "generate%sDatabaseClasses";

  private static final String JOOQ_GENERATOR_TASK_NAME_TEMPLATE = "generate%sJooq";
  private static final String JOOQ_GENERATOR_MAIN_TASK_NAME = "main";

  private static final String DEFAULT_DB_SCHEMA_NAME = "public";

  @Nonnull private final GeneratorExtension generatorExtension;

  @Nonnull private final FlywayExtension flywayExtension;
  @Nonnull private final JooqConfig jooqConfig;

  @Nonnull private final FlywayMigrateTask flywayMigrateTask;
  @Nonnull private final JooqGenerate jooqGenerateTask;

  /**
   * Constructor
   *
   * @param generatorExtension to create Docker image containers
   * @param flywayExtension to alter database migration properties with Docker container properties
   * @param jooqConfig to alter code generation properties with Docker container properties
   * @param flywayMigrateTask to invoke database migration scripts against Docker container
   * @param jooqGenerateTask to invoke code generation against Docker container
   */
  @Inject
  public GeneratorTask(
      @Nonnull GeneratorExtension generatorExtension,
      @Nonnull FlywayExtension flywayExtension,
      @Nonnull JooqConfig jooqConfig,
      @Nonnull FlywayMigrateTask flywayMigrateTask,
      @Nonnull JooqGenerate jooqGenerateTask) {
    setGroup(GENERATOR_GROUP_NAME);

    this.generatorExtension = generatorExtension;

    this.flywayExtension = flywayExtension;
    this.jooqConfig = jooqConfig;

    this.flywayMigrateTask = flywayMigrateTask;
    this.jooqGenerateTask = jooqGenerateTask;
  }

  /**
   * Returns task name for the current {@link GeneratorTask} based off available {@link JooqConfig}.
   *
   * <p>There can be many {@link JooqConfig}s, but only one {@link FlywayExtension}
   *
   * @param jooqConfig to fetch the name from
   * @return new task name for the current {@link GeneratorTask}
   * @throws IllegalStateException in case if we cannot access the {@link JooqConfig}{@code #name}
   *     field
   */
  public static @Nonnull String createTaskName(@Nonnull JooqConfig jooqConfig)
      throws IllegalStateException {
    final var name = getJooqConfigName(jooqConfig);
    return JOOQ_GENERATOR_MAIN_TASK_NAME.equalsIgnoreCase(name)
        ? String.format(GENERATOR_TASK_NAME_TEMPLATE, Strings.EMPTY)
        : String.format(
            GENERATOR_TASK_NAME_TEMPLATE,
            Strings.capitalizeFirstLetter(getJooqConfigName(jooqConfig)));
  }

  /**
   * Returns expected jOOQ {@link JooqGenerate} task name we want to invoke as a part of the {@link
   * GeneratorTask}.
   *
   * <p>Copies the naming logic from {@link nu.studer.gradle.jooq.JooqPlugin}.
   *
   * @param jooqConfig to fetch the name from
   * @return expected {@link JooqGenerate} task name
   * @throws IllegalStateException in case if we cannot access the {@link JooqConfig}{@code #name}
   *     field
   */
  public static @Nonnull String assumeJooqGenerateTaskName(@Nonnull JooqConfig jooqConfig)
      throws IllegalStateException {
    final var name = getJooqConfigName(jooqConfig);
    return JOOQ_GENERATOR_MAIN_TASK_NAME.equalsIgnoreCase(name)
        ? String.format(JOOQ_GENERATOR_TASK_NAME_TEMPLATE, Strings.EMPTY)
        : String.format(JOOQ_GENERATOR_TASK_NAME_TEMPLATE, Strings.capitalizeFirstLetter(name));
  }

  /**
   * Returns {@link JooqConfig} task name via reflection, as the {@link JooqConfig}{@code #name}
   * field has package-private access and {@link org.gradle.api.NamedDomainObjectContainer} within
   * {@link nu.studer.gradle.jooq.JooqExtension} does not provide the ability to peek into task
   * names.
   *
   * @param jooqConfig to fetch the name from
   * @return the value of the {@link JooqConfig}{@code #name} field
   * @throws IllegalStateException in case if we cannot access the {@link JooqConfig}{@code #name}
   *     field
   */
  private static @Nonnull String getJooqConfigName(@Nonnull JooqConfig jooqConfig)
      throws IllegalStateException {
    try {
      final var field = JooqConfig.class.getDeclaredField("name");
      field.setAccessible(true);
      return (String) field.get(jooqConfig);
    } catch (Exception e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  /**
   * Performs the following actions in order:
   *
   * <ul>
   *   <li>Creates defensive property copies of the external tasks
   *   <li>Launches Docker image container with the database instance of choice using Testcontainer
   *   <li>Invokes modified {@link FlywayMigrateTask}
   *   <li>Invokes modified {@link JooqGenerate}
   *   <li>Restores back original properties of the external tasks
   * </ul>
   *
   * <p><b>NOTE</b>: we also alter {@link JooqConfig} to explicitly exclude anything related to
   * Flyway by appending {@code flyway.*} regular expression to jOOQ exclusion ruleset.
   */
  @TaskAction
  public void run() {
    // Flyway property defensive copies - to be restored back after main execution flow
    final var flywayDbDriver = flywayMigrateTask.driver;
    final var flywayDbUrl = flywayMigrateTask.url;
    final var flywayDbUser = flywayMigrateTask.user;
    final var flywayDbPassword = flywayMigrateTask.password;
    final var flywayDbDefaultSchema = flywayMigrateTask.defaultSchema;

    // jOOQ property defensive copies - to be restored back after main execution flow
    final var jooqDbDriver = jooqConfig.getJooqConfiguration().getJdbc().getDriver();
    final var jooqDbUrl = jooqConfig.getJooqConfiguration().getJdbc().getUrl();
    final var jooqDbUser = jooqConfig.getJooqConfiguration().getJdbc().getUser();
    final var jooqDbPassword = jooqConfig.getJooqConfiguration().getJdbc().getPassword();
    final var jooqDbDefaultSchema =
        jooqConfig.getJooqConfiguration().getGenerator().getDatabase().getInputSchema();
    final var jooqExcludes =
        jooqConfig.getJooqConfiguration().getGenerator().getDatabase().getExcludes();

    // Main flow
    final var dbDriverClassName =
        Strings.firstNonBlank(flywayExtension.driver, flywayDbDriver, jooqDbDriver);
    final var dbSchema =
        Strings.firstNonBlank(
            flywayExtension.defaultSchema,
            flywayDbDefaultSchema,
            jooqDbDefaultSchema,
            DEFAULT_DB_SCHEMA_NAME);
    final var excludes =
        jooqExcludes == null || jooqExcludes.isBlank() ? "flyway.*" : jooqExcludes + " | flyway.*";

    try (DatabaseContainer db = generatorExtension.getDatabaseContainer(dbDriverClassName)) {
      // Setting up Flyway and kicking off the standard Flyway migration task
      flywayMigrateTask.driver = db.getDriverClassName();
      flywayMigrateTask.url = db.getJdbcUrl();
      flywayMigrateTask.user = db.getUsername();
      flywayMigrateTask.password = db.getPassword();
      flywayMigrateTask.defaultSchema = dbSchema;

      flywayMigrateTask.runTask();

      // After this task is finished, run jOOQ code generation
      jooqConfig.getJooqConfiguration().getJdbc().setDriver(db.getDriverClassName());
      jooqConfig.getJooqConfiguration().getJdbc().setUrl(db.getJdbcUrl());
      jooqConfig.getJooqConfiguration().getJdbc().setUser(db.getUsername());
      jooqConfig.getJooqConfiguration().getJdbc().setPassword(db.getPassword());
      jooqConfig.getJooqConfiguration().getGenerator().getDatabase().setInputSchema(dbSchema);
      jooqConfig.getJooqConfiguration().getGenerator().getDatabase().setExcludes(excludes);

      jooqGenerateTask.generate();

      // Disable jOOQ task after executing it manually to avoid repetition
      // Otherwise this task will be executed twice, and it will fail the second time
      jooqGenerateTask.setEnabled(false);
    } catch (Exception e) {
      throw new IllegalStateException(e.getMessage(), e);
    } finally {
      // Restoring old values back
      flywayMigrateTask.driver = flywayDbDriver;
      flywayMigrateTask.url = flywayDbUrl;
      flywayMigrateTask.user = flywayDbUser;
      flywayMigrateTask.password = flywayDbPassword;
      flywayMigrateTask.defaultSchema = flywayDbDefaultSchema;

      jooqConfig.getJooqConfiguration().getJdbc().setDriver(jooqDbDriver);
      jooqConfig.getJooqConfiguration().getJdbc().setUrl(jooqDbUrl);
      jooqConfig.getJooqConfiguration().getJdbc().setUser(jooqDbUser);
      jooqConfig.getJooqConfiguration().getJdbc().setPassword(jooqDbPassword);
      jooqConfig
          .getJooqConfiguration()
          .getGenerator()
          .getDatabase()
          .setInputSchema(jooqDbDefaultSchema);
      jooqConfig.getJooqConfiguration().getGenerator().getDatabase().setExcludes(jooqExcludes);
    }
  }
}
