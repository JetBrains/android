/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.snapshots

import com.android.ddmlib.DebugViewDumpHandler
import com.android.ddmlib.internal.ClientImpl
import com.android.ddmlib.testing.FakeAdbRule
import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.TestUtils
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.layoutinspector.LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatisticsImpl
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.DrawViewImage
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.legacy.LegacyClient
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType.SNAPSHOT_CLIENT
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.util.io.readBytes
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

class LegacySnapshotSupportTest {
  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  val adb = FakeAdbRule()

  @get:Rule
  val disposableRule = DisposableRule()

  private val savePath = createInMemoryFileSystemAndFolder("snapshot").resolve("snapshot.li")

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

  @Test
  fun saveAndLoadSnapshot() {
    val imageFile = TestUtils.resolveWorkspacePathUnchecked("${TEST_DATA_PATH}/image1.png")
    val legacyClient = setUpLegacyClient()

    val windowName = "window1"
    setUpDdmClient(treeSample, windowName, imageFile, legacyClient)

    legacyClient.refresh()
    waitForCondition(5, TimeUnit.SECONDS) { !legacyClient.model.isEmpty }
    legacyClient.saveSnapshot(savePath)
    val snapshotLoader = SnapshotLoader.createSnapshotLoader(savePath)!!
    val newModel = InspectorModel(projectRule.project)
    val stats = SessionStatisticsImpl(SNAPSHOT_CLIENT, newModel)
    snapshotLoader.loadFile(savePath, newModel, stats)

    val window = newModel.windows[windowName]!!
    window.refreshImages(1.0)
    val root = window.root
    assertThat(root.drawId).isEqualTo(0x41673e3)
    assertThat(root.layoutBounds.x).isEqualTo(0)
    assertThat(root.layoutBounds.y).isEqualTo(0)
    assertThat(root.layoutBounds.width).isEqualTo(1080)
    assertThat(root.layoutBounds.height).isEqualTo(1920)
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
    val actionMenuView = newModel[0x29668e4]!!
    assertThat(actionMenuView.drawId).isEqualTo(0x29668e4)
    assertThat(actionMenuView.layoutBounds.x).isEqualTo(932)
    assertThat(actionMenuView.layoutBounds.y).isEqualTo(63)
    assertThat(actionMenuView.layoutBounds.width).isEqualTo(148)
    assertThat(actionMenuView.layoutBounds.height).isEqualTo(147)
    assertThat(actionMenuView.viewId.toString()).isEqualTo("ResourceReference{namespace=apk/res-auto, type=id, name=ac}")
    val actualImage = ViewNode.readAccess { window.root.drawChildren.filterIsInstance<DrawViewImage>().first().image }
    ImageDiffUtil.assertImageSimilar(imageFile, actualImage as BufferedImage, 0.0)
  }

  private fun setUpLegacyClient(): LegacyClient {
    val model = model(project = projectRule.project) {}
    val process = LEGACY_DEVICE.createProcess()
    val legacyClient = LegacyClient(process, isInstantlyAutoConnected = true, model,
                                    LayoutInspectorMetrics(projectRule.project, process),
                                    disposableRule.disposable).apply {
      launchMonitor = mock()
    }
    // This causes the current client to register its listeners
    val treeSettings = FakeTreeSettings()
    LayoutInspector(legacyClient, model, treeSettings)
    return legacyClient
  }

  @Suppress("SameParameterValue")
  private fun setUpDdmClient(
    treeSample: String,
    windowName: String,
    imageFile: Path,
    legacyClient: LegacyClient
  ) {
    val client = mock<ClientImpl>()
    whenever(client.device).thenReturn(mock())
    whenever(client.send(ArgumentMatchers.argThat { argument ->
      argument?.payload?.int == DebugViewDumpHandler.CHUNK_VURT &&
        argument.payload.getInt(8) == 1 /* VURT_DUMP_HIERARCHY */
    }, ArgumentMatchers.any())).thenAnswer { invocation ->
      invocation
        .getArgument(1, DebugViewDumpHandler::class.java)
        .handleChunk(client, DebugViewDumpHandler.CHUNK_VURT, ByteBuffer.wrap(treeSample.toByteArray(Charsets.UTF_8)), true, 1)
    }
    whenever(client.dumpViewHierarchy(ArgumentMatchers.eq(windowName), ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean(),
                                            ArgumentMatchers.anyBoolean(),
                                            ArgumentMatchers.any(DebugViewDumpHandler::class.java))).thenCallRealMethod()
    whenever(client.listViewRoots(any())).thenAnswer { invocation ->
      val bytes = ByteBuffer.allocate(windowName.length * 2 + Int.SIZE_BYTES * 2).apply {
        putInt(1)
        putInt(windowName.length)
        put(windowName.toByteArray(Charsets.UTF_16BE))
        rewind()
      }
      invocation
        .getArgument(0, DebugViewDumpHandler::class.java)
        .handleChunk(client, DebugViewDumpHandler.CHUNK_VULW, bytes, true, 1)
    }

    whenever(
      client.captureView(ArgumentMatchers.eq(windowName), ArgumentMatchers.any(), ArgumentMatchers.any())).thenAnswer { invocation ->
      invocation
        .getArgument<DebugViewDumpHandler>(2)
        .handleChunk(client, DebugViewDumpHandler.CHUNK_VUOP, ByteBuffer.wrap(imageFile.readBytes()), true, 1234)
    }
    legacyClient.treeLoader.ddmClientOverride = client
  }

  private fun printTree(node: ViewNode, indent: Int = 0): String {
    val children = ViewNode.readAccess { node.children }
    return " ".repeat(indent) + "0x${node.drawId.toString(16)}\n${children.joinToString("") { printTree(it, indent + 1) }}"
  }
}