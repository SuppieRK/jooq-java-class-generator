# Contributing

When contributing to this repository, please first discuss the change you wish to make via issue,
email, or any other method with the owners of this repository before making a change.

Please note we have a code of conduct, please follow it in all your interactions with the project.

## Pull Request Process

1. Ensure any installation or build dependencies are removed before the end of the layer when doing a
   build.
2. Update the `README.md` with details of changes to the interface, this includes new environment
   variables, exposed ports, useful file locations and container parameters.
3. Increase the version numbers in any example files and the `README.md` to the new version that this
   Pull Request would represent. The versioning scheme we use is [SemVer](http://semver.org/).
4. You may merge the Pull Request in once you have the sign-off of at least one other developer.

## Reference Documentation

For further reference, please consider the following sections:
- [Flyway official Gradle plugin documentation](https://documentation.red-gate.com/fd/gradle-task-184127407.html)
- [jOOQ code generation plugin](https://github.com/etiennestuder/gradle-jooq-plugin)

### Extending the plugin by adding your database

1. You will need to add a [Testcontainers](https://java.testcontainers.org/modules/databases/)-based implementation of your database container:
   - Your class needs to be in `io.github.suppierk.codegen.docker` package.
   - Your class has to implement `io.github.suppierk.codegen.docker.DatabaseContainer` interface.
   - **RECOMMENDED**: take a look at `io.github.suppierk.codegen.docker.PostgreSQL` implementation.
2. You should add your new class to `io.github.suppierk.codegen.GeneratorExtension`:
   - Add constant with default Docker image tag.
   - Add the way to get your database container by database driver name.
3. Update the artifact version **ONLY** in `plugin/build.gradle`.
4. Update `README.md` configuration section to reference a new option to set Docker image for your database.

### Build tools

The command typically used to build the project is:

```shell
./gradlew clean :plugin:clean
./gradlew spotlessApply :plugin:build build
```

* [Official Gradle documentation](https://docs.gradle.org)