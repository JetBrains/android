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
package com.android.tools.idea.tests.gui.cpp

import com.android.tools.idea.gradle.project.sync.GradleSyncState.Companion.getInstance
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.fixture.AddCppToModuleDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.util.WizardUtils
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.wizard.template.BuildConfigurationLanguageForNewProject
import com.android.tools.idea.wizard.template.DEFAULT_CMAKE_VERSION
import com.android.tools.idea.wizard.template.Language.Java
import com.google.common.truth.Truth
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.timing.Wait
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.function.Function

@RunWith(GuiTestRemoteRunner::class)
class AddCppToModuleActionTest {
  @get:Rule
  val guiTest = GuiTestRule().withTimeout(15, TimeUnit.MINUTES)

  @Test
  fun testCreatingNewCppBoilerplate() {
    createPureJavaProject()

    val ideFrame = guiTest.ideFrame()

    ideFrame.openAddCppToModuleDialog().apply {
      selectCreateCppFiles()
      Wait.seconds(10).expecting("OK button to be enabled").until {
        okButton.isEnabled
      }
      okButton.click()
    }
    ideFrame.waitForGradleSyncToFinish(Wait.seconds(240))
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    if (getInstance(ideFrame.project).lastSyncFailed()) {
      Assert.fail("Sync failed after adding new C++ files to current Android project. See logs.")
    }
    ideFrame.editor.waitForFileToActivate()
    Truth.assertThat(ideFrame.editor.currentFile!!.name).isEqualTo("myapplication.cpp")
  }

  @Test
  fun testLinkingExistingCppProject_cmake() {
    createPureJavaProject()

    val ideFrame = guiTest.ideFrame()

    val projectRoot = ideFrame.project.guessProjectDir()!!.toIoFile()
    val cppDir = projectRoot.resolve("cpp").apply { mkdirs() }
    val cmakeListsFile = cppDir.resolve("CMakeLists.txt")
    cmakeListsFile.writeText("""
      cmake_minimum_required(VERSION 3.10.2)
      project("test")
      add_library(native-lib SHARED native-lib.cpp )
      """.trimIndent())
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    cppDir.resolve("native-lib.cpp").writeText("""
      #include <jni.h>
      int i = 2;
      """.trimIndent())

    ideFrame.openAddCppToModuleDialog().apply {
      selectLinkCppProject()
      enabledTextField.setText(cmakeListsFile.absolutePath)
      okButton.click()
    }
    ideFrame.waitForGradleSyncToFinish(Wait.seconds(240))
    GuiTests.waitForBackgroundTasks(guiTest.robot())

    if (getInstance(ideFrame.project).lastSyncFailed()) {
      Assert.fail("Sync failed after adding new C++ files to current Android project. See logs.")
    }

    Truth.assertThat(projectRoot.resolve("app/build.gradle").readText()).contains("../cpp/CMakeLists.txt")
    Truth.assertThat(projectRoot.resolve("app/build.gradle").readText()).contains("version '$DEFAULT_CMAKE_VERSION'")
  }

  @Test
  fun testLinkingExistingCppProject_ndkBuild() {
    createPureJavaProject()

    val ideFrame = guiTest.ideFrame()

    val projectRoot = ideFrame.project.guessProjectDir()!!.toIoFile()
    val cppDir = projectRoot.resolve("cpp").apply { mkdirs() }
    val androidMkFile = cppDir.resolve("Android.mk")
    androidMkFile.writeText("""
      LOCAL_PATH := $(call my-dir)
      LOCAL_MODULE    := native-lib
      LOCAL_SRC_FILES := $(LOCAL_PATH)/native-lib.cpp
      include $(BUILD_SHARED_LIBRARY)
      """.trimIndent())
    cppDir.resolve("native-lib.cpp").writeText("""
      #include <jni.h>
      int i = 2;
      """.trimIndent())

    ideFrame.openAddCppToModuleDialog().apply {
      selectLinkCppProject()
      enabledTextField.setText(androidMkFile.absolutePath)
      okButton.click()
    }

    GuiTests.waitForBackgroundTasks(guiTest.robot())

    if (getInstance(ideFrame.project).lastSyncFailed()) {
      Assert.fail("Sync failed after adding new C++ files to current Android project. See logs.")
    }

    Truth.assertThat(projectRoot.resolve("app/build.gradle").readText()).contains("../cpp/Android.mk")
  }

  @Test
  fun testAddCppToModuleDialogOkButton() {
    createPureJavaProject()

    val ideFrame = guiTest.ideFrame()

    val projectRoot = ideFrame.project.guessProjectDir()!!.toIoFile()
    val cppDir = projectRoot.resolve("cpp").apply { mkdirs() }
    val cmakeListsFile = cppDir.resolve("CMakeLists.txt").apply { writeText("content does not matter") }
    ideFrame.openAddCppToModuleDialog().apply {
      selectCreateCppFiles()
      Truth.assertThat(okButton.isEnabled).isTrue()
      // selection is not a directory
      enabledTextField.setText(cmakeListsFile.absolutePath)
      Truth.assertThat(okButton.isEnabled).isFalse()

      selectLinkCppProject()
      // selection is empty
      Truth.assertThat(okButton.isEnabled).isFalse()
      // selection does not exist
      enabledTextField.setText("/blah")
      Truth.assertThat(okButton.isEnabled).isFalse()
      // selection exists, but it's not a CMakeLists.txt or *.mk file
      enabledTextField.setText(projectRoot.resolve("build.gradle").absolutePath)
      Truth.assertThat(okButton.isEnabled).isFalse()
      // selection is valid
      enabledTextField.setText(cmakeListsFile.absolutePath)
      Truth.assertThat(okButton.isEnabled).isTrue()
      clickCancel()
    }
  }

  private fun createPureJavaProject() {
    WizardUtils.createNewProject(guiTest, "Empty Views Activity", Java, BuildConfigurationLanguageForNewProject.Groovy)
    GuiTests.waitForProjectIndexingToFinish(guiTest.ideFrame().project)
    guiTest.waitForAllBackgroundTasksToBeCompleted()
  }

  private fun IdeFrameFixture.openAddCppToModuleDialog(): AddCppToModuleDialogFixture {
    projectView.selectAndroidPane().clickPath("app")

    return guiTest.ideFrame().openFromMenu(
      Function { ideFrameFixture: IdeFrameFixture -> AddCppToModuleDialogFixture.find(ideFrameFixture) },
      "File",
      "Add C++ to Module"
    )
  }
}