jOOQ Java Class Generator
=========================

> The work on this software project is in no way associated with my employer nor with the role I'm having at my
> employer.
>
> I maintain this project alone and as much or as little as my **spare time** permits.

# Overview

This Gradle plugin acts as a glue between two other great plugins available:

- [Flyway official Gradle plugin](https://plugins.gradle.org/plugin/org.flywaydb.flyway)
- [jOOQ code generation plugin](https://github.com/etiennestuder/gradle-jooq-plugin) made
  by [Etienne Studer](https://github.com/etiennestuder)

This plugin adds a small missing part between those two plugins: [Testcontainers](https://testcontainers.com/) to create
a database during the Gradle `build` stage:

- This allows running Flyway migration scripts against a real database instance.
- And limits the scope of jOOQ code generation to only those made by specific codebase.

# Usage

TO BE ADDED

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
    postgres = 'postgres:16-alpine'
}
```

> This configuration has been made to support jOOQ behavior when it comes to restricting code generation based on the
> database version or other dependencies within your project restricting your
> options, [see the following comment and GitHub issue](https://github.com/jOOQ/jOOQ/issues/12985#issuecomment-1030621355)