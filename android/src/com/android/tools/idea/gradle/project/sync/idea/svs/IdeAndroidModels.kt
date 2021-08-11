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

import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeAndroidProject
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantAbi
import com.android.tools.idea.gradle.model.ndk.v2.IdeNativeModule
import java.io.Serializable

class IdeAndroidModels(
  val androidProject: IdeAndroidProject,
  val fetchedVariants: List<IdeVariant>,
  val selectedVariantName: String,
  val selectedAbiName: String?,
  val syncIssues: List<IdeSyncIssue>,
  val v2NativeModule: IdeNativeModule?,
  val v1NativeProject: IdeNativeAndroidProject?,
  val v1NativeVariantAbi: IdeNativeVariantAbi?
) : Serializable

class IdeAndroidNativeVariantsModels(
  val v1NativeVariantAbis: List<IdeNativeVariantAbi>?, // null if v2.
  val syncIssues: List<IdeSyncIssue>
) : Serializable

/**
 * A model to represent errors usually passed via exceptions. This is necessary to workaround exception serialization issues across
 * different JVM versions.
 */
class IdeAndroidSyncError(val message: String, val stackTrace: List<String>) : Serializable

@JvmName("ideAndroidSyncErrorToException")
fun IdeAndroidSyncError.toException(): AndroidSyncException =
  AndroidSyncException("$message at:\n${stackTrace.joinToString(separator = "\n  ") { it }}")
