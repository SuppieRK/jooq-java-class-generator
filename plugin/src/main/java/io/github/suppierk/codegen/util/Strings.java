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

package io.github.suppierk.codegen.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** {@link String}-related utilities. */
public final class Strings {
  /** Just en empty string. */
  public static final String EMPTY = "";

  private Strings() {
    throw new IllegalAccessError("Utility class");
  }

  /**
   * Returns first non-null, non-blank {@link String}.
   *
   * @param value as a workaround to enforce at least one argument
   * @param values is a variable {@link String} values to pick from
   * @return first non-null, non-blank {@link String}.
   * @throws IllegalArgumentException if all available values are null or blank
   */
  public static @Nonnull String firstNonBlank(@Nullable String value, @Nullable String... values)
      throws IllegalArgumentException {
    if (value != null && !value.isBlank()) {
      return value;
    }

    if (values != null) {
      for (String v : values) {
        if (v != null && !v.isBlank()) {
          return v;
        }
      }
    }

    throw new IllegalArgumentException("All arguments are either null or blank strings");
  }

  /**
   * @param value to capitalize
   * @return initial {@link String} with a capitalized first letter, e.g. 'value' turns into 'Value'
   *     or empty {@link String} if the input was null or blank
   */
  public static @Nonnull String capitalizeFirstLetter(@Nullable String value)
      throws IllegalArgumentException {
    if (value == null || value.isBlank()) {
      return EMPTY;
    }

    var result = value.substring(0, 1).toUpperCase();
    if (value.length() > 1) {
      result += value.substring(1).toLowerCase();
    }
    return result;
  }
}
