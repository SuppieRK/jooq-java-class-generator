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

import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.dockerclient.DockerClientProviderStrategy;

/**
 * General contract describing the behavior of the Docker image container.
 *
 * @see PostgreSQL#supportsDriverClassName(String) - additional recommended contract
 * @see AutoCloseable - enables try-with-resources for Docker image containers
 */
public abstract class DatabaseContainer implements AutoCloseable {
  private final JdbcDatabaseContainer<?> container;

  /**
   * Default constructor which invokes workaround for repeatable builds.
   *
   * @param container to access TestContainers database instance
   */
  protected DatabaseContainer(JdbcDatabaseContainer<?> container) {
    disableTestContainersFailFastBehavior();

    this.container = container;
  }

  /**
   * Provides database container handle.
   *
   * @return container for operations if needed
   */
  @SuppressWarnings("squid:S1452")
  protected JdbcDatabaseContainer<?> getDatabaseContainer() {
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
      failFastField.setAccessible(true);
      ((AtomicBoolean) failFastField.get(null)).set(false);
      failFastField.setAccessible(false);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to reset TestContainers fail fast flag", e);
    }
  }
}
