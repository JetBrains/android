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

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.*
import com.intellij.util.ThrowableRunnable
import org.jetbrains.android.AndroidTestCase

class AgpUpgradeComponentNecessityTest : AndroidTestCase() {
  val one = AgpVersion(1, 0, 0)
  val two = AgpVersion(2, 0, 0)
  val three = AgpVersion(3, 0, 0)
  val four = AgpVersion(4, 0, 0)

  fun testPointNecessityReturns() {
    assertEquals(IRRELEVANT_PAST, PointNecessity(one).computeNecessity(one, one))
    assertEquals(IRRELEVANT_FUTURE, PointNecessity(two).computeNecessity(one, one))
    // 1, 1, 3 == 1, 1, 2
    assertEquals(IRRELEVANT_PAST, PointNecessity(one).computeNecessity(one, two))
    assertEquals(MANDATORY_CODEPENDENT, PointNecessity(two).computeNecessity(one, two))
    assertEquals(IRRELEVANT_FUTURE, PointNecessity(three).computeNecessity(one, two))
    // 1, 3, 1 == 1, 2, 1
    assertEquals(MANDATORY_CODEPENDENT, PointNecessity(two).computeNecessity(one, three))
    // 1, 3, 3 == 1, 2, 2
    assertEquals(IRRELEVANT_PAST, PointNecessity(one).computeNecessity(two, two))
    // 2, 2, 2 == 1, 1, 1
    // 2, 2, 3 == 1, 1, 3
    assertEquals(IRRELEVANT_PAST, PointNecessity(one).computeNecessity(two, three))
    // 2, 3, 2 == 1, 2, 1
    // 2, 3, 3 == 1, 3, 3
  }

  fun testRegionNecessityReturnsSameAsPoint() {
    assertEquals(IRRELEVANT_PAST, RegionNecessity(one, one).computeNecessity(one, one))
    assertEquals(IRRELEVANT_FUTURE, RegionNecessity(two, two).computeNecessity(one, one))
    assertEquals(IRRELEVANT_PAST, RegionNecessity(one, one).computeNecessity(one, two))
    assertEquals(MANDATORY_CODEPENDENT, RegionNecessity(two, two).computeNecessity(one, two))
    assertEquals(IRRELEVANT_FUTURE, RegionNecessity(three, three).computeNecessity(one, two))
    assertEquals(MANDATORY_CODEPENDENT, RegionNecessity(two, two).computeNecessity(one, three))
    assertEquals(IRRELEVANT_PAST, RegionNecessity(one, one).computeNecessity(two, two))
    assertEquals(IRRELEVANT_PAST, RegionNecessity(one, one).computeNecessity(two, three))
  }

  fun testRegionNecessityReturns() {
    assertEquals(OPTIONAL_INDEPENDENT, RegionNecessity(one, two).computeNecessity(one, one))
    assertEquals(IRRELEVANT_FUTURE, RegionNecessity(two, three).computeNecessity(one, one))
    assertEquals(MANDATORY_INDEPENDENT, RegionNecessity(one, two).computeNecessity(one, two))
    assertEquals(OPTIONAL_CODEPENDENT, RegionNecessity(two, three).computeNecessity(one, two))
    assertEquals(IRRELEVANT_FUTURE, RegionNecessity(three, four).computeNecessity(one, two))

    assertEquals(OPTIONAL_CODEPENDENT, RegionNecessity(two, four).computeNecessity(one, three))
    assertEquals(MANDATORY_CODEPENDENT, RegionNecessity(two, three).computeNecessity(one, four))

    assertEquals(IRRELEVANT_PAST, RegionNecessity(one, two).computeNecessity(two, two))
    assertEquals(OPTIONAL_INDEPENDENT, RegionNecessity(one, three).computeNecessity(two, two))
    assertEquals(IRRELEVANT_PAST, RegionNecessity(one, two).computeNecessity(two, three))
    assertEquals(MANDATORY_INDEPENDENT, RegionNecessity(one, three).computeNecessity(two, three))
    assertEquals(OPTIONAL_INDEPENDENT, RegionNecessity(one, four).computeNecessity(two, three))

    assertEquals(MANDATORY_INDEPENDENT, RegionNecessity(one, three).computeNecessity(two, four))

    assertEquals(IRRELEVANT_PAST, RegionNecessity(one, two).computeNecessity(three, four))
  }

  fun testStandardPointNecessityThrows() {
    assertThrows(IllegalArgumentException::class.java, { PointNecessity(one).computeNecessity(two, one) })
    assertThrows(IllegalArgumentException::class.java, { PointNecessity(two).computeNecessity(two, one) })
    assertThrows(IllegalArgumentException::class.java, { PointNecessity(three).computeNecessity(two, one) })
    assertThrows(IllegalArgumentException::class.java, { PointNecessity(one).computeNecessity(three, two) })
  }

  fun testStandardRegionNecessityThrows() {
    assertThrows(IllegalArgumentException::class.java, { RegionNecessity(one, two).computeNecessity(two, one) })
    assertThrows(IllegalArgumentException::class.java, { RegionNecessity(two, three).computeNecessity(two, one) })
    assertThrows(IllegalArgumentException::class.java, { RegionNecessity(three, four).computeNecessity(two, one) })
    assertThrows(IllegalArgumentException::class.java, { RegionNecessity(one, two).computeNecessity(three, two) })
    assertThrows(IllegalArgumentException::class.java, { RegionNecessity(one, two).computeNecessity(four, three) })

    assertThrows(IllegalArgumentException::class.java, { RegionNecessity(two, one).computeNecessity(one, two) })
    assertThrows(IllegalArgumentException::class.java, { RegionNecessity(two, one).computeNecessity(two, three) })
    assertThrows(IllegalArgumentException::class.java, { RegionNecessity(two, one).computeNecessity(three, four) })
    assertThrows(IllegalArgumentException::class.java, { RegionNecessity(three, two).computeNecessity(one, two) })
    assertThrows(IllegalArgumentException::class.java, { RegionNecessity(four, three).computeNecessity(one, two) })
  }

  fun testStandardPointNecessityReturnsOrIllegalArgument() {
    listOf(one, two, three).forEach { i ->
      listOf(one, two, three).forEach { j ->
        listOf(one, two, three).forEach { k ->
          val thrower = ThrowableRunnable<Exception> { PointNecessity(k).computeNecessity(i, j); throw IllegalArgumentException() }
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
            val thrower = ThrowableRunnable<Exception> { RegionNecessity(k, l).computeNecessity(i, j); throw IllegalArgumentException() }
            // assert that standardRegionNecessity does not throw anything *other* than an IllegalArgumentException
            assertThrows(IllegalArgumentException::class.java, thrower)
          }
        }
      }
    }
  }
}