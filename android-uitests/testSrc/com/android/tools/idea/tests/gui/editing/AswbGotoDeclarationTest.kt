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
package com.android.tools.idea.tests.gui.editing

import com.android.tools.idea.tests.gui.framework.BuildSpecificGuiTestRunner
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.TargetBuildSystem
import com.google.common.base.Preconditions.checkState
import com.google.common.base.Verify
import com.intellij.openapi.fileEditor.FileEditorManager
import org.fest.swing.edt.GuiQuery
import org.fest.swing.timing.Wait
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*

@Ignore("b/113117406")
@RunIn(TestGroup.EDITING)
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(BuildSpecificGuiTestRunner.Factory::class)
class AswbGotoDeclarationTest {
  @get:Rule
  val guiTest = GuiTestRule()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): List<TargetBuildSystem.BuildSystem> = Collections.singletonList(TargetBuildSystem.BuildSystem.BAZEL)
  }

  private fun EditorFixture.getCurrentSelection(): String? {
    return GuiQuery.get {
      val editor = FileEditorManager.getInstance(guiTest.ideFrame().project).selectedTextEditor
      checkState(editor != null, "no currently selected text editor")
      editor!!.caretModel.primaryCaret.selectedText
    }
  }

  // Add retry section to avoid b/78573145
  private fun EditorFixture.select(text: String, retryCount: Int): EditorFixture {
    var mySelection: String?
    var count = 0
    do {
      mySelection = select("($text)").getCurrentSelection()
    }
    while (!(mySelection == text || ++count >= retryCount))
    Verify.verify(mySelection == text, "Expect to select $text but was $mySelection")
    return this
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/79783794
  @Test
  @TargetBuildSystem(TargetBuildSystem.BuildSystem.BAZEL)
  fun gotoDeclaration_withExternalResources() {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleBazelApplication", "java/com/foo/gallery/BUILD")
      .editor
      .open("../SimpleBazelApplication/java/com/foo/gallery/activities/MainActivity.java")
      .select("R.style.Base_Highlight", 3)
      .invokeAction(EditorFixture.EditorAction.GOTO_DECLARATION)
    Wait.seconds(20).expecting("file to open")
      .until({ guiTest.ideFrame().editor.currentFileName.equals("styles.xml") })
  }

  @Test
  @TargetBuildSystem(TargetBuildSystem.BuildSystem.BAZEL)
  fun gotoDeclaration_withLocalResources() {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleBazelApplication", "java/com/foo/gallery/BUILD")
      .editor
      .open("../SimpleBazelApplication/java/com/foo/gallery/activities/MainActivity.java")
      .select("R.menu.settings", 3)
      .invokeAction(EditorFixture.EditorAction.GOTO_DECLARATION)
    Wait.seconds(20).expecting("file to open")
      .until({ guiTest.ideFrame().editor.currentFileName.equals("settings.xml") })
  }
}