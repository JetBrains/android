/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.svs

import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.IdeVariant
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeAndroidProject
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeVariantAbi
import com.android.tools.idea.gradle.project.model.V2NdkModel
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueData

class IdeAndroidModels(
  val androidProject: IdeAndroidProject,
  val fetchedVariants: List<IdeVariant>,
  val selectedVariantName: String,
  val syncIssues: List<SyncIssueData>,
  val v2NdkModel: V2NdkModel?,
  val v1NativeProject: IdeNativeAndroidProject?,
  val v1NativeVariantAbis: List<IdeNativeVariantAbi>?
)
