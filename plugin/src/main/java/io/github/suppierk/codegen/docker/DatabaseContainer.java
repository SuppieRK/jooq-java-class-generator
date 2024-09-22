package io.github.suppierk.codegen.docker;

import javax.annotation.Nonnull;

/**
 * General contract describing the behavior of the Docker image container.
 *
 * @see PostgreSQL#supportsDriverClassName(String) - additional recommended contract
 * @see AutoCloseable - enables try-with-resources for Docker image containers
 */
public interface DatabaseContainer extends AutoCloseable {
  /**
   * @return database driver Java class name
   */
  @Nonnull
  String getDriverClassName();

  /**
   * @return database URL
   */
  @Nonnull
  String getJdbcUrl();

  /**
   * @return database user
   */
  @Nonnull
  String getUsername();

  /**
   * @return database user password
   */
  @Nonnull
  String getPassword();
}
