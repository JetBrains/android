/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.diagnostics

import com.android.tools.idea.flags.StudioFlags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NlDiagnosticsTest {
  @Before
  fun setUp() {
    StudioFlags.NELE_RENDER_DIAGNOSTICS.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.NELE_RENDER_DIAGNOSTICS.clearOverride()
  }

  @Test
  fun testGetInstance() {
    assertTrue(NlDiagnosticsManager.getWriteInstance(null) === NopNlDiagnosticsImpl)
    assertTrue(NlDiagnosticsManager.getReadInstance(null) === NopNlDiagnosticsImpl)

    val surface1 = object : NlDiagnosticKey {}
    val surface2 = object : NlDiagnosticKey {}
    assertNotEquals(
      NlDiagnosticsManager.getWriteInstance(surface1),
      NlDiagnosticsManager.getWriteInstance(surface2),
    )
    assertNotEquals(
      NlDiagnosticsManager.getReadInstance(surface1),
      NlDiagnosticsManager.getReadInstance(surface2),
    )
  }

  @Test
  fun testRecording() {
    val surface = object : NlDiagnosticKey {}
    val write = NlDiagnosticsManager.getWriteInstance(surface)
    val read = NlDiagnosticsManager.getReadInstance(surface)

    assertEquals(-1, read.lastRenderImageSize())
    assertEquals(-1, read.renderTime(90))
    assertTrue(read.lastRenders().isEmpty())

    write.recordRender(100, 500)
    assertEquals(500, read.lastRenderImageSize())
    assertEquals(100, read.renderTime(90))
    assertEquals(1, read.lastRenders().size)
    write.recordRender(101, 501)
    assertEquals(501, read.lastRenderImageSize())
    assertEquals(2, read.lastRenders().size)
    assertEquals(100, read.lastRenders()[0])
    assertEquals(101, read.lastRenders()[1])
  }
}
