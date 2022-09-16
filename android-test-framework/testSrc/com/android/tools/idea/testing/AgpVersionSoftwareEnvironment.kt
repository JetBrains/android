/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.testing

/**
 * An AGP Version definition to be used in AGP integration tests.
 */
interface AgpVersionSoftwareEnvironment {
  /**
   * The version of the AGP. `null` means the current `-dev` version.
   */
  val agpVersion: String?

  /**
   * The version of Gradle to be used in integration tests for this AGP version. `null` means the latest/default version.
   */
  val gradleVersion: String?

  /**
   * The version of the Gradle Kotlin plugin to be used in integration tests for this AGP version. `null` means the default version used by
   * Android Studio.
   */
  val kotlinVersion: String?

  /**
   * The compileSdk to use in this test. `null` means the project default.
   */
  val compileSdk: String?

  /**
   * Builder model version to query.
   */
  val modelVersion: ModelVersion
}

data class CustomAgpVersionSoftwareEnvironment @JvmOverloads constructor(
  override val agpVersion: String?,
  override val gradleVersion: String?,
  override val kotlinVersion: String? = null,
  override val compileSdk: String? = null,
  override val modelVersion: ModelVersion = ModelVersion.V2
) : AgpVersionSoftwareEnvironment

fun AgpVersionSoftwareEnvironment.withGradle(gradleVersion: String?): CustomAgpVersionSoftwareEnvironment =
  CustomAgpVersionSoftwareEnvironment(agpVersion, gradleVersion, kotlinVersion, compileSdk, modelVersion)

fun AgpVersionSoftwareEnvironment.withKotlin(kotlinVersion: String?): CustomAgpVersionSoftwareEnvironment =
  CustomAgpVersionSoftwareEnvironment(agpVersion, gradleVersion, kotlinVersion, compileSdk, modelVersion)

fun AgpVersionSoftwareEnvironment.withCompileSdk(compileSdk: String?): CustomAgpVersionSoftwareEnvironment =
  CustomAgpVersionSoftwareEnvironment(agpVersion, gradleVersion, kotlinVersion, compileSdk, modelVersion)

