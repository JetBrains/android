/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.uipreview

import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * [MockVirtualFile] that allows overriding [MockVirtualFile.getTimeStamp] and [MockVirtualFile.isValid] for testing.
 */
private class TestVirtualFile(name: String, content: String) : MockVirtualFile(name, content) {
  var testTimestamp: Long? = null
  var testIsValid: Boolean? = null

  override fun getTimeStamp(): Long = testTimestamp ?: super.getTimeStamp()

  override fun isValid(): Boolean = testIsValid ?: super.isValid()
}

class ClassModificationTimestampTest {
  private var onDiskTimestamp = 0L

  private fun onDiskTimeStampProvider(@Suppress("UNUSED_PARAMETER") vFile: VirtualFile): Long {
    if (onDiskTimestamp == -1L)
      fail("Unexpected access to disk timestamp.")
    return onDiskTimestamp
  }

  @Test
  fun `check content modification makes the file out of date`() {
    val file = MockVirtualFile("testFile", "content of the file")
    val modificationRecord = ClassModificationTimestamp.fromVirtualFileForTest(file, ::onDiskTimeStampProvider)

    assertTrue(modificationRecord.isUpToDate(file))
    // Do not allow to access disk to check the timestamp anymore since we have enough information in the
    // VirtualFile.
    onDiskTimestamp = -1L
    file.setText("other content")
    assertFalse(modificationRecord.isUpToDate(file))
  }

  @Test
  fun `check virtual file timestamp modification makes the file out of date`() {
    val file = TestVirtualFile("testFile", "content of the file").apply {
      testTimestamp = 123L
    }
    val modificationRecord = ClassModificationTimestamp.fromVirtualFileForTest(file, ::onDiskTimeStampProvider)

    assertTrue(modificationRecord.isUpToDate(file))
    // Do not allow to access disk to check the timestamp anymore since we have enough information in the
    // VirtualFile.
    onDiskTimestamp = -1L
    file.testTimestamp = 124L
    assertFalse(modificationRecord.isUpToDate(file))
  }

  @Test
  fun `check on disk file timestamp modification makes the file out of date`() {
    val file = MockVirtualFile("testFile", "content of the file")
    val modificationRecord = ClassModificationTimestamp.fromVirtualFileForTest(file, ::onDiskTimeStampProvider)

    assertTrue(modificationRecord.isUpToDate(file))
    onDiskTimestamp = 124L
    assertFalse(modificationRecord.isUpToDate(file))
  }

  @Test
  fun `check invalid virtual file the file out of date`() {
    val file = TestVirtualFile("testFile", "content of the file")
    val modificationRecord = ClassModificationTimestamp.fromVirtualFileForTest(file, ::onDiskTimeStampProvider)

    assertTrue(modificationRecord.isUpToDate(file))
    // Do not allow to access disk to check the timestamp anymore since we have enough information in the
    // VirtualFile.
    onDiskTimestamp = -1L
    file.testIsValid = false
    assertFalse(modificationRecord.isUpToDate(file))
  }
}