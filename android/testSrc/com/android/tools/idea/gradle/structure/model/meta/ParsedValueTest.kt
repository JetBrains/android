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
    assertThat(ParsedValue.NotSet.getText(), equalTo(""))
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
  fun parsedValueGetText_unparsed() {
    assertThat(
      ParsedValue.Set.Parsed(value = null, dslText = DslText(mode = DslMode.OTHER_UNPARSED_DSL_TEXT, text = "doSomething()")).getText(),
      equalTo("\$\$doSomething()")
    )
  }

  @Test
  fun makeParsedValue_notSet() {
    assertThat(makeParsedValue(null, null), equalTo<ParsedValue<*>>(ParsedValue.NotSet))
  }

  @Test
  fun makeParsedValue_parsed() {
    assertThat(makeParsedValue(1, null), equalTo<ParsedValue<*>>(ParsedValue.Set.Parsed(1)))
  }

  @Test
  fun makeParsedValue_parsedLiteral() {
    assertThat(makeParsedValue(1, DslText(DslMode.LITERAL, "1")),
               equalTo<ParsedValue<*>>(ParsedValue.Set.Parsed(1, DslText(DslMode.LITERAL, "1"))))
  }

  @Test
  fun makeParsedValue_parsedReference() {
    assertThat(makeParsedValue(1, DslText(DslMode.REFERENCE, "var1")),
               equalTo<ParsedValue<*>>(ParsedValue.Set.Parsed(1, DslText(DslMode.REFERENCE, "var1"))))
  }

  @Test
  fun makeParsedValue_parsedInterpolatedString() {
    assertThat(makeParsedValue("a and b", DslText(DslMode.INTERPOLATED_STRING, "\$var1 and \$var2")),
               equalTo<ParsedValue<*>>(ParsedValue.Set.Parsed("a and b", DslText(DslMode.INTERPOLATED_STRING, "\$var1 and \$var2"))))
  }

  @Test
  fun makeParsedValue_parsedUnparsed() {
    assertThat(makeParsedValue(null, DslText(DslMode.OTHER_UNPARSED_DSL_TEXT, "doSomething()")),
               equalTo<ParsedValue<*>>(ParsedValue.Set.Parsed(null, DslText(DslMode.OTHER_UNPARSED_DSL_TEXT, "doSomething()"))))
  }

  @Test
  fun makeParsedValue_invalidLiteral() {
    assertThat(makeParsedValue(null, DslText(DslMode.LITERAL, "1")),
               equalTo<ParsedValue<*>>(ParsedValue.Set.Invalid<Int>("1", "Invalid value")))
  }

  @Test
  fun makeParsedValue_invalidReference() {
    // TODO(b/77627789): Store full DslText() in ParsedValue and change the error message.
    assertThat(makeParsedValue(null, DslText(DslMode.REFERENCE, "var1")),
               equalTo<ParsedValue<*>>(ParsedValue.Set.Invalid<Int>("var1", "Invalid value")))
  }

}
