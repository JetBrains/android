/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.quickfix

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SdkIndexLinkQuickFixTest {
  @Test
  fun `execute logs click`() {
    var browserCalled = false
    var eventReportCalled = false
    val quickfix = SdkIndexLinkQuickFix(text = "Open link text", url = "http://google.com", groupId = "com.google.androidx",
                                        artifactId = "firebase", "2.0.0", browseFunction = { browserCalled = true },
                                        eventReport = { eventReportCalled = true })
    quickfix.applyQuickfix(null)
    assertThat(browserCalled).isTrue()
    assertThat(eventReportCalled).isTrue()
  }
}
