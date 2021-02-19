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

import com.intellij.testFramework.UsefulTestCase.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Test

internal class ModuleClassLoadedDiagnosticsImplTest {
  @Test
  fun testCounters() {
    val diagnostics = ModuleClassLoadedDiagnosticsImpl()
    diagnostics.classFindStart("A")
    diagnostics.classFindEnd("A", true, 1000)
    diagnostics.classRewritten("A", 50, 1000)
    diagnostics.classFindStart("B")
    diagnostics.classFindEnd("B", true, 300)
    diagnostics.classRewritten("B", 50, 200)
    diagnostics.classFindStart("C")
    diagnostics.classFindEnd("C", true, 500)

    assertEquals(3, diagnostics.classesFound)
    assertEquals(1800, diagnostics.accumulatedFindTimeMs)
    assertEquals(1200, diagnostics.accumulatedRewriteTimeMs)
  }

  @Test
  fun testHierarchicalTimeCounter() {
    /**
     * Simple extension to allow adding counters without any children.
     */
    fun HierarchicalTimeCounter.add(value: Long) {
      start("")
      end("", value)
    }

    val counter = HierarchicalTimeCounter()
    counter.start("A")
      counter.start("B")
        counter.add(100L)
        counter.add(100L)
      assertEquals("Self time is expected to be 100ms", 100L, counter.end("B", 300L))
    counter.add(100L)
    counter.add(100L)
    assertEquals(400, counter.end("A", 900L))
  }

  @Test
  fun testHierarchicalTimeCounterUnmatchedEntry() {
    val counter = HierarchicalTimeCounter()
    counter.start("A")
    counter.start("B")
    assertThrows<IllegalStateException>(IllegalStateException::class.java) {
      counter.end("A", 100L)
    }
  }
}