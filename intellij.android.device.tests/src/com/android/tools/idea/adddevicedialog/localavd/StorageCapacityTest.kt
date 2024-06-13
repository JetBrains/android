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
package com.android.tools.idea.adddevicedialog.localavd

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class StorageCapacityTest {
  @Test
  fun withMaxUnit2048Kilobytes() {
    // Arrange
    val capacity = StorageCapacity(2_048, StorageCapacity.Unit.KB)

    // Act
    val capacityWithMaxUnit = capacity.withMaxUnit()

    // Assert
    assertEquals(StorageCapacity(2, StorageCapacity.Unit.MB), capacityWithMaxUnit)
  }

  @Test
  fun withMaxUnit2049Kilobytes() {
    // Arrange
    val capacity = StorageCapacity(2_049, StorageCapacity.Unit.KB)

    // Act
    val capacityWithMaxUnit = capacity.withMaxUnit()

    // Assert
    assertEquals(capacity, capacityWithMaxUnit)
  }

  @Test
  fun valueInBytes() {
    // Act
    val bytes = CAPACITY.valueIn(StorageCapacity.Unit.B)

    // Assert
    assertEquals(2_147_483_648, bytes)
  }

  @Test
  fun valueInKilobytes() {
    // Act
    val kilobytes = CAPACITY.valueIn(StorageCapacity.Unit.KB)

    // Assert
    assertEquals(2_097_152, kilobytes)
  }

  @Test
  fun valueInMegabytes() {
    // Act
    val megabytes = CAPACITY.valueIn(StorageCapacity.Unit.MB)

    // Assert
    assertEquals(2_048, megabytes)
  }

  @Test
  fun valueInGigabytes() {
    // Act
    val gigabytes = CAPACITY.valueIn(StorageCapacity.Unit.GB)

    // Assert
    assertEquals(2, gigabytes)
  }

  @Test
  fun valueInTerabytes() {
    // Act
    val terabytes = CAPACITY.valueIn(StorageCapacity.Unit.TB)

    // Assert
    assertEquals(0, terabytes)
  }

  @Test
  fun testToString() {
    // Act
    val string = CAPACITY.toString()

    // Assert
    assertEquals("2048M", string)
  }
}

private val CAPACITY = StorageCapacity(2_048, StorageCapacity.Unit.MB)
