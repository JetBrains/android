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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRemoteRunner::class)
class AddBenchmarkModuleTest {
  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  @Before
  fun setUp() {
    StudioFlags.NPW_BENCHMARK_TEMPLATE_MODULE.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.NPW_BENCHMARK_TEMPLATE_MODULE.clearOverride()
  }

  /**
   * Verifies that user is able to add a Benchmark Module through the
   * new module wizard.
   *
   * <pre>
   * Test steps:
   * 1. Import simple application project
   * 2. Go to File -> New module to open the new module dialog wizard.
   * 3. Follow through the wizard to add a new Benchmark Module, accepting defaults.
   * 4. Complete the wizard and wait for the build to complete.
   * Verify:
   * 1. The new Benchmark Module is shown in the project explorer pane.
   * 2. Open the Benchmark Module manifest and check that "android:debuggable" and
   * is set to false.
   * 3. Open build.gradle and check that it applies both com.android.library,
   * androidx.benchmark plugins, and the benchmark library added as a androidTest
   * dependency.
   */
  @Test
  @Throws(Exception::class)
  fun addDefaultBenchmarkModule() {
    val ideFrame = guiTest.importSimpleApplication()

    createDefaultBenchmarkModule(ideFrame)

    ideFrame.editor
      .open("benchmark/src/androidTest/AndroidManifest.xml")
      .currentFileContents.run {
      assertThat(this).contains("""android:debuggable="false"""")
    }

    ideFrame.editor
      .open("benchmark/build.gradle")
      .currentFileContents.run {
      assertThat(this).contains("""apply plugin: 'com.android.library'""")
      assertThat(this).contains("""apply plugin: 'androidx.benchmark'""")
      assertThat(this).contains("""androidTestImplementation 'androidx.benchmark:benchmark:'""")
    }
  }

  private fun createDefaultBenchmarkModule(ideFrame: IdeFrameFixture): IdeFrameFixture {
    ideFrame.invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(ideFrame)
      .chooseModuleType("Benchmark Module")
      .clickNext()
      .clickFinish()
      .waitForGradleProjectSyncToFinish()
      .projectView
      .selectAndroidPane()
      .clickPath("benchmark")

    return ideFrame
  }
}
