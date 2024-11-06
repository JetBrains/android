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
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.application
import java.nio.file.Paths

@Suppress("UnstableApiUsage")
internal class AndroidSdkPathStoreTest : LightPlatformTestCase() {

  override fun tearDown() {
    try {
      PropertiesComponent.getInstance().unsetValue("android.sdk.path")
      PropertiesComponent.getInstance().unsetValue("migrate.android.sdk.path.to.roamable.storage")
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testAndroidSDKPathMigration() {
    // Set Android SDK path in old non-roamable storage
    PropertiesComponent.getInstance().setValue("android.sdk.path", ANDROID_SDK_PATH.toString())

    service<IComponentStore>().createAndroidSdkPathStore { androidSdkPathStore ->
      assertNull(PropertiesComponent.getInstance().getValue("android.sdk.path"))
      assertEquals(ANDROID_SDK_PATH, androidSdkPathStore.androidSdkPath)
    }
  }

  fun testSaveAndroidSdkPathWhenStoreIsEmptyAndMigrationIsNotRequired() {
    setMigrationNotRequired()

    service<IComponentStore>().createAndroidSdkPathStore { androidSdkPathStore ->
      assertNull(androidSdkPathStore.androidSdkPath)

      androidSdkPathStore.androidSdkPath = ANDROID_SDK_PATH
      assertEquals(ANDROID_SDK_PATH, androidSdkPathStore.androidSdkPath)

      androidSdkPathStore.androidSdkPath = null
      assertNull(androidSdkPathStore.androidSdkPath)
    }
  }

  fun testSaveAndroidSdkPathWhenStoreIsNotEmptyAndMigrationIsNotRequired() {
    setMigrationNotRequired()

    service<IComponentStore>().createAndroidSdkPathStore(initialAndroidSdkPathState = ANDROID_SDK_PATH.toString()) { androidSdkPathStore ->
      assertEquals(ANDROID_SDK_PATH, androidSdkPathStore.androidSdkPath)

      androidSdkPathStore.androidSdkPath = null
      assertNull(androidSdkPathStore.androidSdkPath)

      val newSdkPath = Paths.get("/home/new-user/Android/Sdk").toAbsolutePath()
      androidSdkPathStore.androidSdkPath = newSdkPath
      assertEquals(newSdkPath, androidSdkPathStore.androidSdkPath)
    }
  }

  private fun setMigrationNotRequired() {
    PropertiesComponent.getInstance().setValue("migrate.android.sdk.path.to.roamable.storage", false, true)
  }

  private fun IComponentStore.createAndroidSdkPathStore(
    initialAndroidSdkPathState: String? = null,
    action: (AndroidSdkPathStore) -> Unit
  ) {
    val androidSdkPathStore = AndroidSdkPathStore()

    if (application.getServiceIfCreated(AndroidSdkPathStore::class.java) != null) {
      unloadComponent(androidSdkPathStore)
    }

    try {
      initComponent(androidSdkPathStore, null, PluginId.findId("org.jetbrains.android")!!)

      if (initialAndroidSdkPathState != null) {
        // Save initial state and re-init component to trigger loadState method
        try {
          androidSdkPathStore.androidSdkPath = Paths.get(initialAndroidSdkPathState)
          saveComponent(androidSdkPathStore)
        }
        finally {
          unloadComponent(androidSdkPathStore)
        }
        initComponent(androidSdkPathStore, null, PluginId.findId("org.jetbrains.android")!!)
      }

      action(androidSdkPathStore)
    }
    finally {
      // clear state
      unloadComponent(androidSdkPathStore)
    }
  }

  companion object {
    private val ANDROID_SDK_PATH = Paths.get("/home/user/Android/sdk").toAbsolutePath()
  }
}
