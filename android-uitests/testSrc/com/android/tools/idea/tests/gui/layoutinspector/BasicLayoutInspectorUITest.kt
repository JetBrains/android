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
package com.android.tools.idea.tests.gui.layoutinspector

import com.android.SdkConstants.ATTR_BACKGROUND_TINT
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.flags.junit.FlagRule
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.bleak.UseBleak
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.TestAndroidModel
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture
import com.android.tools.idea.tests.gui.framework.fixture.inspector.LayoutInspectorFixture
import com.android.tools.idea.tests.gui.framework.fixture.properties.PTableFixture
import com.android.tools.idea.tests.gui.framework.fixture.properties.PropertiesPanelFixture
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.ProjectFacetManager
import com.intellij.notification.EventLog
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.core.MouseButton
import org.fest.swing.data.TableCell
import org.fest.swing.timing.Wait
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.awt.event.KeyEvent.VK_SPACE
import java.net.Socket
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

private const val PROJECT_NAME = "LayoutTest"
private const val NAMESPACE = "com.android.tools.tests.layout"
private const val LAYOUT_NAME = "inspection"

/**
 * UI test for the dynamic layout inspector.
 */
@RunWith(GuiTestRemoteRunner::class)
class BasicLayoutInspectorUITest {
  companion object {
    init {
      System.loadLibrary("layout_inspector_test_support")
    }
  }

  private val commandHandler = MyDeviceCommandHandler()
  private val namespace = ResourceNamespace.fromPackageName(NAMESPACE)
  private val linearLayoutId = ResourceReference(namespace, ResourceType.ID, "linear1")
  private val frameLayoutId = ResourceReference(namespace, ResourceType.ID, "frame2")
  private val button3Id = ResourceReference(namespace, ResourceType.ID, "button3")
  private val button4Id = ResourceReference(namespace, ResourceType.ID, "button4")

  private val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)
  private val devSkia =
    FlagRule(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_USE_DEVBUILD_SKIA_SERVER, true)
  private val transportRule = TransportRule().withDeviceCommandHandler(commandHandler)

  @get:Rule
  val ruleChain = RuleChain.outerRule(guiTest).around(transportRule).around(devSkia)!!

  @Test
  fun testLayoutInspector() {
    try {
      basicLayoutInspectorOperations(init())
    }
    catch (ex: Exception) {
      // Print the stacktrace from the case before any cleanup that may obscure the error.
      ex.printStackTrace()
      throw ex
    }
  }

  @Ignore("b/228615904")
  @Test
  @UseBleak
  @RunIn(TestGroup.PERFORMANCE)
  fun testLayoutInspectorWithBleak() {
    val frame = init()

    // Run once to create the layout inspector tool window outside of the Bleak run
    basicLayoutInspectorOperations(frame)

    guiTest.runWithBleak { basicLayoutInspectorOperations(frame) }
  }

  private fun init(): IdeFrameFixture {
    val frame = guiTest.importProjectAndWaitForProjectSyncToFinish(PROJECT_NAME)

    // Get the bridge synchronously, since we're in test mode.
    val bridge = AdbService.getInstance().getDebugBridge(AndroidSdkUtils.getAdb(frame.project)!!).get()

    // Wait for ADB.
    Wait.seconds(10).expecting("Android Debug Bridge to connect").until { bridge.isConnected }
    Wait.seconds(10).expecting("Initial device list is available").until { bridge.hasInitialDeviceList() }

    // Add the default device and process
    transportRule.addProcess(DEFAULT_DEVICE, DEFAULT_PROCESS)
    Wait.seconds(10).expecting("Device registered").until { bridge.devices.isNotEmpty() }

    transportRule.saveEventPositionMark(DEFAULT_DEVICE.deviceId)

    return frame
  }

  private fun basicLayoutInspectorOperations(frame: IdeFrameFixture) {
    val boxes = FakeBoxes(transportRule.viewInspector)

    val robot = guiTest.robot()

    // Hide the messages tool window which may appear after the build. Remove such that the inspector has more room.
    MessagesToolWindowFixture.ifExists(frame.project, robot)?.hide()

    // Hide notifications since they will overlap the properties panel and interfere with the test.
    EventLog.getLogModel(frame.project).notifications.forEach { it.balloon?.hide() }

    // Override the AndroidModel such that the fake process is recognized
    runInEdtAndWait {
      val facet = ProjectFacetManager.getInstance(frame.project).getFacets(AndroidFacet.ID).first()
      AndroidModel.set(facet, TestAndroidModel(DEFAULT_PROCESS.name));
    }

    // Open the inspector tool window.
    val inspector = LayoutInspectorFixture(frame.project, robot)
    inspector.activate()

    // Resize the root panel to make the layout inspector window taller.
    frame.findToolWindowSplitter().lastSize = 300

    // Select a device and process and wait for the component tree to display the correct amount of nodes.
    inspector.selectDevice("${DEFAULT_DEVICE.manufacturer} ${DEFAULT_DEVICE.model}", DEFAULT_PROCESS.name)

    inspector.waitForComponentTreeToLoad(1)

    // Simulate that the user did something on the device that caused a different skia image to be received.
    runInEdtAndWait { boxes.sendMultipleBoxesTreeEvent() }

    inspector.waitForComponentTreeToLoad(4)

    val properties = inspector.properties

    // Expand the component tree and select button4 (which will be in row 3 of the component tree).
    // This should cause the property panel to populate.
    inspector.tree.expandAll()
    inspector.tree.selectRow(3)
    inspector.waitForPropertiesToPopulate(toUrl(button4Id))

    // Find "backgroundTint" in the "Declared Attributes" section and verify its value.
    val declared = properties.findSectionByName("Declared Attributes")!!
    val table = declared.components.single() as PTableFixture
    assertThat(table.rowCount()).isEqualTo(1)
    val item = table.item(0)
    assertThat(item.name).isEqualTo(ATTR_BACKGROUND_TINT)
    assertThat(item.value).isEqualTo("#FF0000")

    // Find the same property in the "All Attributes" section and go to an indirect source location.
    val all = properties.findSectionByName("All Attributes")!!
    val allTable = all.components.single() as PTableFixture
    val row = allTable.findRowOf(ATTR_BACKGROUND_TINT)
    allTable.click(TableCell.row(row).column(0), MouseButton.LEFT_BUTTON)
    allTable.robot().type('\n') // Toggle (open) name cell expander
    allTable.robot().type('\t') // Puts focus on value cell expander
    allTable.robot().type('\n') // Toggle (open) value cell expander
    allTable.robot().type('\t') // Puts focus on link for the layout inspection.xml
    allTable.robot().type('\t') // Puts focus on link for colors.xml
    allTable.robot().type('\n') // Activates the link

    // Check that the correct editor is loaded and the cursor is at the correct position:
    val editor = frame.editor
    editor.waitForFileToActivate()
    assertThat(editor.currentFileName).isEqualTo("colors.xml")
    assertThat(editor.currentLine.trim()).isEqualTo("<color name=\"back1\">@color/color2</color>")

    // Click on the LinearLayout
    val deviceView = inspector.deviceView
    deviceView.clickOnImage(50, 50)
    inspector.tree.requireSelectedRows(0)
    inspector.waitForPropertiesToPopulate(toUrl(linearLayoutId))
    requireDeclaredAttributeValue(properties, "background", "#FFFF00")
    requireNoDeclaredAttribute(properties, "backgroundTint")

    // Click on the Button4
    deviceView.clickOnImage(500, 1400)
    inspector.tree.requireSelectedRows(3)
    inspector.waitForPropertiesToPopulate(toUrl(button4Id))
    requireNoDeclaredAttribute(properties, "background")
    requireDeclaredAttributeValue(properties, "backgroundTint", "#FF0000")

    // 2D status
    val layerSpacingSlider = inspector.layerSpacingSlider
    assertThat(layerSpacingSlider.isEnabled).isFalse()
    assertThat(deviceView.angleAfterLastPaint).isEqualTo(0.0)
    deviceView.clickOnImage(210, 210)
    inspector.waitForPropertiesToPopulate(toUrl(button3Id))

    // Change to 3D viewing
    deviceView.mode3DActionButton.click()
    deviceView.clickOnImage(210, 210)  // no longer expected to be button3
    inspector.waitForPropertiesToPopulate(toUrl(frameLayoutId))
    deviceView.clickOnImage(410, 320)
    inspector.waitForPropertiesToPopulate(toUrl(button3Id))
    assertThat(deviceView.angleAfterLastPaint).isWithin(1.0).of(26.0)

    // Exaggerate the 3D viewing
    deviceView.clickAndDrag(400, 800, 2000, 1000)
    deviceView.clickOnImage(410, 320)   // no longer expected to be button3
    inspector.waitForPropertiesToPopulate(toUrl(frameLayoutId))
    deviceView.clickOnImage(490, 360)
    inspector.waitForPropertiesToPopulate(toUrl(button3Id))
    assertThat(deviceView.angleAfterLastPaint).isWithin(1.0).of(38.4)

    // Layer spacing
    inspector.waitUntilMode3dSliderEnabled(true)
    assertThat(deviceView.layerSpacing).isEqualTo(150)

    // Maximum layer spacing moves button3 to the right
    layerSpacingSlider.slideToMaximum()
    deviceView.clickOnImage(490, 360) // no longer expected to be button3
    inspector.waitForPropertiesToPopulate(toUrl(frameLayoutId))
    deviceView.clickOnImage(720, 420)
    inspector.waitForPropertiesToPopulate(toUrl(button3Id))
    assertThat(deviceView.layerSpacing).isEqualTo(500)

    // Minimum layer spacing moves button3 to the left
    layerSpacingSlider.slideToMinimum()
    deviceView.clickOnImage(720, 420) // no longer expected to be button3
    inspector.waitForPropertiesToPopulate(toUrl(linearLayoutId))
    deviceView.clickOnImage(400, 340)
    inspector.waitForPropertiesToPopulate(toUrl(button3Id))
    assertThat(deviceView.layerSpacing).isEqualTo(0)

    // Back to start
    deviceView.layerSpacing = 150

    // Reset to 2D
    deviceView.mode3DActionButton.click()
    deviceView.clickOnImage(210, 210)
    inspector.waitForPropertiesToPopulate(toUrl(button3Id))
    assertThat(deviceView.angleAfterLastPaint).isEqualTo(0.0)
    inspector.waitUntilMode3dSliderEnabled(false)

    // Before any zoom check that the view is fully shown.
    // The full height of the root layout should fit in the viewport of the ScrollPane.
    assertThat(deviceView.viewEndPosition.y - deviceView.viewPosition.y >= 2000)

    // Zoom In
    deviceView.zoomInButton.click()
    deviceView.waitUntilExpectedViewportHeight(1100, tolerance = 50)

    // Zoom In a second time
    deviceView.zoomInButton.click()
    deviceView.waitUntilExpectedViewportHeight(730, tolerance = 50)

    // Panning using the space key
    var pos = deviceView.viewPosition
    robot.focusAndWaitForFocusGain(deviceView.contentPanel)
    robot.pressKey(VK_SPACE)
    deviceView.clickAndDrag(50, 800, 0, 100)
    robot.focusAndWaitForFocusGain(deviceView.contentPanel)
    robot.releaseKey(VK_SPACE)
    pos.translate(0, -100)
    deviceView.waitUntilExpectedViewPosition(pos)

    // Panning using the middle mouse button
    pos = deviceView.viewPosition
    deviceView.clickAndDrag(50, 700, 0, 100, MouseButton.MIDDLE_BUTTON)
    pos.translate(0, -100)
    deviceView.waitUntilExpectedViewPosition(pos)

    // Panning using the Pan button
    deviceView.panButton.click()
    pos = deviceView.viewPosition
    deviceView.clickAndDrag(50, 600, 0, 100)
    pos.translate(0, -100)
    deviceView.waitUntilExpectedViewPosition(pos)

    // Restore the pan button to default state
    deviceView.panButton.click()

    // Zoom out
    deviceView.zoomOutButton.click()
    deviceView.waitUntilExpectedViewportHeight(1100, tolerance = 50)

    // Zoom to fit
    deviceView.zoomToFitButton.click()

    // The full height of the root layout should again fit in the viewport of the ScrollPane.
    assertThat(deviceView.viewEndPosition.y - deviceView.viewPosition.y >= 2000)

    inspector.stop()
    Wait.seconds(10).expecting("debug attribute cleanup").until { commandHandler.stopped }
  }

  private fun requireDeclaredAttributeValue(properties: PropertiesPanelFixture<*>, attrName: String, attrValue: String) {
    val declared = properties.findSectionByName("Declared Attributes")!!
    val table = declared.components.single() as PTableFixture
    val row = table.findRowOf(attrName)
    assertThat(row).named("The attribute: ${attrName} was not found").isAtLeast(0)
    val item = table.item(row)
    assertThat(item.name).isEqualTo(attrName)
    assertThat(item.value).isEqualTo(attrValue)
  }

  private fun requireNoDeclaredAttribute(properties: PropertiesPanelFixture<*>, attrName: String) {
    val declared = properties.findSectionByName("Declared Attributes")!!
    val table = declared.components.single() as PTableFixture
    val row = table.findRowOf(attrName)
    assertThat(row).named("The attribute: ${attrName} was found unexpectedly").isEqualTo(-1)
  }

  private fun toUrl(ref: ResourceReference): String =
    ref.getRelativeResourceUrl(namespace).toString()

  private class MyDeviceCommandHandler : DeviceCommandHandler("shell") {
    var debugViewAttributesChanges = 0
      private set
    var debugViewAttributes: String? = null
      private set
    var debugViewAttributesApplicationPackage: String? = null
      private set

    val stopped: Boolean
      get() = debugViewAttributes == null &&
              debugViewAttributesApplicationPackage == null

    override fun accept(server: FakeAdbServer, socket: Socket, device: DeviceState, command: String, args: String): Boolean {
      val response = when (command) {
        "shell" -> handleShellCommand(args) ?: return false
        else -> return false
      }
      writeOkay(socket.getOutputStream())
      writeString(socket.getOutputStream(), response)
      return true
    }

    /**
     * Handle shell commands.
     *
     * Examples:
     *  - "echo anything"
     *  - "settings get global debug_view_attributes"
     *  - "settings get global debug_view_attributes_application_package"
     *  - "settings put global debug_view_attributes 1"
     *  - "settings put global debug_view_attributes_application_package com.example.myapp"
     *  - "settings delete global debug_view_attributes"
     *  - "settings delete global debug_view_attributes_application_package"
     *  - "rm -f /data/local/tmp/perfd/layoutinspector-view-inspection.jar"
     *  - "mkdir -p /data/local/tmp/perfd"
     *  - "sh -c 'trap "settings delete global debug_view_attributes_application_package" EXIT; read'"
     */
    private fun handleShellCommand(command: String): String? {
      val args = ArrayDeque(command.split(' '))
      when (args.poll()) {
        "settings" -> {}
        "echo" -> return args.joinToString()
        "rm" -> return ""
        "mkdir" -> return ""
        "sh" -> return ""
        else -> return null
      }
      val operation = args.poll()
      if (args.poll() != "global") {
        return null
      }
      val variable = when (args.poll()) {
        "debug_view_attributes" -> this::debugViewAttributes
        "debug_view_attributes_application_package" -> this::debugViewAttributesApplicationPackage
        else -> return null
      }
      val argument = if (args.isEmpty()) "" else args.poll()
      if (args.isNotEmpty()) {
        return null
      }
      return when (operation) {
        "get" -> variable.get().toString()
        "put" -> { variable.set(argument); debugViewAttributesChanges++; "" }
        "delete" -> { variable.set(null); debugViewAttributesChanges++; "" }
        else -> null
      }
    }
  }
}
