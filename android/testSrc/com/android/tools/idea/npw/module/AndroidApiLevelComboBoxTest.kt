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
package com.android.tools.idea.npw.module

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem
import com.google.common.collect.Lists
import com.intellij.ide.util.ProjectPropertiesComponentImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class AndroidApiLevelComboBoxTest {
  private lateinit var disposable: Disposable

  @Before
  fun setUp() {
    disposable = Disposable { }
    val instance = MockApplication(disposable)
    instance.registerService(PropertiesComponent::class.java, ProjectPropertiesComponentImpl::class.java)
    ApplicationManager.setApplication(instance, disposable)
  }

  @After
  fun tearDown() {
    Disposer.dispose(disposable)
  }

  @Test
  fun testDefaultSelectedItem() {
    val formFactor = FormFactor.MOBILE
    assertEquals("none", PropertiesComponent.getInstance().getValue(getPropertiesComponentMinSdkKey(formFactor), "none"))
    val items: MutableList<VersionItem> = Lists.newArrayList(
      createMockVersionItem(formFactor.defaultApi - 1),
      createMockVersionItem(formFactor.defaultApi),
      // Default is at position 1
      createMockVersionItem(formFactor.defaultApi + 1),
      createMockVersionItem(formFactor.defaultApi + 2)
    )
    val apiComboBox = AndroidApiLevelComboBox()
    apiComboBox.init(formFactor, items)
    assertEquals(1, apiComboBox.selectedIndex)

    // Make sure the default does not change if the list is reloaded
    apiComboBox.init(formFactor, items)
    assertEquals(1, apiComboBox.selectedIndex)
    apiComboBox.init(formFactor, Lists.reverse(items))
    assertEquals(2, apiComboBox.selectedIndex)
    items.removeAt(1)
    apiComboBox.init(formFactor, items)
    assertEquals(0, apiComboBox.selectedIndex)
    apiComboBox.selectedIndex = 2
    val savedApi = PropertiesComponent.getInstance().getValue(getPropertiesComponentMinSdkKey(formFactor), "none")
    assertEquals(items[2].minApiLevelStr, savedApi)
  }
}

private fun createMockVersionItem(minApiLevel: Int): VersionItem =
  Mockito.mock(AndroidVersionsInfo::class.java).apply {
    Mockito.`when`(highestInstalledVersion).thenReturn(AndroidVersion(100, null))
  }.VersionItem(AndroidVersion(minApiLevel))
