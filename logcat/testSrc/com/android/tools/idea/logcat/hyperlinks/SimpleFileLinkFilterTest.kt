package com.android.tools.idea.logcat.hyperlinks

import com.android.tools.idea.logcat.util.FakePsiShortNamesCache
import com.android.tools.idea.testing.ProjectServiceRule
import com.android.tools.idea.testing.WaitForIndexRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.filters.Filter.ResultItem
import com.intellij.execution.filters.impl.MultipleFilesHyperlinkInfo
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import kotlin.test.fail
import org.junit.Rule
import org.junit.Test

/** Tests for [SimpleFileLinkFilter] */
class SimpleFileLinkFilterTest {
  private val projectRule = ProjectRule()
  private val project
    get() = projectRule.project

  private val projectFiles =
    listOf("File.kt", "File2.kt", "package1/MultiFile.kt", "package2/MultiFile.kt", "_Strange-File")

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      WaitForIndexRule(projectRule),
      ProjectServiceRule(projectRule, PsiShortNamesCache::class.java) {
        FakePsiShortNamesCache(project, projectFiles)
      },
    )

  @Test
  fun applyFilter() {
    val filter = SimpleFileLinkFilter(project)
    val line = "File.kt:12"

    val result = filter.applyFilter(line, line.length) ?: fail()

    assertThat(result.resultItems.map { it.describeLink() }).containsExactly("File.kt:11")
    result.resultItems.forEach { assertHighlight(it, line) }
  }

  @Test
  fun applyFilter_wrappedInSpaces() {
    val filter = SimpleFileLinkFilter(project)
    val line = " File.kt:12 "

    val result = filter.applyFilter(line, line.length) ?: fail()

    assertThat(result.resultItems.map { it.describeLink() }).containsExactly("File.kt:11")
    result.resultItems.forEach { assertHighlight(it, line) }
  }

  @Test
  fun applyFilter_wrappedInParens() {
    val filter = SimpleFileLinkFilter(project)
    val line = "(File.kt:12)"

    val result = filter.applyFilter(line, line.length) ?: fail()

    assertThat(result.resultItems.map { it.describeLink() }).containsExactly("File.kt:11")
    result.resultItems.forEach { assertHighlight(it, line) }
  }

  @Test
  fun applyFilter_wrappedInBrackets() {
    val filter = SimpleFileLinkFilter(project)
    val line = "[File.kt:12]"

    val result = filter.applyFilter(line, line.length) ?: fail()

    assertThat(result.resultItems.map { it.describeLink() }).containsExactly("File.kt:11")
    result.resultItems.forEach { assertHighlight(it, line) }
  }

  @Test
  fun applyFilter_anyWordBoundary() {
    val filter = SimpleFileLinkFilter(project)
    val line = ",File.kt:12!"

    val result = filter.applyFilter(line, line.length) ?: fail()

    assertThat(result.resultItems.map { it.describeLink() }).containsExactly("File.kt:11")
    result.resultItems.forEach { assertHighlight(it, line) }
  }

  @Test
  fun applyFilter_multipleFiles() {
    val filter = SimpleFileLinkFilter(project)
    val line = "hello (MultiFile.kt:12) world"

    val result = filter.applyFilter(line, line.length) ?: fail()

    assertThat(result.resultItems.map { it.describeLink() })
      .containsExactly("[package1/MultiFile.kt:11, package2/MultiFile.kt:11]")
    result.resultItems.forEach { assertHighlight(it, line) }
  }

  @Test
  fun applyFilter_multipleMatches() {
    val filter = SimpleFileLinkFilter(project)
    val line = "File.kt:12 File2.kt:21 "

    val result = filter.applyFilter(line, line.length) ?: fail()

    assertThat(result.resultItems.map { it.describeLink() })
      .containsExactly("File.kt:11", "File2.kt:20")
    result.resultItems.forEach { assertHighlight(it, line) }
  }

  @Test
  fun applyFilter_strangeFileName() {
    val filter = SimpleFileLinkFilter(project)
    val line = "_Strange-File:12"

    val result = filter.applyFilter(line, line.length) ?: fail()

    assertThat(result.resultItems.map { it.describeLink() }).containsExactly("_Strange-File:11")
    result.resultItems.forEach { assertHighlight(it, line) }
  }

  @Test
  fun applyFilter_fileNotInProject() {
    val filter = SimpleFileLinkFilter(project)
    val line = "FileNotInProject:12"

    val result = filter.applyFilter(line, line.length)

    assertThat(result).isNull()
  }
}

/**
 * Asserts that the highlight range in a [ResultItem] corresponds to text in the provided line which
 * matches the hyperlinkInfo.
 *
 * Note that hyperlinkInfo uses 0-index lines while the line text uses 1-index lines, so we need to
 * adjust for that.
 */
@Suppress("UnstableApiUsage")
private fun assertHighlight(resultItem: ResultItem, line: String) {
  val highlight = line.substring(resultItem.highlightStartOffset, resultItem.highlightEndOffset)
  val descriptor = (resultItem.hyperlinkInfo as? MultipleFilesHyperlinkInfo)?.descriptor ?: fail()
  val link = "${descriptor.file.name.substringAfterLast("/")}:${descriptor.line + 1}"
  assertThat(highlight).isEqualTo(link)
}

@Suppress("UnstableApiUsage")
private fun ResultItem.describeLink(): String {
  val info = hyperlinkInfo as MultipleFilesHyperlinkInfo
  val files = info.filesVariants.map { "${it.name}:${info.descriptor?.line}" }
  return when (files.count()) {
    1 -> files[0]
    else -> files.joinToString(prefix = "[", postfix = "]") { it }
  }
}
