/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.espresso

import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.Emulator
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture
import com.android.tools.idea.tests.gui.framework.fixture.RecordingDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.TestClassNameInputDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.clickNo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Ref
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.edt.GuiTask
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.JListFixture
import org.fest.swing.timing.Wait
import org.fest.swing.util.PatternTextMatcher
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern


@RunWith(GuiTestRemoteRunner::class)
class EspressoRecorderTest {
  @JvmField
  @Rule
  val guiTest: GuiTestRule = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)

  @JvmField
  @Rule
  val system: AndroidSystem = AndroidSystem.standard()

  /**
   * The SDK used for this test requires the emulator and the system images to be
   * available. The emulator and system images are not available in the prebuilts
   * SDK. The AvdTestRule should generate such an SDK for us, but we need to set
   * the generated SDK as the SDK to use for our test.
   *
   * Unfortunately, GuiTestRule can overwrite the SDK we set in AvdTestRule, so
   * we need to set this in a place after GuiTestRule has been applied.
   */
  @Before
  fun setupSpecialSdk() {
    GuiTask.execute {
      ApplicationManager.getApplication().runWriteAction {
        IdeSdks.getInstance().setAndroidSdkPath(system.sdk.sourceDir.toFile())
      }
    }
  }

  /**
   * To verify espresso adds dependencies after recording in new project
   *
   * TODO explore if it is feasible to run the test on CI servers given it's heavy computation requirement.
   *
   * This is run to qualify releases. Please involve the test team in substantial changes.
   *
   *
   * TT ID: 908a36f6-4e89-4031-8b3d-c2a75ddc7f08
   *
   *
   * <pre>
   * Test Steps:
   * 1. Import SimpleApplication
   * 2. Run | Record Espresso Test
   * 3. Wait for recording dialog and click OK
   * 5. Wait for test class name input dialog and click OK
   * 6. Click yes to add missing Espresso dependencies
   * 7. Run test
  </pre> *
   *
   *
   */
  @Test
  @Throws(Exception::class)
  @Ignore("b/356678805, b/356678671")
  fun addDependencyOnFly() {
    system.runEmulator(Emulator.SystemImage.API_33) { emulator ->
      guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleApplication", Wait.seconds(120))
      val ideFrameFixture = guiTest.ideFrame()
      emulator.waitForBoot()

      ideFrameFixture.requestProjectSync()
      ideFrameFixture.waitForGradleSyncToFinish(null)

      ideFrameFixture
        .recordEspressoTest()
        .debugToolWindow
        .findContent(TEST_RECORDER_APP)
        .waitForOutput(PatternTextMatcher(DEBUG_OUTPUT), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);

      RecordingDialogFixture.find(guiTest.robot()).clickOk()
      TestClassNameInputDialogFixture.find(guiTest.robot()).clickOk()


      ideFrameFixture.actAndWaitForGradleProjectSyncToFinish {
        MessagesFixture
          .findByTitle(guiTest.robot(), "Missing or obsolete Espresso dependencies")
          // Do not use preview version for test.
          .clickNo()
      }

      try {
        ideFrameFixture.editor
          .waitUntilErrorAnalysisFinishes()
          .moveBetween("public class ", "MyActivityTest")
      }
      catch (ignored: WaitTimedOutError) {
        // We do not care if our cursor is not where it needs to be. We
        // just needed a click inside the editor.
      }

      val popupList = Ref<JListFixture>()
      Wait.seconds(20).expecting("The instrumentation test is ready").until(object : Wait.Objective {
        override fun isMet(): Boolean {
          ideFrameFixture.invokeMenuPath("Run", "Run...")
          val listFixture = JListFixture(guiTest.robot(), GuiTests.waitForPopup(guiTest.robot()))
          if (listFixture.contents().contains(MY_ACTIVITY_TEST)) {
            popupList.set(listFixture)
            return true
          }
          else {
            guiTest.robot().pressAndReleaseKeys(KeyEvent.VK_ESCAPE)
            return false
          }
        }
      })

      popupList.get().clickItem(MY_ACTIVITY_TEST)

      ideFrameFixture
        .runToolWindow
        .findContent(APP_NAME)
        .waitForOutput(PatternTextMatcher(RUN_OUTPUT), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);
    }
  }

  companion object {
    const val APP_NAME: String = "MyActivityTest"
    const val TEST_RECORDER_APP: String = "app"
    private const val MY_ACTIVITY_TEST = "Wrapper[MyActivityTest]"
    val DEBUG_OUTPUT: Pattern = Pattern.compile(".*Connected to the target VM.*",
                                                Pattern.DOTALL)
    val RUN_OUTPUT: Pattern = Pattern.compile(
      ".*adb shell am instrument.*google\\.simpleapplication\\.MyActivityTest.*Tests ran to completion.*", Pattern.DOTALL)
  }
}
