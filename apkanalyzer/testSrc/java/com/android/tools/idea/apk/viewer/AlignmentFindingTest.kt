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
package com.android.tools.idea.apk.viewer

import com.android.tools.idea.apk.viewer.pagealign.getAlignmentFinding
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.android.tools.apk.analyzer.ZipEntryInfo.Alignment

private const val PAGE_ALIGN_2B = 2L
private const val PAGE_ALIGN_4KB = 4L * 1024
private const val PAGE_ALIGN_16KB = 16L * 1024
private const val PAGE_ALIGN_32KB = 32L * 1024

class AlignmentFindingTest {
   @Test
   fun `ELF that supports 16 KB device`() {
     val result = getAlignmentFinding(
       path = "/path/to/my.so",
       elfMinimumLoadSectionAlignment = PAGE_ALIGN_16KB,
       selfOrChildLoadSectionIncompatible = false,
       zipAlignment = Alignment.ALIGNMENT_16K)
     assertEquals("16 KB", result.text)
     assertFalse(result.hasWarning)
   }

  @Test
  fun `ELF with 4 KB LOAD section and it's a problem`() {
    val result = getAlignmentFinding(
      path = "/path/to/my.so",
      elfMinimumLoadSectionAlignment = PAGE_ALIGN_4KB,
      selfOrChildLoadSectionIncompatible = true,
      zipAlignment = Alignment.ALIGNMENT_16K)
    assertEquals("4 KB LOAD section alignment, but 16 KB is required", result.text)
    assertTrue(result.hasWarning)
  }

  @Test
  fun `ELF with 4 KB zip alignment and it's a problem`() {
    val result = getAlignmentFinding(
      path = "/path/to/my.so",
      elfMinimumLoadSectionAlignment = PAGE_ALIGN_16KB,
      selfOrChildLoadSectionIncompatible = true,
      zipAlignment = Alignment.ALIGNMENT_4K)
    assertEquals("4 KB zip alignment, but 16 KB is required", result.text)
    assertTrue(result.hasWarning)
  }

  @Test
  fun `lib folder with ELF with 4 KB LOAD section and it's a problem`() {
    val result = getAlignmentFinding(
      path = "/lib",
      elfMinimumLoadSectionAlignment = -1L,
      selfOrChildLoadSectionIncompatible = true,
      zipAlignment = Alignment.ALIGNMENT_NONE)
    assertEquals("", result.text)
    assertFalse(result.hasWarning)
  }

  // For example, 4 KB LOAD section is not a problem on 32 bit devices
  @Test
  fun `ELF that with 4 KB LOAD section and it's not a problem`() {
    val result = getAlignmentFinding(
      path = "/path/to/my.so",
      elfMinimumLoadSectionAlignment = PAGE_ALIGN_4KB,
      selfOrChildLoadSectionIncompatible = false,
      zipAlignment = Alignment.ALIGNMENT_16K)
    assertEquals("16 KB zip|4 KB LOAD section", result.text)
    assertFalse(result.hasWarning)
  }

  @Test
  fun `root folder with alignment problem`() {
    val result = getAlignmentFinding(
      path = "/",
      elfMinimumLoadSectionAlignment = -1,
      selfOrChildLoadSectionIncompatible = true,
      zipAlignment = Alignment.ALIGNMENT_NONE)
    assertEquals("APK does not support 16 KB devices", result.text)
    assertTrue(result.hasWarning)
  }

  @Test
  fun `ABI folder with alignment problem`() {
    val result = getAlignmentFinding(
      path = "/lib/arm64-v8a",
      elfMinimumLoadSectionAlignment = -1,
      selfOrChildLoadSectionIncompatible = true,
      zipAlignment = Alignment.ALIGNMENT_NONE)
    assertEquals("", result.text)
    assertFalse(result.hasWarning)
  }

  @Test
  fun `4 KB zip alignment with 16 KB page alignment`() {
    val result = getAlignmentFinding(
      path = "/path/to/my.so",
      elfMinimumLoadSectionAlignment = PAGE_ALIGN_16KB,
      selfOrChildLoadSectionIncompatible = false,
      zipAlignment = Alignment.ALIGNMENT_4K)
    assertEquals("4 KB zip|16 KB LOAD section", result.text)
    assertFalse(result.hasWarning)
  }

  @Test
  fun `typical 32-bit library`() {
    val result = getAlignmentFinding(
      path = "/path/to/my.so",
      elfMinimumLoadSectionAlignment = -1,
      selfOrChildLoadSectionIncompatible = false,
      zipAlignment = Alignment.ALIGNMENT_4K)
    assertEquals("4 KB", result.text)
    assertFalse(result.hasWarning)
  }

  // -z,max-page-size flag only constrains the page size to a power of 2
  @Test
  fun `page size is less than kilo`() {
    val result = getAlignmentFinding(
      path = "/path/to/my.so",
      elfMinimumLoadSectionAlignment = PAGE_ALIGN_2B,
      selfOrChildLoadSectionIncompatible = false,
      zipAlignment = Alignment.ALIGNMENT_16K)
    assertEquals("16 KB zip|2 B LOAD section", result.text)
    assertFalse(result.hasWarning)
  }

  @Test
  fun `both zip align and LOAD align are too low`() {
    val result = getAlignmentFinding(
      path = "/path/to/my.so",
      elfMinimumLoadSectionAlignment = PAGE_ALIGN_4KB,
      selfOrChildLoadSectionIncompatible = true,
      zipAlignment = Alignment.ALIGNMENT_4K)
    assertEquals("4 KB zip and 4 KB LOAD section, but 16 KB is required for both", result.text)
    assertTrue(result.hasWarning)
  }

  // It shouldn't be possible to have a page alignment but not a zip alignment.
  // Make sure we return something and don't crash.
  @Test
  fun `fuzz found 1`() {
    val result = getAlignmentFinding(
        path = "/lib/arm64-v8a/lib.so",
        elfMinimumLoadSectionAlignment = PAGE_ALIGN_16KB,
        selfOrChildLoadSectionIncompatible = true,
        zipAlignment = Alignment.ALIGNMENT_NONE)
    assertEquals("", result.text)
    assertFalse(result.hasWarning)
  }

  // It shouldn't be possible to have a page alignment but not a zip alignment.
  // Make sure we return something and don't crash.
  @Test
  fun `fuzz found 2`() {
    val result = getAlignmentFinding(
        path = "/",
        elfMinimumLoadSectionAlignment = PAGE_ALIGN_4KB,
        selfOrChildLoadSectionIncompatible = false,
        zipAlignment = Alignment.ALIGNMENT_NONE)
    assertEquals("", result.text)
    assertFalse(result.hasWarning)
  }

  @Test
  fun fuzz() {
    val allowedFieldText = setOf(
        "APK does not support 16 KB devices",
        "",
        "4 KB",
        "16 KB",
        "4 KB zip and 4 KB LOAD section, but 16 KB is required for both",
        "4 KB LOAD section alignment, but 16 KB is required",
        "4 KB zip alignment, but 16 KB is required",
        "4 KB zip|16 KB LOAD section",
        "16 KB zip|4 KB LOAD section",
        "4 KB zip|32 KB LOAD section",
        "16 KB zip|32 KB LOAD section",
      )
    val seen = mutableSetOf<String>()
    for(path in listOf("/", "/lib", "/lib/arm64-v8a", "/lib/arm64-v8a/lib.so", "/random"))
      for(elfMinimumLoadSectionAlignment in listOf(-1L, PAGE_ALIGN_4KB, PAGE_ALIGN_16KB, PAGE_ALIGN_32KB))
        for(selfOrChildLoadSectionIncompatible in listOf(true, false))
          for(zipAlign in Alignment.entries) {
            val result = getAlignmentFinding(
              path,
              elfMinimumLoadSectionAlignment,
              selfOrChildLoadSectionIncompatible,
              zipAlignment = zipAlign)
            seen.add(result.text)
            if (!allowedFieldText.contains(result.text)) {
              error(result.text)
            }
            val lookLikeWarning = result.text.contains("required")
                                  || result.text.contains("does not support")
            if (result.hasWarning && !lookLikeWarning) {
              error(result.text)
            }
            if (!result.hasWarning && lookLikeWarning) {
              error(result.text)
            }
          }
    val unused = allowedFieldText subtract seen
    if (unused.isNotEmpty()) {
      error(unused)
    }
  }
}