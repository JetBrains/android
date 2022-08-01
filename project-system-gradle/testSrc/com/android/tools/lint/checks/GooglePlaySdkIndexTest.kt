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
package com.android.tools.lint.checks

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.lint.checks.GooglePlaySdkIndex
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GooglePlaySdkIndexTest {
  @Test
  fun `Default options should match StudioFlags`() {
    assertThat(GooglePlaySdkIndex.DEFAULT_SHOW_MESSAGES).isEqualTo(StudioFlags.SHOW_SDK_INDEX_MESSAGES.get())
    assertThat(GooglePlaySdkIndex.DEFAULT_SHOW_LINKS).isEqualTo(StudioFlags.INCLUDE_LINKS_TO_SDK_INDEX.get())
    assertThat(GooglePlaySdkIndex.DEFAULT_SHOW_CRITICAL_ISSUES).isEqualTo(StudioFlags.SHOW_SDK_INDEX_CRITICAL_ISSUES.get())
    assertThat(GooglePlaySdkIndex.DEFAULT_SHOW_POLICY_ISSUES).isEqualTo(StudioFlags.SHOW_SDK_INDEX_POLICY_ISSUES.get())
  }
}