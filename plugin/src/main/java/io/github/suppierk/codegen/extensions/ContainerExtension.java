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

package io.github.suppierk.codegen.extensions;

import java.util.Objects;
import javax.annotation.Nonnull;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

/**
 * Describes optional container overrides for a database. Currently, supports overriding the Docker
 * image used when launching Testcontainers for a specific database entry.
 */
public class ContainerExtension {
  private final Property<String> image;

  /**
   * Default constructor.
   *
   * @param image property containing an optional image override
   */
  public ContainerExtension(@Nonnull ObjectFactory image) {
    this.image = image.property(String.class);
  }

  /**
   * Returns the configured image override property.
   *
   * @return property containing an optional image override
   */
  public @Nonnull Property<String> image() {
    return image;
  }

  /**
   * Sets explicit Docker image to use for this database.
   *
   * @param value Docker image name (e.g. {@code postgres:17-alpine})
   */
  public void setImage(@Nonnull String value) {
    image.set(Objects.requireNonNull(value, "image"));
  }

  /**
   * Groovy-friendly alias for {@link #setImage(String)}.
   *
   * @param value Docker image name (e.g. {@code postgres:17-alpine})
   */
  public void image(@Nonnull String value) {
    setImage(value);
  }
}
