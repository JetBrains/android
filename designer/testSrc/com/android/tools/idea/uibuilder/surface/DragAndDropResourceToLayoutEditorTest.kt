/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.idea.common.LayoutTestUtilities
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.createTransferable
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.util.androidFacet
import org.junit.Test
import java.awt.dnd.DnDConstants
import kotlin.test.assertContains

/**
 * This test mimics the resource drag and drop scenario. b/255741287
 */
class DragAndDropResourceToLayoutEditorTest : LayoutTestCase() {

  @Test
  fun testLayoutResource() {
    myFixture.copyFileToProject("layout/layout.xml", "res/layout/layout_to_include.xml")
    val model = model("model.xml",
                      component(SdkConstants.LINEAR_LAYOUT)
                        .id("@+id/outer")
                        .withBounds(0, 0, 100, 100)
                        .children(
                          component(SdkConstants.TEXT_VIEW).withBounds(10, 0, 10, 10)
                        )
    ).build()
    val surface = LayoutTestUtilities.createScreen(model).surface
    surface.scene!!.buildDisplayList(DisplayList(), 0)
    surface.model = model

    val layoutResource = StudioResourceRepositoryManager
        .getModuleResources(myFixture.module.androidFacet!!)
        .getResources(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT)["layout_to_include"][0]
    val asset = Asset.fromResourceItem(layoutResource) as DesignAsset

    val transferable = createTransferable(asset)

    model.file.document!!.text.let { layoutXmlContent ->
      assertFalse(layoutXmlContent.contains("<${SdkConstants.VIEW_INCLUDE}"))
      assertFalse(layoutXmlContent.contains("layout/layout_to_include"))
    }

    val manager = surface.guiInputHandler
    manager.startListening()
    LayoutTestUtilities.dragDrop(manager, 0, 0, 5, 5, transferable, DnDConstants.ACTION_COPY)

    model.file.document!!.text.let { layoutXmlContent ->
      assertContains(layoutXmlContent, "<${SdkConstants.VIEW_INCLUDE}")
      assertContains(layoutXmlContent, "layout/layout_to_include")
    }
  }

  @Test
  fun testDrawableResource() {
    myFixture.addFileToProject(
      "res/drawable/color_drawable.xml",
      //language=xml
      """
        <?xml version="1.0" encoding="utf-8"?>
        <color xmlns:android="http://schemas.android.com/apk/res/android" android:color="#ff0000" />
      """.trimIndent())
    val model = model("model.xml",
                      component(SdkConstants.LINEAR_LAYOUT)
                        .id("@+id/outer")
                        .withBounds(0, 0, 100, 100)
                        .children(
                          component(SdkConstants.TEXT_VIEW).withBounds(10, 0, 10, 10)
                        )
    ).build()
    val surface = LayoutTestUtilities.createScreen(model).surface
    surface.scene!!.buildDisplayList(DisplayList(), 0)
    surface.model = model

    val layoutResource = StudioResourceRepositoryManager
        .getModuleResources(myFixture.module.androidFacet!!)
        .getResources(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE)["color_drawable"][0]
    val asset = Asset.fromResourceItem(layoutResource) as DesignAsset

    val transferable = createTransferable(asset)

    model.file.document!!.text.let { layoutXmlContent ->
      assertFalse(layoutXmlContent.contains("<${SdkConstants.IMAGE_VIEW}"))
      assertFalse(layoutXmlContent.contains("drawable/color_drawable"))
    }

    val manager = surface.guiInputHandler
    manager.startListening()
    LayoutTestUtilities.dragDrop(manager, 0, 0, 5, 5, transferable, DnDConstants.ACTION_COPY)

    model.file.document!!.text.let { layoutXmlContent ->
      assertContains(layoutXmlContent, "<${SdkConstants.IMAGE_VIEW}")
      assertContains(layoutXmlContent, "drawable/color_drawable")
    }
  }
}