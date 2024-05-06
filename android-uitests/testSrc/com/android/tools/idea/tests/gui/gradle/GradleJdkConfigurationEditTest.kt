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
package com.android.tools.idea.tests.gui.gradle

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeSettingsDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.GradleSettingsDialogFixture
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.JAVA_HOME
import com.intellij.openapi.util.SystemInfo
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.event.KeyEvent

@RunWith(GuiTestRemoteRunner::class)
class GradleJdkConfigurationEditTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  @Test
  fun testNotificationWhenChangeGradleJdkConfiguration() {
    guiTest.importSimpleApplication()
    guiTest.ideFrame().run {
      // Open any file where the notification banner will be displayed
      editor.open("app/build.gradle").waitUntilErrorAnalysisFinishes()
      clearNotificationsPresentOnIdeFrame()

      openSettingsDialog {
        openGradleSettingsPage {
          // Change the Gradle JDK from GRADLE_LOCAL_JAVA_HOME -> JAVA_HOME
          gradleJDKComboBox().selectItem(JAVA_HOME)
          // Apply Gradle JDK configuration changes
          clickButton("OK")

          getEditor().awaitNotification(
            "Gradle JDK configuration has changed. A project sync may be necessary for the IDE to apply those changes.")
        }
      }
    }
  }

  private fun IdeFrameFixture.openSettingsDialog(openedSettingsDialog: IdeSettingsDialogFixture.() -> Unit){
    if (SystemInfo.isMac) {
      robot().run {
        pressKey(KeyEvent.VK_META)
        pressAndReleaseKey(KeyEvent.VK_COMMA)
        releaseKey(KeyEvent.VK_META)
      }
    } else {
      invokeMenuPath("File", "Settings...")
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    openedSettingsDialog(IdeSettingsDialogFixture.find(robot()))
  }

  private fun IdeSettingsDialogFixture.openGradleSettingsPage(openedGradleSettings: GradleSettingsDialogFixture.() -> Unit) {
    selectGradlePage()
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    openedGradleSettings(GradleSettingsDialogFixture(this))
  }
}
