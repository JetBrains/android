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
package com.android.tools.idea.tests.gui.performance

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.UseBleak
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRemoteRunner::class)
@RunIn(TestGroup.PERFORMANCE)
class NavEditorMemoryUseTest {

  @Rule @JvmField val guiTest = GuiTestRule()

  @Test
  @UseBleak
  fun openAndCloseTab() {
    val ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("Navigation")
    guiTest.runWithBleak {
      ideFrame.editor
        .open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN)
        .getLayoutEditor()
        .waitForRenderToFinish()
      ideFrame.editor.close()
    }
  }

}