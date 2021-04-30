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
@file:JvmName("SyncIssueUsageReporterUtils")

package com.android.tools.idea.gradle.project.sync.issues

import com.android.builder.model.SyncIssue
import com.android.tools.idea.gradle.project.sync.hyperlink.AddGoogleMavenRepositoryHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.BuildProjectHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.CreateGradleWrapperHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.DeleteFileAndSyncHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.DisableOfflineModeHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.DownloadAndroidStudioHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.DownloadJdk8Hyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.FileBugHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.FixAndroidGradlePluginVersionHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.FixBuildToolsVersionHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.FixGradleVersionInWrapperHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallBuildToolsHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallNdkHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallPlatformHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallSdkPackageHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenAndroidSdkManagerHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenGradleSettingsHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenHttpSettingsHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenPluginBuildFileHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenProjectStructureHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.RemoveSdkFromManifestHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.SearchInBuildFilesHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.SelectJdkFromFileSystemHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.SetSdkDirHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.ShowDependencyInProjectStructureHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.ShowLogHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.ShowSyncIssuesDetailsHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.StopGradleDaemonsHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.ToggleOfflineModeHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.UpdatePluginHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.UpgradeAppenginePluginVersionHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.UseEmbeddedJdkHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.UseJavaHomeAsJdkHyperlink
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncIssue
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

private val LOG = Logger.getInstance(SyncIssueUsageReporter::class.java)

interface SyncIssueUsageReporter {

  /**
   * Collects a reported sync issue details to be reported as a part of [AndroidStudioEvent.EventKind.GRADLE_SYNC_ISSUES] event. This
   * method is supposed to be called on EDT only.
   */
  fun collect(issue: GradleSyncIssue.Builder)

  /**
   * Collects a sync failure to be reported as a part of [AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS] event. This
   * method is supposed to be called on EDT only.
   */
  fun collect(failure: AndroidStudioEvent.GradleSyncFailure)

  /**
   * Collects a quick fix to be reported as a part of [AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS] event. This
   * method is supposed to be called on EDT only.
   */
  fun collect(quickFixes: Collection<AndroidStudioEvent.GradleSyncQuickFix>)

  /**
   * Logs collected usages to the usage tracker as a [AndroidStudioEvent.EventKind.GRADLE_SYNC_ISSUES] and/or
   * [AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS] event. This method is supposed to be called on EDT only.
   */
  fun reportToUsageTracker()

  companion object {
    fun getInstance(project: Project): SyncIssueUsageReporter {
      return ServiceManager.getService(project, SyncIssueUsageReporter::class.java)
    }
  }
}

fun SyncIssueUsageReporter.collect(issueType: Int, quickFixes: Collection<NotificationHyperlink>) =
    collect(
        GradleSyncIssue
            .newBuilder()
            .setType(issueType.toGradleSyncIssueType() ?: AndroidStudioEvent.GradleSyncIssueType.UNKNOWN_GRADLE_SYNC_ISSUE_TYPE)
            .addAllOfferedQuickFixes(quickFixes.mapNotNull { it.toSyncIssueQuickFix() }))

fun SyncIssueUsageReporter.collect(quickFixes: Collection<NotificationHyperlink>) =
    collect(quickFixes.mapNotNull { it.toSyncIssueQuickFix() })

@Suppress("DUPLICATE_LABEL_IN_WHEN")
private fun NotificationHyperlink.toSyncIssueQuickFix(): AndroidStudioEvent.GradleSyncQuickFix? =
    when (this) {
      is AddGoogleMavenRepositoryHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.ADD_GOOGLE_MAVEN_REPOSITORY_HYPERLINK
      is BuildProjectHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.BUILD_PROJECT_HYPERLINK
      is CreateGradleWrapperHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.CREATE_GRADLE_WRAPPER_HYPERLINK
      is DisableOfflineModeHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.DISABLE_OFFLINE_MODE_HYPERLINK
      is DownloadAndroidStudioHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.DOWNLOAD_ANDROID_STUDIO_HYPERLINK
      is DownloadJdk8Hyperlink -> AndroidStudioEvent.GradleSyncQuickFix.DOWNLOAD_JDK8_HYPERLINK
      is FileBugHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.FILE_BUG_HYPERLINK
      is FixAndroidGradlePluginVersionHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.FIX_ANDROID_GRADLE_PLUGIN_VERSION_HYPERLINK
      is FixBuildToolsVersionHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.FIX_BUILD_TOOLS_VERSION_HYPERLINK
      is FixGradleVersionInWrapperHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.FIX_GRADLE_VERSION_IN_WRAPPER_HYPERLINK
      is InstallBuildToolsHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.INSTALL_BUILD_TOOLS_HYPERLINK
      is InstallNdkHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.INSTALL_NDK_HYPERLINK
      is InstallPlatformHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.INSTALL_PLATFORM_HYPERLINK
      is InstallSdkPackageHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.INSTALL_SDK_PACKAGE_HYPERLINK
      is OpenAndroidSdkManagerHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.OPEN_ANDROID_SDK_MANAGER_HYPERLINK
      is OpenFileHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.OPEN_FILE_HYPERLINK
      is OpenGradleSettingsHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.OPEN_GRADLE_SETTINGS_HYPERLINK
      is OpenHttpSettingsHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.OPEN_HTTP_SETTINGS_HYPERLINK
      is OpenPluginBuildFileHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.OPEN_PLUGIN_BUILD_FILE_HYPERLINK
      is OpenProjectStructureHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.OPEN_PROJECT_STRUCTURE_HYPERLINK
      is OpenUrlHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.OPEN_URL_HYPERLINK
      is RemoveSdkFromManifestHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.REMOVE_SDK_FROM_MANIFEST_HYPERLINK
      is SearchInBuildFilesHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.SEARCH_IN_BUILD_FILES_HYPERLINK
      is SelectJdkFromFileSystemHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.SELECT_JDK_FROM_FILE_SYSTEM_HYPERLINK
      is SetSdkDirHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.SET_SDK_DIR_HYPERLINK
      is ShowDependencyInProjectStructureHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.SHOW_DEPENDENCY_IN_PROJECT_STRUCTURE_HYPERLINK
      is ShowLogHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.SHOW_LOG_HYPERLINK
      is ShowSyncIssuesDetailsHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.SHOW_SYNC_ISSUES_DETAILS_HYPERLINK
      is StopGradleDaemonsHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.STOP_GRADLE_DAEMONS_HYPERLINK
      is SyncProjectWithExtraCommandLineOptionsHyperlink ->
        AndroidStudioEvent.GradleSyncQuickFix.SYNC_PROJECT_WITH_EXTRA_COMMAND_LINE_OPTIONS_HYPERLINK
      is ToggleOfflineModeHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.TOGGLE_OFFLINE_MODE_HYPERLINK
      is UpdatePluginHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.UPDATE_PLUGIN_HYPERLINK
      is UpgradeAppenginePluginVersionHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.UPGRADE_APPENGINE_PLUGIN_VERSION_HYPERLINK
      is UseJavaHomeAsJdkHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.USE_CURRENTLY_RUNNING_JDK_HYPERLINK
      is UseEmbeddedJdkHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.USE_EMBEDDED_JDK_HYPERLINK
      is DeleteFileAndSyncHyperlink -> AndroidStudioEvent.GradleSyncQuickFix.DELETE_FILE_HYPERLINK
      else -> null.also { LOG.warn("Unknown quick fix class: ${javaClass.canonicalName}") }
    }

@Suppress("DEPRECATION")
fun Int.toGradleSyncIssueType(): AndroidStudioEvent.GradleSyncIssueType? =
    when (this) {
      SyncIssue.TYPE_PLUGIN_OBSOLETE -> AndroidStudioEvent.GradleSyncIssueType.TYPE_PLUGIN_OBSOLETE
      SyncIssue.TYPE_UNRESOLVED_DEPENDENCY -> AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY
      SyncIssue.TYPE_DEPENDENCY_IS_APK -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPENDENCY_IS_APK
      SyncIssue.TYPE_DEPENDENCY_IS_APKLIB -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPENDENCY_IS_APKLIB
      SyncIssue.TYPE_NON_JAR_LOCAL_DEP -> AndroidStudioEvent.GradleSyncIssueType.TYPE_NON_JAR_LOCAL_DEP
      SyncIssue.TYPE_NON_JAR_PACKAGE_DEP -> AndroidStudioEvent.GradleSyncIssueType.TYPE_NON_JAR_PACKAGE_DEP
      SyncIssue.TYPE_NON_JAR_PROVIDED_DEP -> AndroidStudioEvent.GradleSyncIssueType.TYPE_NON_JAR_PROVIDED_DEP
      SyncIssue.TYPE_JAR_DEPEND_ON_AAR -> AndroidStudioEvent.GradleSyncIssueType.TYPE_JAR_DEPEND_ON_AAR
      SyncIssue.TYPE_MISMATCH_DEP -> AndroidStudioEvent.GradleSyncIssueType.TYPE_MISMATCH_DEP
      SyncIssue.TYPE_OPTIONAL_LIB_NOT_FOUND -> AndroidStudioEvent.GradleSyncIssueType.TYPE_OPTIONAL_LIB_NOT_FOUND
      SyncIssue.TYPE_JACK_IS_NOT_SUPPORTED -> AndroidStudioEvent.GradleSyncIssueType.TYPE_JACK_IS_NOT_SUPPORTED
      SyncIssue.TYPE_GRADLE_TOO_OLD -> AndroidStudioEvent.GradleSyncIssueType.TYPE_GRADLE_TOO_OLD
      SyncIssue.TYPE_BUILD_TOOLS_TOO_LOW -> AndroidStudioEvent.GradleSyncIssueType.TYPE_BUILD_TOOLS_TOO_LOW
      SyncIssue.TYPE_DEPENDENCY_MAVEN_ANDROID -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPENDENCY_MAVEN_ANDROID
      SyncIssue.TYPE_DEPENDENCY_INTERNAL_CONFLICT -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPENDENCY_INTERNAL_CONFLICT
      SyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION -> AndroidStudioEvent.GradleSyncIssueType.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION
      SyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION ->
        AndroidStudioEvent.GradleSyncIssueType.TYPE_EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION
      SyncIssue.TYPE_JACK_REQUIRED_FOR_JAVA_8_LANGUAGE_FEATURES -> AndroidStudioEvent.GradleSyncIssueType.TYPE_JACK_REQUIRED_FOR_JAVA_8_LANGUAGE_FEATURES
      SyncIssue.TYPE_DEPENDENCY_WEAR_APK_TOO_MANY -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPENDENCY_WEAR_APK_TOO_MANY
      SyncIssue.TYPE_DEPENDENCY_WEAR_APK_WITH_UNBUNDLED -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPENDENCY_WEAR_APK_WITH_UNBUNDLED
      SyncIssue.TYPE_JAR_DEPEND_ON_ATOM -> AndroidStudioEvent.GradleSyncIssueType.TYPE_JAR_DEPEND_ON_ATOM
      SyncIssue.TYPE_AAR_DEPEND_ON_ATOM -> AndroidStudioEvent.GradleSyncIssueType.TYPE_AAR_DEPEND_ON_ATOM
      SyncIssue.TYPE_ATOM_DEPENDENCY_PROVIDED -> AndroidStudioEvent.GradleSyncIssueType.TYPE_ATOM_DEPENDENCY_PROVIDED
      SyncIssue.TYPE_MISSING_SDK_PACKAGE -> AndroidStudioEvent.GradleSyncIssueType.TYPE_MISSING_SDK_PACKAGE
      SyncIssue.TYPE_STUDIO_TOO_OLD -> AndroidStudioEvent.GradleSyncIssueType.TYPE_STUDIO_TOO_OLD
      SyncIssue.TYPE_UNNAMED_FLAVOR_DIMENSION -> AndroidStudioEvent.GradleSyncIssueType.TYPE_UNNAMED_FLAVOR_DIMENSION
      SyncIssue.TYPE_INCOMPATIBLE_PLUGIN -> AndroidStudioEvent.GradleSyncIssueType.TYPE_INCOMPATIBLE_PLUGIN
      SyncIssue.TYPE_DEPRECATED_DSL -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPRECATED_DSL
      SyncIssue.TYPE_DEPRECATED_CONFIGURATION -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPRECATED_CONFIGURATION
      // NOTE: SyncIssue.TYPE_DEPRECATED_DSL_VALUE is not handled since it has the same value as SyncIssue.TYPE_DEPRECATED_CONFIGURATION
      // (see http://issuetracker.google.com/138278313). Also because of this bug, from this statement forward, the actual values of the
      // types on the two sides do not exactly match.
      // SyncIssue.TYPE_DEPRECATED_DSL_VALUE -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPRECATED_DSLVALUE
      SyncIssue.TYPE_MIN_SDK_VERSION_IN_MANIFEST -> AndroidStudioEvent.GradleSyncIssueType.TYPE_MIN_SDK_VERSION_IN_MANIFEST
      SyncIssue.TYPE_TARGET_SDK_VERSION_IN_MANIFEST -> AndroidStudioEvent.GradleSyncIssueType.TYPE_TARGET_SDK_VERSION_IN_MANIFEST
      SyncIssue.TYPE_UNSUPPORTED_PROJECT_OPTION_USE -> AndroidStudioEvent.GradleSyncIssueType.TYPE_UNSUPPORTED_PROJECT_OPTION_USE
      SyncIssue.TYPE_MANIFEST_PARSED_DURING_CONFIGURATION -> AndroidStudioEvent.GradleSyncIssueType.TYPE_MANIFEST_PARSED_DURING_CONFIGURATION
      SyncIssue.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD -> AndroidStudioEvent.GradleSyncIssueType.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD
      SyncIssue.TYPE_SIGNING_CONFIG_DECLARED_IN_DYNAMIC_FEATURE -> AndroidStudioEvent.GradleSyncIssueType.TYPE_SIGNING_CONFIG_DECLARED_IN_DYNAMIC_FEATURE
      SyncIssue.TYPE_SDK_NOT_SET -> AndroidStudioEvent.GradleSyncIssueType.TYPE_SDK_NOT_SET
      SyncIssue.TYPE_AMBIGUOUS_BUILD_TYPE_DEFAULT -> AndroidStudioEvent.GradleSyncIssueType.TYPE_AMBIGUOUS_BUILD_TYPE_DEFAULT
      SyncIssue.TYPE_AMBIGUOUS_PRODUCT_FLAVOR_DEFAULT -> AndroidStudioEvent.GradleSyncIssueType.TYPE_AMBIGUOUS_PRODUCT_FLAVOR_DEFAULT
      SyncIssue.TYPE_COMPILE_SDK_VERSION_NOT_SET -> AndroidStudioEvent.GradleSyncIssueType.TYPE_COMPILE_SDK_VERSION_NOT_SET
      SyncIssue.TYPE_ANDROID_X_PROPERTY_NOT_ENABLED -> AndroidStudioEvent.GradleSyncIssueType.TYPE_ANDROID_X_PROPERTY_NOT_ENABLED
      SyncIssue.TYPE_USING_DEPRECATED_CONFIGURATION -> AndroidStudioEvent.GradleSyncIssueType.TYPE_USING_DEPRECATED_CONFIGURATION
      SyncIssue.TYPE_USING_DEPRECATED_DSL_VALUE -> AndroidStudioEvent.GradleSyncIssueType.TYPE_USING_DEPRECATED_DSL_VALUE
      SyncIssue.TYPE_EDIT_LOCKED_DSL_VALUE -> AndroidStudioEvent.GradleSyncIssueType.TYPE_EDIT_LOCKED_DSL_VALUE
      SyncIssue.TYPE_MISSING_ANDROID_MANIFEST -> AndroidStudioEvent.GradleSyncIssueType.TYPE_MISSING_ANDROID_MANIFEST
      else -> null.also { LOG.warn("Unknown sync issue type: $this") }
    }

