# jOOQ Java Class Generator

[![Build status](https://github.com/SuppieRK/jooq-java-class-generator/actions/workflows/build.yml/badge.svg)](https://github.com/SuppieRK/jooq-java-class-generator/actions/workflows/build.yml)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.suppierk.jooq-java-class-generator.svg?label=Gradle%20Plugin)](https://plugins.gradle.org/plugin/io.github.suppierk.jooq-java-class-generator)
[![Latest release](https://img.shields.io/github/v/release/SuppieRK/jooq-java-class-generator.svg?display_name=tag)](https://github.com/SuppieRK/jooq-java-class-generator/releases)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FSuppieRK%2Fjooq-java-class-generator.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2FSuppieRK%2Fjooq-java-class-generator?ref=badge_shield)

> This project is a personal endeavor and is **not affiliated with my employer**.  
> I maintain it independently in my **spare time**, using my **personal equipment**.

---

## Why use this plugin?

Manually managing ephemeral databases, Flyway migrations, and jOOQ code generation slows down local development and CI
builds.  
**jOOQ Java Class Generator** automates all of it — spin up containers, run migrations, and generate type-safe SQL
classes in a single Gradle task.

---

## Highlights

- Spins up **temporary Testcontainers databases** during the build
- Runs **Flyway migrations** using the Flyway API
- Triggers **jOOQ code generation** — all in one step
- Provides a **dedicated Gradle DSL (`jooqCodegen`)** for configuration
- Tasks are **cacheable** — Gradle marks them `UP-TO-DATE` when inputs remain unchanged
- Compatible with both **Groovy and Kotlin DSLs**

---

## Requirements

| Tool       | Minimum version | Notes                                                                                                         |
|------------|-----------------|---------------------------------------------------------------------------------------------------------------|
| Java       | 21+             | Required by [gradle-jooq-plugin 10.0](https://github.com/etiennestuder/gradle-jooq-plugin/releases/tag/v10.0) |
| jOOQ       | 3.20.3+         | Matches [gradle-jooq-plugin 10.1](https://github.com/etiennestuder/gradle-jooq-plugin/releases/tag/v10.1)     |
| Gradle     | 8.6+            | Same as gradle-jooq 10.0                                                                                      |
| PostgreSQL | 17+             | As per [jOOQ OSS support matrix](https://www.jooq.org/download/support-matrix#PostgreSQL)                     |

---

## Installation

### Using plugins DSL

**Groovy DSL**

```groovy
plugins {
    id 'io.github.suppierk.jooq-java-class-generator' version '3.0.0'
}
```

### Using legacy plugin application

```groovy
buildscript {
    repositories {
        maven { url 'https://plugins.gradle.org/m2/' }
    }
    dependencies {
        classpath 'io.github.suppierk:plugin:3.0.0'
    }
}

apply plugin: 'io.github.suppierk.jooq-java-class-generator'
```

---

## Example Project

See [`example-project`](./example-project) for:

- Flyway migration for PostgreSQL
- Groovy DSL usage

---

## Full Configuration DSL

Declare temporary databases and schemas under the `jooqCodegen` extension. Configure the schema in
at least one place (`generator.database.inputSchema` via jOOQ or `flyway { defaultSchema = ... }`
in the DSL) so the plugin knows which schema to migrate and introspect. Setting both keeps Flyway
and jOOQ in sync.

**Groovy DSL**

```groovy
jooqCodegen {
    database("analyticsDb") {
        driver = "org.postgresql.Driver" // required

        container {
            image = "postgres:17-alpine" // optional override
        }

        schema("public") {
            flyway {
                locations = "classpath:db/migration/public"
                defaultSchema = "public"
                cleanDisabled = true
                placeholder "foo", "bar"
            }
            jooqConfigurations = ['pgPublic']
        }

        schema("audit") {
            flyway {
                locations = "classpath:db/migration/audit"
                defaultSchema = "audit"
            }
            jooqConfigurations = ['pgAudit']
        }
    }
}
```

<details>
<summary>Kotlin DSL</summary>

```kotlin
jooqCodegen {
    database("analyticsDb") {
        driver = "org.postgresql.Driver"

        container {
            image().set("postgres:17-alpine")
        }

        schemas.create("public") {
            flyway {
                locations = listOf("classpath:db/migration/public")
                defaultSchema = "public"
                cleanDisabled = true
                placeholders = mapOf("foo" to "bar")
            }
            jooqConfigurations = listOf("pgPublic")
        }

        schemas.create("audit") {
            flyway {
                locations = listOf("classpath:db/migration/audit")
                defaultSchema = "audit"
            }
            jooqConfigurations = listOf("pgAudit")
        }
    }
}
```

</details>

---

### Key Properties

| Scope      | Property              | Description                                                                                   |
|------------|-----------------------|-----------------------------------------------------------------------------------------------|
| `database` | `driver` *(required)* | JDBC driver class used to determine the Testcontainers implementation                         |
| `database` | `container.image`     | Optional Docker image override (defaults: PostgreSQL `postgres:17-alpine`, MySQL `mysql:8.4`) |
| `schema`   | `flyway { ... }`      | Full Flyway configuration for that schema; overrides built-in defaults provided by the plugin |
| `schema`   | `jooqConfigurations`  | Names of pre-existing jOOQ configurations to execute against the temporary DB                 |

---

### Generated Tasks

Each schema registers its own Gradle task, and an aggregate task coordinates them all.

| Task                                         | Description                          |
|----------------------------------------------|--------------------------------------|
| `generate<ConfigurationName>DatabaseClasses` | Runs Flyway and jOOQ for that schema |
| `generateDatabaseClasses`                    | Runs all schema tasks                |

These tasks depend on `processResources`, run before `compileJava`, and are included in `sourcesJar`.

---

## Caching Behavior

Tasks are annotated with `@CacheableTask`. Gradle considers them `UP-TO-DATE` when the following remain unchanged:

- Effective Flyway configuration (schemas, locations, placeholders, etc.)
- Selected Testcontainers image
- Normalized jOOQ configuration hash
- Resolved migration directories

Any DSL or migration change invalidates the cache. This makes the plugin **CI-friendly and highly incremental**.

---

## Integration Notes

The plugin:

- Applies [`nu.studer.gradle-jooq`](https://github.com/etiennestuder/gradle-jooq-plugin)
- Reuses Flyway’s configuration model **without** applying the official Flyway Gradle plugin

For deeper tuning, see:

- [nu.studer gradle-jooq configuration](https://github.com/etiennestuder/gradle-jooq-plugin?tab=readme-ov-file#configuration)

---

## Supported Databases

| Database   | Supported | Default Image        |
|------------|-----------|----------------------|
| PostgreSQL | ✅         | `postgres:17-alpine` |
| MySQL      | ✅         | `mysql:8.4`          |

---

## Troubleshooting

- Ensure your `jooqConfigurations` match the names declared under `jooq.configurations`.
- You can inspect Flyway configurations with `--info`.
- To debug migrations or container startup, run Gradle with `--stacktrace --debug`.

---

## License

Licensed under [MIT License](./LICENSE).  
Dependency licenses managed
via [FOSSA](https://app.fossa.com/projects/git%2Bgithub.com%2FSuppieRK%2Fjooq-java-class-generator?ref=badge_shield).
