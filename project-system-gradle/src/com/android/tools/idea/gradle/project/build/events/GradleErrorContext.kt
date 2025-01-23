/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.events

import com.intellij.openapi.vfs.VirtualFile

/** Represents a Gradle error context.
 *  The following details are stored in the context:
 *  @param gradleTask The Gradle command that was executed.
 *  @param errorMessage The error message.
 *  @param fullErrorDetails The full error details/stack trace to include.
 *  @param source Whether it is a Build / Sync error
 *  @param sourceFiles Source file(s) of the error.
 */
data class GradleErrorContext(
  val gradleTask: String?,
  val errorMessage: String?,
  val fullErrorDetails: String?,
  val source: Source?,
  val sourceFiles: List<VirtualFile> = emptyList()
  ) {
  enum class Source(private val source: String) {
    BUILD("build"),
    SYNC("sync");
    override fun toString(): String = source
  }
}