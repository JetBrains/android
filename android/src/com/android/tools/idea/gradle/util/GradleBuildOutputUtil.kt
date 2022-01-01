/*
 * Copyright (C) 2020 The Android Open Source Project
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
@file:JvmName("GradleBuildOutputUtil")

package com.android.tools.idea.gradle.util

import com.android.ddmlib.IDevice
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.intellij.openapi.module.Module
import java.io.File

/**
 * Retrieve the location of generated APK or APK from Bundle for the given run configuration.
 *
 * If the generated file is a bundle file, this method returns the location of the single APK extracted from the bundle.
 * If the generated file is a single APK, this method returns the location of the apk.
 * If the generated files are multiple APKs, this method returns the folder that contains the APKs.
 */
@Deprecated("This method supports the case of one app and one test APks only. Use ApkProviders instead.")
fun getSingleApkOrParentFolderForRunConfiguration(
  module: Module,
  configuration: AndroidRunConfigurationBase,
  isTest: Boolean,
  device: IDevice
): File? {
  val projectSystem = module.project.getProjectSystem()
  val apkProvider = projectSystem.getApkProvider(configuration) ?: return null
  val applicationIdProvider = projectSystem.getApplicationIdProvider(configuration) ?: return null
  val applicationId = if (isTest) applicationIdProvider.testPackageName else applicationIdProvider.packageName
  val apks =
    apkProvider.getApks(device).asSequence()
      .filter { info -> info.applicationId == applicationId }
      .flatMap { it.files.asSequence() }
      .map { it.apkFile }
      .distinct()
      .toList()
  return if (apks.size == 1) apks[0]
  else apks.map { it.parentFile }.singleOrNull()
}
