/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.project.messages.SyncMessage
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncIssue
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.SystemIndependent

private val LOG = Logger.getInstance(SyncIssueUsageReporter::class.java)

interface SyncIssueUsageReporter {

  /**
   * Collects a reported sync issue details to be reported as a part of [AndroidStudioEvent.EventKind.GRADLE_SYNC_ISSUES] event. This
   * method is supposed to be called on EDT only.
   */
  fun collect(issue: GradleSyncIssue)

  /**
   * Collects a sync failure to be reported as a part of [AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS] event. This
   * method is supposed to be called on EDT only.
   */
  fun collect(failure: AndroidStudioEvent.GradleSyncFailure)

  /**
   * Logs collected usages to the usage tracker as a [AndroidStudioEvent.EventKind.GRADLE_SYNC_ISSUES] and/or
   * [AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS] event. This method is supposed to be called on EDT only.
   */
  fun reportToUsageTracker(rootProjectPath: @SystemIndependent String)

  companion object {
    fun getInstance(project: Project): SyncIssueUsageReporter {
      return project.getService(SyncIssueUsageReporter::class.java)
    }

    @JvmStatic
    fun createGradleSyncIssue(issueType: Int, message: SyncMessage): GradleSyncIssue {
      return GradleSyncIssue
        .newBuilder()
        .setType(issueType.toGradleSyncIssueType() ?: AndroidStudioEvent.GradleSyncIssueType.UNKNOWN_GRADLE_SYNC_ISSUE_TYPE)
        .addAllOfferedQuickFixes(
          message.quickFixes.flatMap { it.quickFixIds }.distinct()
        )
        .build()
    }

    @JvmStatic
    fun createGradleSyncIssues(issueType: Int, messages: List<SyncMessage>): List<GradleSyncIssue> {
      return messages.map { createGradleSyncIssue(issueType, it) }
    }
  }
}


fun SyncIssueUsageReporter.collect(issueType: Int, messages: List<SyncMessage>) {
  SyncIssueUsageReporter.createGradleSyncIssues(issueType, messages).forEach { collect(it) }
}

@Suppress("DEPRECATION")
fun Int.toGradleSyncIssueType(): AndroidStudioEvent.GradleSyncIssueType? =
  when (this) {
    IdeSyncIssue.TYPE_PLUGIN_OBSOLETE -> AndroidStudioEvent.GradleSyncIssueType.TYPE_PLUGIN_OBSOLETE
    IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY -> AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY
    IdeSyncIssue.TYPE_DEPENDENCY_IS_APK -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPENDENCY_IS_APK
    IdeSyncIssue.TYPE_DEPENDENCY_IS_APKLIB -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPENDENCY_IS_APKLIB
    IdeSyncIssue.TYPE_NON_JAR_LOCAL_DEP -> AndroidStudioEvent.GradleSyncIssueType.TYPE_NON_JAR_LOCAL_DEP
    IdeSyncIssue.TYPE_NON_JAR_PACKAGE_DEP -> AndroidStudioEvent.GradleSyncIssueType.TYPE_NON_JAR_PACKAGE_DEP
    IdeSyncIssue.TYPE_NON_JAR_PROVIDED_DEP -> AndroidStudioEvent.GradleSyncIssueType.TYPE_NON_JAR_PROVIDED_DEP
    IdeSyncIssue.TYPE_JAR_DEPEND_ON_AAR -> AndroidStudioEvent.GradleSyncIssueType.TYPE_JAR_DEPEND_ON_AAR
    IdeSyncIssue.TYPE_MISMATCH_DEP -> AndroidStudioEvent.GradleSyncIssueType.TYPE_MISMATCH_DEP
    IdeSyncIssue.TYPE_OPTIONAL_LIB_NOT_FOUND -> AndroidStudioEvent.GradleSyncIssueType.TYPE_OPTIONAL_LIB_NOT_FOUND
    IdeSyncIssue.TYPE_JACK_IS_NOT_SUPPORTED -> AndroidStudioEvent.GradleSyncIssueType.TYPE_JACK_IS_NOT_SUPPORTED
    IdeSyncIssue.TYPE_GRADLE_TOO_OLD -> AndroidStudioEvent.GradleSyncIssueType.TYPE_GRADLE_TOO_OLD
    IdeSyncIssue.TYPE_BUILD_TOOLS_TOO_LOW -> AndroidStudioEvent.GradleSyncIssueType.TYPE_BUILD_TOOLS_TOO_LOW
    IdeSyncIssue.TYPE_DEPENDENCY_MAVEN_ANDROID -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPENDENCY_MAVEN_ANDROID
    IdeSyncIssue.TYPE_DEPENDENCY_INTERNAL_CONFLICT -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPENDENCY_INTERNAL_CONFLICT
    IdeSyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION -> AndroidStudioEvent.GradleSyncIssueType.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION
    IdeSyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION ->
      AndroidStudioEvent.GradleSyncIssueType.TYPE_EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION
    IdeSyncIssue.TYPE_JACK_REQUIRED_FOR_JAVA_8_LANGUAGE_FEATURES -> AndroidStudioEvent.GradleSyncIssueType.TYPE_JACK_REQUIRED_FOR_JAVA_8_LANGUAGE_FEATURES
    IdeSyncIssue.TYPE_DEPENDENCY_WEAR_APK_TOO_MANY -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPENDENCY_WEAR_APK_TOO_MANY
    IdeSyncIssue.TYPE_DEPENDENCY_WEAR_APK_WITH_UNBUNDLED -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPENDENCY_WEAR_APK_WITH_UNBUNDLED
    IdeSyncIssue.TYPE_JAR_DEPEND_ON_ATOM -> AndroidStudioEvent.GradleSyncIssueType.TYPE_JAR_DEPEND_ON_ATOM
    IdeSyncIssue.TYPE_AAR_DEPEND_ON_ATOM -> AndroidStudioEvent.GradleSyncIssueType.TYPE_AAR_DEPEND_ON_ATOM
    IdeSyncIssue.TYPE_ATOM_DEPENDENCY_PROVIDED -> AndroidStudioEvent.GradleSyncIssueType.TYPE_ATOM_DEPENDENCY_PROVIDED
    IdeSyncIssue.TYPE_MISSING_SDK_PACKAGE -> AndroidStudioEvent.GradleSyncIssueType.TYPE_MISSING_SDK_PACKAGE
    IdeSyncIssue.TYPE_STUDIO_TOO_OLD -> AndroidStudioEvent.GradleSyncIssueType.TYPE_STUDIO_TOO_OLD
    IdeSyncIssue.TYPE_UNNAMED_FLAVOR_DIMENSION -> AndroidStudioEvent.GradleSyncIssueType.TYPE_UNNAMED_FLAVOR_DIMENSION
    IdeSyncIssue.TYPE_INCOMPATIBLE_PLUGIN -> AndroidStudioEvent.GradleSyncIssueType.TYPE_INCOMPATIBLE_PLUGIN
    IdeSyncIssue.TYPE_DEPRECATED_DSL -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPRECATED_DSL
    IdeSyncIssue.TYPE_DEPRECATED_CONFIGURATION -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPRECATED_CONFIGURATION
    // NOTE: SyncIssue.TYPE_DEPRECATED_DSL_VALUE is not handled since it has the same value as SyncIssue.TYPE_DEPRECATED_CONFIGURATION
    // (see http://issuetracker.google.com/138278313). Also because of this bug, from this statement forward, the actual values of the
    // types on the two sides do not exactly match.
    // SyncIssue.TYPE_DEPRECATED_DSL_VALUE -> AndroidStudioEvent.GradleSyncIssueType.TYPE_DEPRECATED_DSLVALUE
    IdeSyncIssue.TYPE_MIN_SDK_VERSION_IN_MANIFEST -> AndroidStudioEvent.GradleSyncIssueType.TYPE_MIN_SDK_VERSION_IN_MANIFEST
    IdeSyncIssue.TYPE_TARGET_SDK_VERSION_IN_MANIFEST -> AndroidStudioEvent.GradleSyncIssueType.TYPE_TARGET_SDK_VERSION_IN_MANIFEST
    IdeSyncIssue.TYPE_UNSUPPORTED_PROJECT_OPTION_USE -> AndroidStudioEvent.GradleSyncIssueType.TYPE_UNSUPPORTED_PROJECT_OPTION_USE
    IdeSyncIssue.TYPE_MANIFEST_PARSED_DURING_CONFIGURATION -> AndroidStudioEvent.GradleSyncIssueType.TYPE_MANIFEST_PARSED_DURING_CONFIGURATION
    IdeSyncIssue.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD -> AndroidStudioEvent.GradleSyncIssueType.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD
    IdeSyncIssue.TYPE_SIGNING_CONFIG_DECLARED_IN_DYNAMIC_FEATURE -> AndroidStudioEvent.GradleSyncIssueType.TYPE_SIGNING_CONFIG_DECLARED_IN_DYNAMIC_FEATURE
    IdeSyncIssue.TYPE_SDK_NOT_SET -> AndroidStudioEvent.GradleSyncIssueType.TYPE_SDK_NOT_SET
    IdeSyncIssue.TYPE_AMBIGUOUS_BUILD_TYPE_DEFAULT -> AndroidStudioEvent.GradleSyncIssueType.TYPE_AMBIGUOUS_BUILD_TYPE_DEFAULT
    IdeSyncIssue.TYPE_AMBIGUOUS_PRODUCT_FLAVOR_DEFAULT -> AndroidStudioEvent.GradleSyncIssueType.TYPE_AMBIGUOUS_PRODUCT_FLAVOR_DEFAULT
    IdeSyncIssue.TYPE_COMPILE_SDK_VERSION_NOT_SET -> AndroidStudioEvent.GradleSyncIssueType.TYPE_COMPILE_SDK_VERSION_NOT_SET
    IdeSyncIssue.TYPE_ANDROID_X_PROPERTY_NOT_ENABLED -> AndroidStudioEvent.GradleSyncIssueType.TYPE_ANDROID_X_PROPERTY_NOT_ENABLED
    IdeSyncIssue.TYPE_USING_DEPRECATED_CONFIGURATION -> AndroidStudioEvent.GradleSyncIssueType.TYPE_USING_DEPRECATED_CONFIGURATION
    IdeSyncIssue.TYPE_USING_DEPRECATED_DSL_VALUE -> AndroidStudioEvent.GradleSyncIssueType.TYPE_USING_DEPRECATED_DSL_VALUE
    IdeSyncIssue.TYPE_EDIT_LOCKED_DSL_VALUE -> AndroidStudioEvent.GradleSyncIssueType.TYPE_EDIT_LOCKED_DSL_VALUE
    IdeSyncIssue.TYPE_MISSING_ANDROID_MANIFEST -> AndroidStudioEvent.GradleSyncIssueType.TYPE_MISSING_ANDROID_MANIFEST
    IdeSyncIssue.TYPE_JCENTER_IS_DEPRECATED -> AndroidStudioEvent.GradleSyncIssueType.TYPE_JCENTER_IS_DEPRECATED
    IdeSyncIssue.TYPE_AGP_USED_JAVA_VERSION_TOO_LOW -> AndroidStudioEvent.GradleSyncIssueType.TYPE_AGP_USED_JAVA_VERSION_TOO_LOW
    IdeSyncIssue.TYPE_COMPILE_SDK_VERSION_TOO_HIGH -> AndroidStudioEvent.GradleSyncIssueType.TYPE_COMPILE_SDK_VERSION_TOO_HIGH
    IdeSyncIssue.TYPE_COMPILE_SDK_VERSION_TOO_LOW -> AndroidStudioEvent.GradleSyncIssueType.TYPE_COMPILE_SDK_VERSION_TOO_LOW
    IdeSyncIssue.TYPE_ACCESSING_DISABLED_FEATURE_VARIANT_API -> AndroidStudioEvent.GradleSyncIssueType.TYPE_ACCESSING_DISABLED_FEATURE_VARIANT_API
    IdeSyncIssue.TYPE_APPLICATION_ID_MUST_NOT_BE_DYNAMIC -> AndroidStudioEvent.GradleSyncIssueType.TYPE_APPLICATION_ID_MUST_NOT_BE_DYNAMIC
    IdeSyncIssue.TYPE_REMOVED_API -> AndroidStudioEvent.GradleSyncIssueType.TYPE_REMOVED_API
    IdeSyncIssue.TYPE_EMPTY_FLAVOR_DIMENSION -> AndroidStudioEvent.GradleSyncIssueType.TYPE_EMPTY_FLAVOR_DIMENSION
    else -> null.also { LOG.warn("Unknown sync issue type: $this") }
    }

