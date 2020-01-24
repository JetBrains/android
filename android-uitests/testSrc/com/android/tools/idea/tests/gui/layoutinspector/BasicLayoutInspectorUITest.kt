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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture
import com.android.tools.idea.tests.gui.framework.fixture.inspector.LayoutInspectorFixture
import com.android.tools.idea.tests.gui.framework.fixture.properties.PTableFixture
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorCommand
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.View
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.intellij.notification.EventLog
import com.intellij.openapi.project.Project
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.core.MouseButton
import org.fest.swing.data.TableCell
import org.fest.swing.timing.Wait
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.Color
import java.net.Socket

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
  val guiTest = GuiTestRule().apply { }

  @get:Rule
  val transportRule = TransportRule()
    .withCommandHandler(LayoutInspectorCommand.Type.START, ::startHandler)
    .withCommandHandler(LayoutInspectorCommand.Type.STOP, ::stopHandler)
    .withCommandHandler(LayoutInspectorCommand.Type.GET_PROPERTIES, ::produceProperties)
    .withTestData(guiTest.copyProjectBeforeOpening("DynamicLayoutInspector"))
    .withDeviceCommandHandler(commandHandler)
    .withFile(PAYLOAD_SINGLE_BOX, "single.skp")
    .withFile(PAYLOAD_BOXES, "boxes.skp")

  // TODO: Consider running this test by using the skia server from prebuilts.
  @get:Rule
  val flagRule = SetFlagRule(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_USE_DEVBUILD_SKIA_SERVER, true)

  @Test
  @RunIn(TestGroup.UNRELIABLE) // new test - wait and investigate test results before moving to default
  fun testLayoutInspector() {
    val robot = guiTest.robot()
    val frame = guiTest.importProjectAndWaitForProjectSyncToFinish(PROJECT_NAME)

    // Initialize the AndroidDebugBridge.
    waitForAdbToConnect(frame.project)

    // Hide the messages tool window which may appear after the build. Remove such that the inspector has more room.
    MessagesToolWindowFixture.ifExists(frame.project, robot)?.hide()

    // Hide notifications since they will overlap the properties panel and interfere with the test.
    EventLog.getLogModel(frame.project).notifications.forEach { it.balloon?.hide() }

    // Open the inspector tool window.
    val inspector = LayoutInspectorFixture(frame.project, robot)
    inspector.activate()

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
    inspector.waitForPropertiesToPopulate(button4Id.name)

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

    inspector.stop()
    Wait.seconds(10).expecting("debug attribute cleanup").until { commandHandler.stopped }
  }

  private fun waitForAdbToConnect(project: Project) {
    // Get the bridge synchronously, since we're in test mode.
    val bridge = AdbService.getInstance().getDebugBridge(AndroidSdkUtils.getAdb(project)!!).get()

    // Wait for ADB.
    Wait.seconds(10).expecting("Android Debug Bridge to connect").until { bridge.isConnected }
    Wait.seconds(10).expecting("Initial device list is available").until { bridge.hasInitialDeviceList() }

    // Add the default device and process
    transportRule.addProcess(DEFAULT_DEVICE, DEFAULT_PROCESS)
  }

  private fun startHandler(command: Commands.Command, events: MutableList<Common.Event>) {
    events.add(createSingleBoxTreeEvent(command.pid))
  }

  private fun stopHandler(command: Commands.Command, events: MutableList<Common.Event>) {
    events.add(Common.Event.newBuilder().apply {
      pid = command.pid
      kind = Common.Event.Kind.PROCESS
      groupId = command.pid.toLong()
      isEnded = true
    }.build())
  }

  private fun produceProperties(command: Commands.Command, events: MutableList<Common.Event>) {
    val event = when (command.layoutInspector.viewId) {
      1L -> createPropertiesForLinearLayout(command.pid)
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
          width = 100
          height = 200
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

  private class MyDeviceCommandHandler : DeviceCommandHandler("shell") {
    private var debugAttributeCleanupCount = 0

    val stopped: Boolean
      get() = debugAttributeCleanupCount == 2

    override fun accept(server: FakeAdbServer, socket: Socket, device: DeviceState, command: String, args: String): Boolean {
      when (args) {
        "settings put global debug_view_attributes 1",
        "settings put global debug_view_attributes_application_package com.example" ->
          return writeOK(socket)

        "settings delete global debug_view_attributes",
        "settings delete global debug_view_attributes_application_package" -> {
          debugAttributeCleanupCount++
          return writeOK(socket)
        }

        else -> return false
      }
    }

    private fun writeOK(socket: Socket): Boolean {
      com.android.fakeadbserver.CommandHandler.writeOkay(socket.getOutputStream())
      return true
    }
  }
}
