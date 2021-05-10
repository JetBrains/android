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
import com.android.flags.junit.SetFlagRule
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.bleak.UseBleak
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture
import com.android.tools.idea.tests.gui.framework.fixture.inspector.LayoutInspectorFixture
import com.android.tools.idea.tests.gui.framework.fixture.properties.PTableFixture
import com.android.tools.idea.tests.gui.framework.fixture.properties.PropertiesPanelFixture
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.intellij.notification.EventLog
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.core.MouseButton
import org.fest.swing.data.TableCell
import org.fest.swing.timing.Wait
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.Color
import java.awt.event.KeyEvent.VK_SPACE
import java.net.Socket
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

private const val PROJECT_NAME = "LayoutTest"
private const val LAYOUT_ID = 1L
private const val PAYLOAD_SINGLE_BOX = 7
private const val PAYLOAD_BOXES = 8
private const val NAMESPACE = "com.android.tools.tests.layout"
private const val LAYOUT_NAME = "inspection"

/**
 * UI test for the dynamic layout inspector.
 */
@RunWith(GuiTestRemoteRunner::class)
class BasicLayoutInspectorUITest {
/*  Disabled pending rewrite to app inspection: b/187734852
  companion object {
    init {
      System.loadLibrary("layout_inspector_test_support")
    }
  }

  private val commandHandler = MyDeviceCommandHandler()
  private val namespace = ResourceNamespace.fromPackageName(NAMESPACE)
  private val layoutReference = ResourceReference(namespace, ResourceType.LAYOUT, LAYOUT_NAME)
  private val buttonMaterialReference = ResourceReference.style(namespace, "Widget.Material.Button")
  private val buttonStyleReference = ResourceReference.style(namespace, "ButtonStyle")
  private val linearLayoutId = ResourceReference(namespace, ResourceType.ID, "linear1")
  private val frameLayoutId = ResourceReference(namespace, ResourceType.ID, "frame2")
  private val button3Id = ResourceReference(namespace, ResourceType.ID, "button3")
  private val button4Id = ResourceReference(namespace, ResourceType.ID, "button4")

  @get:Rule
  val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)

  @get:Rule
  val transportRule = TransportRule()
    .withCommandHandler(LayoutInspectorCommand.Type.START, ::startHandler)
    .withCommandHandler(LayoutInspectorCommand.Type.STOP, ::stopHandler)
    .withCommandHandler(LayoutInspectorCommand.Type.GET_PROPERTIES, ::produceProperties)
    .withDeviceCommandHandler(commandHandler)
    .withFile(PAYLOAD_SINGLE_BOX, generateSingle())
    .withFile(PAYLOAD_BOXES, generateBoxes())

  @get:Rule
  val flagRule = SetFlagRule(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_USE_DEVBUILD_SKIA_SERVER, true)

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

    transportRule.saveEventPositionMark(DEFAULT_DEVICE.deviceId)

    return frame
  }

  private fun basicLayoutInspectorOperations(frame: IdeFrameFixture) {
    val robot = guiTest.robot()

    // Hide the messages tool window which may appear after the build. Remove such that the inspector has more room.
    MessagesToolWindowFixture.ifExists(frame.project, robot)?.hide()

    // Hide notifications since they will overlap the properties panel and interfere with the test.
    EventLog.getLogModel(frame.project).notifications.forEach { it.balloon?.hide() }

    // Open the inspector tool window.
    val inspector = LayoutInspectorFixture(frame.project, robot)
    inspector.activate()

    // Resize the root panel to make the layout inspector window taller.
    frame.findToolWindowSplitter().lastSize = 300

    // Select a device and process and wait for the component tree to display the correct amount of nodes.
    inspector.selectDevice("${DEFAULT_DEVICE.manufacturer} ${DEFAULT_DEVICE.model}",
                           "${DEFAULT_PROCESS.name} (${DEFAULT_PROCESS.pid})")
    inspector.waitForComponentTreeToLoad(1)

    // Simulate that the user did something on the device that caused a different skia image to be received.
    transportRule.addEventToStream(DEFAULT_DEVICE, createBoxesTreeEvent(DEFAULT_PROCESS.pid))
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
    assertThat(table.rowCount()).isEqualTo(2)
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
    inspector.tree.requireSelection(0)
    inspector.waitForPropertiesToPopulate(toUrl(linearLayoutId))
    requireDeclaredAttributeValue(properties, "background", "#FFFF00")
    requireNoDeclaredAttribute(properties, "backgroundTint")

    // Click on the Button4
    deviceView.clickOnImage(500, 1400)
    inspector.tree.requireSelection(3)
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
    deviceView.waitUntilExpectedViewportHeight(1200, tolerance = 50)

    // Zoom In a second time
    deviceView.zoomInButton.click()
    deviceView.waitUntilExpectedViewportHeight(790, tolerance = 50)

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
    deviceView.waitUntilExpectedViewportHeight(1200, tolerance = 50)

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

  private fun startHandler(command: Commands.Command, events: MutableList<Common.Event>) {
    events.add(createSingleBoxTreeEvent(command.pid))
  }

  @Suppress("UNUSED_PARAMETER")
  private fun stopHandler(command: Commands.Command, events: MutableList<Common.Event>) {
    transportRule.revertToEventPositionMark(DEFAULT_DEVICE.deviceId)
  }

  private fun produceProperties(command: Commands.Command, events: MutableList<Common.Event>) {
    val event = when (command.layoutInspector.viewId) {
      1L -> createPropertiesForLinearLayout(command.pid)
      2L -> createPropertiesForFrameLayout(command.pid)
      3L -> createPropertiesForButton3(command.pid)
      4L -> createPropertiesForButton4(command.pid)
      else -> error("unexpected property request")
    }
    events.add(event)
  }

  private fun createSingleBoxTreeEvent(processId: Int): Common.Event {
    val strings = TestStringTable()
    return Common.Event.newBuilder().apply {
      pid = processId
      kind = Common.Event.Kind.LAYOUT_INSPECTOR
      groupId = Common.Event.EventGroupIds.COMPONENT_TREE.number.toLong()
      timestamp = System.currentTimeMillis()
      layoutInspectorEventBuilder.treeBuilder.apply {
        payloadId = PAYLOAD_SINGLE_BOX
        payloadType = ComponentTreeEvent.PayloadType.SKP
        rootBuilder.apply {
          drawId = LAYOUT_ID
          viewId = strings.add(linearLayoutId)
          layout = strings.add(layoutReference)
          x = 0
          y = 0
          width = 1000
          height = 2000
          className = strings.add("LinearLayout")
          packageName = strings.add("android.widget")
        }
        resourcesBuilder.apply {
          appPackageName = strings.add(NAMESPACE)
          theme = strings.add(ResourceReference.style(namespace, "AppTheme"))
        }
        addAllAllWindowIds(listOf(LAYOUT_ID))
        addAllString(strings.asEntryList())
      }
    }.build()
  }

  private fun createBoxesTreeEvent(processId: Int): Common.Event {
    val strings = TestStringTable()
    return Common.Event.newBuilder().apply {
      pid = processId
      kind = Common.Event.Kind.LAYOUT_INSPECTOR
      groupId = Common.Event.EventGroupIds.COMPONENT_TREE.number.toLong()
      timestamp = System.currentTimeMillis()
      layoutInspectorEventBuilder.treeBuilder.apply {
        payloadId = PAYLOAD_BOXES
        payloadType = ComponentTreeEvent.PayloadType.SKP
        rootBuilder.apply {
          drawId = LAYOUT_ID
          viewId = strings.add(linearLayoutId)
          layout = strings.add(layoutReference)
          x = 0
          y = 0
          width = 1000
          height = 2000
          className = strings.add("LinearLayout")
          packageName = strings.add("android.widget")
          addSubView(View.newBuilder().apply {
            drawId = 2L
            viewId = strings.add(frameLayoutId)
            layout = strings.add(layoutReference)
            x = 100
            y = 100
            width = 500
            height = 1000
            className = strings.add("FrameLayout")
            packageName = strings.add("android.widget")
            addSubView(View.newBuilder().apply {
              drawId = 3L
              viewId = strings.add(button3Id)
              layout = strings.add(layoutReference)
              x = 200
              y = 200
              width = 200
              height = 500
              className = strings.add("AppCompatButton")
              packageName = strings.add("androidx.appcompat.widget")
            })
          })
          addSubView(View.newBuilder().apply {
            drawId = 4L
            viewId = strings.add(button4Id)
            layout = strings.add(layoutReference)
            x = 300
            y = 1200
            width = 400
            height = 500
            className = strings.add("AppCompatButton")
            packageName = strings.add("androidx.appcompat.widget")
          })
        }
        resourcesBuilder.apply {
          appPackageName = strings.add(NAMESPACE)
          theme = strings.add(ResourceReference.style(namespace, "AppTheme"))
        }
        addAllAllWindowIds(listOf(LAYOUT_ID))
        addAllString(strings.asEntryList())
      }
    }.build()
  }

  private fun createPropertiesForLinearLayout(processId: Int): Common.Event {
    val strings = TestStringTable()
    return Common.Event.newBuilder().apply {
      pid = processId
      kind = Common.Event.Kind.LAYOUT_INSPECTOR
      groupId = Common.Event.EventGroupIds.PROPERTIES.number.toLong()
      timestamp = System.currentTimeMillis()
      layoutInspectorEventBuilder.propertiesBuilder.apply {
        viewId = 1L
        layout = strings.add(layoutReference)
        addProperty(Property.newBuilder().apply {
          name = strings.add("id")
          type = Property.Type.RESOURCE
          source = strings.add(layoutReference)
          resourceValue = strings.add(linearLayoutId)
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("background")
          type = Property.Type.COLOR
          source = strings.add(layoutReference)
          addResolutionStack(strings.add(layoutReference))
          int32Value = Color.YELLOW.rgb
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("orientation")
          type = Property.Type.INT_ENUM
          source = strings.add(layoutReference)
          int32Value = strings.add("vertical")
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("layout_width")
          type = Property.Type.INT_ENUM
          source = strings.add(layoutReference)
          isLayout = true
          int32Value = strings.add("match_parent")
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("layout_height")
          type = Property.Type.INT_ENUM
          source = strings.add(layoutReference)
          isLayout = true
          int32Value = strings.add("match_parent")
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("elevation")
          type = Property.Type.FLOAT
          floatValue = 0.0f
        })
        addAllString(strings.asEntryList())
      }
    }.build()
  }

  private fun createPropertiesForFrameLayout(processId: Int): Common.Event {
    val strings = TestStringTable()
    return Common.Event.newBuilder().apply {
      pid = processId
      kind = Common.Event.Kind.LAYOUT_INSPECTOR
      groupId = Common.Event.EventGroupIds.PROPERTIES.number.toLong()
      timestamp = System.currentTimeMillis()
      layoutInspectorEventBuilder.propertiesBuilder.apply {
        viewId = 2L
        layout = strings.add(layoutReference)
        addProperty(Property.newBuilder().apply {
          name = strings.add("id")
          type = Property.Type.RESOURCE
          source = strings.add(layoutReference)
          resourceValue = strings.add(frameLayoutId)
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("background")
          type = Property.Type.COLOR
          source = strings.add(layoutReference)
          addResolutionStack(strings.add(layoutReference))
          int32Value = Color.BLUE.rgb
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("layout_width")
          type = Property.Type.INT_ENUM
          source = strings.add(layoutReference)
          isLayout = true
          int32Value = strings.add("500")
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("layout_height")
          type = Property.Type.INT_ENUM
          source = strings.add(layoutReference)
          isLayout = true
          int32Value = strings.add("1000")
        })
        addAllString(strings.asEntryList())
      }
    }.build()
  }

  private fun createPropertiesForButton3(processId: Int): Common.Event {
    val strings = TestStringTable()
    return Common.Event.newBuilder().apply {
      pid = processId
      kind = Common.Event.Kind.LAYOUT_INSPECTOR
      groupId = Common.Event.EventGroupIds.PROPERTIES.number.toLong()
      timestamp = System.currentTimeMillis()
      layoutInspectorEventBuilder.propertiesBuilder.apply {
        viewId = 3L
        layout = strings.add(layoutReference)
        addProperty(Property.newBuilder().apply {
          name = strings.add("id")
          type = Property.Type.RESOURCE
          source = strings.add(layoutReference)
          resourceValue = strings.add(button3Id)
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("backgroundTint")
          type = Property.Type.COLOR
          source = strings.add(layoutReference)
          addResolutionStack(strings.add(layoutReference))
          addResolutionStack(strings.add(buttonStyleReference))
          int32Value = Color.BLACK.rgb
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("layout_width")
          type = Property.Type.INT32
          source = strings.add(layoutReference)
          isLayout = true
          int32Value = 200
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("layout_height")
          type = Property.Type.INT32
          source = strings.add(layoutReference)
          isLayout = true
          int32Value = 500
        })
        addAllString(strings.asEntryList())
      }
    }.build()
  }

  private fun createPropertiesForButton4(processId: Int): Common.Event {
    val strings = TestStringTable()
    return Common.Event.newBuilder().apply {
      pid = processId
      kind = Common.Event.Kind.LAYOUT_INSPECTOR
      groupId = Common.Event.EventGroupIds.PROPERTIES.number.toLong()
      timestamp = System.currentTimeMillis()
      layoutInspectorEventBuilder.propertiesBuilder.apply {
        viewId = 4L
        layout = strings.add(layoutReference)
        addProperty(Property.newBuilder().apply {
          name = strings.add("id")
          type = Property.Type.RESOURCE
          source = strings.add(layoutReference)
          resourceValue = strings.add(button4Id)
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("backgroundTint")
          type = Property.Type.COLOR
          source = strings.add(layoutReference)
          addResolutionStack(strings.add(layoutReference))
          addResolutionStack(strings.add(buttonStyleReference))
          int32Value = Color.RED.rgb
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("clickable")
          type = Property.Type.BOOLEAN
          int32Value = 1
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("elevation")
          type = Property.Type.FLOAT
          floatValue = 5.5f
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("gravity")
          type = Property.Type.GRAVITY
          source = strings.add(buttonMaterialReference)
          addResolutionStack(strings.add(buttonMaterialReference))
          flagValueBuilder.addAllFlag(listOf(strings.add("center")))
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("layout_width")
          type = Property.Type.INT32
          source = strings.add(layoutReference)
          isLayout = true
          int32Value = 400
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("layout_height")
          type = Property.Type.INT32
          source = strings.add(layoutReference)
          isLayout = true
          int32Value = 500
        })
        addProperty(Property.newBuilder().apply {
          name = strings.add("layout_gravity")
          type = Property.Type.GRAVITY
          isLayout = true
          flagValueBuilder.addAllFlag(listOf(strings.add("clip_horizontal"), strings.add("clip_vertical"), strings.add("fill")))
        })
        addAllString(strings.asEntryList())
      }
    }.build()
  }

  private external fun generateSingle(): ByteArray

  private external fun generateBoxes(): ByteArray

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
     *  - "settings get global debug_view_attributes"
     *  - "settings get global debug_view_attributes_application_package"
     *  - "settings put global debug_view_attributes 1"
     *  - "settings put global debug_view_attributes_application_package com.example.myapp"
     *  - "settings delete global debug_view_attributes"
     *  - "settings delete global debug_view_attributes_application_package"
     */
    private fun handleShellCommand(command: String): String? {
      val args = ArrayDeque(command.split(' '))
      if (args.poll() != "settings") {
        return null
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
        "get" -> { variable.get().toString() }
        "put" -> { variable.set(argument); debugViewAttributesChanges++; ""}
        "delete" -> { variable.set(null); debugViewAttributesChanges++; ""}
        else -> null
      }
    }
  }*/
}
