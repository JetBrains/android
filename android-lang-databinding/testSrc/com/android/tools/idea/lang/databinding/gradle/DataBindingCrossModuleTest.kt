/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.lang.databinding.gradle

import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.snapshots.testProjectTemplateFromPath
import com.android.tools.idea.lang.databinding.LangDataBindingTestData.PROJECT_WITH_DATA_BINDING_ANDROID_X
import com.android.tools.idea.lang.databinding.LangDataBindingTestData.PROJECT_WITH_DATA_BINDING_SUPPORT
import com.android.tools.idea.lang.databinding.getTestDataPath
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.buildAndWait
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.ui.UIUtil
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests for DataBinding Reference and Completion across different modules.
 */
@RunsInEdt
@RunWith(Parameterized::class)
class DataBindingCrossModuleTest(private val mode: DataBindingMode) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modes() = listOf(DataBindingMode.SUPPORT, DataBindingMode.ANDROIDX)
  }

  @get:Rule
  val projectRule =
    AndroidProjectRule.testProject(
      testProjectTemplateFromPath(
        path = when (mode) {
          DataBindingMode.SUPPORT -> PROJECT_WITH_DATA_BINDING_SUPPORT
          else -> PROJECT_WITH_DATA_BINDING_ANDROID_X
        },
        testDataPath = getTestDataPath()
      )
    )

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
    get() = projectRule.fixture

  @Test
  fun dbReferencesIncludedLayoutBindingFromLibModule() {
    val assembleDebug = projectRule.project.buildAndWait {
      it.assemble(TestCompileType.NONE)
    }
    assertThat(assembleDebug.isBuildSuccessful).isTrue()
    val syncState = GradleSyncState.getInstance(projectRule.project)
    assertThat(syncState.isSyncNeeded().toBoolean()).isFalse()
    VirtualFileManager.getInstance().syncRefresh()
    UIUtil.dispatchAllInvocationEvents()

    val activityFile = projectRule.project.baseDir.findFileByRelativePath(
      "app/src/main/java/com/android/example/appwithdatabinding/MainActivity.java")!!
    fixture.configureFromExistingVirtualFile(activityFile)

    // Move caret into "binding.included.setStr("x");"
    val editor = fixture.editor
    val text = editor.document.text
    val offset = text.indexOf("ded.setStr")
    Assert.assertTrue(offset > 0)
    fixture.editor.caretModel.moveToOffset(offset)

    // Call configureFromExistingVirtualFile again to set fixture.file to DbFile at the caret position.
    fixture.configureFromExistingVirtualFile(activityFile)

    (fixture.getReferenceAtCaretPosition()!!.resolve() as LightBindingClass.LightDataBindingField).let { includedField ->
      assertThat(includedField.name).isEqualTo("included")
      // Make sure field's package comes from the lib module, where it lives, not app, the calling module
      assertThat(includedField.type.canonicalText).isEqualTo("com.android.example.lib.databinding.IncludedBinding")
    }
  }
}