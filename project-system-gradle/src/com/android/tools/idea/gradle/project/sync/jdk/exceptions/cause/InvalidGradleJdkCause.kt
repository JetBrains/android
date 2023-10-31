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
package com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause

import com.google.wireless.android.sdk.stats.GradleJdkInvalidEvent.InvalidJdkReason
import com.google.wireless.android.sdk.stats.GradleJdkInvalidEvent.InvalidJdkReason.INVALID_ENVIRONMENT_VARIABLE_JAVA_HOME
import com.google.wireless.android.sdk.stats.GradleJdkInvalidEvent.InvalidJdkReason.INVALID_ENVIRONMENT_VARIABLE_STUDIO_GRADLE_JDK
import com.google.wireless.android.sdk.stats.GradleJdkInvalidEvent.InvalidJdkReason.INVALID_GRADLE_JVM_TABLE_ENTRY_JAVA_HOME
import com.google.wireless.android.sdk.stats.GradleJdkInvalidEvent.InvalidJdkReason.INVALID_GRADLE_LOCAL_JAVA_HOME
import com.google.wireless.android.sdk.stats.GradleJdkInvalidEvent.InvalidJdkReason.INVALID_GRADLE_PROPERTIES_JAVA_HOME
import com.google.wireless.android.sdk.stats.GradleJdkInvalidEvent.InvalidJdkReason.UNDEFINED_ENVIRONMENT_VARIABLE_JAVA_HOME
import com.google.wireless.android.sdk.stats.GradleJdkInvalidEvent.InvalidJdkReason.UNDEFINED_ENVIRONMENT_VARIABLE_STUDIO_GRADLE_JDK
import com.google.wireless.android.sdk.stats.GradleJdkInvalidEvent.InvalidJdkReason.UNDEFINED_GRADLE_JVM_TABLE_ENTRY
import com.google.wireless.android.sdk.stats.GradleJdkInvalidEvent.InvalidJdkReason.UNDEFINED_GRADLE_JVM_TABLE_ENTRY_JAVA_HOME
import com.google.wireless.android.sdk.stats.GradleJdkInvalidEvent.InvalidJdkReason.UNDEFINED_GRADLE_LOCAL_JAVA_HOME
import com.google.wireless.android.sdk.stats.GradleJdkInvalidEvent.InvalidJdkReason.UNDEFINED_GRADLE_PROPERTIES_JAVA_HOME
import org.jetbrains.android.util.AndroidBundle
import java.nio.file.Path

/**
 * Enum that contains all the cases of project invalid Gradle JDK configuration
 */
sealed class InvalidGradleJdkCause(
  val description: String,
  val reason: InvalidJdkReason
) {

  object UndefinedGradleLocalJavaHome: InvalidGradleJdkCause(
    AndroidBundle.message("project.sync.exception.gradle.local.java.home.undefined"), UNDEFINED_GRADLE_LOCAL_JAVA_HOME
  )
  class InvalidGradleLocalJavaHome(path: Path): InvalidGradleJdkCause(
    AndroidBundle.message("project.sync.exception.gradle.local.java.home.invalid", path), INVALID_GRADLE_LOCAL_JAVA_HOME
  )
  object UndefinedGradlePropertiesJavaHome: InvalidGradleJdkCause(
    AndroidBundle.message("project.sync.exception.gradle.properties.java.home.undefined"), UNDEFINED_GRADLE_PROPERTIES_JAVA_HOME
  )
  class InvalidGradlePropertiesJavaHome(path: Path): InvalidGradleJdkCause(
    AndroidBundle.message("project.sync.exception.gradle.properties.java.home.invalid", path), INVALID_GRADLE_PROPERTIES_JAVA_HOME
  )
  object UndefinedEnvironmentVariableJavaHome: InvalidGradleJdkCause(
    AndroidBundle.message("project.sync.exception.environment.variable.java.home.undefined"), UNDEFINED_ENVIRONMENT_VARIABLE_JAVA_HOME
  )
  class InvalidEnvironmentVariableJavaHome(path: Path): InvalidGradleJdkCause(
    AndroidBundle.message("project.sync.exception.environment.variable.java.home.invalid", path), INVALID_ENVIRONMENT_VARIABLE_JAVA_HOME
  )
  object UndefinedEnvironmentVariableStudioGradleJdk: InvalidGradleJdkCause(
    AndroidBundle.message("project.sync.exception.environment.variable.studio.gradle.jdk.undefined"), UNDEFINED_ENVIRONMENT_VARIABLE_STUDIO_GRADLE_JDK
  )
  class InvalidEnvironmentVariableStudioGradleJdk(path: Path): InvalidGradleJdkCause(
    AndroidBundle.message("project.sync.exception.environment.variable.studio.gradle.jdk.invalid", path), INVALID_ENVIRONMENT_VARIABLE_STUDIO_GRADLE_JDK
  )
  class UndefinedGradleJvmTableEntry(name: String): InvalidGradleJdkCause(
    AndroidBundle.message("project.sync.exception.gradlejvm.table.entry.undefined", name), UNDEFINED_GRADLE_JVM_TABLE_ENTRY
  )
  class UndefinedGradleJvmTableEntryJavaHome(name: String): InvalidGradleJdkCause(
    AndroidBundle.message("project.sync.exception.gradlejvm.table.entry.java.home.undefined", name), UNDEFINED_GRADLE_JVM_TABLE_ENTRY_JAVA_HOME
  )
  class InvalidGradleJvmTableEntryJavaHome(path: Path, name: String): InvalidGradleJdkCause(
    AndroidBundle.message("project.sync.exception.gradlejvm.table.entry.java.home.invalid", path, name), INVALID_GRADLE_JVM_TABLE_ENTRY_JAVA_HOME
  )
}
