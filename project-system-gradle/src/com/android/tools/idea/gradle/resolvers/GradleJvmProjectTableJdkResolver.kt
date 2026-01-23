/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.resolvers

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.SdkLookupDecision
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo
import org.jetbrains.plugins.gradle.resolvers.GradleJvmResolver
import org.jetbrains.plugins.gradle.settings.GradleSettings

/**
 * A [GradleJvmResolver] implementation to resolve all non-macros gradleJvm (which starts with '#' prefix e.g: #JAVA_HOME),
 * bypassing the existing implementation of [com.intellij.openapi.externalSystem.service.execution.resolveSdkInfoBySdkName]
 * which is flaky caused by IDEA-385084 issue
 */
@Suppress("UnstableApiUsage")
class GradleJvmProjectTableJdkResolver : GradleJvmResolver()  {
  override fun canBeResolved(gradleJvm: String) = !gradleJvm.startsWith("#")

  override fun getResolvedSdkInfo(project: Project,
                                  projectSdk: Sdk?,
                                  externalProjectPath: String?,
                                  sdkLookupProvider: SdkLookupProvider): SdkInfo {
    val externalProjectPath = externalProjectPath ?: return SdkInfo.Undefined
    val settings = GradleSettings.getInstance(project).getLinkedProjectSettings(externalProjectPath) ?: return SdkInfo.Undefined
    val gradleJvm = settings.gradleJvm ?: return SdkInfo.Undefined

    // Use a unique SdkLookupProvider to avoid concurrency issues caused by the fact that
    // same instance is used from different threads, resulting on loosing getSdkInfo()
    // when a new lookup starts from another thread.
    val uniqueSdkLookupProvider = SdkLookupProvider.getInstance(project, UniqueId())
    uniqueSdkLookupProvider.newLookupBuilder()
      .withSdkName(gradleJvm)
      .onBeforeSdkSuggestionStarted { SdkLookupDecision.STOP }
      .executeLookup()

    uniqueSdkLookupProvider.waitForLookup()
    return uniqueSdkLookupProvider.getSdkInfo()
  }

  private class UniqueId : SdkLookupProvider.Id
}