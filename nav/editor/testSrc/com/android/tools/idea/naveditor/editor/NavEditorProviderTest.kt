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
package com.android.tools.idea.naveditor.editor

import com.android.SdkConstants
import com.android.testutils.waitForCondition
import com.android.tools.idea.common.editor.DesignToolsSplitEditor
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.naveditor.NavEditorRule
import com.android.tools.idea.naveditor.scene.updateHierarchy
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.waitForResourceRepositoryUpdates
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import junit.framework.Assert.assertFalse
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.TimeUnit

class NavEditorProviderTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val provider = NavEditorProvider()

  @After
  fun tearDown() {
    DesignerTypeRegistrar.clearRegisteredTypes()
  }

  @Test
  fun testDoNotAcceptNonLayoutFile() {
    val file = projectRule.fixture.addFileToProject("src/SomeFile.kt", "")
    assertFalse(provider.accept(projectRule.project, file.virtualFile))
  }

  @Test
  fun testDoNotAcceptLayoutFile() {
    val file = projectRule.fixture.addFileToProject("res/layout/my_layout.xml", layoutContent())
    assertFalse(provider.accept(projectRule.project, file.virtualFile))
  }

  @Language("XML")
  private fun layoutContent(): String {
    val layout = ComponentDescriptor(SdkConstants.LINEAR_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
    val sb = StringBuilder(1000)
    layout.appendXml(sb, 0)
    return sb.toString()
  }
}

class NavEditorProviderWithNavEditorTest {
  @get:Rule
  val edtRule = EdtRule()
  private val disposableRule = DisposableRule()
  private val projectRule = AndroidProjectRule.withSdk()
  private val navEditorRule = NavEditorRule(projectRule)


  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(navEditorRule).around(disposableRule)

  @RunsInEdt
  @Test
  fun testCaretNotification() = runBlocking {
    @Language("XML") val fileContents = """
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          app:startDestination="@id/donutList">
          <fragment android:id="@+id/donutList">
              <action
                  android:id="@+id/action_donutList_to_donutEntryDialogFragment"
                  app:destination="@id/donutEntryDialogFragment" />
          </fragment>
          <activity android:id="@+id/donutEntryDialogFragment">
              <deepLink app:uri="myapp://navdonutcreator.com/donutcreator" />
              <argument android:name="itemId" app:argType="long"/>
          </activity>
      </navigation>
    """
    val sampleFile = projectRule.fixture.addFileToProject("src/nav.xml", fileContents.trimIndent())

    projectRule.fixture.configureFromExistingVirtualFile(sampleFile.virtualFile)
    waitForResourceRepositoryUpdates(projectRule.module)

    val editor = NavEditorProvider().createEditor(projectRule.project, sampleFile.virtualFile)
    Disposer.register(disposableRule.disposable, editor)
    waitForCondition(10, TimeUnit.SECONDS) {
      (editor as DesignToolsSplitEditor).designerEditor.component.surface.models.isNotEmpty()
    }
    val surface = (editor as DesignToolsSplitEditor).designerEditor.component.surface
    val model = surface.models.first()
    updateHierarchy(model, model)
    editor.editor.caretModel.moveCaretRelatively(0, 1, false, false, false)
    assertThat(surface.selectionModel.selection).isEqualTo(model.treeReader.components)
    editor.editor.caretModel.moveCaretRelatively(10, 2, false, false, false)
    assertThat(surface.selectionModel.selection).isEqualTo(listOf(model.treeReader.find("donutList")))
    editor.editor.caretModel.moveCaretRelatively(0, 1, false, false, false)
    assertThat(surface.selectionModel.selection).isEqualTo(listOf(model.treeReader.find("action_donutList_to_donutEntryDialogFragment")))
    editor.editor.caretModel.moveCaretRelatively(0, 4, false, false, false)
    assertThat(surface.selectionModel.selection).isEqualTo(listOf(model.treeReader.find("donutEntryDialogFragment")))
    editor.editor.caretModel.moveCaretRelatively(0, 1, false, false, false)
    assertThat(surface.selectionModel.selection).isEqualTo(listOf(model.treeReader.find("donutEntryDialogFragment")))
    editor.editor.caretModel.moveCaretRelatively(0, 1, false, false, false)
    assertThat(surface.selectionModel.selection).isEqualTo(listOf(model.treeReader.find("donutEntryDialogFragment")))
  }
}
