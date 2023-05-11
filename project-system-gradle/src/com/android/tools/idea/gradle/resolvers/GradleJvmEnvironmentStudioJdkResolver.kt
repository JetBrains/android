/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.IdeSdks.JDK_LOCATION_ENV_VARIABLE_NAME
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import org.jetbrains.plugins.gradle.resolvers.GradleJvmResolver
import org.jetbrains.plugins.gradle.service.execution.LocalGradleExecutionAware

/**
 * A [GradleJvmResolver] implementation to resolve the environment variable [JDK_LOCATION_ENV_VARIABLE_NAME] and
 * bypass the gradleJvm validation from [LocalGradleExecutionAware.prepareJvmForExecution] using the specified path instead
 */
@Suppress("UnstableApiUsage")
class GradleJvmEnvironmentStudioJdkResolver : GradleJvmResolver()  {

  override fun canBeResolved(gradleJvm: String) = IdeSdks.getInstance().isUsingEnvVariableJdk

  override fun getResolvedSdkInfo(
    project: Project,
    projectSdk: Sdk?,
    externalProjectPath: String?,
    sdkLookupProvider: SdkLookupProvider
  ) = createSdkInfo(
    name = JDK_LOCATION_ENV_VARIABLE_NAME,
    homePath = IdeSdks.getInstance().envVariableJdkValue
  )
}