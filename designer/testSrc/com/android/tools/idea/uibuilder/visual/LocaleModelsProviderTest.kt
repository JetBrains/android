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

import com.android.ide.common.resources.Locale
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.android.tools.idea.uibuilder.type.ZoomableDrawableFileType
import org.intellij.lang.annotations.Language

class LocaleModelsProviderTest : LayoutTestCase() {

  override fun setUp() {
    DesignerTypeRegistrar.register(LayoutFileType)
    DesignerTypeRegistrar.register(ZoomableDrawableFileType)
    super.setUp()
  }

  fun testCreateDifferentModelsForDifferentLocales() {
    val defaultFile = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT);
    val enFile = myFixture.addFileToProject("/res/layout-en/test.xml", LAYOUT_FILE_CONTENT);

    val modelsProvider = LocaleModelsProvider
    val nlModels = modelsProvider.createNlModels(testRootDisposable, defaultFile, myFacet)

    // There is 1 locale "en" in this project.
    // LocaleModelsProvider should provide 2 NlModels, one for no locale and one for en locale.
    // NlModel of default locale is for res/layout/test.xml, and NlModel of en locale is for res/layout-en/test.xml.

    assertEquals(defaultFile.virtualFile, nlModels[0].virtualFile)
    assertEquals(Locale.ANY, nlModels[0].configuration.locale)

    assertEquals(enFile.virtualFile, nlModels[1].virtualFile)
    assertEquals(Locale.create("en"), nlModels[1].configuration.locale)
  }

  fun testUsingDefaultWhenThereIsNoSpecificLayout() {
    val defaultFile = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT);
    myFixture.addFileToProject("/res/values-en/value.xml", VALUE_FILE_CONTENT)

    // There is 1 locale "en" in this project.
    // LocaleModelsProvider should provide 2 NlModels, one for no locale and one for en locale.

    val modelsProvider = LocaleModelsProvider
    val nlModels = modelsProvider.createNlModels(testRootDisposable, defaultFile, myFacet)

    assertEquals(defaultFile.virtualFile, nlModels[0].virtualFile)
    assertEquals(Locale.ANY, nlModels[0].configuration.locale)

    // Because there is no res/layout-en/test.xml, the NlModel of en locale should use /res/layout/test.xml as its file.
    assertEquals(defaultFile.virtualFile, nlModels[1].virtualFile)
    assertEquals(Locale.create("en"), nlModels[1].configuration.locale)
  }

  fun testOpenLocaleFile() {
    // When opening locale file, the returned NlModel list should be same as opening default (no locale) file.

    val defaultFile = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT);
    val enFile = myFixture.addFileToProject("/res/layout-en/test.xml", LAYOUT_FILE_CONTENT);

    val modelsProvider = LocaleModelsProvider
    val nlModels = modelsProvider.createNlModels(testRootDisposable, enFile, myFacet)

    assertEquals(defaultFile.virtualFile, nlModels[0].virtualFile)
    assertEquals(Locale.ANY, nlModels[0].configuration.locale)

    assertEquals(enFile.virtualFile, nlModels[1].virtualFile)
    assertEquals(Locale.create("en"), nlModels[1].configuration.locale)
  }

  fun testReflectConfigurationFromSource() {
    val file = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT)
    myFixture.addFileToProject("/res/layout-fr/test.xml", LAYOUT_FILE_CONTENT)
    myFixture.addFileToProject("/res/layout-jp/test.xml", LAYOUT_FILE_CONTENT)

    val manager = ConfigurationManager.getOrCreateInstance(myModule)
    val sourceConfig = manager.getConfiguration(file.virtualFile)

    val modelsProvider = LocaleModelsProvider
    val nlModels = modelsProvider.createNlModels(testRootDisposable, file, myFacet)

    verifyAdaptiveShapeReflected(sourceConfig, nlModels, true)
    verifyDeviceReflected(sourceConfig, nlModels, true)
    verifyDeviceStateReflected(sourceConfig, nlModels, true)
    verifyUiModeReflected(sourceConfig, nlModels, true)
    verifyNightModeReflected(sourceConfig, nlModels, true)
    verifyThemeReflected(sourceConfig, nlModels, true)
    verifyTargetReflected(sourceConfig, nlModels, true)
    verifyLocaleReflected(sourceConfig, nlModels, false)
    verifyFontReflected(sourceConfig, nlModels, true)
  }
}

@Language("XML")
private const val LAYOUT_FILE_CONTENT = """
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent"
  android:orientation="vertical">
</LinearLayout>
"""

@Language("XML")
private const val VALUE_FILE_CONTENT = """
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="my_value">Value</string>
</resources>
"""
