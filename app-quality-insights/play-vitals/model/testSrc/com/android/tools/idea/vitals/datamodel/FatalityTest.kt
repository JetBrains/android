/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.vitals.datamodel

import com.android.tools.idea.insights.model.issue.FailureType
import com.google.common.truth.Truth.assertThat
import com.google.play.developer.reporting.ErrorType
import org.junit.Assert.assertThrows
import org.junit.Test

class FatalityTest {

  @Test
  fun `toFailureType maps correctly`() {
    assertThat(ErrorType.ERROR_TYPE_UNSPECIFIED.toFailureType()).isEqualTo(FailureType.UNSPECIFIED)
    assertThat(ErrorType.UNRECOGNIZED.toFailureType()).isEqualTo(FailureType.UNSPECIFIED)
    assertThat(ErrorType.APPLICATION_NOT_RESPONDING.toFailureType()).isEqualTo(FailureType.ANR)
    assertThat(ErrorType.CRASH.toFailureType()).isEqualTo(FailureType.FATAL)
  }

  @Test
  fun `toFailureType throws for NON_FATAL`() {
    val exception = assertThrows(RuntimeException::class.java) { ErrorType.NON_FATAL.toFailureType() }
    assertThat(exception).hasMessageThat().isEqualTo("NON_FATAL type not supported.")
  }
}
