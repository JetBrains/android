// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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