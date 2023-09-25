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
package com.android.testutils.junit4

import com.google.common.truth.Truth
import org.junit.Test

class OldAgpTestTargetsCheckerTest {
  @Test
  fun testGeneratedList() {
    val testInstances = OldAgpTestTargetsChecker.parseAllAnnotatedLocations(
      setOf(
        OldAgpSuiteTest.AgpTestMultiple::class.java,
        OldAgpSuiteTest.OverrideAgpTest::class.java,
        OldAgpSuiteTest.MethodOnly::class.java,
        OldAgpSuiteTest.InvalidAnnotation::class.java,
        OldAgpSuiteTest.MissingAnnotation::class.java,
        OldAgpSuiteTest.MissingVersions::class.java,
        OldAgpSuiteTest.ParametrizedAgpTest::class.java,
      )
    )
    Truth.assertThat(testInstances.toTestString()).isEqualTo(mapOf(
      OldAgpTestTargetsChecker.OldAgpTestVersionsPair("4.1", "4.1") to listOf(
        "com.android.testutils.junit4.OldAgpSuiteTest\$OverrideAgpTest"
      ),
      OldAgpTestTargetsChecker.OldAgpTestVersionsPair("4.1", "4.2") to listOf(
        "com.android.testutils.junit4.OldAgpSuiteTest\$OverrideAgpTest",
        "com.android.testutils.junit4.OldAgpSuiteTest\$OverrideAgpTest#shouldRunGradleOnly42"
      ),
      OldAgpTestTargetsChecker.OldAgpTestVersionsPair("4.2", "4.1") to listOf(
        "com.android.testutils.junit4.OldAgpSuiteTest\$AgpTestMultiple",
        "com.android.testutils.junit4.OldAgpSuiteTest\$OverrideAgpTest",
        "com.android.testutils.junit4.OldAgpSuiteTest\$OverrideAgpTest#shouldRunAgpOnly42"
      ),
      OldAgpTestTargetsChecker.OldAgpTestVersionsPair("4.2", "4.2") to listOf(
        "com.android.testutils.junit4.OldAgpSuiteTest\$AgpTestMultiple",
        "com.android.testutils.junit4.OldAgpSuiteTest\$OverrideAgpTest",
        "com.android.testutils.junit4.OldAgpSuiteTest\$OverrideAgpTest#shouldRunGradleOnly42",
        "com.android.testutils.junit4.OldAgpSuiteTest\$OverrideAgpTest#shouldRunAgpOnly42",
        "com.android.testutils.junit4.OldAgpSuiteTest\$MethodOnly#shouldRun"
      ),
    ).toTestString())
  }
}

private fun Map<OldAgpTestTargetsChecker.OldAgpTestVersionsPair, List<String>>.toTestString() = toList()
  .sortedBy { it.first.toString() }
  .map { pair ->
  "${pair.first}:\n${pair.second.sorted().joinToString(separator = "\n") { " $it" }}"
}.joinToString(separator = "\n")
