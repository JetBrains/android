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
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openPsd
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectGradleSetting
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectIdeSdksLocationConfigurable
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains

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
 *   3. Go to File > Project Structure > SDK Location > JDK Location (Verify 1)
 *        Unable to verify default jdk,because Android Studio in UI test uses the pre-built jdk for intellij.
 *        Only verifying whether the Embedded JDK is available in JDK options.
 *
 *   Verify:
 *   1. JDK location should be the one bundled with Android Studio.
 *   </pre>
 * <p>
 */
@RunWith(GuiTestRemoteRunner::class)
class StudioDefaultJDKTest {
  @Rule
  @JvmField
  val guiTest = GuiTestRule().withTimeout(7, TimeUnit.MINUTES)

  @Test
  fun verifyDefaultJDK(){
    val ide = guiTest.welcomeFrame()
      .createNewProject()
      .chooseAndroidProjectStep
      .chooseActivity("Empty Activity")
      .wizard()
      .clickNext()
      .clickFinishAndWaitForSyncToFinish()


    ide.openPsd().run{
      selectIdeSdksLocationConfigurable().run{
        selectGradleSetting(ide).run {
          // As the Android Studio is using the default JDK from the intellij,
          // unable to verify if the embedded JDK is used as the default JDK in studio.
          // Instead, verifying only if the Embedded JDK option is available in studio.
          assertContains(gradleJDKComboBox().contents(), "Android Studio java home")
          clickCancel()
        }
      }
      clickCancel()
    }
  }

}