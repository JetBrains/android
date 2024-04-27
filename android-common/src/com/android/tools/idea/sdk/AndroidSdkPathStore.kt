/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.sdk

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.jetbrains.annotations.SystemDependent

@State(name = "AndroidSdkPathStore", storages = [(Storage("android.sdk.path.xml"))])
class AndroidSdkPathStore : SimplePersistentStateComponent<AndroidSdkPathStore.State>(State()) {

  /**
   * Absolute path to Android SDK location.
   */
  var androidSdkPath: @SystemDependent String?
    get() = state.androidSdkPath
    set(path) {
      this.state.androidSdkPath = path
    }

  class State: BaseState() {
    /**
     * Absolute path to Android SDK location.
     */
    var androidSdkPath: String? by string(null)
  }

  override fun noStateLoaded() {
    migrateAndroidSdkPathToRoamableStorage()
  }

  // TODO https://youtrack.jetbrains.com/issue/IDEA-310964
  private fun migrateAndroidSdkPathToRoamableStorage() {
    val component = PropertiesComponent.getInstance()
    if (component.getBoolean(MIGRATE_ANDROID_SDK_PATH_TO_ROAMABLE_STORAGE_KEY, true)) {
      component.setValue(MIGRATE_ANDROID_SDK_PATH_TO_ROAMABLE_STORAGE_KEY, false, true)

      this.androidSdkPath = component.getValue(MIGRATION_ANDROID_SDK_PATH_KEY)

      component.unsetValue(MIGRATION_ANDROID_SDK_PATH_KEY)
    }
  }

  companion object {
    private const val MIGRATION_ANDROID_SDK_PATH_KEY: String = "android.sdk.path"
    private const val MIGRATE_ANDROID_SDK_PATH_TO_ROAMABLE_STORAGE_KEY = "migrate.android.sdk.path.to.roamable.storage"

    @JvmStatic
    fun getInstance(): AndroidSdkPathStore = service()
  }
}