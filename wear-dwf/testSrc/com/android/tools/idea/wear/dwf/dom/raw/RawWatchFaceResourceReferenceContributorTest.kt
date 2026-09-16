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

class RawWatchFaceResourceReferenceContributorTest {
  @get:Rule val projectRule = AndroidProjectRule.onDisk().initAndroid(true)

  private val fixture
    get() = projectRule.fixture

  private val icon = "style_wfs_40fc6b01_0756_400d_8903_20a8808c8115_1"
  private val resource = "wfs_0_c779e5a8_9290_400f_a0ad_761627ba3685"
  private val drawables = listOf(icon, resource, "some_drawable")

  private val fonts = listOf("font1", "font2", "roboto")

  @Before
  fun setup() {
    projectRule.fixture.testDataPath = resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/").toString()

    for (drawable in drawables) {
      fixture.addFileToProject("res/drawable/$drawable.png", "")
    }
    for (font in fonts) {
      fixture.addFileToProject("res/font/$font.png", "")
    }
    projectRule.waitForResourceRepositoryUpdates()
  }

  @Test
  fun `raw watch face drawable attribute references are not provided when the flag is disabled`() {
    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.overrideForTest(false, projectRule.testRootDisposable)
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
    val watchFaceFile = fixture.copyFileToProject("res/raw/watch_face_example.xml")
    fixture.configureFromExistingVirtualFile(watchFaceFile)

    val iconAttributeReference = runInEdtAndGet {
      fixture.moveCaret("icon=\"$icon|\"")
      fixture.file.findReferenceAt(fixture.caretOffset)
    }
    assertThat(iconAttributeReference).isNotNull()
    val iconReference = runReadAction { iconAttributeReference?.resolve() as? ResourceReferencePsiElement }?.resourceReference
    assertThat(iconReference?.resourceType).isEqualTo(ResourceType.DRAWABLE)
    assertThat(iconReference?.resourceUrl?.name).isEqualTo(icon)

    val resourceAttributeReference = runInEdtAndGet {
      fixture.moveCaret("resource=\"$resource|\"")
      fixture.file.findReferenceAt(fixture.caretOffset)
    }
    assertThat(resourceAttributeReference).isNotNull()
    val resourceReference = runReadAction { resourceAttributeReference?.resolve() as? ResourceReferencePsiElement }?.resourceReference
    assertThat(resourceReference?.resourceType).isEqualTo(ResourceType.DRAWABLE)
    assertThat(resourceReference?.resourceUrl?.name).isEqualTo(resource)
  }

  @Test
  fun `references are not provided for non-Declarative Watch Face files`() {
    fixture.addFileToProject("res/drawable/some_image.png", "")
    projectRule.waitForResourceRepositoryUpdates()

    val nonDWFFile =
      fixture.addFileToProject(
        "res/xml/non_dwf_file.xml",
        // language=XML
        """
        <resource>
          <someTag resource="some<caret>_image" />
        </resource>
        """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(nonDWFFile.virtualFile)

    assertThat(fixture.getReferenceAtCaretPosition()).isNull()
  }

  @Test
  fun `drawables do not show up as completion variants if the flag is disabled`() {
    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.overrideForTest(false, projectRule.testRootDisposable)
    val watchFaceFile = fixture.copyFileToProject("res/raw/watch_face_example.xml")
    fixture.configureFromExistingVirtualFile(watchFaceFile)

    runInEdt { fixture.moveCaret("icon=\"|") }
    assertThat(fixture.complete(CompletionType.BASIC)).isEmpty()

    runInEdt { fixture.moveCaret("resource=\"|") }
    assertThat(fixture.complete(CompletionType.BASIC)).isEmpty()

    runInEdt { fixture.moveCaret("defaultImageResource=\"|") }
    assertThat(fixture.complete(CompletionType.BASIC)).isEmpty()
  }

  @Test
  fun `drawables show up as completion variants`() {
    val watchFaceFile = fixture.copyFileToProject("res/raw/watch_face_example.xml")
    fixture.configureFromExistingVirtualFile(watchFaceFile)

    runInEdt { fixture.moveCaret("icon=\"|") }
    val iconAttributeCompletions = fixture.complete(CompletionType.BASIC).map { it.lookupString }
    assertThat(iconAttributeCompletions).containsExactlyElementsIn(drawables)

    runInEdt { fixture.moveCaret("resource=\"|") }
    val resourceAttributeCompletions = fixture.complete(CompletionType.BASIC).map { it.lookupString }
    assertThat(resourceAttributeCompletions).containsExactlyElementsIn(drawables)

    runInEdt { fixture.moveCaret(" defaultImageResource=\"|") }
    val defaultImageResourceAttributeCompletions = fixture.complete(CompletionType.BASIC).map { it.lookupString }
    assertThat(defaultImageResourceAttributeCompletions).containsExactlyElementsIn(drawables)
  }

  @Test
  // Regression test for b/477170943
  fun `font attribute references have PSI references`() {
    val font = "my_custom_font"
    fixture.addFileToProject("res/font/$font.ttf", "")
    projectRule.waitForResourceRepositoryUpdates()

    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watchface.xml",
        """
      <WatchFace width="450" height="450">
        <Scene>
          <PartText x="0" y="0" width="100" height="100">
            <Text align="CENTER">
              <Font family="$font" size="20" />
            </Text>
          </PartText>
        </Scene>
      </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    val familyAttributeReference = runInEdtAndGet {
      fixture.moveCaret("family=\"$font|\"")
      fixture.file.findReferenceAt(fixture.caretOffset)
    }
    assertThat(familyAttributeReference).isNotNull()
    val resourceReference = runReadAction { familyAttributeReference?.resolve() as? ResourceReferencePsiElement }?.resourceReference
    assertThat(resourceReference?.resourceType).isEqualTo(ResourceType.FONT)
    assertThat(resourceReference?.resourceUrl?.name).isEqualTo(font)
  }

  @Test
  // Regression test for b/477170943
  fun `SYNC_TO_DEVICE does not have a PSI reference`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watchface.xml",
        """
        <WatchFace width="450" height="450">
          <Scene>
            <PartText x="0" y="0" width="100" height="100">
              <Text align="CENTER">
                <Font family="SYNC_TO_DEVICE" size="20" />
              </Text>
            </PartText>
          </Scene>
        </WatchFace>
        """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    val familyAttributeReference = runInEdtAndGet {
      fixture.moveCaret("family=\"SYNC_TO_DEVICE|\"")
      fixture.file.findReferenceAt(fixture.caretOffset)
    }
    assertThat(familyAttributeReference).isNull()
  }

  @Test
  // Regression test for b/477170943
  fun `fonts show up as completion variants`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watchface.xml",
        """
        <WatchFace width="450" height="450">
          <Scene>
            <PartText x="0" y="0" width="100" height="100">
              <Text align="CENTER">
                <Font family="" size="20" />
              </Text>
            </PartText>
          </Scene>
        </WatchFace>
        """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    runInEdt { fixture.moveCaret("family=\"|") }
    val completions = fixture.complete(CompletionType.BASIC).map { it.lookupString }
    assertThat(completions).containsExactlyElementsIn(fonts + "SYNC_TO_DEVICE")
  }
}
