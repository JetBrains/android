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
@file:JvmName("SdkSyncUtil")
package com.android.tools.idea.gradle.project.sync.idea

import com.android.SdkConstants.FN_FRAMEWORK_LIBRARY
import com.android.repository.api.RepoManager
import com.android.tools.idea.gradle.project.sync.SdkSync
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver.RESOLVER_LOG
import com.android.tools.idea.gradle.project.sync.idea.issues.SdkPlatformNotFoundException
import com.android.tools.idea.gradle.util.LocalProperties
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType.CLASSES
import com.intellij.openapi.util.io.FileUtil.filesEqual
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import org.jetbrains.annotations.SystemDependent
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import java.io.File

/**
 * Sync the SDK known by Android Studio with the SDKs listed in local.properties.
 *
 * This method ensures that the SDK location is set in local.properties so that the
 * Android Gradle plugin can find it. It may create or adjust this file as needed.
 */
fun SdkSync.syncAndroidSdks(projectPath: @SystemDependent String) {
  val projectDir = File(projectPath)
  if (!projectDir.isDirectory) {
    throw IllegalStateException("The project directory does not exist!")
  }

  val localProperties = LocalProperties(projectDir)
  syncIdeAndProjectAndroidSdks(localProperties)
}

/**
 * Attempts to find a matching SDK that has been setup in the IDE matching the compile target that
 * was obtained from Gradle via the [IdeAndroidProject].
 *
 * First we check to see if an Android Sdk that fits that compile target has already been registered,
 * if it has we use that one.
 *
 * Secondly we check to see if we can find a new Android SDK by refreshing them via the [RepoManager].
 * This can be the case when the Android Gradle plugin downloads the SDK during sync.
 *
 * Thirdly we check to see if the SDK is an add-on.
 */
fun AndroidSdks.computeSdkReloadingAsNeeded(
  moduleName: String,
  compileTarget: String,
  bootClasspath: Collection<String>,
  ideSdks: IdeSdks
) : Sdk? {
  // 1 - Find the SDK if it already exists.
  val sdk = findSuitableAndroidSdk(compileTarget)
  if (sdk != null) {
    logSdkFound(sdk, moduleName)
    return sdk
  }

  // 2 - We may have had an Sdk downloaded by AGP and it has not yet been registered by studio. Here we attempt to
  // find any unregistered sdks.
  val progress = StudioLoggerProgressIndicator(AndroidGradleProjectResolver::class.java)
  tryToChooseSdkHandler().getSdkManager(progress).reloadLocalIfNeeded(progress)

  val androidSdkHomePath = ideSdks.androidSdkPath
  if (androidSdkHomePath == null) {
    logAndroidSdkHomeNotFound()
    return null
  }

  var newSdk : Sdk? = null
  invokeAndWaitIfNeeded {
    newSdk = runWriteAction {
      tryToCreate(androidSdkHomePath, compileTarget)
    }
  }

  if (newSdk != null) {
    logSdkFound(newSdk as Sdk, moduleName)
    return (newSdk as Sdk)
  }

  // 3 - We might have an SDK add-on being used attempt to find the SDK for an addon.
  val addonSdk = findMatchingSdkForAddon(bootClasspath)

  if (addonSdk == null) {
    val message = "Module: '${moduleName}' platform '${compileTarget}' not found."
    RESOLVER_LOG.warn(message)

    throw SdkPlatformNotFoundException(message)
  }

  return addonSdk
}

private fun AndroidSdks.findMatchingSdkForAddon(
  bootClasspath: Collection<String>
) : Sdk? {
  // There will always be an android.jar, any add-ons will be in addition to that.
  if (bootClasspath.size <= 1) return null

  // Find the path to the android.jar, so we can match this to the sdks and find which one is in use.
  val androidJarPath = bootClasspath.map { path ->
    File(path)
  }.firstOrNull { file ->
    file.name == FN_FRAMEWORK_LIBRARY
  } ?: return null

  // There is no android.jar file in the bootClasspath, we can't find the SDK
  // TODO: Maybe log here, this condition should never happen AFAIK.

  return allAndroidSdks.first { sdk ->
    sdk.rootProvider.getFiles(CLASSES).any { sdkFile ->
      filesEqual(virtualToIoFile(sdkFile), androidJarPath)
    }
  }
}

private fun logAndroidSdkHomeNotFound() {
  RESOLVER_LOG.warn("Path to Android SDK not set")
  val sdks = IdeSdks.getInstance().eligibleAndroidSdks
  RESOLVER_LOG.warn("# of eligible SDKs: ${sdks.size}")
  sdks.forEach { sdk ->
    RESOLVER_LOG.info("sdk: $sdk")
  }
}

private fun logSdkFound(sdk: Sdk, moduleName: String) {
  val sdkPath = sdk.homePath ?: "<path not set>"
  RESOLVER_LOG.info("Set Android SDK '${sdk.name}' ($sdkPath) to module $moduleName")
}