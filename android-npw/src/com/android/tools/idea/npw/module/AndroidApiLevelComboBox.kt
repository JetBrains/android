/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.repository.targets.AddonTarget
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.JComboBox

/**
 * A labeled combo box of available SDK Android API Levels for a given FormFactor.
 */
class AndroidApiLevelComboBox : JComboBox<VersionItem?>() {
  // Keep a reference to the lambda to avoid creating a new object each time we reference it.
  private val itemListener = ItemListener { e: ItemEvent -> saveSelectedApi(e) }
  private lateinit var formFactor: FormFactor

  fun init(ff: FormFactor, items: List<VersionItem>) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    formFactor = ff
    name = "minSdkComboBox" // Name used for testing

    val selectedItem = selectedItem
    removeItemListener(itemListener)
    removeAllItems()
    // Filter addons because we do not support older compile targets in wizards. See bug 148015659.
    for (item in items.filterNot { it.androidTarget is AddonTarget }) {
      addItem(item)
    }

    // Try to keep the old selection. If not possible (or no previous selection), use the last saved selection.
    setSelectedItem(selectedItem)
    getSelectedItem() ?: loadSavedApi()
    addItemListener(itemListener)
  }

  /**
   * Load the saved value for this ComboBox.
   */
  private fun loadSavedApi() {
    // Check for a saved value for the min api level
    val savedApiLevel = PropertiesComponent.getInstance().getValue(
      getPropertiesComponentMinSdkKey(formFactor), formFactor.defaultApi.toString()
    )

    selectedIndex = (0 until itemCount).firstOrNull {
      getItemAt(it)!!.minApiLevelStr == savedApiLevel
    } ?: (itemCount - 1) // If the savedApiLevel is not available, just pick the last target in the list (-1 if the list is empty)
  }

  private fun saveSelectedApi(e: ItemEvent) {
    if (e.stateChange == ItemEvent.SELECTED && e.item != null) {
      val item = e.item as VersionItem
      PropertiesComponent.getInstance().setValue(getPropertiesComponentMinSdkKey(formFactor), item.minApiLevelStr)
    }
  }
}

fun getPropertiesComponentMinSdkKey(formFactor: FormFactor): String = formFactor.id + "minApi"

/**
 * Ensures that the saved/persistent API level is at least the recommended version.
 * This is done in a separate method here such that it can be called only from the
 * new project wizard, not for every scenario the API level combo box is used (such as
 * new module wizards.)
 */
fun ensureDefaultApiLevelAtLeastRecommended() {
  val key = getPropertiesComponentMinSdkKey(FormFactor.MOBILE)
  val recommended = SdkVersionInfo.RECOMMENDED_MIN_SDK_VERSION
  val savedApiLevel = PropertiesComponent.getInstance().getValue(key, recommended.toString()).toIntOrNull() ?: return
  if (savedApiLevel < recommended) {
    PropertiesComponent.getInstance().setValue(key, recommended.toString())
  }
}