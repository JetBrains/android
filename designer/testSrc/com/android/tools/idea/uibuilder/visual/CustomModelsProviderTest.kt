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

import com.android.resources.ScreenOrientation
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.rendering.Locale
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.type.LayoutFileType
import org.mockito.Mockito

class CustomModelsProviderTest : LayoutTestCase() {

  override fun setUp() {
    DesignerTypeRegistrar.register(LayoutFileType)
    super.setUp()
  }

  override fun tearDown() {
    super.tearDown()
    DesignerTypeRegistrar.clearRegisteredTypes()
  }

  fun testCreateActions() {
    val file = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT)

    val listener = Mockito.mock(ConfigurationSetListener::class.java)

    val modelsProvider = CustomModelsProvider(listener)
    val actions = modelsProvider.createActions(file, myFacet).getChildren(null)

    assertSize(2, actions)
    assertTrue(actions[0] is AddCustomConfigurationAction)
    assertTrue(actions[1] is RemoveCustomConfigurationAction)
  }

  fun testOnlyCreateDefaultModel() {
    val file = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT)

    val listener = Mockito.mock(ConfigurationSetListener::class.java)

    val modelsProvider = CustomModelsProvider(listener)
    val nlModels = modelsProvider.createNlModels(testRootDisposable, file, myFacet)
    val config = ConfigurationManager.getOrCreateInstance(myFacet).getConfiguration(file.virtualFile)

    assertSize(1, nlModels)
    assertEquals(config, nlModels[0].configuration)
  }

  fun testAddAndRemoveCustomConfig() {
    val file = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT)

    val listener = Mockito.mock(ConfigurationSetListener::class.java)

    val modelsProvider = CustomModelsProvider(listener)
    val configurationManager = ConfigurationManager.getOrCreateInstance(myFacet)
    val defaultConfig = configurationManager.getConfiguration(file.virtualFile)

    // Create additional config
    val newConfig = Configuration.create(defaultConfig, file.virtualFile)
    val device = configurationManager.getDeviceById("pixel_3")!!
    val state = device.defaultState.deepCopy().apply { orientation = ScreenOrientation.PORTRAIT }
    newConfig.setEffectiveDevice(device, state)
    newConfig.locale = Locale.ANY
    newConfig.setTheme(configurationManager.computePreferredTheme(defaultConfig))
    modelsProvider.addConfiguration(CustomConfiguration("", newConfig))

    assertSize(1, modelsProvider.customConfigurations)
    // The created nlModels contains default one plus all custom configurations,
    // so its size is CustomModelsProvider.customConfigurations.size() + 1.
    val nlModelsAfterAdded = modelsProvider.createNlModels(testRootDisposable, file, myFacet)
    assertSize(2, nlModelsAfterAdded)
    assertEquals(defaultConfig, nlModelsAfterAdded[0].configuration)
    assertEquals(newConfig, nlModelsAfterAdded[1].configuration)

    modelsProvider.removeConfiguration(CustomConfiguration("", newConfig))
    assertSize(0, modelsProvider.customConfigurations)
    val nlModelsAfterRemoved = modelsProvider.createNlModels(testRootDisposable, file, myFacet)
    assertSize(1, nlModelsAfterRemoved)
    assertEquals(defaultConfig, nlModelsAfterRemoved[0].configuration)
  }
}

private const val LAYOUT_FILE_CONTENT = """
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent"
  android:orientation="vertical">
</LinearLayout>
"""
