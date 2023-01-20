// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.sdk

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.application

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
    PropertiesComponent.getInstance().setValue("android.sdk.path", ANDROID_SDK_PATH)

    service<IComponentStore>().createAndroidSdkPathStore { androidSdkPathStore ->
      assertNull(PropertiesComponent.getInstance().getValue("android.sdk.path"))
      assertEquals(ANDROID_SDK_PATH, androidSdkPathStore.androidSdkPath)
    }
  }

  fun testSaveAndroidSDKPathWhenStoreIsEmptyAndMigrationIsNotRequired() {
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

    service<IComponentStore>().createAndroidSdkPathStore(initialAndroidSdkPathState = ANDROID_SDK_PATH) { androidSdkPathStore ->
      assertEquals(ANDROID_SDK_PATH, androidSdkPathStore.androidSdkPath)

      androidSdkPathStore.androidSdkPath = null
      assertNull(androidSdkPathStore.androidSdkPath)

      androidSdkPathStore.androidSdkPath = "/home/new-user/Android/Sdk"
      assertEquals("/home/new-user/Android/Sdk", androidSdkPathStore.androidSdkPath)
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
      initComponent(androidSdkPathStore, null, null)

      if (initialAndroidSdkPathState != null) {
        // Save initial state and re-init component to trigger loadState method
        try {
          androidSdkPathStore.androidSdkPath = initialAndroidSdkPathState
          saveComponent(androidSdkPathStore)
        }
        finally {
          unloadComponent(androidSdkPathStore)
        }
        initComponent(androidSdkPathStore, null, null)
      }

      action(androidSdkPathStore)
    }
    finally {
      // clear state
      unloadComponent(androidSdkPathStore)
    }
  }

  companion object {
    private const val ANDROID_SDK_PATH = "home/user/Android/sdk"
  }
}