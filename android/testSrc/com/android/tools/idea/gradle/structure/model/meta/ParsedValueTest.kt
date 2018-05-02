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

    assertThat(ParsedValue.NotSet.getText(Any::testToString), equalTo(""))
    assertThat(ParsedValue.Set.Parsed(1, DslText.Literal).getText(Any::testToString), equalTo("1@"))
    assertThat(ParsedValue.Set.Parsed("a", DslText.Literal).getText(Any::testToString), equalTo("a@"))
    assertThat(ParsedValue.Set.Parsed("AA", DslText.Reference(text = "var")).getText(Any::testToString), equalTo("\$var"))
    assertThat(
      ParsedValue.Set.Parsed("Z QQ Z", DslText.InterpolatedString(text = "Z \$var Z")).getText(Any::testToString),
      equalTo("\"Z \$var Z\"")
    )
    assertThat(ParsedValue.Set.Invalid<String>("fun1()", "cannot be parsed").getText(Any::testToString), equalTo("\$\$fun1()"))
  }

  @Test
  fun parsedValueGetText_unparsed() {
    assertThat(
      ParsedValue.Set.Parsed(
        value = null,
        dslText = DslText.OtherUnparsedDslText(text = "doSomething()")).getText(Any::testToString),
      equalTo("\$\$doSomething()")
    )
  }

  @Test
  fun makeParsedValue_notSet() {
    assertThat(makeParsedValue(null, null), equalTo<ParsedValue<*>>(ParsedValue.NotSet))
  }

  @Test
  fun makeParsedValue_parsed() {
    assertThat(makeParsedValue(1, null), equalTo<ParsedValue<*>>(ParsedValue.Set.Parsed(1, DslText.Literal)))
  }

  @Test
  fun makeParsedValue_parsedReference() {
    assertThat(makeParsedValue(1, DslText.Reference("var1")),
               equalTo<ParsedValue<*>>(ParsedValue.Set.Parsed(1, DslText.Reference("var1"))))
  }

  @Test
  fun makeParsedValue_parsedInterpolatedString() {
    assertThat(makeParsedValue("a and b", DslText.InterpolatedString("\$var1 and \$var2")),
               equalTo<ParsedValue<*>>(ParsedValue.Set.Parsed("a and b", DslText.InterpolatedString("\$var1 and \$var2"))))
  }

  @Test
  fun makeParsedValue_parsedUnparsed() {
    assertThat(makeParsedValue(null, DslText.OtherUnparsedDslText("doSomething()")),
               equalTo<ParsedValue<*>>(ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText("doSomething()"))))
  }

  @Test
  fun makeParsedValue_invalidLiteral() {
    // TODO(b/77627789): Currently we do not distinguish different unparsed cases. Implement or simplify.
    assertThat(makeParsedValue(null, DslText.Literal),
               equalTo<ParsedValue<*>>(ParsedValue.Set.Invalid<Int>("", "Invalid value")))
  }

  @Test
  fun makeParsedValue_invalidReference() {
    // TODO(b/77627789): Store full DslText() in ParsedValue and change the error message ('Unresolved' might be too strong.).
    assertThat(makeParsedValue(null, DslText.Reference("var1")),
               equalTo<ParsedValue<*>>(ParsedValue.Set.Invalid<Int>("var1", "Unresolved reference: 'var1'")))
  }

}

// Use custom toString() in tests to ensure it is called when it is appropriate.
private fun Any.testToString() = toString() + "@"

