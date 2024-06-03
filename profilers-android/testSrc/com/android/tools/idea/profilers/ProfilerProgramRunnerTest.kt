/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProfilerProgramRunnerTest {

  @Test
  fun `test buildProfileableRequirementMessage`() {
    for (projectSupported in listOf(false, true)) {
      for (apiLevelSupported in listOf(false, true)) {
        for (systemSupported in listOf(false, true)) {
          if (projectSupported && apiLevelSupported && systemSupported) continue
          verifyProfileableRequirementMessage(projectSupported, apiLevelSupported, systemSupported)
        }
      }
    }
  }

  @Test(expected = java.lang.AssertionError::class)
  fun `test buildProfileableRequirementMessage should not be called if all criteria are met`() {
    verifyProfileableRequirementMessage(true, true, true)
  }

  private fun verifyProfileableRequirementMessage(isProjectSupported: Boolean, isApiLevelSupported: Boolean, isSystemSupported: Boolean) {
    val actual = ProfilerProgramRunner.buildProfileableRequirementMessage(isProjectSupported, isApiLevelSupported, isSystemSupported)

    if (isProjectSupported)  {assertThat(actual).doesNotContain("Gradle")}
    else {assertThat(actual).contains("Gradle")}
    if (isApiLevelSupported) assertThat(actual).doesNotContain("API")
    else assertThat(actual).contains("API")
    if (isSystemSupported) assertThat(actual).doesNotContain("a system that is not debuggable")
    else assertThat(actual).contains("a system that is not debuggable")

    assertThat(actual).startsWith("<html>“Run as profileable (low overhead)” is not available because it requires")
    assertThat(actual).endsWith("<br><br>To proceed, either choose a device or emulator that meets the requirements above or run the " +
                                "app with \"Profiler: Run as debuggable (complete data)\". " +
                                "<a href=\"https://d.android.com/r/studio-ui/profiler/profileable\">More Info</a></html>")
  }
}
