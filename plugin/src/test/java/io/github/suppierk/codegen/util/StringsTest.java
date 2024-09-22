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
    assertEquals("Test", capitalizeFirstLetter("TEST"));
    assertEquals("Test", capitalizeFirstLetter("test"));
    assertEquals("Test", capitalizeFirstLetter("tEsT"));
    assertEquals("Test", capitalizeFirstLetter("TeSt"));
  }
}
