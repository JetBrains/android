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
package com.android.tools.idea.ui.resourcemanager.rendering

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.ide.common.resources.ResourceFile
import com.android.ide.common.resources.ResourceMergerItem
import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.BaseAsset
import com.intellij.configurationStore.runInAllowSaveMode
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.ImageUtil
import org.intellij.lang.annotations.Language
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.awt.image.BufferedImage
import java.io.File
import javax.swing.Icon
import javax.swing.JLabel
import kotlin.test.assertEquals

@Language("XML")
private const val STATELIST_COLOR_FILE_CONTENTS =
  "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
  "    <item android:state_selected=\"true\" android:color=\"#F00\"/>\n" +
  "    <item android:state_activated=\"true\" android:color=\"#0F0\"/>\n" +
  "</selector>"

@Language("XML")
private const val COLOR_RESOURCE_FILE_CONTENTS =
  "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
  "    <color name=\"my_color\">#F00</color>\n" +
  "</resources>"


class MultipleColorIconProviderTest {
  @get:Rule
  val rule = AndroidProjectRule.onDisk()

  @Before
  fun setup() {
    runInEdtAndWait { runInAllowSaveMode { rule.project.save() } }
  }


  @Test
  fun getStateListColorIcon() {
    rule.fixture.addFileToProject("res/color/my_statelist_color.xml", STATELIST_COLOR_FILE_CONTENTS)
    val statelistResource = ResourceRepositoryManager.getInstance(rule.module)!!.appResources.getResources(ResourceNamespace.RES_AUTO,
                                                                                                           ResourceType.COLOR,
                                                                                                           "my_statelist_color").first()
    val statelistAsset = Asset.fromResourceItem(statelistResource, ResourceType.COLOR)

    val colorIconProvider = createColorIconProvider()
    val colorIcon = colorIconProvider.getIcon(statelistAsset, 20, 20, JLabel(), {})
    val colorImage = colorIcon.createBufferedImage()

    assertEquals(0xffff0000.toInt(), colorImage.getRGB(0, 0))
    assertEquals(0xff00ff00.toInt(), colorImage.getRGB(19, 0))
  }

  @Test
  fun getColorIconFromResourceFile() {
    rule.fixture.addFileToProject("res/values/values.xml", COLOR_RESOURCE_FILE_CONTENTS)
    val colorResource = ResourceRepositoryManager.getInstance(rule.module)!!.appResources.getResources(ResourceNamespace.RES_AUTO,
                                                                                                       ResourceType.COLOR,
                                                                                                       "my_color").first()
    val colorAsset = Asset.fromResourceItem(colorResource, ResourceType.COLOR)

    val colorIconProvider = createColorIconProvider()
    val colorIcon = colorIconProvider.getIcon(colorAsset, 20, 20, JLabel(), {})
    val colorImage = colorIcon.createBufferedImage()

    assertEquals(0xffff0000.toInt(), colorImage.getRGB(10, 10))
  }

  @Test
  fun getColorFromNonDesignAsset() {
    ResourceRepositoryManager.getInstance(rule.module)!!
    val asset = BaseAsset(ResourceType.COLOR, "my_color")
    ResourceFile.createSingle(File("source"), asset.resourceItem as ResourceMergerItem, "")

    val resourceResolver = Mockito.mock(ResourceResolver::class.java)
    whenever(resourceResolver.resolveResValue(ArgumentMatchers.any())).thenReturn(
      ResourceValueImpl(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "my_color", "#00F")
    )

    val colorIconProvider = createColorIconProvider(resourceResolver)

    val colorIcon = colorIconProvider.getIcon(asset, 20, 20, JLabel(), {})
    val colorImage = colorIcon.createBufferedImage()

    assertEquals(0xff0000ff.toInt(), colorImage.getRGB(10, 10))
  }

  private fun createColorIconProvider(): ColorIconProvider {
    val configuration = ConfigurationManager.getOrCreateInstance(rule.module).getConfiguration(rule.project.baseDir!!)
    return createColorIconProvider(configuration.resourceResolver)
  }

  private fun createColorIconProvider(resourceResolver: ResourceResolver): ColorIconProvider {
    return ColorIconProvider(rule.project, resourceResolver)
  }
}

private fun Icon.createBufferedImage(): BufferedImage {
  return ImageUtil.createImage(this.iconWidth, this.iconHeight, BufferedImage.TYPE_INT_ARGB).apply {
    val g = createGraphics()
    this@createBufferedImage.paintIcon(null, g, 0, 0)
    g.dispose()
  }
}