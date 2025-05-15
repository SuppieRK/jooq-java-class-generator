[jOOQ Java Class Generator](https://plugins.gradle.org/plugin/io.github.suppierk.jooq-java-class-generator)
=========================

> The work on this software project is in no way associated with my employer nor with the role I'm having at my
> employer.
>
> I maintain this project alone and as much or as little as my **spare time** permits using my **personal** equipment.

# Overview
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FSuppieRK%2Fjooq-java-class-generator.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2FSuppieRK%2Fjooq-java-class-generator?ref=badge_shield)


This Gradle plugin acts as a glue between (and **exposes**) two other great plugins available:

- [Flyway official Gradle plugin](https://plugins.gradle.org/plugin/org.flywaydb.flyway)
- [jOOQ code generation plugin](https://github.com/etiennestuder/gradle-jooq-plugin) made
  by [Etienne Studer](https://github.com/etiennestuder)

This plugin adds a small missing part between those two plugins: [Testcontainers](https://testcontainers.com/) to create
a database during the Gradle `build` stage:

- This allows running Flyway migration scripts against a real database instance.
- And limits the scope of jOOQ code generation to only those made by specific codebase.

# Usage

## Requirements

- Java 21 or above (as per [gradle-jooq-plugin 10.0 release notes](https://github.com/etiennestuder/gradle-jooq-plugin/releases/tag/v10.0))
- jOOQ 2.20.3 or above (as per [gradle-jooq-plugin 10.1 release notes](https://github.com/etiennestuder/gradle-jooq-plugin/releases/tag/v10.1))
- Gradle 8.6 or above (as per [gradle-jooq-plugin 10.0 release notes](https://github.com/etiennestuder/gradle-jooq-plugin/releases/tag/v10.0))
- PostgreSQL 17 or above (as per [jOOQ OSS support matrix](https://www.jooq.org/download/support-matrix#PostgreSQL))

## Using plugins DSL

```groovy
plugins {
  id 'io.github.suppierk.jooq-java-class-generator' version '2.0.0'
}
```

## Using legacy plugin application

```groovy
buildscript {
  repositories {
    maven {
      url 'https://plugins.gradle.org/m2/'
    }
  }
  dependencies {
    classpath 'io.github.suppierk:plugin:2.0.0'
  }
}

apply plugin: 'io.github.suppierk.jooq-java-class-generator'
```

> You can also check `example-project` in this repo

# Configuration

Because this plugin represents a glue between other plugins, please consult with their documentation first:

- [Flyway official Gradle plugin documentation](https://documentation.red-gate.com/fd/gradle-task-184127407.html)
- [jOOQ code generation plugin configuration](https://github.com/etiennestuder/gradle-jooq-plugin?tab=readme-ov-file#configuration)

This plugin will simply set certain configurations for those plugins during Gradle `build` stage.

The only configurable thing this plugin offers is to set a specific Docker image to be used by Testcontainers via:

```groovy
jooqDockerImages {
    // These are default values that can be overridden
    postgres = 'postgres:17-alpine'
}
```

> This configuration has been made to support jOOQ behavior when it comes to restricting code generation based on the
> database version or other dependencies within your project restricting your
> options, [see the following comment](https://github.com/jOOQ/jOOQ/issues/12985#issuecomment-1030621355)
>
> Arguably, this can also be automated based on [jOOQ version support matrix](https://www.jooq.org/download/support-matrix),
> however I do not want to take away the ability to define your own Docker image.


## License
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FSuppieRK%2Fjooq-java-class-generator.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2FSuppieRK%2Fjooq-java-class-generator?ref=badge_large)
