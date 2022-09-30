/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.*
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.Companion.standardPointNecessity
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.Companion.standardRegionNecessity
import com.intellij.util.ThrowableRunnable
import org.jetbrains.android.AndroidTestCase

class AgpUpgradeComponentNecessityTest : AndroidTestCase() {
  val one = AgpVersion(1, 0, 0)
  val two = AgpVersion(2, 0, 0)
  val three = AgpVersion(3, 0, 0)
  val four = AgpVersion(4, 0, 0)

  fun testStandardPointNecessityReturns() {
    assertEquals(IRRELEVANT_PAST, standardPointNecessity(one, one, one))
    assertEquals(IRRELEVANT_FUTURE, standardPointNecessity(one, one, two))
    // 1, 1, 3 == 1, 1, 2
    assertEquals(IRRELEVANT_PAST, standardPointNecessity(one, two, one))
    assertEquals(MANDATORY_CODEPENDENT, standardPointNecessity(one, two, two))
    assertEquals(IRRELEVANT_FUTURE, standardPointNecessity(one, two, three))
    // 1, 3, 1 == 1, 2, 1
    assertEquals(MANDATORY_CODEPENDENT, standardPointNecessity(one, three, two))
    // 1, 3, 3 == 1, 2, 2
    assertEquals(IRRELEVANT_PAST, standardPointNecessity(two, two, one))
    // 2, 2, 2 == 1, 1, 1
    // 2, 2, 3 == 1, 1, 3
    assertEquals(IRRELEVANT_PAST, standardPointNecessity(two, three, one))
    // 2, 3, 2 == 1, 2, 1
    // 2, 3, 3 == 1, 3, 3
  }

  fun testRegionNecessityReturnsSameAsPoint() {
    assertEquals(IRRELEVANT_PAST, standardRegionNecessity(one, one, one, one))
    assertEquals(IRRELEVANT_FUTURE, standardRegionNecessity(one, one, two, two))
    assertEquals(IRRELEVANT_PAST, standardRegionNecessity(one, two, one, one))
    assertEquals(MANDATORY_CODEPENDENT, standardRegionNecessity(one, two, two, two))
    assertEquals(IRRELEVANT_FUTURE, standardRegionNecessity(one, two, three, three))
    assertEquals(MANDATORY_CODEPENDENT, standardRegionNecessity(one, three, two, two))
    assertEquals(IRRELEVANT_PAST, standardRegionNecessity(two, two, one, one))
    assertEquals(IRRELEVANT_PAST, standardRegionNecessity(two, three, one, one))
  }

  fun testRegionNecessityReturns() {
    assertEquals(OPTIONAL_INDEPENDENT, standardRegionNecessity(one, one, one, two))
    assertEquals(IRRELEVANT_FUTURE, standardRegionNecessity(one, one, two, three))
    assertEquals(MANDATORY_INDEPENDENT, standardRegionNecessity(one, two, one, two))
    assertEquals(OPTIONAL_CODEPENDENT, standardRegionNecessity(one, two, two, three))
    assertEquals(IRRELEVANT_FUTURE, standardRegionNecessity(one, two, three, four))

    assertEquals(OPTIONAL_CODEPENDENT, standardRegionNecessity(one, three, two, four))
    assertEquals(MANDATORY_CODEPENDENT, standardRegionNecessity(one, four, two, three))

    assertEquals(IRRELEVANT_PAST, standardRegionNecessity(two, two, one, two))
    assertEquals(OPTIONAL_INDEPENDENT, standardRegionNecessity(two, two, one, three))
    assertEquals(IRRELEVANT_PAST, standardRegionNecessity(two, three, one, two))
    assertEquals(MANDATORY_INDEPENDENT, standardRegionNecessity(two, three, one, three))
    assertEquals(OPTIONAL_INDEPENDENT, standardRegionNecessity(two, three, one, four))

    assertEquals(MANDATORY_INDEPENDENT, standardRegionNecessity(two, four, one, three))

    assertEquals(IRRELEVANT_PAST, standardRegionNecessity(three, four, one, two))
  }

  fun testStandardPointNecessityThrows() {
    assertThrows(IllegalArgumentException::class.java, { standardPointNecessity(two, one, one) })
    assertThrows(IllegalArgumentException::class.java, { standardPointNecessity(two, one, two) })
    assertThrows(IllegalArgumentException::class.java, { standardPointNecessity(two, one, three) })
    assertThrows(IllegalArgumentException::class.java, { standardPointNecessity(three, two, one) })
  }

  fun testStandardRegionNecessityThrows() {
    assertThrows(IllegalArgumentException::class.java, { standardRegionNecessity(two, one, one, two) })
    assertThrows(IllegalArgumentException::class.java, { standardRegionNecessity(two, one, two, three) })
    assertThrows(IllegalArgumentException::class.java, { standardRegionNecessity(two, one, three, four) })
    assertThrows(IllegalArgumentException::class.java, { standardRegionNecessity(three, two, one, two) })
    assertThrows(IllegalArgumentException::class.java, { standardRegionNecessity(four, three, one, two) })

    assertThrows(IllegalArgumentException::class.java, { standardRegionNecessity(one, two, two, one) })
    assertThrows(IllegalArgumentException::class.java, { standardRegionNecessity(two, three, two, one) })
    assertThrows(IllegalArgumentException::class.java, { standardRegionNecessity(three, four, two, one) })
    assertThrows(IllegalArgumentException::class.java, { standardRegionNecessity(one, two, three, two) })
    assertThrows(IllegalArgumentException::class.java, { standardRegionNecessity(one, two, four, three) })
  }

  fun testStandardPointNecessityReturnsOrIllegalArgument() {
    listOf(one, two, three).forEach { i ->
      listOf(one, two, three).forEach { j ->
        listOf(one, two, three).forEach { k ->
          val thrower = ThrowableRunnable<Exception> { standardPointNecessity(i, j, k); throw IllegalArgumentException() }
          // assert that standardPointNecessity does not throw anything *other* than an IllegalArgumentException
          assertThrows(IllegalArgumentException::class.java, thrower)
        }
      }
    }
  }

  fun testStandardRegionNecessityReturnsOrIllegalArgument() {
    listOf(one, two, three, four).forEach { i ->
      listOf(one, two, three, four).forEach { j ->
        listOf(one, two, three, four).forEach { k ->
          listOf(one, two, three, four).forEach { l ->
            val thrower = ThrowableRunnable<Exception> { standardRegionNecessity(i, j, k, l); throw IllegalArgumentException() }
            // assert that standardRegionNecessity does not throw anything *other* than an IllegalArgumentException
            assertThrows(IllegalArgumentException::class.java, thrower)
          }
        }
      }
    }
  }
}