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
package com.android.tools.idea.gradle.project.sync.idea.data.model

enum class KotlinMultiplatformAndroidSourceSetType {
  MAIN,
  UNIT_TEST,
  ANDROID_TEST,
}

/**
 * Contains the table of android sourceSets existing in a kotlin multiplatform module.
 * Currently, there should be only one sourceSet per [KotlinMultiplatformAndroidSourceSetType].
 */
data class KotlinMultiplatformAndroidSourceSetData(
  val sourceSetsByGradleProjectPath: Map<String, Map<KotlinMultiplatformAndroidSourceSetType, String>>
)
