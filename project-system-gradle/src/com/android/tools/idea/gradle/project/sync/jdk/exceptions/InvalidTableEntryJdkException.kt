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
package com.android.tools.idea.gradle.project.sync.jdk.exceptions

import com.android.tools.idea.gradle.project.sync.jdk.exceptions.base.GradleJdkException
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.InvalidGradleJvmTableEntryJavaHome
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.UndefinedGradleJvmTableEntry
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.UndefinedGradleJvmTableEntryJavaHome
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.kotlin.tools.projectWizard.core.asPath
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings

/**
 * A [GradleJdkException] when gradle root [GradleProjectSettings.getGradleJvm] is configured with unknown macro or
 * desired jdk name that represents an undefined or invalid [ProjectJdkTable] entry.
 */
class InvalidTableEntryJdkException(
  project: Project,
  gradleRootPath: @SystemIndependent String,
  private val jdkName: String,
): GradleJdkException(project, gradleRootPath) {

  override val cause: InvalidGradleJdkCause
    get() {
      val javaSdkTypeName = ExternalSystemJdkUtil.getJavaSdkType().name
      val existingJdk = ProjectJdkTable.getInstance().findJdk(jdkName, javaSdkTypeName) ?: return UndefinedGradleJvmTableEntry(jdkName)
      val jdkPath = existingJdk.homePath?.asPath() ?: return UndefinedGradleJvmTableEntryJavaHome(jdkName)
      return InvalidGradleJvmTableEntryJavaHome(jdkPath, jdkName)
    }
}