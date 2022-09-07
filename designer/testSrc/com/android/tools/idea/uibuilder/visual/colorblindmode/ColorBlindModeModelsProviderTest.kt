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
package com.android.tools.idea.uibuilder.visual.colorblindmode

import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.android.tools.idea.uibuilder.visual.ColorBlindModeModelsProvider
import com.android.tools.idea.uibuilder.visual.verifyAdaptiveShapeReflected
import com.android.tools.idea.uibuilder.visual.verifyDeviceReflected
import com.android.tools.idea.uibuilder.visual.verifyFontReflected
import com.android.tools.idea.uibuilder.visual.verifyLocaleReflected
import com.android.tools.idea.uibuilder.visual.verifyNightModeReflected
import com.android.tools.idea.uibuilder.visual.verifyDeviceStateReflected
import com.android.tools.idea.uibuilder.visual.verifyTargetReflected
import com.android.tools.idea.uibuilder.visual.verifyThemeReflected
import com.android.tools.idea.uibuilder.visual.verifyUiModeReflected
import org.intellij.lang.annotations.Language

class ColorBlindModeModelsProviderTest : LayoutTestCase() {

  override fun setUp() {
    DesignerTypeRegistrar.register(LayoutFileType)
    super.setUp()
  }

  override fun tearDown() {
    super.tearDown()
    DesignerTypeRegistrar.clearRegisteredTypes()
  }

  fun testCreateColorBlindModeModels() {
    val file = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT);

    val modelsProvider = ColorBlindModeModelsProvider
    val nlModels = modelsProvider.createNlModels(testRootDisposable, file, myFacet)

    assertNotEmpty(nlModels)
    val displayNames = ColorBlindMode.values().map { it.displayName }
    nlModels.forEach {
      assertTrue(displayNames.contains(it.modelDisplayName))
    }
  }

  fun testReflectConfigurationFromSource() {
    val file = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT)
    myFixture.addFileToProject("/res/layout-en/test.xml", LAYOUT_FILE_CONTENT)
    myFixture.addFileToProject("/res/layout-fr/test.xml", LAYOUT_FILE_CONTENT)
    myFixture.addFileToProject("/res/layout-jp/test.xml", LAYOUT_FILE_CONTENT)

    val manager = ConfigurationManager.getOrCreateInstance(myFacet)
    val sourceConfig = manager.getConfiguration(file.virtualFile)

    val modelsProvider = ColorBlindModeModelsProvider
    val nlModels = modelsProvider.createNlModels(testRootDisposable, file, myFacet)

    verifyAdaptiveShapeReflected(sourceConfig, nlModels, true)
    verifyDeviceReflected(sourceConfig, nlModels, true)
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
private const val LAYOUT_FILE_CONTENT = """
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent"
  android:orientation="vertical">
</LinearLayout>
"""
