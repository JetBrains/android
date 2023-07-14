/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.sdk

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.WritingAccessProvider
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

/** Test for [SdkWritingAccessProvider]. */
class SdkWritingAccessProviderTest {

  // TODO(b/291755082): Update to 34 once 34 sources are published
  @get:Rule
  val projectRule = AndroidProjectRule.withSdk(AndroidVersion(33))

  @get:Rule
  val edtRule = EdtRule()

  @Test
  @RunsInEdt
  fun testSdkFilesAreReadOnly() {
    val fixture = projectRule.fixture
    val sdkFile = fixture.findClass("android.view.View").navigationElement.containingFile.virtualFile

    // Should be a source file, not a .class file.
    assertThat(sdkFile.fileType).isEqualTo(JavaFileType.INSTANCE)

    // Assert that SDK sources are standalone files on disk. If in the future SDK sources are
    // packaged into a jar instead, then SdkWritingAccessProvider may no longer be necessary.
    assertThat(sdkFile.isInLocalFileSystem).isTrue()

    // Assert SdkWritingAccessProvider considers SDK sources read-only.
    val sdkWritingAccessProvider = WritingAccessProvider.EP.findExtensionOrFail(SdkWritingAccessProvider::class.java, fixture.project)
    assertThat(sdkWritingAccessProvider.isPotentiallyWritable(sdkFile)).isFalse()
    assertThat(sdkWritingAccessProvider.requestWriting(listOf(sdkFile))).containsExactly(sdkFile)

    // Make sure the top-level ReadonlyStatusHandler is influenced by SdkWritingAccessProvider.
    assertThat(ReadonlyStatusHandler.ensureFilesWritable(fixture.project, sdkFile)).isFalse()
  }

  @Test
  @RunsInEdt
  fun testNonSdkFile() {
    // Non-SDK files should be writeable as usual.
    val fixture = projectRule.fixture
    val nonSdkFile = fixture.addFileToProject("src/Test.kt", "fun main() {}").virtualFile

    val sdkWritingAccessProvider = WritingAccessProvider.EP.findExtensionOrFail(SdkWritingAccessProvider::class.java, fixture.project)
    assertThat(sdkWritingAccessProvider.isPotentiallyWritable(nonSdkFile)).isTrue()
    assertThat(sdkWritingAccessProvider.requestWriting(listOf(nonSdkFile))).isEmpty()

    assertThat(ReadonlyStatusHandler.ensureFilesWritable(fixture.project, nonSdkFile)).isTrue()
  }
}
