/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.sdk.isValid
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.jetbrains.annotations.NonNls
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

@Service
@State(name = "AndroidSdkPathStore", storages = [(Storage("android.sdk.path.xml", roamingType = RoamingType.LOCAL))])
class AndroidSdkPathStore : SimplePersistentStateComponent<AndroidSdkPathStore.State>(State()) {

  var androidSdkPath: Path?
    get() = state.androidSdkAbsolutePath?.let { Path.of(it) }
    set(androidSdkPath) {
      state.androidSdkAbsolutePath = androidSdkPath?.absolutePathString()
    }

  val androidSdkPathIfValid: Path?
    get() = androidSdkPath?.takeIf { isValid(it) }

  class State : BaseState() {
    /**
     * Absolute path to Android SDK location.
     */
    var androidSdkAbsolutePath: String? by string(null)
  }

  override fun noStateLoaded() {
    migrateAndroidSdkPathToRoamableStorage()
  }

  // TODO IDEA-310964
  private fun migrateAndroidSdkPathToRoamableStorage() {
    val component = PropertiesComponent.getInstance()
    if (component.getBoolean(MIGRATE_ANDROID_SDK_PATH_TO_ROAMABLE_STORAGE_KEY, true)) {
      component.setValue(MIGRATE_ANDROID_SDK_PATH_TO_ROAMABLE_STORAGE_KEY, false, true)

      androidSdkPath = component.getValue(ANDROID_SDK_PATH_KEY)?.let { Paths.get(it) }

      component.unsetValue(ANDROID_SDK_PATH_KEY)
    }
  }

  companion object {
    private const val ANDROID_SDK_PATH_KEY: @NonNls String = "android.sdk.path"
    private const val MIGRATE_ANDROID_SDK_PATH_TO_ROAMABLE_STORAGE_KEY = "migrate.android.sdk.path.to.roamable.storage"

    @JvmStatic
    fun getInstance(): AndroidSdkPathStore = service()
  }
}
