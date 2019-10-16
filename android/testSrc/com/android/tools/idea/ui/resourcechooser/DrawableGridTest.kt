/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcechooser

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.resources.ResourceType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.plugin.DesignAssetRenderer
import com.google.common.truth.Truth
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class DrawableGridTest {

  companion object {
    @JvmField
    @ClassRule
    val rule = AndroidProjectRule.onDisk()

    @JvmStatic
    @BeforeClass
    fun setUp() {
      rule.fixture.testDataPath = getTestDataDirectory()
    }
  }

  @Test
  fun canTSelectNullElement() {
    val resourceValue = ResourceValueImpl(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "resourceValue", "")
    val grid = DrawableGrid(rule.fixture.module,
                            DefaultListModel<ResourceValue>().apply {
                              addElement(resourceValue)
                              addElement(null)
                            })

    grid.selectedIndex = 1
    Truth.assertThat(grid.selectedIndex).isEqualTo(1)
    grid.selectedIndex = 2
    Truth.assertThat(grid.selectedIndex).isEqualTo(1)
  }

  private val testColor = Color(0xFF, 0xAA, 0xBB, 0xFF)
  private val disabledNonNullColor = 0xff80555e.toInt()
  private val enabledNullColor = 0xff222222.toInt()
  private val disabledNullColor = 0xff222222.toInt()

  @Test
  fun renderCell() {

    val image = UIUtil.createImage(1, 1, BufferedImage.TYPE_INT_ARGB).apply {
      with(createGraphics()) {
        color = testColor
        drawRect(0, 0, 1, 1)
        dispose()
      }
    }
    val renderer = StubRenderer()
    renderer.registerAsExtension(rule.fixture.project)
    val childFile = createFakeFile("file")
    val resourceValue = ResourceValueImpl(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "resourceValue", childFile.path)
    val grid = DrawableGrid(rule.fixture.module,
                            DefaultListModel<ResourceValue>().apply {
                              addElement(resourceValue)
                              addElement(null)
                            })

    grid.cellRenderer.getListCellRendererComponent(grid, resourceValue, 1, false, false)

    UIUtil.invokeAndWaitIfNeeded(Runnable {
      renderer.simulateRender(image)
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    })

    renderer.waitForRender()
    Truth.assertWithMessage("Renderer was never called").that(renderer.hasRendered()).isTrue()

    val component1 = grid.cellRenderer.getListCellRendererComponent(grid, resourceValue, 1, false, false) as JComponent
    val list = UIUtil.findComponentsOfType(component1, JLabel::class.java)

    assertColor(list[0].icon, testColor.rgb)
    assertColor(list[0].disabledIcon, disabledNonNullColor)

    val component2 = grid.cellRenderer.getListCellRendererComponent(grid, null, 2, false, false) as JComponent
    val list2 = UIUtil.findComponentsOfType(component2, JLabel::class.java)
    assertColor(list2[0].icon, enabledNullColor)
    assertColor(list2[0].disabledIcon, disabledNullColor)
  }

  private fun createFakeFile(name: String) =
    LocalFileSystem.getInstance().createChildFile(this, VfsUtil.createDirectories(rule.fixture.tempDirPath), name)
}

private val testingImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
private val testingComponent = JPanel()

private fun assertColor(icon: Icon,
                        pixelRgb: Int) {
  val graphics = testingImage.createGraphics()
  graphics.clearRect(0, 0, 1, 1)
  graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_DEFAULT)
  icon.paintIcon(testingComponent, graphics, 0, 0)
  graphics.dispose()
  Truth.assertThat(Integer.toHexString(testingImage.getRGB(0, 0))).isEqualTo(Integer.toHexString(pixelRgb))
}

class StubRenderer : DesignAssetRenderer {
  private val future = CompletableFuture<BufferedImage?>()
  private val latch = CountDownLatch(1)

  override fun isFileSupported(file: VirtualFile) = true

  override fun getImage(file: VirtualFile,
                        module: Module?,
                        dimension: Dimension): CompletableFuture<out BufferedImage?> = future

  fun simulateRender(image: BufferedImage?) {
    future.complete(image)
    latch.countDown()
  }

  fun waitForRender() {
    latch.await(1, TimeUnit.SECONDS)
  }

  fun hasRendered() = latch.count == 0L

  fun registerAsExtension(disposable: Disposable) {
    PlatformTestUtil.registerExtension(ExtensionPointName.create<DesignAssetRenderer>("com.android.resourceViewer"),
                                       this,
                                       disposable)
  }
}