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
package com.android.tools.idea.gradle.model.impl

import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeApiVersion
import com.android.tools.idea.gradle.model.IdeClassField
import com.android.tools.idea.gradle.model.IdeJavaArtifact
import com.android.tools.idea.gradle.model.IdeTestedTargetVariant
import com.android.tools.idea.gradle.model.IdeVariant
import java.io.File
import java.io.Serializable

data class IdeVariantImpl(
    override val name: String,
    override val displayName: String,
    override val mainArtifact: IdeAndroidArtifact,
    override val unitTestArtifact: IdeJavaArtifact?,
    override val androidTestArtifact: IdeAndroidArtifact?,
    override val buildType: String,
    override val productFlavors: List<String>,
    override val minSdkVersion: IdeApiVersion?,
    override val targetSdkVersion: IdeApiVersion?,
    override val maxSdkVersion: Int?,
    override val versionCode: Int?,
    override val versionNameWithSuffix: String?,
    override val versionNameSuffix: String?,
    override val instantAppCompatible: Boolean,
    override val vectorDrawablesUseSupportLibrary: Boolean,
    override val resourceConfigurations: Collection<String>,
    override val resValues: Map<String, IdeClassField>,
    override val proguardFiles: Collection<File>,
    override val consumerProguardFiles: Collection<File>,
    override val manifestPlaceholders: Map<String, String>,
    override val testApplicationId: String?,
    override val testInstrumentationRunner: String?,
    override val testInstrumentationRunnerArguments: Map<String, String>,
    override val testedTargetVariants: List<IdeTestedTargetVariant>,
    // TODO(b/178961768); Review usages and replace with the correct alternatives or rename.
    override val deprecatedPreMergedApplicationId: String?,
) : IdeVariant, Serializable
