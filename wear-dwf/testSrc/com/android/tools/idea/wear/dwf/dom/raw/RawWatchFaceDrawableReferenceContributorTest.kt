/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw

import com.android.resources.ResourceType
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.overrideForTest
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.runInEdtAndGet
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RawWatchFaceDrawableReferenceContributorTest {
  @get:Rule val projectRule = AndroidProjectRule.onDisk().initAndroid(true)

  private val fixture
    get() = projectRule.fixture

  @Before
  fun setup() {
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/").toString()
  }

  @Test
  fun `raw watch face drawable attribute references are not provided when the flag is disabled`() {
    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.overrideForTest(
      false,
      projectRule.testRootDisposable,
    )
    val icon = "style_wfs_40fc6b01_0756_400d_8903_20a8808c8115_1"
    val resource = "wfs_0_c779e5a8_9290_400f_a0ad_761627ba3685"
    fixture.addFileToProject("res/drawable/$icon.png", "")
    fixture.addFileToProject("res/drawable/$resource.png", "")
    projectRule.waitForResourceRepositoryUpdates()

    val watchFaceFile = fixture.copyFileToProject("res/raw/watch_face_example.xml")
    fixture.configureFromExistingVirtualFile(watchFaceFile)

    val iconAttributeReference = runInEdtAndGet {
      fixture.moveCaret("icon=\"$icon|\"")
      fixture.file.findReferenceAt(fixture.caretOffset)
    }
    assertThat(iconAttributeReference).isNull()

    val resourceAttributeReference = runInEdtAndGet {
      fixture.moveCaret("resource=\"$resource|\"")
      fixture.file.findReferenceAt(fixture.caretOffset)
    }
    assertThat(resourceAttributeReference).isNull()
  }

  @Test
  fun `raw watch face drawable attributes have PSI references`() {
    val icon = "style_wfs_40fc6b01_0756_400d_8903_20a8808c8115_1"
    val resource = "wfs_0_c779e5a8_9290_400f_a0ad_761627ba3685"
    fixture.addFileToProject("res/drawable/$icon.png", "")
    fixture.addFileToProject("res/drawable/$resource.png", "")
    projectRule.waitForResourceRepositoryUpdates()

    val watchFaceFile = fixture.copyFileToProject("res/raw/watch_face_example.xml")
    fixture.configureFromExistingVirtualFile(watchFaceFile)

    val iconAttributeReference = runInEdtAndGet {
      fixture.moveCaret("icon=\"$icon|\"")
      fixture.file.findReferenceAt(fixture.caretOffset)
    }
    assertThat(iconAttributeReference).isNotNull()
    val iconReference =
      runReadAction { iconAttributeReference?.resolve() as? ResourceReferencePsiElement }
        ?.resourceReference
    assertThat(iconReference?.resourceType).isEqualTo(ResourceType.DRAWABLE)
    assertThat(iconReference?.resourceUrl?.name).isEqualTo(icon)

    val resourceAttributeReference = runInEdtAndGet {
      fixture.moveCaret("resource=\"$resource|\"")
      fixture.file.findReferenceAt(fixture.caretOffset)
    }
    assertThat(resourceAttributeReference).isNotNull()
    val resourceReference =
      runReadAction { resourceAttributeReference?.resolve() as? ResourceReferencePsiElement }
        ?.resourceReference
    assertThat(resourceReference?.resourceType).isEqualTo(ResourceType.DRAWABLE)
    assertThat(resourceReference?.resourceUrl?.name).isEqualTo(resource)
  }

  @Test
  fun `references are not provided for non-Declarative Watch Face files`() {
    fixture.addFileToProject("res/drawable/some_image.png", "")
    projectRule.waitForResourceRepositoryUpdates()

    val nonDWFFile = fixture.addFileToProject("res/xml/non_dwf_file.xml",
    // language=XML
    """
      <resource>
        <someTag resource="some<caret>_image" />
      </resource>
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(nonDWFFile.virtualFile)

    assertThat(fixture.getReferenceAtCaretPosition()).isNull()
  }

  @Test
  fun `drawables do not show up as completion variants if the flag is disabled`() {
    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.overrideForTest(
      false,
      projectRule.testRootDisposable,
    )
    val drawables =
      listOf(
        "style_wfs_40fc6b01_0756_400d_8903_20a8808c8115_1",
        "wfs_0_c779e5a8_9290_400f_a0ad_761627ba3685",
        "some_other_drawable",
      )
    for (drawable in drawables) {
      fixture.addFileToProject("res/drawable/$drawable.png", "")
    }
    projectRule.waitForResourceRepositoryUpdates()

    val watchFaceFile = fixture.copyFileToProject("res/raw/watch_face_example.xml")
    fixture.configureFromExistingVirtualFile(watchFaceFile)

    runInEdt { fixture.moveCaret("icon=\"|") }
    assertThat(fixture.complete(CompletionType.BASIC)).isEmpty()

    runInEdt { fixture.moveCaret("resource=\"|") }
    assertThat(fixture.complete(CompletionType.BASIC)).isEmpty()
  }

  @Test
  fun `drawables show up as completion variants`() {
    val drawables =
      listOf(
        "style_wfs_40fc6b01_0756_400d_8903_20a8808c8115_1",
        "wfs_0_c779e5a8_9290_400f_a0ad_761627ba3685",
        "some_other_drawable",
      )
    for (drawable in drawables) {
      fixture.addFileToProject("res/drawable/$drawable.png", "")
    }
    projectRule.waitForResourceRepositoryUpdates()

    val watchFaceFile = fixture.copyFileToProject("res/raw/watch_face_example.xml")
    fixture.configureFromExistingVirtualFile(watchFaceFile)

    runInEdt { fixture.moveCaret("icon=\"|") }
    val iconAttributeCompletions = fixture.complete(CompletionType.BASIC).map { it.lookupString }
    assertThat(iconAttributeCompletions).containsExactlyElementsIn(drawables)

    runInEdt { fixture.moveCaret("resource=\"|") }
    val resourceAttributeCompletions =
      fixture.complete(CompletionType.BASIC).map { it.lookupString }
    assertThat(resourceAttributeCompletions).containsExactlyElementsIn(drawables)
  }
}
