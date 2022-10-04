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
package com.android.tools.idea.layoutinspector.pipeline.legacy

import com.android.ddmlib.ByteBufferUtil
import com.android.ddmlib.Client
import com.android.ddmlib.DebugViewDumpHandler
import com.android.ddmlib.DebugViewDumpHandler.CHUNK_VULW
import com.android.ddmlib.FakeClientBuilder
import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket
import com.android.ddmlib.testing.FakeAdbRule
import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoCleanerRule
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.TestUtils
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.layoutinspector.LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.DrawViewImage
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.DimensionUnits
import com.android.tools.idea.layoutinspector.properties.PropertiesSettings
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.util.CheckUtil.assertDrawTreesEqual
import com.android.tools.idea.layoutinspector.view
import com.android.tools.idea.testing.registerServiceInstance
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.DisposableRule
import com.intellij.util.io.readBytes
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.verify
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import javax.imageio.ImageIO

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

class LegacyTreeLoaderTest {
  private val disposableRule = DisposableRule()

  @get:Rule
  val chain = RuleChain.outerRule(FakeAdbRule()).around(MockitoCleanerRule()).around(disposableRule)!!

  companion object {
    @JvmField
    @ClassRule
    val rule = com.intellij.testFramework.ApplicationRule()
  }

  @Before
  fun init() {
    val propertiesComponent = PropertiesComponentMock()
    val application = ApplicationManager.getApplication()
    application.registerServiceInstance(PropertiesComponent::class.java, propertiesComponent, disposableRule.disposable)
    PropertiesSettings.dimensionUnits = DimensionUnits.PIXELS
  }

  private val treeSample = """
com.android.internal.policy.DecorView@41673e3 mID=5,NO_ID layout:getHeight()=4,1920 layout:getLocationOnScreen_x()=1,0 layout:getLocationOnScreen_y()=1,0 layout:getWidth()=4,1080
 android.widget.LinearLayout@8dc1681 mID=5,NO_ID layout:getHeight()=4,1794 layout:getLocationOnScreen_x()=1,0 layout:getLocationOnScreen_y()=1,0 layout:getWidth()=4,1080
  androidx.appcompat.widget.FitWindowsLinearLayout@d0e237b mID=18,id/action_bar_root layout:getHeight()=4,1794 layout:getLocationOnScreen_x()=1,0 layout:getLocationOnScreen_y()=1,0 layout:getWidth()=4,1080
  androidx.coordinatorlayout.widget.CoordinatorLayout@1d72495 mID=5,NO_ID layout:getHeight()=4,1794 layout:getLocationOnScreen_x()=1,0 layout:getLocationOnScreen_y()=1,0 layout:getWidth()=4,1080
   com.google.android.material.appbar.AppBarLayout@51a200b mID=9,id/appbar layout:getHeight()=3,730 layout:getLocationOnScreen_x()=1,0 layout:getLocationOnScreen_y()=1,0 layout:getWidth()=4,1080
   androidx.appcompat.widget.Toolbar@fbf7138 mID=10,id/toolbar layout:getHeight()=3,147 layout:getLocationOnScreen_x()=1,0 layout:getLocationOnScreen_y()=2,63 layout:getWidth()=4,1080
    androidx.appcompat.widget.AppCompatImageButton@2527511 mID=5,NO_ID layout:getHeight()=3,147 layout:getLocationOnScreen_x()=1,0 layout:getLocationOnScreen_y()=2,63 layout:getWidth()=3,147
    androidx.appcompat.widget.ActionMenuView@29668e4 mID=5,id/actionMenu layout:getHeight()=3,147 layout:getLocationOnScreen_x()=3,932 layout:getLocationOnScreen_y()=2,63 layout:getWidth()=3,148
   androidx.core.widget.NestedScrollView@5652ee8 mID=26,id/plant_detail_scrollview layout:getHeight()=4,1584 layout:getLocationOnScreen_x()=1,0 layout:getLocationOnScreen_y()=3,730 layout:getWidth()=4,1080
    com.google.android.material.textview.MaterialTextView@2d35b6f mID=20,id/plant_detail_name layout:getHeight()=2,85 layout:getLocationOnScreen_x()=2,63 layout:getLocationOnScreen_y()=3,772 layout:getWidth()=3,954
    com.google.android.material.textview.MaterialTextView@b3f07c needs mID=24,id/plant_watering_header layout:getHeight()=2,51 layout:getLocationOnScreen_x()=2,63 layout:getLocationOnScreen_y()=3,899 layout:getWidth()=3,954
   com.google.android.material.floatingactionbutton.FloatingActionButton@fcfd901 mID=6,id/fab layout:getHeight()=3,147 layout:getLocationOnScreen_x()=4,1856 layout:getLocationOnScreen_y()=4,1388 layout:getWidth()=3,147
 android.view.View@3d2ff9c mID=22,id/statusBarBackground layout:getHeight()=2,63 layout:getLocationOnScreen_x()=1,0 layout:getLocationOnScreen_y()=1,0 layout:getWidth()=4,1080
DONE.
""".trim()

  /**
   * Creates a real [LegacyClient] that's good enough for most tests and provides access to an
   * internally constructed [LegacyTreeLoader]
   */
  private fun createSimpleLegacyClient(): LegacyClient {
    val model = model {}
    val process = LEGACY_DEVICE.createProcess()
    return LegacyClient(process, isInstantlyAutoConnected = false, model, LayoutInspectorMetrics(model.project, process),
                        disposableRule.disposable).apply {
      launchMonitor = mock()
    }
  }

  /**
   * Creates a mock [LegacyClient] with tree loader and screenshots initialized.
   *
   * Callers can continue to mock the returned client if necessary.
   */
  private fun createMockLegacyClient(): LegacyClient {
    val legacyClient = mock<LegacyClient>()
    whenever(legacyClient.latestScreenshots).thenReturn(mutableMapOf())
    whenever(legacyClient.treeLoader).thenReturn(LegacyTreeLoader(legacyClient))
    whenever(legacyClient.process).thenReturn(LEGACY_DEVICE.createProcess())
    whenever(legacyClient.launchMonitor).thenReturn(mock())
    return legacyClient
  }

  @Test
  fun testParseNodes() {
    val lookup = mock<ViewNodeAndResourceLookup>()
    val resourceLookup = mock<ResourceLookup>()
    whenever(lookup.resourceLookup).thenReturn(resourceLookup)
    whenever(resourceLookup.dpi).thenReturn(-1)
    val provider = LegacyPropertiesProvider()
    val propertiesUpdater = LegacyPropertiesProvider.Updater(lookup)

    val (root, hash) = LegacyTreeParser.parseLiveViewNode(treeSample.toByteArray(Charsets.UTF_8), propertiesUpdater)!!
    propertiesUpdater.apply(provider)
    provider.requestProperties(root)
    assertThat(hash).isEqualTo("com.android.internal.policy.DecorView@41673e3")
    assertThat(root.drawId).isEqualTo(0x41673e3)
    assertThat(root.layoutBounds.x).isEqualTo(0)
    assertThat(root.layoutBounds.y).isEqualTo(0)
    assertThat(root.layoutBounds.width).isEqualTo(1080)
    assertThat(root.layoutBounds.height).isEqualTo(1920)
    assertThat(root.renderBounds).isSameAs(root.layoutBounds)
    assertThat(root.viewId).isNull()
    assertThat(printTree(root).trim()).isEqualTo("""
          0x41673e3
           0x8dc1681
            0xd0e237b
            0x1d72495
             0x51a200b
             0xfbf7138
              0x2527511
              0x29668e4
             0x5652ee8
              0x2d35b6f
              0xb3f07c
             0xfcfd901
           0x3d2ff9c
           """.trimIndent())
    val actionMenuView = findView(listOf(root), 0x29668e4)
    assertThat(actionMenuView.drawId).isEqualTo(0x29668e4)
    assertThat(actionMenuView.layoutBounds.x).isEqualTo(932)
    assertThat(actionMenuView.layoutBounds.y).isEqualTo(63)
    assertThat(actionMenuView.layoutBounds.width).isEqualTo(148)
    assertThat(actionMenuView.layoutBounds.height).isEqualTo(147)
    assertThat(actionMenuView.viewId.toString()).isEqualTo("ResourceReference{namespace=apk/res-auto, type=id, name=ac}")
  }

  private fun printTree(node: ViewNode, indent: Int = 0): String {
    val children = ViewNode.readAccess { node.children }
    return " ".repeat(indent) + "0x${node.drawId.toString(16)}\n${children.joinToString("") { printTree(it, indent + 1) }}"
  }

  private fun findView(nodes: List<ViewNode>, drawId: Long): ViewNode =
    ViewNode.readAccess {
      nodes.find { it.drawId == drawId } ?: findView(nodes.flatMap { it.children }, drawId)
    }

  @Test
  fun testGetAllWindowIds() {
    val requestMatcher: ArgumentMatcher<JdwpPacket> = ArgumentMatcher { it.payload.getInt(0) == CHUNK_VULW }
    val window1 = "myWindowNumberOne"
    val window2 = "theOtherWindow"
    val responseBytes = ByteBuffer.allocate(window1.length * 2 + window2.length * 2 + 4 * 3)
    responseBytes.putInt(2)
    responseBytes.putInt(window1.length)
    ByteBufferUtil.putString(responseBytes, window1)
    responseBytes.putInt(window2.length)
    ByteBufferUtil.putString(responseBytes, window2)

    val legacyClient = createSimpleLegacyClient()
    legacyClient.treeLoader.ddmClientOverride = FakeClientBuilder().registerResponse(requestMatcher, CHUNK_VULW, responseBytes).build()
    val result = legacyClient.treeLoader.getAllWindowIds(null)
    assertThat(result).containsExactly(window1, window2)
    val launchMonitor = legacyClient.launchMonitor
    verify(launchMonitor).updateProgress(DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_WINDOW_LIST_REQUESTED)
    verify(launchMonitor).updateProgress(DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_WINDOW_LIST_RECEIVED)
  }

  @Test
  fun testLoadComponentTree() {
    val imageBytes = TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/image1.png").readBytes()
    val lookup = mock<ViewNodeAndResourceLookup>()
    val resourceLookup = mock<ResourceLookup>()
    val legacyClient = createMockLegacyClient()
    val device = mock<IDevice>()
    val client = mock<Client>()
    whenever(lookup.resourceLookup).thenReturn(resourceLookup)
    whenever(device.density).thenReturn(560)
    whenever(client.device).thenReturn(device)
    whenever(client.dumpViewHierarchy(eq("window1"), anyBoolean(), anyBoolean(), anyBoolean(),
                                    any(DebugViewDumpHandler::class.java))).thenAnswer { invocation ->
      verify(legacyClient.launchMonitor).updateProgress(DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_HIERARCHY_REQUESTED)
      invocation
        .getArgument<DebugViewDumpHandler>(4)
        .handleChunkData(ByteBuffer.wrap(treeSample.toByteArray(Charsets.UTF_8)))
    }
    whenever(client.captureView(eq("window1"), any(), any())).thenAnswer { invocation ->
      verify(legacyClient.launchMonitor).updateProgress(DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_SCREENSHOT_REQUESTED)
      invocation
        .getArgument<DebugViewDumpHandler>(2)
        .handleChunk(client, DebugViewDumpHandler.CHUNK_VUOP, ByteBuffer.wrap(imageBytes), true, 1234)
    }
    legacyClient.treeLoader.ddmClientOverride = client
    val window = legacyClient.treeLoader.loadComponentTree(
      LegacyEvent("window1", LegacyPropertiesProvider.Updater(lookup), listOf("window1")),
      resourceLookup,
      legacyClient.process
    )!!.window!!
    window.refreshImages(1.0)

    assertThat(window.id).isEqualTo("window1")

    val expected = view(0x41673e3, width = 585, height = 804) {
      image(ImageIO.read(ByteArrayInputStream(imageBytes)))
      view(0x8dc1681) {
        view(0xd0e237b)
        view(0x1d72495) {
          view(0x51a200b)
          view(0xfbf7138) {
            view(0x2527511)
            view(0x29668e4)
          }
          view(0x5652ee8) {
            view(0x2d35b6f)
            view(0xb3f07c)
          }
          view(0xfcfd901)
        }
      }
      view(0x3d2ff9c)
    }
    assertDrawTreesEqual(expected, window.root)
    verify(resourceLookup).updateConfiguration(eq(560), isNull(), isNull())
    verify(legacyClient.launchMonitor).updateProgress(DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_HIERARCHY_RECEIVED)
    verify(legacyClient.launchMonitor).updateProgress(DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_SCREENSHOT_RECEIVED)
  }

  @Suppress("UndesirableClassUsage")
  @Test
  fun testRefreshImages() {
    val imageBytes = TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/image1.png").readBytes()
    val image1 = ImageIO.read(ByteArrayInputStream(imageBytes))
    val lookup = mock<ViewNodeAndResourceLookup>()
    val resourceLookup = mock<ResourceLookup>()
    val legacyClient = createMockLegacyClient()
    val device = mock<IDevice>()
    val client = mock<Client>()
    whenever(client.device).thenReturn(device)
    whenever(lookup.resourceLookup).thenReturn(resourceLookup)
    whenever(client.dumpViewHierarchy(eq("window1"), anyBoolean(), anyBoolean(), anyBoolean(),
                                    any(DebugViewDumpHandler::class.java))).thenAnswer { invocation ->
      invocation
        .getArgument<DebugViewDumpHandler>(4)
        .handleChunkData(ByteBuffer.wrap("""
          com.android.internal.policy.DecorView@41673e3 mID=5,NO_ID layout:getHeight()=4,1920 layout:getWidth()=4,1080
           android.widget.LinearLayout@8dc1681 mID=5,NO_ID layout:getHeight()=4,1794 layout:getWidth()=4,1080
          DONE.

        """.trimIndent().toByteArray(Charsets.UTF_8)))
    }
    whenever(client.captureView(eq("window1"), any(), any())).thenAnswer { invocation ->
      invocation
        .getArgument<DebugViewDumpHandler>(2)
        .handleChunk(client, DebugViewDumpHandler.CHUNK_VUOP, ByteBuffer.wrap(imageBytes), true, 1234)
    }

    legacyClient.treeLoader.ddmClientOverride = client
    val window = legacyClient.treeLoader.loadComponentTree(
      LegacyEvent("window1", LegacyPropertiesProvider.Updater(lookup), listOf("window1")),
      resourceLookup,
      legacyClient.process
    )!!.window!!
    val model = InspectorModel(mock())
    model.update(window, listOf("window1"), 0)

    window.refreshImages(1.0)

    ImageDiffUtil.assertImageSimilar("image1.png", image1, ViewNode.readAccess { window.root.drawChildren }
      .filterIsInstance<DrawViewImage>()
      .first()
      .image, 0.0)

    // Zoom and verify the image is rescaled
    window.refreshImages(0.5)
    val scaledImage1 = BufferedImage(image1.width / 2, image1.height / 2, BufferedImage.TYPE_INT_ARGB)
    scaledImage1.graphics.drawImage(image1, 0, 0, scaledImage1.width, scaledImage1.height, null)

    ImageDiffUtil.assertImageSimilar("image1.png", scaledImage1, ViewNode.readAccess { window.root.drawChildren }
      .filterIsInstance<DrawViewImage>()
      .first()
      .image, 0.0)

    // Update the image returned by the device and verify the draw image is not refreshed yet
    val image2Bytes = TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/image2.png").readBytes()
    val image2 = ImageIO.read(ByteArrayInputStream(image2Bytes))

    whenever(client.captureView(eq("window1"), any(), any())).thenAnswer { invocation ->
      invocation
        .getArgument<DebugViewDumpHandler>(2)
        .handleChunk(client, DebugViewDumpHandler.CHUNK_VUOP, ByteBuffer.wrap(image2Bytes), true, 1234)
    }

    window.refreshImages(1.0)
    ImageDiffUtil.assertImageSimilar("image1.png", image1, ViewNode.readAccess { window.root.drawChildren }
      .filterIsInstance<DrawViewImage>()
      .first()
      .image, 0.0)

    // Update and verify the image is updated
    val (updatedWindow, _) = legacyClient.treeLoader.loadComponentTree(
      LegacyEvent("window1", LegacyPropertiesProvider.Updater(lookup), listOf("window1")), resourceLookup, legacyClient.process
    )!!
    model.update(updatedWindow, listOf("window1"), 1)

    window.refreshImages(1.0)
    ImageDiffUtil.assertImageSimilar("image2.png", image2, ViewNode.readAccess { window.root.drawChildren }
      .filterIsInstance<DrawViewImage>()
      .first()
      .image, 0.0)
  }
}
