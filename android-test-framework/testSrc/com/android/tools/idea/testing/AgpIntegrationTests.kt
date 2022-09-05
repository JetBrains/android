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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_31
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_33_WITH_5_3_1
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT

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
  val kotlinVersion: String? = null,

  /**
   * Builder model version to query.
   */
  val modelVersion: ModelVersion = ModelVersion.V2
) {
  AGP_31("3.1.4", gradleVersion = "5.3.1", kotlinVersion = "1.4.32", modelVersion = ModelVersion.V1),
  AGP_33_WITH_5_3_1("3.3.2", gradleVersion = "5.3.1", kotlinVersion = "1.4.32", modelVersion = ModelVersion.V1),
  AGP_33("3.3.2", gradleVersion = "5.5", kotlinVersion = "1.4.32", modelVersion = ModelVersion.V1),
  AGP_35("3.5.0", gradleVersion = "5.5", kotlinVersion = "1.4.32", modelVersion = ModelVersion.V1),
  AGP_40("4.0.0", gradleVersion = "6.7.1", modelVersion = ModelVersion.V1),
  AGP_41("4.1.0", gradleVersion = "6.7.1", modelVersion = ModelVersion.V1),
  AGP_42("4.2.0", gradleVersion = "6.7.1", modelVersion = ModelVersion.V1),
  AGP_70("7.0.0", gradleVersion = "7.0.2", modelVersion = ModelVersion.V1),
  AGP_71("7.1.0", gradleVersion = "7.2", modelVersion = ModelVersion.V1),
  AGP_72_V1("7.2.0", gradleVersion = "7.3.3", modelVersion = ModelVersion.V1),
  AGP_72("7.2.0", gradleVersion = "7.3.3", modelVersion = ModelVersion.V2),
  AGP_73("7.3.0-rc01", gradleVersion = "7.4", modelVersion = ModelVersion.V2),
  AGP_74("7.4.0-alpha09", gradleVersion = "7.5", modelVersion = ModelVersion.V2),
  // Must be last to represent the newest version.
  AGP_80_V1(null, gradleVersion = null, modelVersion = ModelVersion.V1),
  AGP_80(null, gradleVersion = null, modelVersion = ModelVersion.V2);

  override fun toString(): String {
    return "Agp($agpVersion, g=$gradleVersion, k=$kotlinVersion, m=$modelVersion)"
  }
  companion object {
    val AGP_CURRENT_V1 = AGP_80_V1
    val AGP_CURRENT = AGP_80
    val selected: AgpVersionSoftwareEnvironmentDescriptor
      get() {
        if (OldAgpSuite.AGP_VERSION == null && OldAgpSuite.GRADLE_VERSION == null) return AGP_CURRENT
        val applicableAgpVersions = applicableAgpVersions()
        return applicableAgpVersions.singleOrNull()
          ?: error("Multiple AGP versions selected: $applicableAgpVersions. A parameterised test is required.")
      }
  }
}

enum class ModelVersion {
  V1,
  V2;

  companion object {
    val selected: ModelVersion get() = if (StudioFlags.GRADLE_SYNC_USE_V2_MODEL.get()) V2 else V1
  }
}

interface AgpIntegrationTestDefinition {
  val name: String
  val agpVersion: AgpVersionSoftwareEnvironmentDescriptor
  fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): AgpIntegrationTestDefinition
  fun displayName(): String = "$name${if (agpVersion != AGP_CURRENT) "-${agpVersion}" else ""}"
  fun isCompatible(): Boolean = agpVersion > AGP_33_WITH_5_3_1 /* Not supported special cases */
}

/**
 * Applies AGP versions selected for testing in the current test target to the list of test definitions.
 */
fun List<AgpIntegrationTestDefinition>.applySelectedAgpVersions(): List<AgpIntegrationTestDefinition> =
  applicableAgpVersions()
    .flatMap { version -> map { it.withAgpVersion(version) } }
    .filter { it.isCompatible() }
    .sortedWith(compareBy({ it.agpVersion.gradleVersion }, { it.agpVersion.agpVersion }))

fun applicableAgpVersions() = AgpVersionSoftwareEnvironmentDescriptor.values()
  .filter {
    val pass = (OldAgpSuite.AGP_VERSION == null || (it.agpVersion ?: "LATEST") == OldAgpSuite.AGP_VERSION) &&
      (OldAgpSuite.GRADLE_VERSION == null || (it.gradleVersion ?: "LATEST") == OldAgpSuite.GRADLE_VERSION)
    println("${it.name}($it) : $pass")
    pass
  }

/**
 * Prints a message describing the currently running test to the standard output.
 */
fun IntegrationTestEnvironment.outputCurrentlyRunningTest(testDefinition: AgpIntegrationTestDefinition) {
  println("Testing: ${this.javaClass.simpleName}[${testDefinition.displayName()}]")
}
