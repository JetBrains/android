/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.npw

import com.android.ide.common.repository.AgpVersion
import kotlin.test.assertEquals
import org.junit.Test

class NpwUiUtilsTest {

  @Test
  fun getMinimumAgpVersionForTestSuiteSupport_returns_9_0_0_whenVersionIsLatestKnown() {
    assertEquals("9.0.0", getMinimumAgpVersionForTestSuiteSupport(AgpVersion.parse("9.0.0")))
  }

  @Test
  fun getMinimumAgpVersionForTestSuiteSupport_returns_9_0_0_whenNewerVersionAvailable() {
    assertEquals("9.0.0", getMinimumAgpVersionForTestSuiteSupport(AgpVersion.parse("9.0.1")))
  }

  @Test
  fun getMinimumAgpVersionForTestSuiteSupport_returnsBetaVersion_ifLatestVersionIsBeta() {
    assertEquals(
      "9.0.0-beta05",
      getMinimumAgpVersionForTestSuiteSupport(AgpVersion.parse("9.0.0-beta05")),
    )
  }

  @Test
  fun getMinimumAgpVersionForTestSuiteSupport_returnsDefaultAlphaVersion_ifLatestVersionIsOlder() {
    assertEquals(
      "9.0.0-beta05",
      getMinimumAgpVersionForTestSuiteSupport(AgpVersion.parse("8.13.0")),
    )
  }
}
