/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.meta

import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test

class ParsedValueTest {

  @Test
  fun parsedValueGetText() {
    assertThat(ParsedValue.NotSet<String>().getText(), equalTo(""))
    assertThat(ParsedValue.Set.Parsed(1, DslText(mode = DslMode.LITERAL, text = "1")).getText(), equalTo("1"))
    assertThat(ParsedValue.Set.Parsed("a", DslText(mode = DslMode.LITERAL, text = "1")).getText(), equalTo("a"))
    assertThat(ParsedValue.Set.Parsed("AA", DslText(mode = DslMode.REFERENCE, text = "var")).getText(), equalTo("\$var"))
    assertThat(
      ParsedValue.Set.Parsed("Z QQ Z", DslText(mode = DslMode.INTERPOLATED_STRING, text = "Z \$var Z")).getText(),
      equalTo("\"Z \$var Z\"")
    )
    assertThat(ParsedValue.Set.Invalid<String>("fun1()", "cannot be parsed").getText(), equalTo("\$\$fun1()"))
  }

  @Test
  fun parsedValueGetText_wellKnownValue() {
    assertThat(ParsedValue.NotSet<Int>().getText(mapOf(null to "(def)", 1 to "one")), equalTo("(def)"))
    assertThat(
      ParsedValue.Set.Parsed(1, DslText(mode = DslMode.LITERAL, text = "1")).getText(
        mapOf(null to "(def)", 1 to "one")
      ), equalTo("one")
    )
    assertThat(ParsedValue.Set.Parsed("a").getText(mapOf("AA" as String? to "Long text")), equalTo("a"))
    assertThat(ParsedValue.Set.Parsed("AA").getText(mapOf("AA" as String? to "Long text")), equalTo("Long text"))
    assertThat(ParsedValue.Set.Parsed(1, DslText(mode = DslMode.REFERENCE, text = "var")).getText(), equalTo("\$var"))
    assertThat(
      ParsedValue.Set.Parsed(
        "AA",
        DslText(mode = DslMode.REFERENCE, text = "var")
      ).getText(mapOf("AA" as String? to "Variables are more important")),
      equalTo("\$var")
    )
  }
}
