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

import com.android.tools.idea.gradle.model.IdeAndroidGradlePluginProjectFlags
import java.io.Serializable

/**
 * Represents flags that affect the semantic of the build in the Android Gradle Plugin
 * that also should affect the behavior of Android Studio.
 */
data class IdeAndroidGradlePluginProjectFlagsImpl(
  /**
   * Whether the R class in applications and dynamic features are constant.
   *
   * If they are constant they can be inlined by the java compiler and used in places that
   * require constants such as annotations and cases of switch statements.
   */
  override val applicationRClassConstantIds: Boolean,

  /**
   * Whether the R class in instrumentation tests are constant.
   *
   * If they are constant they can be inlined by the java compiler and used in places that
   * require constants such as annotations and cases of switch statements.
   */
  override val testRClassConstantIds: Boolean,

  /**
   * Whether the R class generated for this project is Transitive.
   *
   * If it is transitive it will contain all of the resources defined in its transitive
   * dependencies alongside those defined in this project.
   * If non-transitive it will only contain the resources defined in this project.
   */
  override val transitiveRClasses: Boolean,

  /** Whether the Jetpack Compose feature is enabled for this project. */
  override val usesCompose: Boolean,

  /** Whether the ML model binding feature is enabled for this project. */
  override val mlModelBindingEnabled: Boolean,

  override val androidResourcesEnabled: Boolean,

  /** Whether the Android Test Platform is enabled for this project. */
  override val unifiedTestPlatformEnabled: Boolean,

  override val useAndroidX: Boolean,

  override val dataBindingEnabled: Boolean,

  override val generateManifestClass: Boolean,

) : Serializable, IdeAndroidGradlePluginProjectFlags
