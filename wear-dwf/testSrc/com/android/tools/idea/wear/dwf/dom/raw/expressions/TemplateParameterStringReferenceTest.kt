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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.flags.junit.FlagRule
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.flags.overrideForTest
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.intention.impl.QuickEditAction
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class TemplateParameterStringReferenceTest {

  @get:Rule val edtRule = EdtRule()

  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  @get:Rule
  val flagRule = FlagRule(StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT, true)

  private val fixture
    get() = projectRule.fixture

  @Before
  fun setup() {
    projectRule.fixture.testDataPath =
      TestUtils.resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/").toString()

    fixture.addFileToProject(
      "res/values/strings.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="greeting">Hello, World!</string>
          <string name="title">Some title</string>
          <string name="my_parameter">Some parameter</string>
      </resources>
    """
        .trimIndent(),
    )

    fixture.addFileToProject(
      "res/values-fr/strings.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="greeting">Bonjour, Monde !</string>
      </resources>
    """
        .trimIndent(),
    )
    projectRule.waitForResourceRepositoryUpdates()
  }

  @Test
  fun `references are created for template parameters that are simple strings`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Template>%s
            <Parameter expression="greeting" />
            <Parameter expression="title" />
          </Template>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    // greeting
    run {
      fixture.moveCaret("gree|ting")
      val reference = findTemplateParameterStringReferenceAtCaret()
      assertThat(reference).isNotNull()
      val resolved = reference!!.resolve() as? ResourceReferencePsiElement
      assertThat(resolved).isNotNull()
      assertThat(resolved!!.resourceReference)
        .isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STRING, "greeting"))
    }

    // title
    run {
      fixture.moveCaret("ti|tle")
      val reference = findTemplateParameterStringReferenceAtCaret()
      assertThat(reference).isNotNull()
      val resolved = reference!!.resolve() as? ResourceReferencePsiElement
      assertThat(resolved).isNotNull()
      assertThat(resolved!!.resourceReference)
        .isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STRING, "title"))
    }
  }

  @Test
  fun `references are created for template parameters that are simple strings surrounded by quotes`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Template>%s
            <Parameter expression='"greeting"' />
            <Parameter expression="'greeting'" />
          </Template>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    // double quote
    run {
      fixture.moveCaret("\"gree|ting\"")
      val resolved =
        findTemplateParameterStringReferenceAtCaret()?.resolve() as? ResourceReferencePsiElement
      assertThat(resolved).isNotNull()
      assertThat(resolved!!.resourceReference)
        .isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STRING, "greeting"))
    }

    // simple quote
    run {
      fixture.moveCaret("'gree|ting'")
      val resolved =
        findTemplateParameterStringReferenceAtCaret()?.resolve() as? ResourceReferencePsiElement
      assertThat(resolved).isNotNull()
      assertThat(resolved!!.resourceReference)
        .isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STRING, "greeting"))
    }
  }

  @Test
  fun `references are not created for simple strings that are not part of a template's parameter`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Transform value="greeting" />
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.moveCaret("gree|ting")
    assertThat(findTemplateParameterStringReferenceAtCaret()).isNull()
  }

  @Test
  fun `references are not created when the flag is disabled`() {
    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.overrideForTest(
      false,
      projectRule.testRootDisposable,
    )
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Template>%s
            <Parameter expression="greeting" />
          </Template>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.moveCaret("gree|ting")
    assertThat(findTemplateParameterStringReferenceAtCaret()).isNull()
  }

  @Test
  fun `references are not created for template parameters that are not simple strings`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Template>%s
            <Parameter expression="greeting + 10" />
            <Parameter expression="[STEP_COUNT]" />
            <Parameter expression="23" />
          </Template>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.moveCaret("gree|ting")
    assertThat(findTemplateParameterStringReferenceAtCaret()).isNull()

    fixture.moveCaret("STEP_|COUNT")
    assertThat(findTemplateParameterStringReferenceAtCaret()).isNull()

    fixture.moveCaret("2|3")
    assertThat(findTemplateParameterStringReferenceAtCaret()).isNull()
  }

  @Test
  fun `string resources are autocompleted in template parameters`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Template>%s
            <Parameter expression="$caret" />
          </Template>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    val lookupStrings = fixture.completeBasic().map { it.lookupString }
    assertThat(lookupStrings).containsAllOf("greeting", "title", "my_parameter")
  }

  @Test
  fun `string resources are not autocomplete outside of template parameters`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Transform value="$caret" />
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    val lookupStrings = fixture.completeBasic().map { it.lookupString }
    assertThat(lookupStrings).containsNoneOf("greeting", "title", "my_parameter")
  }

  @Test
  fun `invalid string resources are not highlighted as unknown`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Template>%s
            <Parameter expression="not_a_string_resource" />
          </Template>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)

    fixture.checkHighlighting(true, false, false)
  }

  // Regression test for b/443685010
  @Test
  fun `string resource references are supported in quick edit fragments`() {
    val watchFaceFile =
      fixture.addFileToProject(
        "res/raw/watch_face.xml",
        // language=XML
        """
        <WatchFace>
          <Template>%s
            <Parameter expression="gree${caret}ting" />
          </Template>
        </WatchFace>
      """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(watchFaceFile.virtualFile)
    val injectionTestFixture = InjectionTestFixture(fixture)
    val quickEditHandler =
      QuickEditAction()
        .invokeImpl(
          fixture.project,
          injectionTestFixture.topLevelEditor,
          injectionTestFixture.topLevelFile,
        )
    val fragmentFile = quickEditHandler.newFile
    fixture.openFileInEditor(fragmentFile.virtualFile)

    val reference =
      fixture
        .findElementByText("greeting", WFFExpressionLiteralExpr::class.java)
        .templateParameterReference

    assertThat(reference).isNotNull()
    val resolved = reference?.resolve()
    assertThat(resolved).isNotNull()
    assertThat(resolved).isInstanceOf(ResourceReferencePsiElement::class.java)
    val lookupStrings = fixture.completeBasic().map { it.lookupString }
    assertThat(lookupStrings).containsAllOf("greeting", "title", "my_parameter")
  }

  private fun findTemplateParameterStringReferenceAtCaret(): TemplateParameterStringReference? {
    val injectedElement =
      InjectedLanguageManager.getInstance(projectRule.project)
        .findInjectedElementAt(fixture.file, fixture.caretOffset)
    return injectedElement
      ?.parentOfType<WFFExpressionLiteralExpr>(withSelf = true)
      ?.templateParameterReference
  }

  private val WFFExpressionLiteralExpr.templateParameterReference
    get() = references.filterIsInstance<TemplateParameterStringReference>().firstOrNull()
}
