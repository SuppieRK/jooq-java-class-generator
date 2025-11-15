package io.github.suppierk.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.suppierk.codegen.config.FlywayConfig;
import io.github.suppierk.codegen.extensions.DatabaseExtension;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import nu.studer.gradle.jooq.JooqConfig;
import nu.studer.gradle.jooq.JooqExtension;
import nu.studer.gradle.jooq.JooqPlugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class GeneratorTaskTest {
  @Test
  void getJooqConfigNameResolvesViaDetectedAccessor() {
    final Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply(JavaPlugin.class);
    project.getPlugins().apply(JooqPlugin.class);

    final JooqExtension extension = project.getExtensions().getByType(JooqExtension.class);
    final JooqConfig config = extension.getConfigurations().maybeCreate("main");

    assertEquals("main", GeneratorTask.getJooqConfigName(config));
  }

  @Test
  void resolveDriverThrowsMeaningfulExceptionWhenDriverIsMissing() {
    final StubDatabaseTask task = createStubTask();

    final IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> task.resolveDriverForTests(null),
            "Expected failure");
    assertEquals(
        "Database driver class must be declared. Set 'driver' in the jooqCodegen database DSL, "
            + "configure 'flyway { driver = ... }', or provide JDBC driver coordinates in the jOOQ configuration.",
        exception.getMessage());
  }

  @Test
  void resolveDriverPrefersDslOverride() {
    final StubDatabaseTask task = createStubTask();
    task.applyDslOverrides("tempDb", "public", "com.example.Driver");

    assertEquals("com.example.Driver", task.resolveDriverForTests(null));
  }

  @Test
  void resolveSchemaFallsBackToFlywaySchemasList() {
    final FlywayConfig overrides = new FlywayConfig();
    overrides.setSchemas(List.of("analytics"));
    final StubDatabaseTask task = createStubTask(overrides);
    task.applyDslOverrides("tempDb", "analytics", null);

    assertEquals("analytics", task.resolveSchemaForTests(null));
  }

  @Test
  void resolveSchemaIgnoresCaseDifferences() {
    final FlywayConfig overrides = new FlywayConfig();
    overrides.setDefaultSchema("public");
    final StubDatabaseTask task = createStubTask(overrides);
    task.applyDslOverrides("tempDb", "public", null);

    assertEquals("public", task.resolveSchemaForTests("PUBLIC"));
  }

  private @Nonnull StubDatabaseTask createStubTask() {
    return createStubTask(new FlywayConfig());
  }

  private @Nonnull StubDatabaseTask createStubTask(@Nonnull FlywayConfig flywayConfig) {
    final Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply(JavaPlugin.class);
    project.getPlugins().apply(JooqPlugin.class);

    final JooqExtension extension = project.getExtensions().getByType(JooqExtension.class);
    final JooqConfig config = extension.getConfigurations().maybeCreate("main");
    final DatabaseExtension databaseExtension =
        new DatabaseExtension("tempDb", project.getObjects(), project.getLogger());

    return project
        .getTasks()
        .register("stubTask", StubDatabaseTask.class, databaseExtension, flywayConfig, config)
        .get();
  }

  public static class StubDatabaseTask extends AbstractDatabaseTask {
    @Inject
    public StubDatabaseTask(
        @Nonnull DatabaseExtension databaseExtension,
        @Nonnull FlywayConfig flywayConfig,
        @Nonnull JooqConfig jooqConfig) {
      super(databaseExtension, flywayConfig, jooqConfig);
    }

    @Nonnull
    String resolveDriverForTests(@Nullable String fallback) {
      return resolveDriver(fallback);
    }

    @Nonnull
    String resolveSchemaForTests(@Nullable String fallback) {
      return resolveSchema(fallback);
    }
  }
}
