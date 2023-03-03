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
package com.android.tools.idea.gradle.structure.configurables.ui.properties

import com.android.tools.idea.gradle.structure.configurables.ui.TextRenderer
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.structure.model.meta.*
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test

class ParsedValueRendererTest {

  @Test
  fun testBuildKnownValueRenderers_empty() {
    val renderers = buildKnownValueRenderers(emptyKnownValues(), Any::testToString, null)
    assertThat(renderers.size, equalTo(0))
  }

  @Test
  fun testBuildKnownValueRenderers_noDefault() {
    val valueDescriptors = listOf(ValueDescriptor(ParsedValue.NotSet, "Null"), ValueDescriptor(1, "One"), ValueDescriptor(2, "Two"))
    val renderers = buildKnownValueRenderers(knownValuesFrom(valueDescriptors), Any::testToString, null)
    assertThat(renderers.size, equalTo(3))

    assertThat(testRender(renderers[ParsedValue.NotSet]), equalTo("<comment>(Null)"))
    assertThat(testRender(renderers[1.asParsed()]), equalTo("1@ <comment>(One)"))
    assertThat(testRender(renderers[2.asParsed()]), equalTo("2@ <comment>(Two)"))
  }

  @Test
  fun testBuildKnownValueRenderers_describedDefault() {
    val valueDescriptors = listOf(ValueDescriptor(1, "One"), ValueDescriptor(2, "Two"))
    val renderers = buildKnownValueRenderers(knownValuesFrom(valueDescriptors), Any::testToString, 1)
    assertThat(renderers.size, equalTo(3))

    assertThat(testRender(renderers[ParsedValue.NotSet]), equalTo("<i><comment>1@ (One)"))
    assertThat(testRender(renderers[1.asParsed()]), equalTo("1@ <comment>(One)"))
    assertThat(testRender(renderers[2.asParsed()]), equalTo("2@ <comment>(Two)"))
  }

  @Test
  fun testBuildKnownValueRenderers_notSetAndDescribedDefault() {
    val valueDescriptors = listOf(ValueDescriptor(ParsedValue.NotSet, "Null"), ValueDescriptor(1, "One"), ValueDescriptor(2, "Two"))
    val renderers = buildKnownValueRenderers(knownValuesFrom(valueDescriptors), Any::testToString, 1)
    assertThat(renderers.size, equalTo(3))

    assertThat(testRender(renderers[ParsedValue.NotSet]), equalTo("<comment>(Null)"))
    assertThat(testRender(renderers[1.asParsed()]), equalTo("1@ <comment>(One)"))
    assertThat(testRender(renderers[2.asParsed()]), equalTo("2@ <comment>(Two)"))
  }

  @Test
  fun testBuildKnownValueRenderers_undescribedDefault() {
    val valueDescriptors = listOf(ValueDescriptor(1, "One"), ValueDescriptor(2, "Two"))
    val renderers = buildKnownValueRenderers(knownValuesFrom(valueDescriptors), Any::testToString, -1)
    assertThat(renderers.size, equalTo(3))

    assertThat(testRender(renderers[ParsedValue.NotSet]), equalTo("<i><comment>-1@"))
    assertThat(testRender(renderers[1.asParsed()]), equalTo("1@ <comment>(One)"))
    assertThat(testRender(renderers[2.asParsed()]), equalTo("2@ <comment>(Two)"))
  }

  @Test
  fun testBuildKnownValueRenderers_notDescribedKnownValues() {
    val valueDescriptors = listOf(ValueDescriptor(true), ValueDescriptor(false))
    val renderers = buildKnownValueRenderers(knownValuesFrom(valueDescriptors), Any::testToString, false)
    assertThat(renderers.size, equalTo(3))

    assertThat(testRender(renderers[ParsedValue.NotSet]), equalTo("<i><comment>false@"))
    assertThat(testRender(renderers[true.asParsed()]), equalTo("true@"))
    assertThat(testRender(renderers[false.asParsed()]), equalTo("false@"))
  }

  @Test
  fun testBuildKnownValueRenderers_withVariables() {
    val notDescribedTrue = ParsedValue.Set.Parsed(true, DslText.Reference("notDescribedTrue"))
    val describedFalse = ParsedValue.Set.Parsed(false, DslText.Reference("describedFalse"))
    val valueDescriptors = listOf(
      ValueDescriptor(true),
      ValueDescriptor(false),
      ValueDescriptor(notDescribedTrue),
      ValueDescriptor(describedFalse, "Lie")
    )
    val renderers = buildKnownValueRenderers(knownValuesFrom(valueDescriptors), Any::testToString, false)
    assertThat(renderers.size, equalTo(5))

    assertThat(testRender(renderers[ParsedValue.NotSet]), equalTo("<i><comment>false@"))
    assertThat(testRender(renderers[true.asParsed()]), equalTo("true@"))
    assertThat(testRender(renderers[false.asParsed()]), equalTo("false@"))
    assertThat(testRender(renderers[notDescribedTrue]), equalTo("<var>\$notDescribedTrue<comment> : true@"))
    assertThat(testRender(renderers[describedFalse]), equalTo("<var>\$describedFalse<comment> : false@<nocolor> <comment>(Lie)"))
  }

  @Test
  fun testRenderParsedValue_notSet() {
    assertThat(
      ParsedValue.NotSet.testRenderWith<Int>(buildKnownValueRenderers(emptyKnownValues(), Any::testToString, null)),
      equalTo(""))
    assertThat(
      ParsedValue.NotSet.testRenderWith<Int>(
        buildKnownValueRenderers(knownValuesFrom(listOf(ValueDescriptor<Int>(ParsedValue.NotSet, "Null"))), Any::testToString, null)),
      equalTo("<comment>(Null)"))
    assertThat(
      ParsedValue.NotSet.testRenderWith<Int>(
        buildKnownValueRenderers(knownValuesFrom(listOf(ValueDescriptor(1, "One"))), Any::testToString, 1)),
      equalTo("<i><comment>1@ (One)")
    )
  }

  @Test
  fun testRenderParsedValue_notSet_annotated() {
    assertThat(
      ParsedValue.NotSet.annotateWithError("error").testRenderWith<Int>(
        buildKnownValueRenderers(emptyKnownValues(), Any::testToString, null)),
      equalTo("<error>(error)"))
    assertThat(
      ParsedValue.NotSet.annotateWithError("error").testRenderWith<Int>(
        buildKnownValueRenderers(knownValuesFrom(listOf(ValueDescriptor<Int>(ParsedValue.NotSet, "Null"))), Any::testToString, null)),
      equalTo("<w><comment>(Null)</w><error> (error)"))
    assertThat(
      ParsedValue.NotSet.annotateWithError("error").testRenderWith<Int>(
        buildKnownValueRenderers(knownValuesFrom(listOf(ValueDescriptor(1, "One"))), Any::testToString, 1)),
      equalTo("<i><w><comment>1@ (One)</i></w><error> (error)")
    )
  }

  @Test
  fun testRenderParsedValue_setValue() {
    val one = ParsedValue.Set.Parsed(1, DslText.Literal)
    assertThat(one.testRenderWith<Int>(buildKnownValueRenderers(emptyKnownValues(), Any::testToString, null)), equalTo("1@"))
    assertThat(
      one.testRenderWith<Int>(buildKnownValueRenderers(knownValuesFrom(listOf(ValueDescriptor(1, "One"))), Any::testToString, null)),
      equalTo("1@ <comment>(One)"))
  }

  @Test
  fun testRenderParsedValue_setValue_annotated() {
    val one = ParsedValue.Set.Parsed(1, DslText.Literal)
    assertThat(
      one.annotateWithError("error").testRenderWith<Int>(buildKnownValueRenderers(emptyKnownValues(), Any::testToString, null)),
      equalTo("<w>1@</w><error> (error)"))
    assertThat(
      one.annotateWithError("error").testRenderWith<Int>(
        buildKnownValueRenderers(knownValuesFrom(listOf(ValueDescriptor(1, "One"))), Any::testToString, null)),
      equalTo("<w>1@ <comment>(One)</w><error> (error)"))
  }

  @Test
  fun testRenderParsedValue_setEmptyValue() {
    val one = ParsedValue.Set.Parsed("", DslText.Literal)
    assertThat(
      testRender { one.renderTo(this, Any::toString, buildKnownValueRenderers<String>(emptyKnownValues(), Any::toString, null)) },
      equalTo(""))
    assertThat(
      testRender {
        one.renderTo(this, Any::toString,
                     buildKnownValueRenderers(knownValuesFrom(listOf(ValueDescriptor("", "One"))), Any::toString, null))
      },
      equalTo("<comment>(One)"))
  }

  @Test
  fun testRenderParsedValue_setValueWitLiteral() {
    val one = ParsedValue.Set.Parsed(1, DslText.Literal)
    assertThat(one.testRenderWith<Int>(buildKnownValueRenderers(emptyKnownValues(), Any::testToString, null)), equalTo("1@"))
    assertThat(
      one.testRenderWith<Int>(buildKnownValueRenderers(knownValuesFrom(listOf(ValueDescriptor(1, "One"))), Any::testToString, null)),
      equalTo("1@ <comment>(One)"))
  }

  @Test
  fun testRenderParsedValue_setReference() {
    val var1 = ParsedValue.Set.Parsed(1, DslText.Reference("var1"))
    assertThat(var1.testRenderWith<Int>(buildKnownValueRenderers(emptyKnownValues(), Any::testToString, null)),
               equalTo("<var>\$var1<comment> : 1@"))
    assertThat(
      var1.testRenderWith<Int>(buildKnownValueRenderers(knownValuesFrom(listOf(ValueDescriptor(1, "One"))), Any::testToString, null)),
      equalTo("<var>\$var1<comment> : 1@ (One)"))
  }

  @Test
  fun testRenderParsedValue_setReference_annotated() {
    val var1 = ParsedValue.Set.Parsed(1, DslText.Reference("var1")).annotateWithError("error")
    assertThat(var1.testRenderWith<Int>(buildKnownValueRenderers(emptyKnownValues(), Any::testToString, null)),
               equalTo("<w><var>\$var1<comment> : 1@</w><error> (error)"))
    assertThat(
      var1.testRenderWith<Int>(buildKnownValueRenderers(knownValuesFrom(listOf(ValueDescriptor(1, "One"))), Any::testToString, null)),
      equalTo("<w><var>\$var1<comment> : 1@ (One)</w><error> (error)"))
  }

  @Test
  fun testRenderParsedValue_setReferenceToEmpty() {
    val var1 = ParsedValue.Set.Parsed("", DslText.Reference("var1"))
    assertThat(
      testRender { var1.renderTo(this, Any::toString, buildKnownValueRenderers<String>(emptyKnownValues(), Any::toString, null)) },
      equalTo("<var>\$var1"))
  }

  @Test
  fun testRenderParsedValue_setInterpolatedString() {
    val interpolated = ParsedValue.Set.Parsed("a nd b", DslText.InterpolatedString("\$var1 and \$var2"))
    // TODO(b/77618752): Variable references should be highlighted separately.
    assertThat(interpolated.testRenderWith<String>(buildKnownValueRenderers(emptyKnownValues(), Any::testToString, null)),
               equalTo("<var>\"\$var1 and \$var2\"<comment> : \"a nd b@\""))
    assertThat(interpolated.testRenderWith<String>(
      buildKnownValueRenderers(knownValuesFrom(listOf(ValueDescriptor("a and b", "does not matter"))), Any::testToString, null)),
               equalTo("<var>\"\$var1 and \$var2\"<comment> : \"a nd b@\""))
  }

  @Test
  fun testRenderParsedValue_setInterpolatedString_annotated() {
    val interpolated = ParsedValue.Set.Parsed("a nd b", DslText.InterpolatedString("\$var1 and \$var2")).annotateWithError("error")
    // TODO(b/77618752): Variable references should be highlighted separately.
    assertThat(interpolated.testRenderWith<String>(buildKnownValueRenderers(emptyKnownValues(), Any::testToString, null)),
               equalTo("<w><var>\"\$var1 and \$var2\"<comment> : \"a nd b@\"</w><error> (error)"))
  }

  @Test
  fun testRenderParsedValue_setOtherDsl() {
    val dsl = ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText("doSomething()"))
    assertThat(dsl.testRenderWith<String>(buildKnownValueRenderers(emptyKnownValues(), Any::testToString, null)),
               equalTo("<var>\$<nocolor>doSomething()"))
  }

  @Test
  fun testRenderParsedValue_setInvalid() {
    val error = ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText("something bad")).annotateWithError("error message")
    assertThat(error.testRenderWith<String>(buildKnownValueRenderers(emptyKnownValues(), Any::testToString, null)),
               equalTo("<w><var>\$<nocolor>something bad</w><error> (error message)"))
  }

  @Test
  fun testRenderParsedValue_setInvalidNoMessage() {
    val error = ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText("something bad")).annotated()
    assertThat(error.testRenderWith<String>(buildKnownValueRenderers(emptyKnownValues(), Any::testToString, null)),
               equalTo("<var>\$<nocolor>something bad"))
  }

  @Test
  fun testRenderAny_list() {
    val list = listOf(1.asParsed(), "a".asParsed(), ("v" to true).asParsed()).asParsed()
    assertThat(testRender { list.renderAnyTo(this, mapOf()) },
               equalTo("[1, a, <var>\$v<comment> : true<nocolor>]"))
  }

  @Test
  fun testRenderAny_map() {
    val map = mapOf("a" to 1.asParsed(), "b" to "a".asParsed(), "c" to ("v" to true).asParsed()).asParsed()
    assertThat(testRender { map.renderAnyTo(this, mapOf()) },
               equalTo("[a : 1, b : a, c : <var>\$v<comment> : true<nocolor>]"))
  }

  @Test
  fun testRenderAny_mapWithRferencesToNestedMaps() {
    val map = mapOf(
      "a" to (mapOf("b" to "val".asParsed())).asParsed(),
      "c" to (mapOf("d" to ("var" to "val").asParsed())).asParsed()
    ).asParsed()
    assertThat(testRender { map.renderAnyTo(this, mapOf()) },
               equalTo("[a : [b : val], c : [d : <var>\$var<comment> : val<nocolor>]]"))
  }

  @Test
  fun testRenderAny_referenceToMapWithRferencesToNestedMaps() {
    val map = ("ref" to mapOf(
      "a" to (mapOf("b" to "val".asParsed())).asParsed(),
      "c" to (mapOf("d" to ("var" to "val").asParsed())).asParsed()
    )).asParsed()
    assertThat(testRender { map.renderAnyTo(this, mapOf()) },
               equalTo("<var>\$ref<comment> : [a : [b : val], c : [d : \$var]]"))
  }
}

private fun testRender(action: TextRenderer.() -> Unit): String =
  object : TextRenderer {
    var result = ""

    var bold: Boolean = false
    var italic: Boolean = false
    var waved: Boolean = false
    var color: String = ""

    override fun append(text: String, attributes: SimpleTextAttributes) {
      if (text.isEmpty()) return;
      if (attributes.style and SimpleTextAttributes.STYLE_BOLD == 0 && bold) {
        result += "</b>"; bold = false
      }
      if (attributes.style and SimpleTextAttributes.STYLE_ITALIC == 0 && italic) {
        result += "</i>"; italic = false
      }
      if (attributes.style and SimpleTextAttributes.STYLE_WAVED == 0 && waved) {
        result += "</w>"; waved = false
      }
      if (attributes.style and SimpleTextAttributes.STYLE_BOLD != 0 && !bold) {
        result += "<b>"; bold = true
      }
      if (attributes.style and SimpleTextAttributes.STYLE_ITALIC != 0 && !italic) {
        result += "<i>"; italic = true
      }
      if (attributes.style and SimpleTextAttributes.STYLE_WAVED != 0 && !waved) {
        result += "<w>"; waved = true
      }
      if (attributes.fgColor == SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor && color != "comment") {
        result += "<comment>"; color = "comment"
      }
      if (attributes.fgColor == JBUI.CurrentTheme.Link.Foreground.ENABLED && color != "var") {
        result += "<var>"; color = "var"
      }
      if (attributes.fgColor == SimpleTextAttributes.ERROR_ATTRIBUTES.fgColor && color != "error") {
        result += "<error>"; color = "error"
      }
      if (attributes.fgColor == JBColor.BLACK && color != "") {
        result += "<nocolor>"; color = ""
      }
      result += text
    }
  }
    .apply(action)
    .result

private fun testRender(valueRenderer: ValueRenderer?): String = testRender { valueRenderer?.renderTo(this) }

private fun <T : Any> ParsedValue<T>.testRenderWith(valueToText: Map<ParsedValue<T>, ValueRenderer>): String =
  annotated().testRenderWith(valueToText)

private fun <T : Any> Annotated<ParsedValue<T>>.testRenderWith(valueToText: Map<ParsedValue<T>, ValueRenderer>): String = let { value ->
  testRender { value.renderTo(this, Any::testToString, valueToText) }
}

// Use custom toString() in tests to ensure it is called when it is appropriate.
private fun Any.testToString() = toString() + "@"

private fun <T : Any> knownValuesFrom(literals: List<ValueDescriptor<T>>) = object : KnownValues<T> {
  override val literals: List<ValueDescriptor<T>> = literals
  override fun isSuitableVariable(variable: Annotated<ParsedValue.Set.Parsed<T>>): Boolean = true
}
