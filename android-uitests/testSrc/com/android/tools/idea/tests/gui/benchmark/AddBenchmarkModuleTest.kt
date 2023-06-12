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

import com.android.flags.junit.FlagRule
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.benchmark.BenchmarkModuleType
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture
import com.android.tools.idea.tests.gui.kotlin.JavaToKotlinConversionTest
import com.android.tools.idea.tests.util.WizardUtils
import com.android.tools.idea.wizard.template.Language.Java
import com.android.tools.idea.wizard.template.Language.Kotlin
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.timing.Wait
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class AddBenchmarkModuleTest {
  @get:Rule
  val guiTest = GuiTestRule().withTimeout(10, TimeUnit.MINUTES)

  @get:Rule
  val flagRule = FlagRule(StudioFlags.NPW_NEW_MACRO_BENCHMARK_MODULE, true)

  @Before
  @Throws(java.lang.Exception::class)
  fun setUp() {
    val ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleAndroidxApplication")
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()

    //Clearing notifications present on the screen.
    ideFrame.clearNotificationsPresentOnIdeFrame()
    guiTest.waitForAllBackgroundTasksToBeCompleted()
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
   * 4. Select Microbenchmark as the benchmark module type.
   * 5. Select Java as the source language.
   * 6. Complete the wizard and wait for the build to complete.
   * Verify:
   * 1. The new Benchmark Module is shown in the project explorer pane.
   * 2. Open the Benchmark Module manifest and check that "android:debuggable" and
   * is set to false.
   * 3. Open build.gradle and check that it applies:
   *     - both com.android.library and androidx.benchmark plugins
   *     - testBuildType set to "release"
   *     - the test runner
   *     - the benchmark library added as an androidTest dependency.
   */
  @Test
  @Throws(Exception::class)
  fun addJavaMicrobenchmarkModule() {
    NewModuleWizardFixture.find(guiTest.ideFrame().invokeMenuPath("File", "New", "New Module\u2026"))
      .clickNextToBenchmarkModule()
      .selectBenchmarkType(BenchmarkModuleType.MICROBENCHMARK)
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Java)
      .wizard()
      .clickFinishAndWaitForSyncToFinish(Wait.seconds(150))

    guiTest.getProjectFileText("benchmark/src/androidTest/AndroidManifest.xml").run {
      assertThat(this).contains("""android:debuggable="false"""")
    }

    guiTest.getProjectFileText("benchmark/build.gradle").run {
      assertThat(this).contains("""id 'com.android.library'""")
      assertThat(this).contains("""id 'androidx.benchmark'""")
      assertThat(this).contains("""testBuildType = "release"""")
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
   * 4. Select Microbenchmark as the benchmark module type.
   * 5. Select Kotlin as the source language.
   * 6. Complete the wizard and wait for the build to complete.
   * Verify:
   * 1. The new Benchmark Module is shown in the project explorer pane.
   * 2. Open the Benchmark Module manifest and check that "android:debuggable" and
   * is set to false.
   * 3. Open build.gradle and check that it applies:
   *     - both com.android.library and androidx.benchmark plugins
   *     - testBuildType set to "release"
   *     - the test runner
   *     - the benchmark library added as an androidTest dependency.
   */
  @Test
  @Throws(Exception::class)
  fun addKotlinMicrobenchmarkModule() {
    NewModuleWizardFixture.find(guiTest.ideFrame().invokeMenuPath("File", "New", "New Module\u2026"))
      .clickNextToBenchmarkModule()
      .selectBenchmarkType(BenchmarkModuleType.MICROBENCHMARK)
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Kotlin)
      .wizard()
      .clickFinishAndWaitForSyncToFinish(Wait.seconds(150))

    guiTest.getProjectFileText("benchmark/src/androidTest/AndroidManifest.xml").run {
      assertThat(this).contains("""android:debuggable="false"""")
    }

    guiTest.getProjectFileText("benchmark/build.gradle").run {
      assertThat(this).contains("""id 'com.android.library'""")
      assertThat(this).contains("""id 'androidx.benchmark'""")
      assertThat(this).contains("""testBuildType = "release"""")
      assertThat(this).contains("""testInstrumentationRunner 'androidx.benchmark.junit4.AndroidBenchmarkRunner'""")
      assertThat(this).contains("""androidTestImplementation 'androidx.benchmark:benchmark-junit4:""")
    }
  }

  /**
   * Verifies that user is able to add a Java Macrobenchmark Module through the
   * new module wizard.
   *
   * <pre>
   * Test steps:
   * 1. Import simple application project
   * 2. Go to File -> New module to open the new module dialog wizard.
   * 3. Choose Benchmark Module and click next.
   * 4. Select Macrobenchmark as the benchmark module type.
   * 5. Select Java as the source language.
   * 6. Complete the wizard and wait for the build to complete.
   * Verify:
   * 1. The new Marobenchmark Module is shown in the project explorer pane.
   * 2. Open the Macrobenchmark Module main manifest and check that:
   *     - Query for target application is declared
   *     - android.permission.WRITE_EXTERNAL_STORAGE is declared
   * 3. Open build.gradle and check that:
   *     - com.android.test plugin is applied
   *     - the macrobenchmark library added as an implementation dependency
   *     - targetProjectPath is set to :app
   *     - The android.experimental.self-instrumenting property is set to true
   */
  @Test
  @Throws(Exception::class)
  fun addJavaMacrobenchmarkModule() {
    NewModuleWizardFixture.find(guiTest.ideFrame().invokeMenuPath("File", "New", "New Module\u2026"))
      .clickNextToBenchmarkModule()
      .selectBenchmarkType(BenchmarkModuleType.MACROBENCHMARK)
      .enterPackageName("com.example.macrobenchmark")
      .selectTargetApplicationModule("SimpleAndroidxApplication.app")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.Q)
      .setSourceLanguage(Java)
      .wizard()
      .clickFinishAndWaitForSyncToFinish(Wait.seconds(150))

    guiTest.getProjectFileText("benchmark/src/main/AndroidManifest.xml")

    guiTest.getProjectFileText("benchmark/build.gradle").run {
      assertThat(this).contains("""id 'com.android.test'""")
      assertThat(this).contains("""implementation 'androidx.benchmark:benchmark-macro-junit4:""")
      assertThat(this).contains("""experimentalProperties["android.experimental.self-instrumenting"] = true""")
      assertThat(this).contains("""targetProjectPath = ":app"""")
    }
  }

  /**
   * Verifies that user is able to add a Kotlin Macrobenchmark Module through the
   * new module wizard.
   *
   * <pre>
   * Test steps:
   * 1. Import simple application project
   * 2. Go to File -> New module to open the new module dialog wizard.
   * 3. Choose Benchmark Module and click next.
   * 4. Select Macrobenchmark as the benchmark module type.
   * 5. Select Java as the source language.
   * 6. Complete the wizard and wait for the build to complete.
   * Verify:
   * 1. The new Marobenchmark Module is shown in the project explorer pane.
   * 2. Open the Macrobenchmark Module main manifest and check that:
   *     - Query for target application is declared
   *     - android.permission.WRITE_EXTERNAL_STORAGE is declared
   * 3. Open build.gradle and check that:
   *     - com.android.test plugin is applied
   *     - the macrobenchmark library added as an implementation dependency
   *     - targetProjectPath is set to :app
   *     - The android.experimental.self-instrumenting property is set to true
   */
  @Test
  @Throws(Exception::class)
  fun addKotlinMacrobenchmarkModule() {
    NewModuleWizardFixture.find(guiTest.ideFrame().invokeMenuPath("File", "New", "New Module\u2026"))
      .clickNextToBenchmarkModule()
      .selectBenchmarkType(BenchmarkModuleType.MACROBENCHMARK)
      .enterPackageName("com.example.macrobenchmark")
      .selectTargetApplicationModule("SimpleAndroidxApplication.app")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.Q)
      .setSourceLanguage(Kotlin)
      .wizard()
      .clickFinishAndWaitForSyncToFinish(Wait.seconds(150))

    guiTest.getProjectFileText("benchmark/src/main/AndroidManifest.xml")

    guiTest.getProjectFileText("benchmark/build.gradle").run {
      assertThat(this).contains("""id 'com.android.test'""")
      assertThat(this).contains("""implementation 'androidx.benchmark:benchmark-macro-junit4:""")
      assertThat(this).contains("""experimentalProperties["android.experimental.self-instrumenting"] = true""")
      assertThat(this).contains("""targetProjectPath = ":app"""")
    }
  }
}
