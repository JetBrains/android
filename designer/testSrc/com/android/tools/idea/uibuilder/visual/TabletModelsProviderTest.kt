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
package com.android.tools.idea.uibuilder.visual

import com.android.resources.ScreenOrientation
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.android.tools.idea.uibuilder.type.ZoomableDrawableFileType
import com.intellij.openapi.util.Disposer
import org.intellij.lang.annotations.Language

class TabletModelsProviderTest : LayoutTestCase() {

  override fun setUp() {
    DesignerTypeRegistrar.register(LayoutFileType)
    DesignerTypeRegistrar.register(ZoomableDrawableFileType)
    super.setUp()
  }

  override fun tearDown() {
    super.tearDown()
    DesignerTypeRegistrar.clearRegisteredTypes()
  }

  fun testCreatePixelModels() {
    val file = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT);

    val modelsProvider = TabletModelsProvider
    val nlModels = modelsProvider.createNlModels(testRootDisposable, file, myFacet)

    assertNotEmpty(nlModels)
    val deviceOrientations = mutableMapOf<String, MutableList<ScreenOrientation>>()
    for (nlModel in nlModels) {
      val deviceName = nlModel.configuration.device!!.displayName
      val orientation = nlModel.configuration.deviceState!!.orientation
      assertTrue(TABLETS_TO_DISPLAY.contains(deviceName))
      deviceOrientations.computeIfAbsent(deviceName) { mutableListOf() }.add(orientation)
    }
    for (device in TABLETS_TO_DISPLAY) {
      val orientations = deviceOrientations[device]
      assertNotNull(orientations)
      assertSameElements(orientations!!, listOf(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE))
    }
  }

  fun testNotCreatePixelModelsForNonLayoutFile() {
    val file = myFixture.addFileToProject("/res/drawable/test.xml", DRAWABLE_FILE_CONTENT)

    val modelsProvider = TabletModelsProvider
    val nlModels = modelsProvider.createNlModels(testRootDisposable, file, myFacet)
    assertEmpty(nlModels)
  }

  fun testDisposedConfigurationManagerShouldCleanTheCached() {
    val file = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT);
    val modelsProvider = TabletModelsProvider
    val manager = ConfigurationManager.getOrCreateInstance(myFacet)
    modelsProvider.createNlModels(testRootDisposable, file, myFacet)
    assertTrue(modelsProvider.deviceCaches.containsKey(manager))
    Disposer.dispose(manager)
    assertFalse(modelsProvider.deviceCaches.containsKey(manager))
  }
}

@Language("Xml")
private const val LAYOUT_FILE_CONTENT = """
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent"
  android:orientation="vertical">
</LinearLayout>
"""

@Language("Xml")
private const val DRAWABLE_FILE_CONTENT = """
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
  android:shape="line">
</shape>
"""