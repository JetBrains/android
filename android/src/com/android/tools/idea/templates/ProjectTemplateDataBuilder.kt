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
package com.android.tools.idea.templates

import com.android.ide.common.repository.GradleVersion
import com.android.repository.Revision
import com.android.tools.idea.gradle.npw.project.GradleBuildSettings.getRecommendedBuildToolsRevision
import com.android.tools.idea.gradle.npw.project.GradleBuildSettings.needsExplicitBuildToolsVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.npw.module.ConfigureAndroidModuleStep
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.template.KotlinVersionProvider
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.util.lang.JavaVersion
import org.jetbrains.android.refactoring.isAndroidx
import java.io.File

val log: Logger get() = logger<ProjectTemplateDataBuilder>()

/**
 * Builder for [ProjectTemplateData].
 *
 * Extracts information from various data sources.
 */
class ProjectTemplateDataBuilder(val isNewProject: Boolean) {
  var androidXSupport: Boolean? = null
  var addJetifierSupport = false
  var gradlePluginVersion: GradleVersion? = null
  var javaVersion: JavaVersion? = null
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
    kotlinVersion = KotlinVersionProvider.getInstance().kotlinVersionForGradle
    gradlePluginVersion = determineGradlePluginVersion(project)
    javaVersion = determineJavaVersion(project)
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
    val progress = StudioLoggerProgressIndicator(ConfigureAndroidModuleStep::class.java)

    addBuildToolVersion(project, getRecommendedBuildToolsRevision(sdkHandler, progress))

    val sdkPath = sdkHandler.location?.path
    if (sdkPath != null) {
      sdkDir = File(sdkPath)
    }
  }

  /**
   * Same as [setFacet], but uses a [AndroidVersionsInfo.VersionItem]. This version is used when the Module is not created yet.
   *
   * @param buildVersion Build version information for the new Module being created.
   * @param project      Used to find the Gradle Dependencies versions. If null, it will use the most recent values known.
   */
  fun setBuildVersion(buildVersion: AndroidVersionsInfo.VersionItem, project: Project) {
    val info = buildVersion.androidTarget?.buildToolInfo // for preview release
    if (info != null) {
      addBuildToolVersion(project, info.revision)
    }

    setEssentials(project)
  }

  // We can't JUST look at the overall project level language level, since  Gradle sync appears not
  // to sync the overall project level; instead we  have to take the min of all the modules
  private fun determineJavaVersion(project: Project) = runReadAction {
    ModuleManager.getInstance(project).modules
      .mapNotNull { LanguageLevelUtil.getCustomLanguageLevel(it) }
      .min() ?: LanguageLevelProjectExtension.getInstance(project).languageLevel
  }.toJavaVersion()

  private fun addBuildToolVersion(project: Project, buildToolRevision: Revision) {
    val gradlePluginVersion = determineGradlePluginVersion(project)
    buildToolsVersion = buildToolRevision
    explicitBuildToolsVersion = needsExplicitBuildToolsVersion(gradlePluginVersion, buildToolRevision)
  }

  /** Find the most appropriated Gradle Plugin version for the specified project. */
  private fun determineGradlePluginVersion(project: Project?): GradleVersion {
    val defaultGradleVersion = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
    if (project == null || isNewProject) {
      return defaultGradleVersion
    }

    val versionInUse = GradleUtil.getAndroidGradleModelVersionInUse(project)
    val androidPluginInfo = AndroidPluginInfo.findFromBuildFiles(project)
    return versionInUse ?: androidPluginInfo?.pluginVersion ?: defaultGradleVersion
  }

  fun build() = ProjectTemplateData(
    androidXSupport!!,
    gradlePluginVersion!!.toString(),
    javaVersion!!.toString(),
    sdkDir,
    Language.valueOf(language!!.toString()),
    kotlinVersion!!,
    buildToolsVersion!!.toString(),
    topOut!!,
    applicationPackage,
    includedFormFactorNames,
    debugKeyStoreSha1,
    overridePathCheck,
    isNewProject
  )
}

