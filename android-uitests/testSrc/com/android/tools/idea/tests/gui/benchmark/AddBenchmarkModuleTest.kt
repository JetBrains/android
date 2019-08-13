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
package com.android.tools.idea.tests.gui.benchmark

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRemoteRunner::class)
class AddBenchmarkModuleTest {
  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  /**
   * Verifies that user is able to add a Benchmark Module through the
   * new module wizard.
   *
   * <pre>
   * Test steps:
   * 1. Import simple application project
   * 2. Go to File -> New module to open the new module dialog wizard.
   * 3. Choose Benchmark Module and click next.
   * 4. Select Java as the source lanaguage.
   * 5. Complete the wizard and wait for the build to complete.
   * Verify:
   * 1. The new Benchmark Module is shown in the project explorer pane.
   * 2. Open the Benchmark Module manifest and check that "android:debuggable" and
   * is set to false.
   * 3. Open build.gradle and check that it applies both com.android.library,
   * androidx.benchmark plugins, the test runner, and the benchmark library added
   * as a androidTest dependency.
   */
  @Test
  @Throws(Exception::class)
  fun addJavaBenchmarkModule() {
    val ideFrame = guiTest.importSimpleApplication()
    ideFrame.invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(ideFrame)
      .clickNextToBenchmarkModule()
      .setSourceLanguage("Java")
      .wizard()
      .clickFinish()
      .waitForGradleProjectSyncToFinish()
      .projectView
      .selectAndroidPane()
      .clickPath("benchmark")

    guiTest.getProjectFileText("benchmark/src/androidTest/AndroidManifest.xml").run {
      assertThat(this).contains("""android:debuggable="false"""")
    }

    guiTest.getProjectFileText("benchmark/build.gradle").run {
      assertThat(this).contains("""apply plugin: 'com.android.library'""")
      assertThat(this).contains("""apply plugin: 'androidx.benchmark'""")
      assertThat(this).contains("""testInstrumentationRunner 'androidx.benchmark.junit4.AndroidBenchmarkRunner'""")
      assertThat(this).contains("""androidTestImplementation 'androidx.benchmark:benchmark-junit4:""")
    }
  }

  /**
   * Verifies that user is able to add a Benchmark Module through the
   * new module wizard.
   *
   * <pre>
   * Test steps:
   * 1. Import simple application project
   * 2. Go to File -> New module to open the new module dialog wizard.
   * 3. Choose Benchmark Module and click next.
   * 4. Select Kotlin as the source lanaguage.
   * 5. Complete the wizard and wait for the build to complete.
   * Verify:
   * 1. The new Benchmark Module is shown in the project explorer pane.
   * 2. Open the Benchmark Module manifest and check that "android:debuggable" and
   * is set to false.
   * 3. Open build.gradle and check that it applies both com.android.library,
   * androidx.benchmark plugins, the test runner, and the benchmark library added
   * as a androidTest dependency.
   */
  @Test
  @Throws(Exception::class)
  fun addKotlinBenchmarkModule() {
    val ideFrame = guiTest.importSimpleApplication()
    ideFrame.invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(ideFrame)
      .clickNextToBenchmarkModule()
      .setSourceLanguage("Kotlin")
      .wizard()
      .clickFinish()
      .waitForGradleProjectSyncToFinish()
      .projectView
      .selectAndroidPane()
      .clickPath("benchmark")

    guiTest.getProjectFileText("benchmark/src/androidTest/AndroidManifest.xml").run {
      assertThat(this).contains("""android:debuggable="false"""")
    }

    guiTest.getProjectFileText("benchmark/build.gradle").run {
      assertThat(this).contains("""apply plugin: 'com.android.library'""")
      assertThat(this).contains("""apply plugin: 'androidx.benchmark'""")
      assertThat(this).contains("""testInstrumentationRunner 'androidx.benchmark.junit4.AndroidBenchmarkRunner'""")
      assertThat(this).contains("""androidTestImplementation 'androidx.benchmark:benchmark-junit4:""")
    }
  }
}
