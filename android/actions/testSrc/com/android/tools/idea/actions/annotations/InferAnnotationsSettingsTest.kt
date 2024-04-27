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
package com.android.tools.idea.actions.annotations

import org.junit.Assert.assertEquals
import org.junit.Assert.*
import org.junit.Test

class InferAnnotationsSettingsTest {
  @Test
  fun testPersistence() {
    val settings = InferAnnotationsSettings()
    assertEquals("", settings.toString())
    settings.generateReport = false
    settings.optimistic = true // same as default
    settings.annotateLocalVariables = true
    assertEquals("annotateLocalVariables=true,generateReport=false", settings.toString())

    val newSettings = InferAnnotationsSettings()
    assertTrue(newSettings.generateReport)
    assertTrue(newSettings.optimistic)
    assertFalse(newSettings.annotateLocalVariables)
    newSettings.apply("annotateLocalVariables=true,generateReport=false")
    assertFalse(newSettings.generateReport)
    assertTrue(newSettings.optimistic)
    assertTrue(newSettings.annotateLocalVariables)
  }
}