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

import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.intellij.pom.java.LanguageLevel
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.Test
import java.io.File

class InputParsersTest {

  @Test
  fun string() {
    val parsed = parseString("abc")
    assertTrue(parsed is ParsedValue.Set.Parsed)
    assertEquals("abc", (parsed as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun string_empty() {
    val parsed = parseString("")
    assertTrue(parsed is ParsedValue.NotSet)
  }

  @Test
  fun file() {
    val parsed = parseFile("/tmp/abc")
    assertTrue(parsed is ParsedValue.Set.Parsed)
    assertEquals(File("/tmp/abc"), (parsed as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun file_empty() {
    val parsed = parseFile("")
    assertTrue(parsed is ParsedValue.NotSet)
  }

  @Test
  fun enum() {
    val parsed = parseEnum("1.7", LanguageLevel::parse)
    assertTrue(parsed is ParsedValue.Set.Parsed)
    assertEquals(LanguageLevel.JDK_1_7, (parsed as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun enum_empty() {
    val parsed = parseEnum("", LanguageLevel::parse)
    assertTrue(parsed is ParsedValue.NotSet)
  }

  @Test
  fun enum_invalid() {
    val parsed = parseEnum("1_7", LanguageLevel::parse)
    assertTrue(parsed is ParsedValue.Set.Invalid)
    assertEquals("1_7", (parsed as ParsedValue.Set.Invalid).dslText)
    assertEquals("'1_7' is not a valid value of type LanguageLevel", parsed.errorMessage)
  }

  @Test
  fun boolean_empty() {
    val parsed = parseBoolean("")
    assertTrue(parsed is ParsedValue.NotSet)
  }

  @Test
  fun boolean_true() {
    val parsed = parseBoolean("true")
    assertTrue(parsed is ParsedValue.Set.Parsed)
    assertEquals(true, (parsed as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun boolean_false() {
    val parsed = parseBoolean("FALSE")
    assertTrue(parsed is ParsedValue.Set.Parsed)
    assertEquals(false, (parsed as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun boolean_invalid() {
    val parsed = parseBoolean("yes")
    assertTrue(parsed is ParsedValue.Set.Invalid)
    assertEquals("yes", (parsed as ParsedValue.Set.Invalid).dslText)
    assertEquals("Unknown boolean value: 'yes'. Expected 'true' or 'false'", parsed.errorMessage)
  }

  @Test
  fun int_empty() {
    val parsed = parseInt("")
    assertTrue(parsed is ParsedValue.NotSet)
  }

  @Test
  fun int() {
    val parsed = parseInt("123")
    assertTrue(parsed is ParsedValue.Set.Parsed)
    assertEquals(123, (parsed as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun int_invalid() {
    val parsed = parseInt("123.4")
    assertTrue(parsed is ParsedValue.Set.Invalid)
    assertEquals("123.4", (parsed as ParsedValue.Set.Invalid).dslText)
    assertEquals("'123.4' is not a valid integer value", parsed.errorMessage)
  }
}