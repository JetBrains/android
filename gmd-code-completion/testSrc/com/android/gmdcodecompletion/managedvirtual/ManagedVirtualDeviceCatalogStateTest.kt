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
package com.android.gmdcodecompletion.managedvirtual

import com.android.gmdcodecompletion.freshFtlDeviceCatalogState
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import junit.framework.Assert.fail
import org.junit.Test
import java.util.Calendar

class ManagedVirtualDeviceCatalogStateTest {
  @Test
  fun testEmptyManagedVirtualDeviceCatalogState() {
    assertFalse(ManagedVirtualDeviceCatalogState().isCacheFresh())
  }

  @Test
  fun testManagedVirtualDeviceCatalogStateOutdated() {
    assertFalse(ManagedVirtualDeviceCatalogState(Calendar.getInstance().time,
                                                 ManagedVirtualDeviceCatalog().syncDeviceCatalog()).isCacheFresh())
  }

  @Test
  fun testManagedVirtualDeviceCatalogStateEmptyDeviceCatalog() {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DATE, 1)
    assertFalse(ManagedVirtualDeviceCatalogState(calendar.time, ManagedVirtualDeviceCatalog()).isCacheFresh())
  }

  @Test
  fun testFtlDeviceCatalogStateIsFresh() {
    assertTrue(freshFtlDeviceCatalogState().isCacheFresh())
  }

  @Test
  fun testManagedVirtualDeviceCatalogConverter() {
    val converter = ManagedVirtualDeviceCatalogState.ManagedVirtualDeviceCatalogConverter()
    try {
      val testManagedVirtualDeviceCatalog = ManagedVirtualDeviceCatalog().syncDeviceCatalog()
      val serializedString = converter.toString(testManagedVirtualDeviceCatalog)
      val deserializedDeviceCatalog = converter.fromString(serializedString)
      // If there are serialization issues it will throw error before reaching assertTrue
      assertTrue(deserializedDeviceCatalog.isEmpty() == testManagedVirtualDeviceCatalog.isEmpty())
      assertTrue(deserializedDeviceCatalog.devices == testManagedVirtualDeviceCatalog.devices)
      assertTrue(deserializedDeviceCatalog.apiLevels == testManagedVirtualDeviceCatalog.apiLevels)
      assertTrue(deserializedDeviceCatalog.orientation == testManagedVirtualDeviceCatalog.orientation)
    }
    catch (e: Exception) {
      fail("ManagedVirtualDeviceCatalog fails to serialize / deserialize")
    }
  }
}