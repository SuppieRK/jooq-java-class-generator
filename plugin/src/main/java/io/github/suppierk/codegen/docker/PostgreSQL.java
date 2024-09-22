package io.github.suppierk.codegen.docker;

import javax.annotation.Nonnull;
import org.testcontainers.containers.PostgreSQLContainer;

/** Defines database container with PostgreSQL database. */
public class PostgreSQL implements DatabaseContainer {
  private static final String DRIVER_CLASS_NAME = "org.postgresql.Driver";

  private final PostgreSQLContainer<?> container;

  /**
   * Constructor.
   *
   * @param dockerImageName to use as a base
   */
  public PostgreSQL(String dockerImageName) {
    this.container = new PostgreSQLContainer<>(dockerImageName);
    this.container.start();
  }

  /**
   * <b>RECOMMENDED TO BE CREATED ACROSS ALL OTHER {@link DatabaseContainer} IMPLEMENTATIONS</b>
   *
   * @param driverClassName used in Flyway / jOOQ Gradle configurations
   * @return {@code true} if current implementation supports given driver, {@code false} otherwise
   */
  public static boolean supportsDriverClassName(String driverClassName) {
    return DRIVER_CLASS_NAME.equals(driverClassName);
  }

  /** {@inheritDoc} */
  @Override
  public @Nonnull String getDriverClassName() {
    return container.getDriverClassName();
  }

  /** {@inheritDoc} */
  @Override
  public @Nonnull String getJdbcUrl() {
    return container.getJdbcUrl();
  }

  /** {@inheritDoc} */
  @Override
  public @Nonnull String getUsername() {
    return container.getUsername();
  }

  /** {@inheritDoc} */
  @Override
  public @Nonnull String getPassword() {
    return container.getPassword();
  }

  /** {@inheritDoc} */
  @Override
  public void close() throws Exception {
    container.stop();
  }
}
