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
package com.android.tools.idea.layoutinspector.legacydevice

import com.android.ddmlib.ByteBufferUtil
import com.android.ddmlib.DebugViewDumpHandler
import com.android.ddmlib.DebugViewDumpHandler.CHUNK_VULW
import com.android.ddmlib.DebugViewDumpHandler.CHUNK_VURT
import com.android.ddmlib.FakeClientBuilder
import com.android.ddmlib.internal.ClientImpl
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket
import com.android.ddmlib.testing.FakeAdbRule
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.nio.ByteBuffer

class LegacyTreeLoaderTest {

  @Rule
  @JvmField
  val adb = FakeAdbRule()

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
  fun testParseNodes() {
    val provider = LegacyPropertiesProvider()
    val propertiesUpdater = LegacyPropertiesProvider.Updater()
    val (root, hash) = LegacyTreeLoader.parseLiveViewNode(treeSample.toByteArray(Charsets.UTF_8), propertiesUpdater)!!
    propertiesUpdater.apply(provider)
    provider.requestProperties(root)
    assertThat(hash).isEqualTo("com.android.internal.policy.DecorView@41673e3")
    assertThat(root.drawId).isEqualTo(0x41673e3)
    assertThat(root.x).isEqualTo(0)
    assertThat(root.y).isEqualTo(0)
    assertThat(root.width).isEqualTo(1080)
    assertThat(root.height).isEqualTo(1920)
    assertThat(root.scrollX).isEqualTo(0)
    assertThat(root.scrollY).isEqualTo(0)
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
    assertThat(actionMenuView.x).isEqualTo(932)
    assertThat(actionMenuView.y).isEqualTo(63)
    assertThat(actionMenuView.width).isEqualTo(148)
    assertThat(actionMenuView.height).isEqualTo(147)
    assertThat(actionMenuView.scrollX).isEqualTo(0)
    assertThat(actionMenuView.scrollY).isEqualTo(0)
    assertThat(actionMenuView.viewId.toString()).isEqualTo("ResourceReference{namespace=apk/res-auto, type=id, name=ac}")
  }

  private fun printTree(node: ViewNode, indent: Int = 0): String =
    " ".repeat(indent) + "0x${node.drawId.toString(16)}\n${node.children.joinToString("") { printTree(it, indent + 1) }}"

  private fun findView(nodes: List<ViewNode>, drawId: Long): ViewNode =
    nodes.find { it.drawId == drawId } ?: findView(nodes.flatMap { it.children }, drawId)

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
    val ddmClient = FakeClientBuilder().registerResponse(requestMatcher, CHUNK_VULW, responseBytes).build()

    val legacyClient = LegacyClient(mock(Project::class.java))
    legacyClient.selectedClient = ddmClient

    val result = LegacyTreeLoader.getAllWindowIds(null, legacyClient)
    assertThat(result).containsExactly(window1, window2)
  }

  @Test
  fun testLoadComponentTree() {
    val legacyClient = mock(LegacyClient::class.java)
    val client = mock(ClientImpl::class.java)
    `when`(legacyClient.selectedClient).thenReturn(client)
    `when`(client.send(argThat { argument ->
      argument?.payload?.int == CHUNK_VURT &&
      argument.payload.getInt(8) == 1 /* VURT_DUMP_HIERARCHY */
    }, any())).thenAnswer { invocation ->
      invocation
        .getArgument(1, DebugViewDumpHandler::class.java)
        .handleChunk(client, CHUNK_VURT, ByteBuffer.wrap(treeSample.toByteArray(Charsets.UTF_8)), true, 1)
    }
    `when`(client.dumpViewHierarchy(eq("window1"), anyBoolean(), anyBoolean(), anyBoolean(),
                                    any(DebugViewDumpHandler::class.java))).thenCallRealMethod()
    val (viewNode, windowId) = LegacyTreeLoader.loadComponentTree(
      LegacyEvent("window1", LegacyPropertiesProvider.Updater(), listOf("window1")),
      mock(ResourceLookup::class.java), legacyClient)!!
    assertThat(windowId).isEqualTo("window1")
    assertThat(viewNode.drawId).isEqualTo(0x41673e3)
  }
}
