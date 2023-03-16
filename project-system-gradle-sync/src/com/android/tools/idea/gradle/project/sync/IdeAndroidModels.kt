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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeAndroidProject
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantAbi
import com.android.tools.idea.gradle.model.ndk.v2.IdeNativeModule
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel
import java.io.Serializable

class IdeAndroidModels(
  val androidProject: IdeAndroidProjectImpl,
  val fetchedVariants: List<IdeVariantCoreImpl>,
  val selectedVariantName: String,
  val selectedAbiName: String?,
  val syncIssues: List<IdeSyncIssue>,
  val v2NativeModule: IdeNativeModule?,
  val v1NativeProject: IdeNativeAndroidProject?,
  val v1NativeVariantAbi: IdeNativeVariantAbi?,
  val kaptGradleModel: KaptGradleModel?
) : Serializable

class IdeAndroidNativeVariantsModels(
  val v1NativeVariantAbis: List<IdeNativeVariantAbi>?, // null if v2.
  val syncIssues: List<IdeSyncIssue>
) : Serializable

/**
 * A model to represent unexpected exceptions/errors suppressed during sync.
 *
 * In the Gradle phase of sync, if an unexpected exception is suppressed it likely results in invalid/incomplete sync results, however it is
 * likely to be a better outcome for the user than not having any result at all. [IdeAndroidSyncExceptions] module level model is used to
 * report exceptions suppressed while building IDE models to the IDE process where they are supposed to be logged as errors. The IDE phase
 * of sync should also generate a sync issue notifying the user about potentially incompletely synced state of the project.
 */
class IdeAndroidSyncExceptions(val exceptions: List<Throwable>): Serializable

/**
 * A model to represent a fatal sync error such as one that would normally be passed as an exception. This is necessary to workaround
 * exception serialization issues across different JVM versions.
 */
class IdeAndroidSyncError(
  val message: String,
  val stackTrace: List<String>,
  val buildPath: String? = null,
  val modulePath: String? = null,
  val syncIssues: List<IdeSyncIssue>? = null) : Serializable

@JvmName("ideAndroidSyncErrorToException")
fun IdeAndroidSyncError.toException(): AndroidSyncException =
  AndroidSyncException(message = "$message at:\n${stackTrace.joinToString(separator = "\n  ") { it }}",
                       buildPath = buildPath,
                       modulePath = modulePath,
                       syncIssues = syncIssues)
