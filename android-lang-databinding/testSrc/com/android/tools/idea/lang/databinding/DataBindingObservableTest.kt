/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lang.databinding

import com.android.ide.common.blame.Message
import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.lang.databinding.LangDataBindingTestData.PROJECT_WITH_DATA_BINDING_ANDROID_X
import com.android.tools.idea.lang.databinding.LangDataBindingTestData.PROJECT_WITH_DATA_BINDING_SUPPORT
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.ui.UIUtil
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests for DataBinding Reference and Completion from Observable Classes
 */
@RunsInEdt
@RunWith(Parameterized::class)
class DataBindingObservableTest(private val mode: DataBindingMode) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modes() = listOf(DataBindingMode.SUPPORT,
                         DataBindingMode.ANDROIDX)
  }

  /**
   * AndroidGradleProjectRule is needed for importing LiveData and ObservableField
   */
  private val projectRule = AndroidGradleProjectRule()

  @get:Rule
  val ruleChain = org.junit.rules.RuleChain.outerRule(projectRule).around(EdtRule())!!

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a [JavaCodeInsightTestFixture] because our
   * [AndroidProjectRule] is initialized to use the disk.
   *
   * In some cases, using the specific subclass provides us with additional methods we can
   * use to inspect the state of our parsed files. In other cases, it's just fewer characters
   * to type.
   */
  private val fixture: JavaCodeInsightTestFixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  @Before
  fun setUp() {
    fixture.testDataPath = getTestDataPath()
    projectRule.load(when (mode) {
                       DataBindingMode.SUPPORT -> PROJECT_WITH_DATA_BINDING_SUPPORT
                       else -> PROJECT_WITH_DATA_BINDING_ANDROID_X
                     })
  }

  private fun moveCaretToString(substring: String) {
    val editor = fixture.editor
    val text = editor.document.text
    val offset = text.indexOf(substring)
    Assert.assertTrue(offset > 0)
    fixture.editor.caretModel.moveToOffset(offset)
  }

  @Test
  fun dbReferencesLiveData() {
    val assembleDebug = projectRule.invokeTasks("assembleDebug")
    assertWithMessage(assembleDebug.getCompilerMessages(Message.Kind.ERROR).joinToString("\n"))
      .that(assembleDebug.isBuildSuccessful).isTrue()
    val syncState = GradleSyncState.getInstance(projectRule.project)
    assertThat(syncState.isSyncNeeded().toBoolean()).isFalse()
    VirtualFileManager.getInstance().syncRefresh()
    UIUtil.dispatchAllInvocationEvents()

    val layoutFile = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    fixture.configureFromExistingVirtualFile(layoutFile)

    moveCaretToString("getLiveDataString")
    // Call configureFromExistingVirtualFile again to set fixture.file to DbFile at the caret position.
    fixture.configureFromExistingVirtualFile(layoutFile)

    val javaStrValue = fixture.findClass("com.android.example.appwithdatabinding.DummyVo").findMethodsByName("getLiveDataString",
                                                                                                             false)[0].sourceElement!!
    val xmlStrValue = fixture.getReferenceAtCaretPosition()!!
    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlStrValue.isReferenceTo(javaStrValue)).isTrue()
    assertThat(xmlStrValue.resolve()).isEqualTo(javaStrValue)
  }

  @Test
  fun dbReferencesObservableFields() {
    val assembleDebug = projectRule.invokeTasks("assembleDebug")
    assertWithMessage(assembleDebug.getCompilerMessages(Message.Kind.ERROR).joinToString("\n"))
      .that(assembleDebug.isBuildSuccessful).isTrue()
    val syncState = GradleSyncState.getInstance(projectRule.project)
    assertThat(syncState.isSyncNeeded().toBoolean()).isFalse()
    VirtualFileManager.getInstance().syncRefresh()
    UIUtil.dispatchAllInvocationEvents()

    val layoutFile = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    fixture.configureFromExistingVirtualFile(layoutFile)

    moveCaretToString("getObservableFieldString")
    // Call configureFromExistingVirtualFile again to set fixture.file to DbFile at the caret position.
    fixture.configureFromExistingVirtualFile(layoutFile)
    val javaStrValue = fixture.findClass("com.android.example.appwithdatabinding.DummyVo").findMethodsByName("getObservableFieldString",
                                                                                                             false)[0].sourceElement!!
    val xmlStrValue = fixture.getReferenceAtCaretPosition()!!
    // If both of these are true, it means XML can reach Java and Java can reach XML
    assertThat(xmlStrValue.isReferenceTo(javaStrValue)).isTrue()
    assertThat(xmlStrValue.resolve()).isEqualTo(javaStrValue)
  }
}