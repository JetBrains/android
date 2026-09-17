/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.util

import com.android.ide.common.repository.AgpVersion
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class KotlinGradleProjectSystemUtilTest {

  @Test
  fun testAgpVersionGetKotlinVersion() {
    assertNull(AgpVersion.parse("8.0.0").getKotlinVersion())
    assertEquals("2.2.10", AgpVersion.parse("9.0.0").getKotlinVersion())
    assertEquals(AGP_BUILT_IN_KOTLIN_VERSION, AgpVersion.parse("100.0.0").getKotlinVersion())
  }
}
