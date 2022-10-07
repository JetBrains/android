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

import com.android.tools.idea.codenavigation.ComposeTracingNavSource.Companion.filterByMaxLibraryVersion
import com.android.tools.idea.codenavigation.ComposeTracingNavSource.Companion.filterByMaxPackageNameLength
import com.android.tools.idea.codenavigation.ComposeTracingNavSource.LibrarySignature
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import org.apache.maven.artifact.versioning.ComparableVersion
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
      createNavigatable = { files, lineNumber -> FakeNavigatable(files, lineNumber) },
    )

    // when
    val codeLocation = "androidx.compose.foundation.lazy.grid.LazyVerticalGrid (LazyGridDsl.kt:62)".toCodeLocation()!!
    val actual = navSource.lookUp(codeLocation, null) as FakeNavigatable

    // then
    assertThat(actual.files).isEmpty()
    navSource.assertMetricsEventResultCount(0)
  }

  @Test
  fun test_singleFileMatch() {
    // given
    val fileName = "LazyGridDsl.kt"
    val packageName = "androidx.compose.foundation.lazy.grid"
    val lineNumber = 62
    val targetFile = FakeCodeFile("/path/to/file/$fileName", lineNumber, packageName)
    val navSource = ComposeTracingNavSource(
      getFilesByName = { listOf(targetFile) },
      createNavigatable = { files, line -> FakeNavigatable(files, line) },
    )

    // when
    val codeLocation = "$packageName.LazyVerticalGrid ($fileName:$lineNumber)".toCodeLocation()!!
    val actual = navSource.lookUp(codeLocation, null)

    // then
    assertThat((actual as FakeNavigatable).files).isEqualTo(listOf(targetFile))
    navSource.assertMetricsEventResultCount(1)
  }

  @Test
  fun test_filerByPackageName() {
    // given
    val fileName = "LazyGridDsl.kt"
    val packageName = "androidx.compose.foundation.lazy.grid"
    val lineNumber = 62
    val targetFile = FakeCodeFile("/path/to/file/$fileName", lineNumber, packageName)
    val otherFile = FakeCodeFile("/path/to/file/$fileName", lineNumber, "wrong-package")
    val navSource = ComposeTracingNavSource(
      getFilesByName = { listOf(targetFile, otherFile) },
      createNavigatable = { files, line -> FakeNavigatable(files, line) },
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
    val targetFile = FakeCodeFile("/path/to/file/$fileName", lineNumber, packageName)
    val otherFile = FakeCodeFile("/path/to/file/$fileName", lineNumber - 1, packageName)
    val navSource = ComposeTracingNavSource(
      getFilesByName = { listOf(targetFile, otherFile) },
      createNavigatable = { files, line -> FakeNavigatable(files, line) },
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
    val targetFile = FakeCodeFile("/path/to/file/$fileName", lineNumber, packageNameTarget)
    val otherFile = FakeCodeFile("/path/to/file/$fileName", lineNumber, packageNameShorter)
    val navSource = ComposeTracingNavSource(
      getFilesByName = { listOf(targetFile, otherFile) },
      createNavigatable = { files, line -> FakeNavigatable(files, line) },
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
    val targetFile1 = FakeCodeFile("/path/to/file/1/$fileName", lineNumber, packageName)
    val targetFile2 = FakeCodeFile("/path/to/file/2/$fileName", lineNumber, packageName)
    val navSource = ComposeTracingNavSource(
      getFilesByName = { listOf(targetFile1, targetFile2) },
      createNavigatable = { files, line -> FakeNavigatable(files, line) },
    )

    // when
    val codeLocation = "$packageName.LazyVerticalGrid ($fileName:$lineNumber)".toCodeLocation()!!
    val actual = navSource.lookUp(codeLocation, null)

    // then
    assertThat((actual as FakeNavigatable).files).isEqualTo(listOf(targetFile1, targetFile2))
    navSource.assertMetricsEventResultCount(2)
  }

  @Test
  fun test_maxPackageNameLength() {
    // given
    val files: List<PsiClassOwner> = listOf(
      FakeCodeFile("", 1, packageName = "abcd"),
      FakeCodeFile("", 1, packageName = "abc"),
      FakeCodeFile("", 1, packageName = "abcs"),
      FakeCodeFile("", 1, packageName = "ab"),
      FakeCodeFile("", 1, packageName = "bbaa"),
    )

    // when
    val actual = files.filterByMaxPackageNameLength()

    // then
    assertThat(actual).isEqualTo(listOf(files.first(), files[2], files.last()))
  }

  @Test
  fun test_maxLibraryVersion_oneLibraryDuplicated() {
    // given
    val files: List<PsiClassOwner> = listOf(
      FakeCodeFile(path = "", lineCount = 99, packageName = "", librarySignatureString = "g:a:1.3.1-beta02"),
      FakeCodeFile(path = "", lineCount = 99, packageName = "", librarySignatureString = "g:a:1.0.1")
    )

    // when
    val actual = files.filterByMaxLibraryVersion { (it as FakeCodeFile).librarySignature }

    // then
    assertThat(actual).isEqualTo(listOf(files.first()))
  }

  @Test
  fun test_maxLibraryVersion_twoLibrariesDuplicated() {
    // given
    val files: List<PsiClassOwner> = listOf(
      FakeCodeFile(path = "", lineCount = 99, packageName = "", librarySignatureString = "group1:artifact1:1.3.1-beta02"), // 0
      FakeCodeFile(path = "", lineCount = 99, packageName = "", librarySignatureString = "group1:artifact1:1.0.1"), // 1
      FakeCodeFile(path = "", lineCount = 99, packageName = "", librarySignatureString = "group2:artifact2:1.1.0-alpha01"), // 2
      FakeCodeFile(path = "", lineCount = 99, packageName = "", librarySignatureString = "group2:artifact2:1.0.1"), // 3
    )

    // when
    val actual = files.filterByMaxLibraryVersion { (it as FakeCodeFile).librarySignature }

    // then
    assertThat(actual).isEqualTo(listOf(0, 2).map { files[it] })
  }

  @Test
  fun test_maxLibraryVersion_twoLibrariesDuplicated_nonLibraryFilesPresent() {
    // given
    val files: List<PsiClassOwner> = listOf(
      FakeCodeFile(path = "", lineCount = 99, packageName = "", librarySignatureString = null), // 0
      FakeCodeFile(path = "", lineCount = 99, packageName = "", librarySignatureString = "group1:artifact1:1.3.1-beta02"), // 1
      FakeCodeFile(path = "", lineCount = 99, packageName = "", librarySignatureString = "group1:artifact1:1.0.1"), // 2
      FakeCodeFile(path = "", lineCount = 99, packageName = "", librarySignatureString = "group2:artifact2:1.1.0-alpha01"), // 3
      FakeCodeFile(path = "", lineCount = 99, packageName = "", librarySignatureString = "group2:artifact2:1.0.1"), // 4
      FakeCodeFile(path = "", lineCount = 99, packageName = "", librarySignatureString = null), // 5
    )

    // when
    val actual = files.filterByMaxLibraryVersion { (it as FakeCodeFile).librarySignature }

    // then
    assertThat(actual).isEqualTo(listOf(0, 1, 3, 5).map { files[it] })
  }

  @Test
  fun test_allRules_integration() {
    // given
    val codeLocation = "pn.LazyVerticalGrid (F.kt:50)".toCodeLocation()!! // package=pn, lineNumber=50
    val files: List<PsiClassOwner> = listOf(
      FakeCodeFile(path = "/p/F.kt", lineCount = 99, packageName = "p", librarySignatureString = null), // 0 packageName
      FakeCodeFile(path = "/p/F.kt", lineCount = 99, packageName = "pn", librarySignatureString = null), // 1 OK
      FakeCodeFile(path = "/p/F.kt", lineCount = 99, packageName = "pn", librarySignatureString = "group1:artifact1:1.3.1-beta02"), // 2 OK
      FakeCodeFile(path = "/p/F.kt", lineCount = 99, packageName = "pn", librarySignatureString = "group1:artifact1:1.0.1"), // 3 version
      FakeCodeFile(path = "/p/F.kt", lineCount = 99, packageName = "pn", librarySignatureString = "group2:artifact2:1.1.0-alpha01"), // 4 OK
      FakeCodeFile(path = "/p/F.kt", lineCount = 99, packageName = "pn", librarySignatureString = "group2:artifact2:1.0.1"), // 5 version
      FakeCodeFile(path = "/p/F.kt", lineCount = 99, packageName = "pn", librarySignatureString = null), // 6 OK
      FakeCodeFile(path = "/p/F.kt", lineCount = 10, packageName = "pn", librarySignatureString = null), // 7 lineCount
      FakeCodeFile(path = "/p/F.kt", lineCount = 99, packageName = "qwerty", librarySignatureString = null), // 8 packageName
    )
    val navSource = ComposeTracingNavSource(
      getFilesByName = { files },
      createNavigatable = { fs, line -> FakeNavigatable(fs, line) },
      mavenSignatureResolver = { (it as FakeCodeFile).librarySignature }
    )

    // when
    val actual = navSource.lookUp(codeLocation, null)

    // then
    assertThat(actual).isEqualTo(FakeNavigatable(listOf(1, 2, 4, 6).map { files[it] }, 50))
    navSource.assertMetricsEventResultCount(4)
  }
}

private data class FakeNavigatable(val files: List<PsiFile>, val lineNumber: Int) : Navigatable {
  override fun navigate(requestFocus: Boolean): Unit = throw IllegalStateException("Not implemented")
  override fun canNavigate(): Boolean = throw IllegalStateException("Not implemented")
  override fun canNavigateToSource(): Boolean = throw IllegalStateException("Not implemented")
}

private class FakeCodeFile private constructor(
  private val virtualFile: VirtualFile,
  private val fileContents: String,
  private val packageName: String,
  val librarySignature: LibrarySignature?,
  mock: PsiClassOwner = mock(PsiClassOwner::class.java),
) : PsiClassOwner by mock {
  constructor(path: String, lineCount: Int, packageName: String, librarySignatureString: String? = null) : this(
    virtualFile = mock(VirtualFile::class.java).also { Mockito.`when`(it.path).thenReturn(path) },
    fileContents = (1..lineCount).joinToString(separator = "\n") { "" },
    packageName = packageName,
    librarySignature = librarySignatureString?.let {
      val (groupId, artifactId, version) = it.split(":")
      LibrarySignature(groupId, artifactId, ComparableVersion(version))
    }
  )
  override fun getPackageName() = packageName
  override fun getVirtualFile() = virtualFile
  override fun getText(): String = fileContents
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

private fun ComposeTracingNavSource.assertMetricsEventResultCount(expectedCount: Int) {
  with(lastMetricsEvent!!) {
    assertThat(hasAndroidProfilerEvent()).isTrue()
    assertThat(androidProfilerEvent.hasResolveComposeTracingCodeLocationMetadata()).isTrue()
    assertThat(androidProfilerEvent.resolveComposeTracingCodeLocationMetadata.resultCount).isEqualTo(expectedCount)
  }
}
