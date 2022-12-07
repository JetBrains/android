/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.filters.parser

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.REGEX_KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.STRING_KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.VALUE
import com.android.tools.idea.logcat.util.LogcatFilterLanguageRule
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import java.text.ParseException

private val STRING_KEYS = listOf("tag", "package", "process", "message", "line")
private val NON_STRING_KEYS = listOf("level", "age", "is", "name")

@RunsInEdt
class LogcatFilterPsiTest {
  private val projectRule = ProjectRule()
  private val project by lazy(projectRule::project)

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), LogcatFilterLanguageRule(), FlagRule(StudioFlags.LOGCAT_IS_FILTER))

  @Test
  fun nonStringKeys() {
    StudioFlags.LOGCAT_IS_FILTER.override(true)
    for (key in NON_STRING_KEYS) {
      assertThat(parse("$key: bar").toFilter()).isEqualTo(KeyFilter(key, "bar"))
      assertThat(parse("$key:bar").toFilter()).isEqualTo(KeyFilter(key, "bar"))
    }
  }

  @Test
  fun stringKeys_unquoted() {
    for (key in STRING_KEYS) {
      val psi = parse("""
            $key: bar $key: b\ a\ r $key: b\a\r $key:bar $key:b\ a\ r $key:b\a\r
          """.trim())

      assertThat(psi.toFilter()).isEqualTo(
        AndFilter(
          KeyFilter(key, "bar"),
          KeyFilter(key, "b a r"),
          KeyFilter(key, "b\\a\\r"),
          KeyFilter(key, "bar"),
          KeyFilter(key, "b a r"),
          KeyFilter(key, "b\\a\\r"),
        )
      )
    }
  }

  @Test
  fun stringKeys_singleQuote() {
    for (key in STRING_KEYS) {
      val psi = parse("""
            $key: 'bar' $key: 'b\'a\'r' $key: 'b\a\r' $key:'bar' $key:'b\'a\'r' $key:'b\a\r' $key:'foo "bar" foo' $key: 'foo "bar" foo'
          """.trim())

      assertThat(psi.toFilter()).isEqualTo(
        AndFilter(
          KeyFilter(key, "bar"),
          KeyFilter(key, "b'a'r"),
          KeyFilter(key, "b\\a\\r"),
          KeyFilter(key, "bar"),
          KeyFilter(key, "b'a'r"),
          KeyFilter(key, "b\\a\\r"),
          KeyFilter(key, """foo "bar" foo"""),
          KeyFilter(key, """foo "bar" foo"""),
        )
      )
    }
  }

  @Test
  fun stringKeys_doubleQuote() {
    for (key in STRING_KEYS) {
      val psi = parse("""
            $key: "bar" $key: "b\"a\"r" $key: "b\a\r" $key:"bar" $key:"b\"a\"r" $key:"b\a\r" $key:"foo 'bar' foo" $key: "foo 'bar' foo"
          """.trim())

      assertThat(psi.toFilter()).isEqualTo(
        AndFilter(
          KeyFilter(key, "bar"),
          KeyFilter(key, "b\"a\"r"),
          KeyFilter(key, "b\\a\\r"),
          KeyFilter(key, "bar"),
          KeyFilter(key, "b\"a\"r"),
          KeyFilter(key, "b\\a\\r"),
          KeyFilter(key, "foo 'bar' foo"),
          KeyFilter(key, "foo 'bar' foo"),
        )
      )
    }
  }

  @Test
  fun stringKeys_negate() {
    for (key in STRING_KEYS) {
      assertThat(parse("-$key: bar").toFilter()).isEqualTo(KeyFilter(key, "bar", isNegated = true))
      assertThat(parse("-$key:bar").toFilter()).isEqualTo(KeyFilter(key, "bar", isNegated = true))
    }
  }

  @Test
  fun stringKeys_regex() {
    for (key in STRING_KEYS) {
      assertThat(parse("$key~: foo|bar").toFilter()).isEqualTo(KeyFilter(key, "foo|bar", isRegex = true))
      assertThat(parse("$key~:foo|bar").toFilter()).isEqualTo(KeyFilter(key, "foo|bar", isRegex = true))
    }
  }

  @Test
  fun stringKeys_negateRegex() {
    for (key in STRING_KEYS) {
      assertThat(parse("-$key~: foo|bar").toFilter()).isEqualTo(KeyFilter(key, "foo|bar", isNegated = true, isRegex = true))
      assertThat(parse("-$key~:foo|bar").toFilter()).isEqualTo(KeyFilter(key, "foo|bar", isNegated = true, isRegex = true))
    }
  }

  @Test
  fun stringKeys_exact() {
    for (key in STRING_KEYS) {
      assertThat(parse("$key=: bar").toFilter()).isEqualTo(KeyFilter(key, "bar", isExact = true))
      assertThat(parse("$key=:bar").toFilter()).isEqualTo(KeyFilter(key, "bar", isExact = true))
    }
  }

  @Test
  fun stringKeys_negatedExact() {
    for (key in STRING_KEYS) {
      assertThat(parse("-$key=: bar").toFilter()).isEqualTo(KeyFilter(key, "bar", isNegated = true, isExact = true))
      assertThat(parse("-$key=:bar").toFilter()).isEqualTo(KeyFilter(key, "bar", isNegated = true, isExact = true))
    }
  }

  @Test
  fun topLevelValue_unquoted() {
    val psi = parse("""
        bar b\ a\ r b\a\r
      """.trim())

    assertThat(psi.toFilter()).isEqualTo(
      TopLevelFilter("bar b a r b\\a\\r"),
    )
  }

  @Test
  fun topLevelValue_singleQuote() {
    val psi = parse("""
        'bar' 'b\'a\'r' 'b\a\r'
      """.trim())

    assertThat(psi.toFilter()).isEqualTo(
      TopLevelFilter("bar b'a'r b\\a\\r"),
    )
  }

  @Test
  fun topLevelValue_doubleQuote() {
    val psi = parse("""
        "bar" "b\"a\"r" "b\a\r"
      """.trim())

    assertThat(psi.toFilter()).isEqualTo(
      TopLevelFilter("bar b\"a\"r b\\a\\r"),
    )
  }

  @Test
  fun topLevelExpressions() {
    val psi = parse("level: I foo    bar   tag: bar   package: foobar")

    assertThat(psi.toFilter()).isEqualTo(
      AndFilter(
        KeyFilter("level", "I"),
        TopLevelFilter("foo    bar"),
        KeyFilter("tag", "bar"),
        KeyFilter("package", "foobar"),
      )
    )
  }

  @Test
  fun and() {
    val psi = parse("tag: bar & foo & package: foobar")

    assertThat(psi.toFilter()).isEqualTo(
      AndFilter(
        KeyFilter("tag", "bar"),
        TopLevelFilter("foo"),
        KeyFilter("package", "foobar"),
      )
    )
  }

  @Test
  fun or() {
    val psi = parse("tag: bar | foo | package: foobar")

    assertThat(psi.toFilter()).isEqualTo(
      OrFilter(
        KeyFilter("tag", "bar"),
        TopLevelFilter("foo"),
        KeyFilter("package", "foobar"),
      )
    )
  }

  @Test
  fun operatorPrecedence() {
    val psi = parse("f1 & f2 | f3 & f4")

    assertThat(psi.toFilter()).isEqualTo(
      OrFilter(
        AndFilter(
          TopLevelFilter("f1"),
          TopLevelFilter("f2"),
        ),
        AndFilter(
          TopLevelFilter("f3"),
          TopLevelFilter("f4"),
        ),
      )
    )
  }

  @Test
  fun parens_topLevelFilters() {
    val psi = parse("f1 & ( f2 | f3 ) & f4")

    assertThat(psi.toFilter()).isEqualTo(
      AndFilter(
        TopLevelFilter("f1"),
        OrFilter(
          TopLevelFilter("f2"),
          TopLevelFilter("f3"),
        ),
        TopLevelFilter("f4"),
      )
    )
  }

  @Test
  fun parens_keyFilters() {
    val psi = parse("f1 & (tag: foo | tag: 'bar') & f4")

    assertThat(psi.toFilter()).isEqualTo(
      AndFilter(
        TopLevelFilter("f1"),
        OrFilter(
          KeyFilter("tag", "foo"),
          KeyFilter("tag", "bar"),
        ),
        TopLevelFilter("f4"),
      )
    )
  }

  @Test
  fun parse_escapedColon() {
    assertThat(parse("tag\\:foo tag:foo").toFilter())
      .isEqualTo(
        AndFilter(
          TopLevelFilter("tag:foo"),
          KeyFilter("tag", "foo")))
  }

  @Test
  fun parse_quotedColon() {
    assertThat(parse("'tag:foo' tag:foo").toFilter())
      .isEqualTo(
        AndFilter(
          TopLevelFilter("tag:foo"),
          KeyFilter("tag", "foo")))
  }

  @Test
  fun singleChar() {
    assertThat(parse("a").toFilter()).isEqualTo(TopLevelFilter("a"))
  }

  private fun parse(text: String): PsiFile {
    val psi = PsiFileFactory.getInstance(project).createFileFromText("temp.lcf", LogcatFilterFileType, text)
    if (PsiTreeUtil.hasErrorElements(psi)) {
      val errorElement = PsiTreeUtil.findChildOfType(psi, PsiErrorElement::class.java) as PsiErrorElement
      throw IllegalArgumentException(errorElement.errorDescription)
    }
    return psi
  }
}

private fun PsiFile.toFilter(): Filter {
  val expressions = PsiTreeUtil.getChildrenOfType(this, LogcatFilterExpression::class.java)

  return when {
    expressions == null -> EmptyFilter()
    expressions.size == 1 -> expressions[0].toFilter()
    else -> {
      // treat consecutive top level values as concatenations rather than an 'and'. This isn't really testing the parser code, rather it
      // serves as a proof of concept that we can process the results in this fashion.

      // First, group consecutive top level value expressions.
      val grouped = expressions.fold(mutableListOf<MutableList<LogcatFilterExpression>>()) { accumulator, expression ->
        if (expression.isTopLevelValue() && accumulator.isNotEmpty() && accumulator.last().last().isTopLevelValue()) {
          accumulator.last().add(expression)
        }
        else {
          accumulator.add(mutableListOf(expression))
        }
        accumulator
      }

      // Then, combine in an AndFilter while creating a single TopLevelFilter for consecutive top-level expressions.
      val filters = grouped.map { if (it.size == 1) it[0].toFilter() else TopLevelFilter.from(it) }
      if (filters.size == 1) filters[0] else AndFilter(filters)
    }
  }
}

private fun LogcatFilterExpression.toFilter(): Filter {
  return when (this) {
    is LogcatFilterLiteralExpression -> this.literalToFilter()
    is LogcatFilterParenExpression -> expression!!.toFilter()
    is LogcatFilterAndExpression -> AndFilter(flattenAndExpression(this).map(LogcatFilterExpression::toFilter))
    is LogcatFilterOrExpression -> OrFilter(flattenOrExpression(this).map(LogcatFilterExpression::toFilter))
    else -> throw ParseException("Unexpected element: ${this::class.simpleName}", -1)
  }
}

private fun LogcatFilterLiteralExpression.literalToFilter() =
  when (firstChild.elementType) {
    VALUE -> TopLevelFilter(firstChild.toText())
    KEY, STRING_KEY, REGEX_KEY -> KeyFilter(this)
    else -> throw ParseException("Unexpected elementType: $firstChild.elementType", -1)
  }

private interface Filter

private class EmptyFilter : Filter

private data class TopLevelFilter(val text: String) : Filter {
  companion object {
    fun from(expressions: List<LogcatFilterExpression>): TopLevelFilter {
      val text = expressions.joinToString("") {
        val expression = it as LogcatFilterLiteralExpression
        expression.firstChild.toText() + if (expression.nextSibling is PsiWhiteSpace) expression.nextSibling.text else ""
      }
      return TopLevelFilter(text.trim())
    }
  }
}

private data class KeyFilter(
  val key: String,
  val text: String,
  val isNegated: Boolean = false,
  val isRegex: Boolean = false,
  val isExact: Boolean = false,
) : Filter {
  constructor(element: PsiElement) : this(
    element.firstChild.text.trim(':', '-', '~', '='),
    element.lastChild.toText(),
    element.firstChild.text.startsWith('-'),
    element.firstChild.text.endsWith("~:"),
    element.firstChild.text.endsWith("=:"),
  )
}

private data class AndFilter(val filters: List<Filter>) : Filter {
  constructor(vararg filters: Filter) : this(filters.asList())
}

private data class OrFilter(val filters: List<Filter>) : Filter {
  constructor(vararg filters: Filter) : this(filters.asList())
}

// A convenience function that makes asserting more clear by flattening nested OR expressions
private fun flattenOrExpression(expression: LogcatFilterExpression): List<LogcatFilterExpression> =
  if (expression is LogcatFilterOrExpression) {
    flattenOrExpression(expression.expressionList[0]) + flattenOrExpression(expression.expressionList[1])
  }
  else {
    listOf(expression)
  }

// A convenience function that makes asserting more clear by flattening nested AND expressions
private fun flattenAndExpression(expression: LogcatFilterExpression): List<LogcatFilterExpression> =
  if (expression is LogcatFilterAndExpression) {
    flattenAndExpression(expression.expressionList[0]) + flattenAndExpression(expression.expressionList[1])
  }
  else {
    listOf(expression)
  }
