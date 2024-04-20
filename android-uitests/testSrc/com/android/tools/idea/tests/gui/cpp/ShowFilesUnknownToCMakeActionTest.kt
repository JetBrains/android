package com.android.tools.idea.tests.gui.cpp

import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.NewFilePopupFixture
import com.android.tools.idea.tests.util.WizardUtils
import com.android.tools.idea.wizard.template.Language
import com.google.common.truth.Truth
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.core.MouseButton.RIGHT_BUTTON
import org.fest.swing.exception.LocationUnavailableException
import org.fest.swing.timing.Wait
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class ShowFilesUnknownToCMakeActionTest {
  @get:Rule
  val guiTest = GuiTestRule().withTimeout(15, TimeUnit.MINUTES)

  @Test
  fun actionShouldToggleVisibilityOfUnusedFiles() {
    // Set up a normal C++ project
    WizardUtils.createCppProject(guiTest, FormFactor.MOBILE, "Native C++", Language.Java)
    GuiTests.waitForProjectIndexingToFinish(guiTest.ideFrame().project)
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    val ideFrame = guiTest.ideFrame()
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    // Add an unused C file
    val unusedFile1 = guiTest.projectPath.resolve("app/src/main/cpp/unused1.c")
    unusedFile1.writeText("int i1 = 1;")

    ideFrame.requestProjectSyncAndWaitForSyncToFinish()
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    val projectView = ideFrame.focus().projectView
    val androidPane = projectView.selectAndroidPane()
    Wait.seconds(10).expecting("Path is loaded for clicking").until {
      try {
        androidPane.clickPath("app", "cpp")
        return@until true
      }
      catch (e: LocationUnavailableException) {
        return@until false
      }
    }

    // Turn off show unused files
    projectView.showOptionsMenu()
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    ideFrame.clickPopupMenuItem("Show Files Unknown to CMake")

    // Add a new file inside the IDE
    ideFrame.projectView
      .selectAndroidPane()
      .clickPath(RIGHT_BUTTON, "app", "cpp")
      .openFromContextualMenu(NewFilePopupFixture::find, "New", "File")
      .setFilePath("unused.c")
      .pressEnter()

    androidPane.doubleClickPath("app", "cpp")

    ideFrame.editor
      .enterText("int i2 = 1;")
      .invokeAction(EditorFixture.EditorAction.SAVE)
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    // Check that the new file is shown
    Truth.assertThat(androidPane.hasPath("app", "cpp", "unused.c")).isTrue()

    // And the new file should be hidden after sync since it's not used.
    ideFrame.requestProjectSync()
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    Truth.assertThat(androidPane.hasPath("app", "cpp", "unused.c")).isFalse()

    // Turn on show unused files
    projectView.showOptionsMenu()
    ideFrame.clickPopupMenuItem("Show Files Unknown to CMake")

    Truth.assertThat(androidPane.hasPath("app", "cpp", "unused.c")).isTrue()
  }
}