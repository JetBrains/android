/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.structure.model.helpers

import com.android.ide.common.gradle.Version
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.structure.model.meta.*
import com.intellij.pom.java.LanguageLevel
import junit.framework.Assert.*
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File

class InputParsersTest {

  @Test
  fun any_empty() {
    val parsed = parseAny("")
    assertTrue(parsed.value === ParsedValue.NotSet)
    assertNull(parsed.annotation)
  }

  @Test
  fun any_int() {
    val parsed = parseAny("123")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertNull(parsed.annotation)
    assertEquals(123, (parsed.value as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun any_decimal() {
    val parsed = parseAny("123.1")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertNull(parsed.annotation)
    assertEquals("123.1".toBigDecimal(), (parsed.value as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun any_boolean() {
    val parsed = parseAny("false")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertNull(parsed.annotation)
    assertEquals(false, (parsed.value as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun any_string() {
    val parsed = parseAny("NoNo")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertNull(parsed.annotation)
    assertEquals("NoNo", (parsed.value as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun string() {
    val parsed = parseString("abc")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertNull(parsed.annotation)
    assertEquals("abc", (parsed.value as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun string_empty() {
    val parsed = parseString("")
    assertTrue(parsed.value === ParsedValue.NotSet)
    assertNull(parsed.annotation)
  }

  @Test
  fun file() {
    val parsed = parseFile("/tmp/abc")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertNull(parsed.annotation)
    assertEquals(File("/tmp/abc"), (parsed.value as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun file_empty() {
    val parsed = parseFile("")
    assertTrue(parsed.value === ParsedValue.NotSet)
    assertNull(parsed.annotation)
  }

  @Test
  fun enum() {
    val parsed = parseEnum("1.7", LanguageLevel::parse)
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertNull(parsed.annotation)
    assertEquals(LanguageLevel.JDK_1_7, (parsed.value as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun enum_empty() {
    val parsed = parseEnum("", LanguageLevel::parse)
    assertTrue(parsed.value === ParsedValue.NotSet)
    assertNull(parsed.annotation)
  }

  @Test
  fun enum_invalid() {
    val parsed = parseEnum("1_7", LanguageLevel::parse)
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertEquals(ValueAnnotation.Error("'1_7' is not a valid value of type LanguageLevel"), parsed.annotation)
    assertEquals(DslText.OtherUnparsedDslText("1_7"), (parsed.value as ParsedValue.Set.Parsed).dslText)
  }

  @Test
  fun boolean_empty() {
    val parsed = parseBoolean("")
    assertTrue(parsed.value === ParsedValue.NotSet)
    assertNull(parsed.annotation)
  }

  @Test
  fun boolean_true() {
    val parsed = parseBoolean("true")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertNull(parsed.annotation)
    assertEquals(true, (parsed.value as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun boolean_false() {
    val parsed = parseBoolean("FALSE")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertNull(parsed.annotation)
    assertEquals(false, (parsed.value as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun boolean_invalid() {
    val parsed = parseBoolean("yes")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertEquals(ValueAnnotation.Error("Unknown boolean value: 'yes'. Expected 'true' or 'false'"), parsed.annotation)
    assertEquals(DslText.OtherUnparsedDslText("yes"), (parsed.value as ParsedValue.Set.Parsed).dslText)
  }

  @Test
  fun int_empty() {
    val parsed = parseInt("")
    assertTrue(parsed.value === ParsedValue.NotSet)
    assertNull(parsed.annotation)
  }

  @Test
  fun int() {
    val parsed = parseInt("123")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertNull(parsed.annotation)
    assertEquals(123, (parsed.value as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun int_invalid() {
    val parsed = parseInt("123.4")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertEquals(ValueAnnotation.Error("'123.4' is not a valid integer value"), parsed.annotation)
    assertEquals(DslText.OtherUnparsedDslText("123.4"), (parsed.value as ParsedValue.Set.Parsed).dslText)
  }

  @Test
  fun languageLevel_empty() {
    val parsed = parseLanguageLevel("")
    assertTrue(parsed.value === ParsedValue.NotSet)
    assertNull(parsed.annotation)
  }

  @Test
  fun languageLevel() {
    assertEquals(parseLanguageLevel("1.8"), ParsedValue.Set.Parsed(LanguageLevel.JDK_1_8, DslText.Literal).annotated())
    assertEquals(parseLanguageLevel("VERSION_1_7"), ParsedValue.Set.Parsed(LanguageLevel.JDK_1_7, DslText.Literal).annotated())
    assertEquals(parseLanguageLevel("JavaVersion.VERSION_1_6"),
                 ParsedValue.Set.Parsed(LanguageLevel.JDK_1_6, DslText.Literal).annotated())
  }

  @Test
  fun hashString_empty() {
    assertEquals(ParsedValue.NotSet.annotated(), parseHashString(""))
  }

  @Test
  fun hashString() {
    assertEquals("26".asParsed().annotated(), parseHashString("26"))
    assertEquals("android-26".asParsed().annotated(), parseHashString("android-26"))
    assertEquals("android-P".asParsed().annotated(), parseHashString("android-P"))
    assertEquals("P".asParsed().annotated(), parseHashString("P"))
    assertEquals("Superlogic:Superlogic SDK v7.34".asParsed().annotated(),
                 parseHashString("Superlogic:Superlogic SDK v7.34"))
  }

  @Test
  fun gradleVersion_empty() {
    assertEquals(ParsedValue.NotSet.annotated(), parseGradleVersion(""))
  }

  @Test
  fun gradleVersion() {
    assertEquals(Version.parse("1.1.1").asParsed().annotated(), parseGradleVersion("1.1.1"))
  }

  @Test
  fun referenceOnly_empty() {
    val parsed = parseReferenceOnly("")
    assertTrue(parsed.value === ParsedValue.NotSet)
    assertNull(parsed.annotation)
  }

  @Test
  fun matcher_hashStrings() {
    assertTrue(matchHashStrings(null, "26", "26"))
    assertFalse(matchHashStrings(null, null, "26"))
    assertTrue(matchHashStrings(null, "android-P", "28"))
    assertTrue(matchHashStrings(null, "android-26", "26"))
  }

  @Test
  fun matcher_files() {
    assertTrue(matchFiles(File("/tmp"), File("a"), File("/tmp/a")))
    assertTrue(matchFiles(File("/tmp"), File("/tmp/a"), File("/tmp/a")))
    assertFalse(matchFiles(File("/tmp"), null, File("/tmp/a")))
    assertFalse(matchFiles(File("/tmp"), File("b"), File("/tmp/a")))
  }

  @Test
  fun formatAny_ambiguous() {
    assertThat(formatAny(1), equalTo("1"))
    assertThat(formatAny("1"), equalTo("\"1\""))
    assertThat(formatAny(1.1), equalTo("1.1"))
    assertThat(formatAny("1.1"), equalTo("\"1.1\""))
    assertThat(formatAny(true), equalTo("true"))
    assertThat(formatAny(false), equalTo("false"))
    assertThat(formatAny("true"), equalTo("\"true\""))
    assertThat(formatAny("false"), equalTo("\"false\""))
    assertThat(formatAny("xyz"), equalTo("xyz"))
  }
}