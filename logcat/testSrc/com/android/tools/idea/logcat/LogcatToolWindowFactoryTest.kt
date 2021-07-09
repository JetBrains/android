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
package com.android.tools.idea.logcat

import org.junit.Test
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.flags.StudioFlags
import org.junit.After

class LogcatToolWindowFactoryTest {
  private val project = mock<Project>()

  @After
  fun tearDown() {
    StudioFlags.LOGCAT_V2_ENABLE.clearOverride()
  }

  @Test
  fun shouldBeAvailable_isFalse() {
    assertThat(LogcatToolWindowFactory().shouldBeAvailable(project)).isFalse()
  }

  @Test
  fun shouldBeAvailable_obeysFlag_true() {
    StudioFlags.LOGCAT_V2_ENABLE.override(true)
    assertThat(LogcatToolWindowFactory().shouldBeAvailable(project)).isTrue()
  }

  @Test
  fun shouldBeAvailable_obeysFlag_false() {
    StudioFlags.LOGCAT_V2_ENABLE.override(false)
    assertThat(LogcatToolWindowFactory().shouldBeAvailable(project)).isFalse()
  }

}