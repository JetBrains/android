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
package com.android.tools.property.testing

import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.registerServiceInstance
import org.mockito.Mockito.mock

class PropertyAppRule : ApplicationRule() {

  /**
   * Setup a test Application instance with a few common services needed for property tests.
   */
  override fun before() {
    super.before()
    testApplication.registerServiceInstance(PropertiesComponent::class.java, PropertiesComponentMock())
    testApplication.registerServiceInstance(ActionManager::class.java, mock(ActionManager::class.java))
    testApplication.registerServiceInstance(DataManager::class.java, mock(DataManager::class.java))
  }
}
