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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.android.tools.idea.uibuilder.type.ZoomableDrawableFileType
import com.intellij.openapi.util.Disposer
import junit.framework.TestCase
import org.intellij.lang.annotations.Language

class PixelDeviceModelsProviderTest : LayoutTestCase() {

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
    val file = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT)

    val modelsProvider = PixelDeviceModelsProvider
    val nlModels = modelsProvider.createNlModels(testRootDisposable, file, myBuildTarget)

    assertNotEmpty(nlModels)
    for (nlModel in nlModels) {
      assertTrue(DEVICES_TO_DISPLAY.contains(nlModel.configuration.device!!.displayName))
    }
  }

  fun testNotCreatePixelModelsForNonLayoutFile() {
    val file = myFixture.addFileToProject("/res/drawable/test.xml", DRAWABLE_FILE_CONTENT)

    val modelsProvider = PixelDeviceModelsProvider
    val nlModels = modelsProvider.createNlModels(testRootDisposable, file, myBuildTarget)
    assertEmpty(nlModels)
  }

  fun testDisposedConfigurationManagerShouldCleanTheCached() {
    val file = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT)
    val modelsProvider = PixelDeviceModelsProvider
    val manager = ConfigurationManager.getOrCreateInstance(myModule)
    modelsProvider.createNlModels(testRootDisposable, file, myBuildTarget)
    TestCase.assertTrue(modelsProvider.deviceCaches.containsKey(manager))
    Disposer.dispose(manager)
    TestCase.assertFalse(modelsProvider.deviceCaches.containsKey(manager))
  }

  fun testPickCorrectFile() {
    val defaultFile = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT)
    val bigFile = myFixture.addFileToProject("/res/layout-sw600dp/test.xml", LAYOUT_FILE_CONTENT)

    val modelsProvider = PixelDeviceModelsProvider
    val nlModels = modelsProvider.createNlModels(testRootDisposable, defaultFile, myBuildTarget)

    assertNotEmpty(nlModels)
    for (nlModel in nlModels) {
      val device = nlModel.configuration.device!!
      if (device.displayName == "Pixel C") {
        assertEquals(bigFile, nlModel.file)
      } else {
        assertEquals(defaultFile, nlModel.file)
      }
    }
  }

  fun testReflectConfigurationFromSource() {
    val file = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT)
    myFixture.addFileToProject("/res/layout-en/test.xml", LAYOUT_FILE_CONTENT)
    myFixture.addFileToProject("/res/layout-fr/test.xml", LAYOUT_FILE_CONTENT)
    myFixture.addFileToProject("/res/layout-jp/test.xml", LAYOUT_FILE_CONTENT)

    val manager = ConfigurationManager.getOrCreateInstance(myModule)
    val sourceConfig = manager.getConfiguration(file.virtualFile)

    val modelsProvider = PixelDeviceModelsProvider
    val nlModels = modelsProvider.createNlModels(testRootDisposable, file, myBuildTarget)

    verifyAdaptiveShapeReflected(sourceConfig, nlModels, true)
    verifyDeviceReflected(sourceConfig, nlModels, false)
    verifyDeviceStateReflected(sourceConfig, nlModels, true)
    verifyUiModeReflected(sourceConfig, nlModels, true)
    verifyNightModeReflected(sourceConfig, nlModels, true)
    verifyThemeReflected(sourceConfig, nlModels, true)
    verifyTargetReflected(sourceConfig, nlModels, true)
    verifyLocaleReflected(sourceConfig, nlModels, true)
    verifyFontReflected(sourceConfig, nlModels, true)
  }
}

@Language("Xml")
private const val LAYOUT_FILE_CONTENT =
  """
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent"
  android:orientation="vertical">
</LinearLayout>
"""

@Language("Xml")
private const val DRAWABLE_FILE_CONTENT =
  """
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
  android:shape="line">
</shape>
"""
