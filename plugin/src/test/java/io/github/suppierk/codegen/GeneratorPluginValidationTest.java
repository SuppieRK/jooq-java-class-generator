package io.github.suppierk.codegen;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.suppierk.codegen.extensions.GeneratorExtension;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import nu.studer.gradle.jooq.JooqExtension;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class GeneratorPluginValidationTest {

  @Test
  void failsWhenSchemaReferencesMissingJooqConfiguration() {
    final Project project =
        createProject(
            jooq -> registerConfig(jooq, "main"),
            extension ->
                configureDatabase(
                    extension,
                    schema -> schema.jooqConfigurations(List.of("main", "missingConfig"))));

    assertThrows(
        IllegalStateException.class, () -> executeVerificationTask(project), "Expected failure");
  }

  @Test
  void failsWhenJooqConfigurationIsReusedAcrossSchemas() {
    assertThrows(
        IllegalStateException.class,
        () ->
            createProject(
                jooq -> registerConfig(jooq, "main"),
                extension ->
                    configureDatabase(
                        extension,
                        schema -> schema.jooqConfigurations(List.of("main")),
                        schema -> schema.jooqConfigurations(List.of("main")))),
        "Expected failure");
  }

  @Test
  void failsWhenSchemaOmitsJooqConfigurations() {
    final Project project =
        createProject(
            jooq -> registerConfig(jooq, "main"),
            extension ->
                configureDatabase(
                    extension, schema -> schema.getJooqConfigurations().set(List.of())));

    assertThrows(
        IllegalStateException.class, () -> executeVerificationTask(project), "Expected failure");
  }

  private @Nonnull Project createProject(
      @Nonnull Consumer<JooqExtension> jooqConfigurer,
      @Nonnull Consumer<GeneratorExtension> dslConfigurer) {
    final Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply(GeneratorPlugin.class);
    final JooqExtension jooq = project.getExtensions().getByType(JooqExtension.class);
    jooqConfigurer.accept(jooq);
    final GeneratorExtension generatorExtension =
        project.getExtensions().getByType(GeneratorExtension.class);
    dslConfigurer.accept(generatorExtension);
    return project;
  }

  @SafeVarargs
  private void configureDatabase(
      @Nonnull GeneratorExtension extension,
      @Nonnull
          Consumer<io.github.suppierk.codegen.extensions.SchemaExtension>... schemaConfigurators) {
    extension.database(
        "postgresDb",
        database -> {
          database.driver("org.postgresql.Driver");
          for (int i = 0; i < schemaConfigurators.length; i++) {
            final String schemaName = schemaConfigurators.length == 1 ? "public" : "schema" + i;
            final Consumer<io.github.suppierk.codegen.extensions.SchemaExtension> schemaConsumer =
                schemaConfigurators[i];
            database.schema(
                schemaName,
                schema -> {
                  schema.getFlyway().setDefaultSchema(schemaName);
                  schema.getFlyway().setLocations(List.of("classpath:db/migration/" + schemaName));
                  schemaConsumer.accept(schema);
                });
          }
        });
  }

  private void registerConfig(@Nonnull JooqExtension extension, @Nonnull String name) {
    extension.getConfigurations().maybeCreate(name);
  }

  private void executeVerificationTask(@Nonnull Project project) {
    final Task task = project.getTasks().getByName("verifyJooqCodegenConfiguration");
    for (Action<? super Task> action : task.getActions()) {
      action.execute(task);
    }
  }
}
