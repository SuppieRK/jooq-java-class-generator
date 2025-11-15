/*
 * MIT License
 *
 * Copyright (c) 2024 Roman Khlebnov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
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

package io.github.suppierk.codegen.docker;

import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.dockerclient.DockerClientProviderStrategy;

/**
 * Base class that wraps a Testcontainers {@link JdbcDatabaseContainer} and exposes the common
 * JDBC-oriented methods used by {@code AbstractDatabaseTask}. Concrete subclasses provide driver
 * specific behaviour (e.g. MySQL driver loading).
 *
 * @see PostgreSQL
 * @see MySQL
 */
public abstract class AbstractDatabaseContainer implements AutoCloseable {
  private static final Logger LOGGER = Logging.getLogger(AbstractDatabaseContainer.class);
  private final JdbcDatabaseContainer<?> container;

  /**
   * Default constructor which invokes workaround for repeatable builds.
   *
   * @param container to access TestContainers database instance
   */
  protected AbstractDatabaseContainer(@Nonnull JdbcDatabaseContainer<?> container) {
    disableTestContainersFailFastBehavior();

    this.container = container;
  }

  /**
   * Provides database container handle.
   *
   * @return container for operations if needed
   */
  @SuppressWarnings("squid:S1452")
  protected @Nonnull JdbcDatabaseContainer<?> getDatabaseContainer() {
    return container;
  }

  /**
   * Provides database driver Java class name
   *
   * @return database driver Java class name
   */
  @Nonnull
  public String getDriverClassName() {
    return container.getDriverClassName();
  }

  /**
   * Provides database URL to the container
   *
   * @return database URL
   */
  @Nonnull
  public String getJdbcUrl() {
    return container.getJdbcUrl();
  }

  /**
   * Provides database username
   *
   * @return database user
   */
  @Nonnull
  public String getUsername() {
    return container.getUsername();
  }

  /**
   * Provides database password
   *
   * @return database user password
   */
  @Nonnull
  public String getPassword() {
    return container.getPassword();
  }

  /** {@inheritDoc} */
  @Override
  public void close() {
    container.close();
  }

  /**
   * Workaround for non-repeatable nature of TestContainers execution in cases when Docker
   * environment was not available at the start of the build stage.
   *
   * <p>The problem is that TestContainers {@link DockerClientProviderStrategy} has internal flag
   * which flips to {@code true} once there was a failure.
   *
   * <p>Here we are prying open {@link DockerClientProviderStrategy} with reflection to "unflip"
   * that flag to allow retries.
   *
   * @see <a href="https://github.com/testcontainers/testcontainers-java/issues/6441">Related GitHub
   *     issue</a>
   */
  @SuppressWarnings("squid:S3011")
  private void disableTestContainersFailFastBehavior() {
    try {
      final var failFastField =
          DockerClientProviderStrategy.class.getDeclaredField("FAIL_FAST_ALWAYS");

      if (!Modifier.isStatic(failFastField.getModifiers())
          || !AtomicBoolean.class.isAssignableFrom(failFastField.getType())) {
        LOGGER.debug(
            "Testcontainers FAIL_FAST_ALWAYS field no longer matches the expected static AtomicBoolean signature; skipping fail-fast relaxation.");
        return;
      }

      failFastField.setAccessible(true);
      final AtomicBoolean flag = (AtomicBoolean) failFastField.get(null);
      if (flag != null) {
        flag.set(false);
      } else {
        LOGGER.debug(
            "Resolved Testcontainers FAIL_FAST_ALWAYS flag to null; leaving fail-fast behaviour untouched.");
      }
    } catch (NoSuchFieldException e) {
      LOGGER.debug(
          "Testcontainers {} no longer exposes FAIL_FAST_ALWAYS; skipping fail-fast relaxation.",
          resolveTestcontainersVersion());
    } catch (Exception e) {
      LOGGER.warn(
          "Unable to adjust Testcontainers fail-fast flag; continuing with default behaviour.", e);
    }
  }

  private @Nonnull String resolveTestcontainersVersion() {
    final Package pkg = DockerClientProviderStrategy.class.getPackage();
    final String version = pkg != null ? pkg.getImplementationVersion() : null;
    return version != null ? version : "unknown";
  }
}
