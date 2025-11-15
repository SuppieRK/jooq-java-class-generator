/*
 * MIT License
 *
 * Copyright (c) 2024 Roman Khlebnov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to do so, subject to the following conditions:
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

package io.github.suppierk.codegen.util;

import groovy.lang.Closure;
import javax.annotation.Nonnull;

/**
 * Utility class for common extension-related functionality. Currently, provides a Groovy {@link
 * Closure}-aware helper that mirrors the behaviour of Gradle's built-in DSL when delegating
 * closures to extension objects.
 */
public final class Extensions {
  /** Hidden constructor to prevent instantiation. */
  private Extensions() {
    throw new IllegalAccessError("Utility class");
  }

  /**
   * Configures given object using given closure.
   *
   * @param closure to apply
   * @param target to configure
   */
  public static void configureWithClosure(@Nonnull Closure<?> closure, @Nonnull Object target) {
    final var config = closure.rehydrate(target, closure.getOwner(), closure.getThisObject());
    config.setResolveStrategy(Closure.DELEGATE_FIRST);
    config.setDelegate(target);
    final int parameterCount = config.getMaximumNumberOfParameters();
    if (parameterCount == 0) {
      config.call();
    } else {
      config.call(target);
    }
  }
}
