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
package com.android.tools.idea.templates.diff

import com.android.testutils.TestUtils
import com.android.tools.idea.templates.SDK_VERSION_FOR_TEMPLATE_TESTS
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironment
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.withCompileSdk
import com.android.tools.idea.testing.withTargetSdk
import java.nio.file.Path
import java.nio.file.Paths

object TemplateDiffTestUtils {
  /** Gets the path where golden files are stored */
  internal fun getTestDataRoot(): Path {
    return TestUtils.resolveWorkspacePath("tools/adt/idea/android-templates/testData")
  }

  /**
   * Gets the Android Gradle Plugin version that we should use in the template-generated files
   *
   * TODO: extend this to more versions
   */
  internal fun getPinnedAgpVersion(): AgpVersionSoftwareEnvironment {
    return AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT.withCompileSdk(
        SDK_VERSION_FOR_TEMPLATE_TESTS.toString()
      )
      .withTargetSdk(SDK_VERSION_FOR_TEMPLATE_TESTS.toString())
  }

  /**
   * Whether to use smart diff for the AGP version string found in a couple template-generated
   * files, usually properties files. This is because the version numbers change frequently (~ every
   * week) on the latest major AGP version in development, so we want to be able to diff the other
   * file contents without having to update golden files every week just for this version string.
   */
  internal fun smartDiffAgpVersion(): Boolean {
    return getPinnedAgpVersion().agpVersion == AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT.agpVersion
  }

  /**
   * Gets the output directory where we should put generated files. Bazel places files in
   * TEST_UNDECLARED_OUTPUTS_DIR which produces outputs.zip as a test artifact, so we can put any
   * files there. We use a "golden" subdirectory in the zip for the template-generated golden files
   * and a "lintBaseline" subdirectory for the Lint baseline XMLs.
   */
  internal fun getOutputDir(subDirName: String): Path {
    val undeclaredOutputs = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")
    checkNotNull(undeclaredOutputs) {
      "The 'TEST_UNDECLARED_OUTPUTS_DIR' env. variable should already be set, because TemplateDiffTest#setUp checks for it"
    }

    return Paths.get(undeclaredOutputs).resolve(subDirName)
  }
}
