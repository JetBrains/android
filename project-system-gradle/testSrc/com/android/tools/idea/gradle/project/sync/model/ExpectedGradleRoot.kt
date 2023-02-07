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
package com.android.tools.idea.gradle.project.sync.model

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.service.GradleInstallationManager

/**
 * Project expected gradle root representation model
 * @param ideaGradleJdk The jdk.table.xml entry name or macro defined on [ExternalSystemJdkUtil] used to configure the gradle java for sync
 * @param gradleExecutionDaemonJdkPath The jdk path used to configure the gradle daemon and trigger sync [GradleInstallationManager.getGradleJvmPath]
 * @param gradleLocalJavaHome The java.home property located on .gradle/config.properties
 */
data class ExpectedGradleRoot(
  val ideaGradleJdk: String? = null,
  val gradleExecutionDaemonJdkPath: @SystemIndependent String? = null,
  val gradleLocalJavaHome: @SystemIndependent String? = null
)
