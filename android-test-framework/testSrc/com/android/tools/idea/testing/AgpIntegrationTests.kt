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
package com.android.tools.idea.testing

import com.android.testutils.junit4.OldAgpSuite

/**
 * An AGP Version definition to be used in AGP integration tests.
 */
enum class AgpVersionSoftwareEnvironmentDescriptor(
  /**
   * The version of the AG. `null` means the current `-dev` version.
   */
  val agpVersion: String?,

  /**
   * The version of Gradle to be used in integration tests for this AGP version. `null` means the latest/default version.
   */
  val gradleVersion: String?,

  /**
   * The version of the Gradle Kotlin plugin to be used in integration tests for this AGP version. `null` means the default version used by
   * Android Studio.
   */
  val kotlinVersion: String? = null
) {
  AGP_CURRENT(null, gradleVersion = null),
  AGP_35("3.5.0", gradleVersion = "5.5", kotlinVersion = "1.4.32"),
  AGP_40("4.0.0", gradleVersion = "6.5"),

  // TODO(b/194469137): Use correct Gradle version.
  AGP_41("4.1.0", gradleVersion = null);

  override fun toString(): String {
    return "Agp($agpVersion, g=$gradleVersion, k=$kotlinVersion)"
  }
}

interface AgpIntegrationTestDefinition<T> {
  val name: String
  val agpVersion: AgpVersionSoftwareEnvironmentDescriptor
  fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): T
  fun displayName(): String = "$name @ $agpVersion]"
}

/**
 * Applies AGP versions selected for testing in the current test target to the list of test definitions.
 */
fun <T : AgpIntegrationTestDefinition<T>> List<T>.applySelectedAgpVersions(): List<T> =
  AgpVersionSoftwareEnvironmentDescriptor.values()
    .filter {
      val pass = (OldAgpSuite.AGP_VERSION == null || (it.agpVersion ?: "LATEST") == OldAgpSuite.AGP_VERSION) &&
                 (OldAgpSuite.GRADLE_VERSION == null || (it.gradleVersion ?: "LATEST") == OldAgpSuite.GRADLE_VERSION)
      println("${it.name}($it) : $pass")
      pass
    }
    .flatMap { version -> map { it.withAgpVersion(version) } }
    .sortedWith(compareBy({ it.agpVersion.gradleVersion }, { it.agpVersion.agpVersion }))

/**
 * Prints a message describing the currently running test to the standard output.
 */
fun GradleIntegrationTest.outputCurrentlyRunningTest(testDefinition: AgpIntegrationTestDefinition<*>) {
  println("Testing: ${this.javaClass.simpleName}.${this.getName()}[${testDefinition.displayName()}]")
}
