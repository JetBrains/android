/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.npw.template

import com.android.annotations.concurrency.Slow
import com.android.ide.common.repository.AgpVersion
import com.android.repository.Revision
import com.android.tools.idea.npw.project.determineAgpVersion
import com.android.tools.idea.npw.project.determineKotlinVersion
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.jetbrains.android.refactoring.isAndroidx
import java.io.File

private val log: Logger get() = logger<ProjectTemplateDataBuilder>()

/**
 * Builder for [ProjectTemplateData].
 *
 * Extracts information from various data sources.
 */

class ProjectTemplateDataBuilder(val isNewProject: Boolean) {
  var androidXSupport: Boolean? = null
  var agpVersion: AgpVersion? = null
  var sdkDir: File? = null
  var language: Language? = null
  var kotlinVersion: String? = null
  var buildToolsVersion: Revision? = null
  var explicitBuildToolsVersion: Boolean? = null
  var topOut: File? = null
  var applicationPackage: PackageName? = null
  val includedFormFactorNames = mutableMapOf<FormFactor, MutableList<String>>()
  var debugKeyStoreSha1: String? = null
  var overridePathCheck: Boolean? = null
  var applicationName: String? = null

  internal fun setEssentials(project: Project) {
    applicationName = project.name
    kotlinVersion = determineKotlinVersion(project)
    agpVersion = determineAgpVersion(project)
    // If we create a new project, then we have a checkbox for androidX support
    if (!isNewProject) {
      androidXSupport = project.isAndroidx()
    }
  }

  /**
   * Sets basic information which is available in [Project].
   */
  fun setProjectDefaults(project: Project) {
    setEssentials(project)

    val basePath = project.basePath
    if (basePath != null) {
      topOut = File(basePath)
    }

    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()

    sdkDir = sdkHandler.location?.toFile()
  }

  /** Find the most appropriate Android Gradle Plugin version for the specified project. */
  @Slow
  private fun determineAgpVersion(project: Project): AgpVersion {
    // Could be expensive to calculate, so return any cached value.
    return agpVersion ?: determineAgpVersion(project, isNewProject)
  }

  @Slow
  private fun determineKotlinVersion(project: Project): String {
    return determineKotlinVersion(project, isNewProject)
  }

  fun build() = ProjectTemplateData(
    androidXSupport!!,
    agpVersion!!.toString(),
    sdkDir,
    Language.valueOf(language!!.toString()),
    kotlinVersion!!,
    topOut!!,
    applicationPackage,
    includedFormFactorNames,
    debugKeyStoreSha1,
    overridePathCheck,
    isNewProject
  )
}

