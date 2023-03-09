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
package com.android.gmdcodecompletion.ftl

import com.android.gmdcodecompletion.freshFtlDeviceCatalog
import com.android.gmdcodecompletion.freshFtlDeviceCatalogState
import com.android.gmdcodecompletion.fullAndroidDeviceCatalog
import com.android.gmdcodecompletion.matchFtlDeviceCatalog
import junit.framework.Assert.assertFalse
import junit.framework.Assert.fail
import junit.framework.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FtlDeviceCatalogStateTest {

  @Test
  fun testEmptyFtlDeviceCatalogState() {
    assertFalse(FtlDeviceCatalogState().isCacheFresh())
  }

  @Test
  fun testFtlDeviceCatalogStateOutdated() {
    assertFalse(FtlDeviceCatalogState(Calendar.getInstance().time).isCacheFresh())
  }

  @Test
  fun testFtlDeviceCatalogStateEmptyDeviceCatalog() {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DATE, 1)
    assertFalse(FtlDeviceCatalogState(calendar.time).isCacheFresh())
  }

  @Test
  fun testFtlDeviceCatalogStateIsFresh() {
    assertTrue(freshFtlDeviceCatalogState().isCacheFresh())
  }

  @Test
  fun testLocalFtlDeviceCatalogConverter() {
    val converter = FtlDeviceCatalogState.FtlDeviceCatalogConverter()
    try {
      val testFtlDeviceCatalog = freshFtlDeviceCatalog()
      val serializedString = converter.toString(testFtlDeviceCatalog)
      val deserializedDeviceCatalog = converter.fromString(serializedString)
      // If there are serialization issues it will throw error before reaching assertTrue
      assertTrue(deserializedDeviceCatalog.isEmpty() == testFtlDeviceCatalog.isEmpty())
      assertTrue(matchFtlDeviceCatalog(deserializedDeviceCatalog, fullAndroidDeviceCatalog))
    }
    catch (e: Exception) {
      fail("FtlDeviceCatalog fails to serialize / deserialize")
    }
  }
}