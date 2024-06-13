/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.newpsd

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeSettingsDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.GradleSettingsDialogFixture
import com.android.tools.idea.tests.util.WizardUtils
import com.google.common.truth.Truth
import com.intellij.openapi.util.SystemInfo
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@RunWith(GuiTestRemoteRunner::class)
class StudioDefaultJDKTest {
  @Rule
  @JvmField
  val guiTest = GuiTestRule().withTimeout(15, TimeUnit.MINUTES)

  /**
   * To verify that studio is taking bundled JDK instead of installed JDK by default
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 21086b78-1c44-49b2-9571-998e282fd514
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Make a fresh installation of Android Studio.
   *   2. Create a project with all defaults.
   *   3. Go to Settings/Build, Execution, Deployment/Build Tools/Gradle (Verify 1,2)
   *   4. Change the gradle JDK to any other JAVA path (verify 3)
   *   5. Open gradle.xml (Verify 4)
   *   Verify:
   *   1. Verify if the gradle JDK top combo box is pointed to GRADLE_LOCAL_JAVA_HOME by default.
   *   2. Verify if the gradle JDK bottom combo is assigned to a path and enabled.
   *   3. Verify that the gradle JDK bottom combo box is disabled.
   *   4. Verify that gradle.xml contains "<option name='gradleJvm"'
   *   </pre>
   * <p>
   */
  @Test
  fun testExistingProject() {
    val ide = guiTest.importSimpleApplication()
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    ide.clearNotificationsPresentOnIdeFrame()

    testGradleSettings(ide)

    val editor = ide.editor
    GuiTests.refreshFiles()

    // Additional verification step to make sure `name="gradleJvm" value="#GRADLE_LOCAL_JAVA_HOME"` is added to idea/gradle.xml file.
    editor.open(".idea/gradle.xml")
    val gradleFileContents = editor.currentFileContents
    Truth.assertThat(gradleFileContents).contains("name=\"gradleJvm\" value=\"#GRADLE_LOCAL_JAVA_HOME\"")
  }

  /**
   * To verify that studio is taking bundled JDK instead of installed JDK by default for new projects.
   */
  @Test
  fun testNewProject() {
    WizardUtils.createNewProject(guiTest, "Empty Views Activity")
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    val ide: IdeFrameFixture = guiTest.ideFrame()
    ide.clearNotificationsPresentOnIdeFrame()

    testGradleSettings(ide)
    //gradle.xml file verification is skipped due to flakiness in opening that file for new projects (b/308437557)
  }

  private fun testGradleSettings(ide: IdeFrameFixture) {
    if (SystemInfo.isMac) {
      ide.robot().pressKey(KeyEvent.VK_META);
      ide.robot().pressAndReleaseKey(KeyEvent.VK_COMMA)
      ide.robot().releaseKey(KeyEvent.VK_META);
    } else {
      ide.invokeMenuPath("File", "Settings...")
    }
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    val settings = IdeSettingsDialogFixture.find(ide.robot())
    settings.selectGradlePage()
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    val gradleSettings =  GradleSettingsDialogFixture(settings)
    val gradleJDK = gradleSettings.gradleJDKComboBox()
    val gradleJdkPathEditComboBox = gradleSettings.gradleJDKPathComboBox()

    Truth.assertThat(gradleJDK.selectedItem()).contains("GRADLE_LOCAL_JAVA_HOME")
    Truth.assertThat(gradleJDK.isEnabled).isTrue()
    // GradleJdkPathEditComboBox will be visible only when gradleJDK is selected to GRADLE_LOCAL_JAVA_HOME.
    Truth.assertThat(gradleJdkPathEditComboBox.target().parent.isVisible).isTrue()

    //Change the GRADLE_LOCAL_JAVA_HOME -> any other JDK
    gradleJDK.selectItem(Pattern.compile("jbr-17"))
    Truth.assertThat(gradleJDK.isEnabled).isTrue()
    // If any other JDK other "GRADLE_LOCAL_JAVA_HOME" is selected,
    // the combo box (GradleJdkPathEditComboBox) should not be visible
    Truth.assertThat(gradleJdkPathEditComboBox.target().parent.isVisible).isFalse()

    settings.clickButton("Cancel")
  }
}