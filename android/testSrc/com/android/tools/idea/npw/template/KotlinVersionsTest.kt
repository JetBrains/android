/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.template

import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertWithMessage
import org.jetbrains.kotlin.idea.versions.bundledRuntimeVersion
import org.junit.Test

class KotlinVersionsTest {
  @Test
  fun testKotlinVersionConsistentWithOtherTests() {
    // Conversion code should get its Kotlin version from the Kotlin IDE plugin, while most tests (including gradle integration tests) read
    // the version from the compiler prebuilt. These two should be in sync, so we know we tested everything against the right version.
    assertWithMessage("Kotlin version used for testing is not the same as the one used by the IDE.")
      .that(bundledRuntimeVersion())
      .isEqualTo(TestUtils.getKotlinVersionForTests())
  }
}
