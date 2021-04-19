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
package com.android.tools.idea.gradle.project.sync.idea

import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.kapt.idea.KaptGradleModel

object AndroidGradleProjectResolverKeys {
  @JvmField
  val MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI: Key<String> = Key.create("module.with.build.variant.switched.from.ui")
  @JvmField
  val USE_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS: Key<Boolean> = Key.create("use.variants.from.previous.gradle.syncs")
  @JvmField
  val REFRESH_EXTERNAL_NATIVE_MODELS_KEY: Key<Boolean> = Key.create("refresh.external.native.models")

  // For variant switching we need to store the Kapt model with all the source set information as we only setup one
  // variant at a time
  @JvmField
  val KAPT_GRADLE_MODEL_KEY: Key<KaptGradleModel> = Key.create("KAPT_GRADLE_MODEL_KEY")
  @JvmField
  val REQUESTED_PROJECT_RESOLUTION_MODE_KEY: Key<ProjectResolutionMode> = Key.create("REQUESTED_PROJECT_RESOLUTION_MODE")
}