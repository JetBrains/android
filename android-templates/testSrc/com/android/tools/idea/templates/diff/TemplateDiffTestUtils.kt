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

import com.android.test.testutils.TestUtils
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import java.nio.file.Path

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
  internal fun getPinnedAgpVersion(): AgpVersionSoftwareEnvironmentDescriptor {
    return AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
  }

  /**
   * Whether to use smart diff for the AGP version string found in a couple template-generated
   * files, usually properties files. This is because the version numbers change frequently (~ every
   * week) on the latest major AGP version in development, so we want to be able to diff the other
   * file contents without having to update golden files every week just for this version string.
   */
  internal fun smartDiffAgpVersion(): Boolean {
    return getPinnedAgpVersion() == AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
  }
}
