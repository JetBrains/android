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
package com.android.tools.idea.tests.gui.compose

import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.CommandHandler
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.tools.compose.COMPOSE_PREVIEW_ACTIVITY_FQN
import com.android.tools.idea.bleak.UseBleak
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.RunToolWindowFixture
import com.android.tools.idea.tests.gui.framework.fixture.compose.getNotificationsFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.SplitEditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.getSplitEditorFixture
import com.android.tools.idea.tests.gui.uibuilder.RenderTaskLeakCheckRule
import com.android.tools.idea.tests.util.ddmlib.AndroidDebugBridgeUtils
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import icons.StudioIcons
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.JPopupMenuFixture
import org.fest.swing.timing.Wait
import org.fest.swing.util.PatternTextMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.KeyEvent
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.swing.JMenuItem

@RunWith(GuiTestRemoteRunner::class)
class ComposePreviewTest {
  @get:Rule
  val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)
  @get:Rule
  val renderTaskLeakCheckRule = RenderTaskLeakCheckRule()

  private val commandHandler = DeployPreviewCommandHandler()

  @get:Rule
  val adbRule: FakeAdbRule = FakeAdbRule().initAbdBridgeDuringSetup(false).withDeviceCommandHandler(commandHandler)

  private fun openComposePreview(fixture: IdeFrameFixture, fileName: String = "MainActivity.kt", assertNoNotifications: Boolean = true):
    SplitEditorFixture {
    // Open the main compose activity and check that the preview is present
    val editor = fixture.editor
    val file = "app/src/main/java/google/simpleapplication/$fileName"

    fixture.invokeAndWaitForBuildAction("Build", "Make Project")

    editor.open(file)

    return editor.getSplitEditorFixture().waitForRenderToFinish().apply {
      if (assertNoNotifications) {
        getNotificationsFixture().waitForNotificationsToDisappear()
      }
    }
  }

  private fun getSyncedProjectFixture() = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleComposeApplication")

  @Test
  @Throws(Exception::class)
  fun testOpenAndClosePreview() {
    openAndClosePreview(getSyncedProjectFixture())
  }

  @Test
  @Throws(Exception::class)
  fun testCopyPreviewImage() {
    val fixture = getSyncedProjectFixture()
    val composePreview = openComposePreview(fixture)

    assertFalse(composePreview.hasRenderErrors())

    clearClipboard()
    assertFalse(Toolkit.getDefaultToolkit().systemClipboard.getContents(this).isDataFlavorSupported(DataFlavor.imageFlavor))

    val designSurfaceTarget = composePreview.designSurface.target()
    composePreview.robot.click(designSurfaceTarget)
    JPopupMenuFixture(composePreview.robot(), composePreview.robot.showPopupMenu(designSurfaceTarget))
      .menuItem(object : GenericTypeMatcher<JMenuItem>(JMenuItem::class.java) {
        override fun isMatching(component: JMenuItem): Boolean {
          return "Copy Image" == component.text
        }
      }).click()

    assertTrue(getClipboardContents().isDataFlavorSupported(DataFlavor.imageFlavor))

    fixture.editor.close()
  }

  /**
   * Returns the current system clipboard contents.
   */
  private fun getClipboardContents(): Transferable = Toolkit.getDefaultToolkit().systemClipboard.getContents(this)

  /**
   * Clears the system clipboard by copying an "Empty" [Transferable]. This can be used to verify that copy operations of other elements
   * do succeed.
   */
  private fun clearClipboard() {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(object : Transferable {
      override fun getTransferData(flavor: DataFlavor?): Any = when (flavor) {
        DataFlavor.stringFlavor -> "Empty"
        else -> throw UnsupportedFlavorException(flavor)
      }

      override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == DataFlavor.stringFlavor

      override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.stringFlavor)

    }, null)
  }

  @Test
  @UseBleak
  @RunIn(TestGroup.PERFORMANCE)
  @Throws(Exception::class)
  fun testOpenAndClosePreviewWithBleak() {
    val fixture = getSyncedProjectFixture()
    guiTest.runWithBleak { openAndClosePreview(fixture) }
  }

  @Throws(Exception::class)
  private fun openAndClosePreview(fixture: IdeFrameFixture) {
    val composePreview = openComposePreview(fixture).waitForSceneViewsCount(1)

    assertFalse(composePreview.hasRenderErrors())

    // Verify that the element rendered correctly by checking it's not empty
    val singleSceneView = composePreview.designSurface.allSceneViews.single().size()
    assertTrue(singleSceneView.width > 10 && singleSceneView.height > 10)

    val editor = fixture.editor

    // Now let's make a change on the source code and check that the notification displays
    val modification = "Random modification"
    editor.typeText(modification)

    guiTest.robot().waitForIdle()

    composePreview
      .getNotificationsFixture()
      .waitForNotificationContains("syntax errors")

    // Undo modifications and close editor to return to the initial state
    editor.select("(${modification})")
    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE)
    editor.close()
  }

  @Test
  @Throws(Exception::class)
  fun testRemoveExistingPreview() {
    val fixture = getSyncedProjectFixture()
    val composePreview = openComposePreview(fixture)

    assertFalse(composePreview.hasRenderErrors())

    val editor = fixture.editor
    editor.select("(@Preview)")
    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE)
    guiTest.ideFrame().invokeMenuPath("Code", "Optimize Imports") // This will remove the Preview import
    composePreview
      .designSurface
      .waitUntilNotShowing(Wait.seconds(10));

    editor.close()
  }

  @Test
  @Ignore("b/149464527")
  @Throws(Exception::class)
  fun testAddAdditionalPreview() {
    val fixture = getSyncedProjectFixture()
    val composePreview = openComposePreview(fixture)

    // Commented until b/156216008 is solved
    //assertFalse(composePreview.hasRenderErrors())
    composePreview.waitForSceneViewsCount(1)

    val editor = fixture.editor
    editor.invokeAction(EditorFixture.EditorAction.TEXT_END)
      .pressAndReleaseKeys(KeyEvent.VK_ENTER)
      // The closing braces are not needed since they are added by the editor automatically
      .typeText("""
        @Preview(name = "Second")
        @Composable
        fun SecondPreview() {
          MaterialTheme {
            Text(text = "A second preview")
      """.trimIndent())

    composePreview
      .getNotificationsFixture()
      .waitForNotificationContains("out of date")

    assertTrue("Build failed",
               fixture.actAndWaitForBuildToFinish {
                 composePreview
                   .findActionButtonByText("Build  Refresh")
                   .waitUntilEnabledAndShowing()
                   .click()
               }.isBuildSuccessful)

    composePreview.waitForRenderToFinish()

    // Check the new preview has been added
    composePreview.waitForSceneViewsCount(2)

    editor.close()
  }

  @Test
  @Throws(Exception::class)
  fun testInteractivePreview() {
    val fixture = getSyncedProjectFixture()
    val composePreview = openComposePreview(fixture, "MultipleComposePreviews.kt")

    composePreview.waitForSceneViewsCount(3)

    composePreview.designSurface
      .allSceneViews
      .first()
      .toolbar()
      .findButtonByIcon(StudioIcons.Compose.Toolbar.INTERACTIVE_PREVIEW)
      .waitUntilEnabledAndShowing()
      .click()

    composePreview
      .waitForRenderToFinish()

    composePreview.waitForSceneViewsCount(1)

    composePreview
      .findActionButtonByText("Stop Interactive Mode")
      .waitUntilEnabledAndShowing()
      .click()

    composePreview
      .waitForRenderToFinish()

    composePreview.waitForSceneViewsCount(3)

    fixture.editor.close()
  }

  @Test
  @RunIn(TestGroup.UNRELIABLE) // b/160776556
  @Throws(Exception::class)
  fun testAnimationInspector() {
    fun SplitEditorFixture.findAnimationInspector() =
      try {
        guiTest.ideFrame().robot().finder().findByName(this.editor.component, "Animation Preview")
      }
      catch (e: ComponentLookupException) {
        null
      }

    val fixture = getSyncedProjectFixture()
    val noAnimationsComposePreview = openComposePreview(fixture, "MultipleComposePreviews.kt", false)
      .waitForRenderToFinish()
      .waitForSceneViewsCount(3)

    val previewToolbar =
      noAnimationsComposePreview
        .designSurface
        .allSceneViews
        .first()
        .toolbar()

    try {
      // MultipleComposePreviews does not have animations, so the animation preview button is expected not to be displayed.
      previewToolbar.findButtonByIcon(StudioIcons.Compose.Toolbar.ANIMATION_INSPECTOR)
      fail("The animation preview icon is not expected to be found.")
    }
    catch (_: WaitTimedOutError) {
      // Expected to be thrown
    }
    fixture.editor.closeFile("app/src/main/java/google/simpleapplication/MultipleComposePreviews.kt")

    val composePreview = openComposePreview(fixture, "Animations.kt")
      .waitForRenderToFinish()
      .waitForSceneViewsCount(2)

    // First preview have an animation
    composePreview.designSurface
      .allSceneViews
      .first()
      .toolbar()
      .findButtonByIcon(StudioIcons.Compose.Toolbar.ANIMATION_INSPECTOR)
      .waitUntilEnabledAndShowing()
      .click()
    assertNotNull(composePreview.findAnimationInspector())

    // Open the animation inspector in another file
    val otherComposePreview = openComposePreview(fixture, "Animations2.kt", false)
      .waitForRenderToFinish()
      .waitForSceneViewsCount(1)

    otherComposePreview
      .designSurface
      .allSceneViews
      .first()
      .toolbar()
      .findButtonByIcon(StudioIcons.Compose.Toolbar.ANIMATION_INSPECTOR)
      .waitUntilEnabledAndShowing()
      .click()
    assertNotNull(otherComposePreview.findAnimationInspector())

    val animations1Relative = "app/src/main/java/google/simpleapplication/Animations.kt"
    fixture.editor.open(animations1Relative)
    // Animation Preview was closed in Animations.kt after we opened it in Animations2.kt
    assertNull(composePreview.findAnimationInspector())

    // Return to Animations2.kt, where the Animation Preview should still be open
    fixture.editor.closeFile(animations1Relative)
    guiTest.robot().focusAndWaitForFocusGain(otherComposePreview.target())
    assertNotNull(otherComposePreview.findAnimationInspector())

    // Clicking on the "Stop Animation Inspection" button should close the animation preview panel
    otherComposePreview
      .waitForRenderToFinish()
      .waitForSceneViewsCount(1)
      .findActionButtonByText("Stop Animation Inspection")
      .waitUntilEnabledAndShowing()
      .click()
    assertNull(otherComposePreview.findAnimationInspector())

    fixture.editor.closeFile("app/src/main/java/google/simpleapplication/Animations2.kt")
  }

  @Test
  @Throws(Exception::class)
  fun testDeployPreview() {
    // Enable the fake ADB server and attach a fake device to which the preview will be deployed.
    AndroidDebugBridgeUtils.enableFakeAdbServerMode(adbRule.fakeAdbServerPort)
    adbRule.attachDevice("42", "Google", "Pix3l", "versionX", "29")

    val fixture = getSyncedProjectFixture()
    val composePreview = openComposePreview(fixture, "MultipleComposePreviews.kt", false)
    commandHandler.composablePackageName = "google.simpleapplication"
    commandHandler.composableFqn = "google.simpleapplication.MultipleComposePreviewsKt.Preview1"
    commandHandler.previewActivityName = COMPOSE_PREVIEW_ACTIVITY_FQN

    composePreview.designSurface
      .allSceneViews
      .first()
      .toolbar()
      .findButtonByIcon(StudioIcons.Compose.Toolbar.RUN_ON_DEVICE).waitUntilEnabledAndShowing().click()

    val runToolWindowFixture = RunToolWindowFixture(guiTest.ideFrame())
    val contentFixture = runToolWindowFixture.findContent("Preview1")
    // We should display "Launching '<Compose Preview Configuration Name>' on <Device>"
    val launchingPreview = Pattern.compile(".*Launching 'Preview1' on Google Pix3l.*", Pattern.DOTALL)
    contentFixture.waitForOutput(PatternTextMatcher(launchingPreview), 10)
    // We should display the adb shell command containing androidx.compose.ui.tooling.preview.PreviewActivity, which wraps the @Composable
    val previewActivityPattern = COMPOSE_PREVIEW_ACTIVITY_FQN.replace(".", "\\.")
    val previewActivity = Pattern.compile(".*${previewActivityPattern}.*", Pattern.DOTALL)
    contentFixture.waitForOutput(PatternTextMatcher(previewActivity), 10)

    guiTest.ideFrame().invokeMenuPath("Run", "Stop 'Preview1'")
    fixture.editor.close()
  }

  private class DeployPreviewCommandHandler : DeviceCommandHandler("shell") {
    var composablePackageName: String = "com.example"
    var composableFqn: String = "com.example.MyComposable"
    var previewActivityName: String = "Activity"

    override fun accept(server: FakeAdbServer, socket: Socket, device: DeviceState, command: String, args: String): Boolean {
      val deployArgs = "am start -n \"$composablePackageName/$previewActivityName\"" +
                       " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --es composable $composableFqn"
      val stopArgs = "am force-stop $composablePackageName"
      return when (args) {
        deployArgs, stopArgs -> {
          CommandHandler.writeOkay(socket.getOutputStream())
          true
        }

        else -> false
      }
    }
  }
}
