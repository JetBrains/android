/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.codenavigation

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.util.regex.Matcher
import java.util.regex.Pattern

class ComposeTracingNavSourceTest {
  @Test
  fun test_noResults() {
    // given
    val navSource = ComposeTracingNavSource(
      getFilesByName = { emptyList() },
      createNavigatable = { files, lineNumber -> FakeNavigatable(files, lineNumber) }
    )

    // when
    val codeLocation = "androidx.compose.foundation.lazy.grid.LazyVerticalGrid (LazyGridDsl.kt:62)".toCodeLocation()!!
    val actual = navSource.lookUp(codeLocation, null)

    // then
    assertThat(actual).isEqualTo(null)
  }

  @Test
  fun test_singleFileMatch() {
    // given
    val fileName = "LazyGridDsl.kt"
    val packageName = "androidx.compose.foundation.lazy.grid"
    val lineNumber = 62
    val targetFile = CodeFile("/path/to/file/$fileName", packageName, lineNumber).toPsiClassOwner()
    val navSource = ComposeTracingNavSource(
      getFilesByName = { listOf(targetFile) },
      createNavigatable = { files, line -> FakeNavigatable(files, line) }
    )

    // when
    val codeLocation = "$packageName.LazyVerticalGrid ($fileName:$lineNumber)".toCodeLocation()!!
    val actual = navSource.lookUp(codeLocation, null)

    // then
    assertThat((actual as FakeNavigatable).files).isEqualTo(listOf(targetFile))
  }

  @Test
  fun test_filerByPackageName() {
    // given
    val fileName = "LazyGridDsl.kt"
    val packageName = "androidx.compose.foundation.lazy.grid"
    val lineNumber = 62
    val targetFile = CodeFile("/path/to/file/$fileName", packageName, lineNumber).toPsiClassOwner()
    val otherFile = CodeFile("/path/to/file/$fileName", "wrong-package", lineNumber).toPsiClassOwner()
    val navSource = ComposeTracingNavSource(
      getFilesByName = { listOf(targetFile, otherFile) },
      createNavigatable = { files, line -> FakeNavigatable(files, line) }
    )

    // when
    val codeLocation = "$packageName.LazyVerticalGrid ($fileName:$lineNumber)".toCodeLocation()!!
    val actual = navSource.lookUp(codeLocation, null)

    // then
    assertThat((actual as FakeNavigatable).files).isEqualTo(listOf(targetFile))
  }

  @Test
  fun test_filterByFileLength() {
    // given
    val fileName = "LazyGridDsl.kt"
    val packageName = "androidx.compose.foundation.lazy.grid"
    val lineNumber = 62
    val targetFile = CodeFile("/path/to/file/$fileName", packageName, lineNumber).toPsiClassOwner()
    val otherFile = CodeFile("/path/to/file/$fileName", packageName, lineNumber - 1).toPsiClassOwner()
    val navSource = ComposeTracingNavSource(
      getFilesByName = { listOf(targetFile, otherFile) },
      createNavigatable = { files, line -> FakeNavigatable(files, line) }
    )

    // when
    val codeLocation = "$packageName.LazyVerticalGrid ($fileName:$lineNumber)".toCodeLocation()!!
    val actual = navSource.lookUp(codeLocation, null)

    // then
    assertThat((actual as FakeNavigatable).files).isEqualTo(listOf(targetFile))
  }

  @Test
  fun test_filterByPackageNameLength() {
    // given
    val fileName = "LazyGridDsl.kt"
    val packageNameTarget = "androidx.compose.foundation.lazy.grid"
    val packageNameShorter = "androidx.compose.foundation.lazy"
    val lineNumber = 62
    val targetFile = CodeFile("/path/to/file/$fileName", packageNameTarget, lineNumber).toPsiClassOwner()
    val otherFile = CodeFile("/path/to/file/$fileName", packageNameShorter, lineNumber).toPsiClassOwner()
    val navSource = ComposeTracingNavSource(
      getFilesByName = { listOf(targetFile, otherFile) },
      createNavigatable = { files, line -> FakeNavigatable(files, line) }
    )

    // when
    val codeLocation = "$packageNameTarget.LazyVerticalGrid ($fileName:$lineNumber)".toCodeLocation()!!
    val actual = navSource.lookUp(codeLocation, null)

    // then
    assertThat((actual as FakeNavigatable).files).isEqualTo(listOf(targetFile))
  }

  @Test
  fun test_multipleResults() {
    // given
    val fileName = "LazyGridDsl.kt"
    val packageName = "androidx.compose.foundation.lazy.grid"
    val lineNumber = 62
    val targetFile1 = CodeFile("/path/to/file/1/$fileName", packageName, lineNumber).toPsiClassOwner()
    val targetFile2 = CodeFile("/path/to/file/2/$fileName", packageName, lineNumber).toPsiClassOwner()
    val navSource = ComposeTracingNavSource(
      getFilesByName = { listOf(targetFile1, targetFile2) },
      createNavigatable = { files, line -> FakeNavigatable(files, line) }
    )

    // when
    val codeLocation = "$packageName.LazyVerticalGrid ($fileName:$lineNumber)".toCodeLocation()!!
    val actual = navSource.lookUp(codeLocation, null)

    // then
    assertThat((actual as FakeNavigatable).files).isEqualTo(listOf(targetFile1, targetFile2))
  }
}

private data class FakeNavigatable(val files: List<PsiFile>, val lineNumber: Int) : Navigatable {
  override fun navigate(requestFocus: Boolean): Unit = throw IllegalStateException("Not implemented")
  override fun canNavigate(): Boolean = throw IllegalStateException("Not implemented")
  override fun canNavigateToSource(): Boolean = throw IllegalStateException("Not implemented")
}

private data class CodeFile(val path: String, val packageName: String, val lineCount: Int)

private fun CodeFile.toPsiClassOwner(): PsiClassOwner = this.run {
  val result = mock(PsiClassOwner::class.java)
  Mockito.`when`(result.packageName).thenReturn(packageName)
  val fakeText = List(lineCount) { "" }.joinToString(separator = "\n") { it }
  Mockito.`when`(result.text).thenReturn(fakeText)
  val fakeVirtualFile = mock(VirtualFile::class.java)
  Mockito.`when`(result.virtualFile).thenReturn(fakeVirtualFile)
  Mockito.`when`(fakeVirtualFile.path).thenReturn(path)
  return result
}

/**
 * Converts a Compose trace-string to a [CodeLocation] object
 *
 * Example:
 * - Input: androidx.compose.runtime.produceState (ProduceState.kt:213)
 * - Output: CodeLocation { fullComposableName = "androidx.compose.runtime.produceState", fileName = "ProduceState.kt", lineNumber = 213 }
 */
private fun String.toCodeLocation(): CodeLocation? {
  val rx = Pattern.compile("^(.*) \\((.*\\.(kt|java)):(-?\\d+)\\)$")
  val matcher: Matcher = rx.matcher(this)
  return if (!matcher.find()) null
  else CodeLocation.Builder(null as String?)
    .setFullComposableName(matcher.group(1))
    .setFileName(matcher.group(2))
    .setLineNumber(matcher.group(4).toInt())
    .build()
}