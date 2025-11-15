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

package io.github.suppierk.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.suppierk.codegen.extensions.DatabaseExtension;
import io.github.suppierk.codegen.extensions.GeneratorExtension;
import io.github.suppierk.codegen.extensions.SchemaExtension;
import java.util.List;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GeneratorExtensionTest {
  private GeneratorExtension extension;

  @BeforeEach
  void setUp() {
    final var project = ProjectBuilder.builder().build();
    extension =
        project
            .getExtensions()
            .create(
                GeneratorExtension.EXTENSION_NAME,
                GeneratorExtension.class,
                project.getObjects(),
                project.getLogger());
  }

  @Test
  void createBlocksPopulateDatabasesAndSchemas() {
    extension.databases(
        databases ->
            databases.create(
                "tempDatabase",
                database -> {
                  database.getDriver().set("org.postgresql.Driver");
                  database.schemas(
                      schemas ->
                          schemas.create(
                              "public",
                              schema -> {
                                schema.getFlyway().setDefaultSchema("public");
                                schema
                                    .getFlyway()
                                    .setLocations(List.of("classpath:db/migration/public"));
                                schema.jooqConfigurations("main");
                              }));
                }));

    final DatabaseExtension database = extension.getDatabases().getByName("tempDatabase");
    assertEquals("tempDatabase", database.getName());
    assertEquals("org.postgresql.Driver", database.getDriver().get());

    final SchemaExtension schema = database.getSchemas().getByName("public");
    assertEquals("public", schema.getName());
    assertEquals(List.of("main"), schema.getJooqConfigurations().get());
    assertEquals("public", schema.getFlyway().getDefaultSchema());
    assertEquals(List.of("classpath:db/migration/public"), schema.getFlyway().getLocations());
  }

  @Test
  void databaseMethodDelegatesToContainerCreate() {
    extension.database(
        "reportsDb",
        database -> {
          database.driver("com.mysql.cj.jdbc.Driver");
          database.schema("core", schema -> schema.jooqConfigurations("reports"));
        });

    final DatabaseExtension database = extension.getDatabases().getByName("reportsDb");
    assertEquals("reportsDb", database.getName());
    assertEquals("com.mysql.cj.jdbc.Driver", database.getDriver().get());

    final SchemaExtension schema = database.getSchemas().getByName("core");
    assertEquals(List.of("reports"), schema.getJooqConfigurations().get());
  }

  @Test
  void containerImageOverridesAreCaptured() {
    extension.database(
        "postgresDb",
        database -> {
          database.driver("org.postgresql.Driver");
          database.container(container -> container.image("postgres:16-alpine"));
        });

    final DatabaseExtension database = extension.getDatabases().getByName("postgresDb");
    assertEquals("postgres:16-alpine", database.getContainer().image().get());
  }

  @Test
  void databaseInvocationReusesExistingSpec() {
    extension.database(
        "tempDb",
        db -> db.schema("public", schema -> schema.getJooqConfigurations().set(List.of("main"))));

    extension.database(
        "tempDb",
        db -> {
          db.getDriver().set("org.postgresql.Driver");
          db.schema(
              "public", schema -> schema.getJooqConfigurations().set(List.of("main", "extra")));
        });

    final DatabaseExtension database = extension.getDatabases().getByName("tempDb");
    assertEquals("tempDb", database.getName());
    assertEquals("org.postgresql.Driver", database.getDriver().get());

    final SchemaExtension schema = database.getSchemas().getByName("public");
    assertEquals(List.of("main", "extra"), schema.getJooqConfigurations().get());
  }
}
