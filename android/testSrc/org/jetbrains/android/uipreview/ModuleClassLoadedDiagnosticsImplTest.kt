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

import org.junit.Assert.assertEquals
import org.junit.Test


internal class ModuleClassLoadedDiagnosticsImplTest {
  @Test
  fun testCounters() {
    val diagnostics = ModuleClassLoadedDiagnosticsImpl()
    diagnostics.classFound("A", 1000)
    diagnostics.classRewritten("A", 50, 1000)
    diagnostics.classFound("B", 300)
    diagnostics.classRewritten("B", 50, 200)
    diagnostics.classFound("C", 500)

    assertEquals(3, diagnostics.classesFound)
    assertEquals(1800, diagnostics.accumulatedFindTimeMs)
    assertEquals(1200, diagnostics.accumulatedRewriteTimeMs)
  }
}