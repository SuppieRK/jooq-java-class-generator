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

import static io.github.suppierk.codegen.util.Strings.capitalizeFirstLetter;
import static io.github.suppierk.codegen.util.Strings.firstNonBlank;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StringsTest {
  @Test
  void firstNonBlankMustReturnFirstNonBlankValue() {
    final var value = "value";
    final var defaultValue = "defaultValue";

    final var emptyValue = "";
    final var blankValue = "   ";

    assertEquals(defaultValue, firstNonBlank(null, defaultValue));
    assertEquals(defaultValue, firstNonBlank(null, null, defaultValue));
    assertEquals(defaultValue, firstNonBlank(emptyValue, defaultValue));
    assertEquals(defaultValue, firstNonBlank(blankValue, defaultValue));
    assertEquals(value, firstNonBlank(value, defaultValue));
    assertEquals(value, firstNonBlank(value, (String) null));
    assertEquals(value, firstNonBlank(value, emptyValue));
    assertEquals(value, firstNonBlank(value, blankValue));

    // Corner cases
    assertThrows(IllegalArgumentException.class, () -> firstNonBlank(null, (String) null));
    assertThrows(IllegalArgumentException.class, () -> firstNonBlank(null, blankValue));
    assertThrows(IllegalArgumentException.class, () -> firstNonBlank(null, emptyValue));
    assertThrows(IllegalArgumentException.class, () -> firstNonBlank(blankValue, (String) null));
    assertThrows(IllegalArgumentException.class, () -> firstNonBlank(blankValue, blankValue));
    assertThrows(IllegalArgumentException.class, () -> firstNonBlank(blankValue, emptyValue));
    assertThrows(IllegalArgumentException.class, () -> firstNonBlank(emptyValue, (String) null));
    assertThrows(IllegalArgumentException.class, () -> firstNonBlank(emptyValue, blankValue));
    assertThrows(IllegalArgumentException.class, () -> firstNonBlank(emptyValue, emptyValue));
  }

  @Test
  void capitalizeFirstLetterShouldProperlyCapitalizeStrings() {
    assertEquals("", capitalizeFirstLetter(null));
    assertEquals("", capitalizeFirstLetter(""));
    assertEquals("", capitalizeFirstLetter("    "));
    assertEquals("Test", capitalizeFirstLetter("Test"));
    assertEquals("TEST", capitalizeFirstLetter("TEST"));
    assertEquals("Test", capitalizeFirstLetter("test"));
    assertEquals("TEsT", capitalizeFirstLetter("tEsT"));
    assertEquals("TeSt", capitalizeFirstLetter("TeSt"));
  }
}
