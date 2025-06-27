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

package com.android.tools.idea.diagnostics.crash

import org.junit.Test
import kotlin.test.assertTrue

class StudioExceptionReportTest {

  @Test
  fun `test getRootCause`(){
    val rootException = IllegalArgumentException("Root exception", null)
    val outerException = IllegalStateException("Outer exception", rootException)
    val exampleException = RuntimeException("Example exception", outerException)

    assertTrue(StudioExceptionReport.getRootCause(exampleException) == rootException)
  }
}
