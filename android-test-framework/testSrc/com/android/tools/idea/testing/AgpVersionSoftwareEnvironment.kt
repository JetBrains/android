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
@file:JvmName("AgpVersionSoftwareEnvironmentUtil")

package com.android.tools.idea.testing

import com.android.SdkConstants
import com.android.testutils.TestUtils.KOTLIN_VERSION_FOR_TESTS
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk

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
   * The version of the JDK to launch Gradle with. `null` means the current version used by the IDE.
   */
  val jdkVersion: JavaSdkVersion?

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

/**
 * [AgpVersionSoftwareEnvironment] with all versions resolved.
 */
interface ResolvedAgpVersionSoftwareEnvironment : AgpVersionSoftwareEnvironment {
  /**
   * The version of the JDK to launch Gradle with.
   */
  override val jdkVersion: JavaSdkVersion

  /**
   * The version of the AGP.
   */
  override val agpVersion: String

  /**
   * The version of Gradle to be used in integration tests for this AGP version.
   */
  override val gradleVersion: String

  /**
   * The version of the Gradle Kotlin plugin to be used in integration tests for this AGP version.
   */
  override val kotlinVersion: String

  /**
   * The compileSdk to use in this test. `null` means the project default.
   */
  override val compileSdk: String
}

data class CustomAgpVersionSoftwareEnvironment @JvmOverloads constructor(
  override val agpVersion: String?,
  override val gradleVersion: String?,
  override val jdkVersion: JavaSdkVersion? = null,
  override val kotlinVersion: String? = null,
  override val compileSdk: String? = null,
  override val modelVersion: ModelVersion = ModelVersion.V2
) : AgpVersionSoftwareEnvironment

fun AgpVersionSoftwareEnvironment.withJdk(jdkVersion: JavaSdkVersion?): CustomAgpVersionSoftwareEnvironment =
  CustomAgpVersionSoftwareEnvironment(agpVersion, gradleVersion, jdkVersion, kotlinVersion, compileSdk, modelVersion)

fun AgpVersionSoftwareEnvironment.withGradle(gradleVersion: String?): CustomAgpVersionSoftwareEnvironment =
  CustomAgpVersionSoftwareEnvironment(agpVersion, gradleVersion, jdkVersion, kotlinVersion, compileSdk, modelVersion)

fun AgpVersionSoftwareEnvironment.withKotlin(kotlinVersion: String?): CustomAgpVersionSoftwareEnvironment =
  CustomAgpVersionSoftwareEnvironment(agpVersion, gradleVersion, jdkVersion, kotlinVersion, compileSdk, modelVersion)

fun AgpVersionSoftwareEnvironment.withCompileSdk(compileSdk: String?): CustomAgpVersionSoftwareEnvironment =
  CustomAgpVersionSoftwareEnvironment(agpVersion, gradleVersion, jdkVersion, kotlinVersion, compileSdk, modelVersion)

@JvmName("resolveAgpVersionSoftwareEnvironment")
fun AgpVersionSoftwareEnvironment.resolve(): ResolvedAgpVersionSoftwareEnvironment {
  val buildEnvironment = BuildEnvironment.getInstance()

  val jdkVersion: JavaSdkVersion? = jdkVersion
  val gradleVersion: String? = gradleVersion
  val gradlePluginVersion: String? = agpVersion
  val kotlinVersion: String? = kotlinVersion
  val compileSdk: String? = compileSdk
  val modelVersion: ModelVersion = modelVersion

  val ideSdksJdk = IdeSdks.getInstance().jdk ?: error("IdeSdks.jdk is null")
  val resolvedJdkVersion = jdkVersion
    ?: ideSdksJdk.getJdkVersion() ?: error("Cannot obtain the JDK version of $ideSdksJdk")

  return object : ResolvedAgpVersionSoftwareEnvironment {
    override val agpVersion: String = gradlePluginVersion ?: buildEnvironment.gradlePluginVersion
    override val gradleVersion: String = gradleVersion ?: SdkConstants.GRADLE_LATEST_VERSION
    override val jdkVersion: JavaSdkVersion = resolvedJdkVersion
    override val kotlinVersion: String = kotlinVersion ?: KOTLIN_VERSION_FOR_TESTS
    override val compileSdk: String = compileSdk ?: buildEnvironment.compileSdkVersion
    override val modelVersion: ModelVersion = modelVersion
  }
}

fun Sdk.getJdkVersion(): JavaSdkVersion? = JavaSdk.getInstance().getVersion(this)