package io.github.suppierk.codegen;

import io.github.suppierk.codegen.docker.DatabaseContainer;
import io.github.suppierk.codegen.docker.PostgreSQL;
import io.github.suppierk.codegen.util.Strings;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Defines properties for the plugin.
 *
 * <p>The only properties we require are Docker image tags in case if user wants to override jOOQ
 * code generator version.
 *
 * @see <a
 *     href="https://github.com/etiennestuder/gradle-jooq-plugin/tree/main?tab=readme-ov-file#synchronizing-the-jooq-version-between-the-spring-boot-gradle-plugin-and-the-jooq-gradle-plugin">jOOQ
 *     version override consideration</a>
 */
public class GeneratorExtension {
  /** Default extension name. */
  public static final String EXTENSION_NAME = "jooqDockerImages";

  /**
   * Default PostgreSQL Docker image tag.
   *
   * @see <a href="https://hub.docker.com/_/postgres">Available tags on Docker hub</a>
   */
  private static final String DEFAULT_POSGTRES_IMAGE = "postgres:16-alpine";

  /** User-defined PostgreSQL Docker image tag. */
  @Nullable public String postgres;

  /** Default constructor. */
  public GeneratorExtension() {
    // No actions required
  }

  /**
   * Factory method to create the appropriate Docker image container to execute Flyway and jOOQ
   * against.
   *
   * @param driverClassName of the database
   * @return {@link DatabaseContainer} to use during execution
   */
  public @Nonnull DatabaseContainer getDatabaseContainer(String driverClassName) {
    if (PostgreSQL.supportsDriverClassName(driverClassName)) {
      return new PostgreSQL(getPostgreSQLDockerImage());
    } else {
      throw new UnsupportedOperationException(
          "[INTERNAL PLUGIN ERROR] Driver '%s' is not yet supported".formatted(driverClassName));
    }
  }

  /**
   * @return null-safe Docker image tag for PostgreSQL database
   */
  public @Nonnull String getPostgreSQLDockerImage() {
    return Strings.firstNonBlank(postgres, DEFAULT_POSGTRES_IMAGE);
  }
}
