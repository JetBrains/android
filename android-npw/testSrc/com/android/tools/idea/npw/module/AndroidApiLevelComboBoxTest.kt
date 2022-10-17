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

import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API
import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API
import com.android.sdklib.SdkVersionInfo.RECOMMENDED_MIN_SDK_VERSION
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem
import com.google.common.collect.Lists
import com.intellij.ide.util.PropertiesComponent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidApiLevelComboBoxTest {

  @Test
  fun testDefaultSelectedItem() = runBlocking(AndroidDispatchers.uiThread) {
    val formFactor = FormFactor.MOBILE
    assertEquals("none", PropertiesComponent.getInstance().getValue(getPropertiesComponentMinSdkKey(formFactor), "none"))
    val items: MutableList<VersionItem> = mutableListOf(
      VersionItem.fromStableVersion(formFactor.defaultApi - 1),
      VersionItem.fromStableVersion(formFactor.defaultApi),
      // Default is at position 1
      VersionItem.fromStableVersion(formFactor.defaultApi + 1),
      VersionItem.fromStableVersion(formFactor.defaultApi + 2)
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

    // Makes sure that if you already have a previously saved API level, we force it up to at least the recommended
    // API level
    PropertiesComponent.getInstance().setValue(getPropertiesComponentMinSdkKey(formFactor), LOWEST_ACTIVE_API.toString())
    ensureDefaultApiLevelAtLeastRecommended()
    val apiLevelItems = mutableListOf<VersionItem>()
    for (level in LOWEST_ACTIVE_API..HIGHEST_KNOWN_API) {
      apiLevelItems.add(VersionItem.fromStableVersion(level))
    }
    val comboBox = AndroidApiLevelComboBox()
    comboBox.init(formFactor, apiLevelItems)
    assertEquals(RECOMMENDED_MIN_SDK_VERSION - LOWEST_ACTIVE_API, comboBox.selectedIndex)
  }
}

