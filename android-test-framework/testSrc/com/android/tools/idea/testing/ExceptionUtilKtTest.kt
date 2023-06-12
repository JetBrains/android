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
package com.android.tools.idea.testing

import com.google.common.truth.Truth.assertThat
import com.jetbrains.rd.util.getThrowableText
import org.junit.Test
import java.util.regex.Pattern

class ExceptionUtilKtTest {

  @Test
  fun aggregateAndThrowIfAny_passes() {
    val aggregateException = kotlin.runCatching {
      aggregateAndThrowIfAny {
        println("ok")
      }
    }.exceptionOrNull()

    assertThat(aggregateException).isNull()
  }

  @Test
  fun aggregateAndThrowIfAny() {
    val aggregateException = kotlin.runCatching {
      aggregateAndThrowIfAny {
        runCatchingAndRecord { error("ABC") }
        runCatchingAndRecord { error("XYZ") }
      }
    }.exceptionOrNull()

    assertThat(aggregateException?.getThrowableText().orEmpty())
      .containsMatch(Pattern.compile("ABC.*XYZ", Pattern.DOTALL))
  }

  @Test
  fun aggregateAndThrowIfAny_throwsItself() {
    val aggregateException = kotlin.runCatching {
      aggregateAndThrowIfAny {
        runCatchingAndRecord { error("ABC") }
        error("123")
      }
    }.exceptionOrNull()

    assertThat(aggregateException?.getThrowableText().orEmpty())
      .containsMatch(Pattern.compile("ABC.*123", Pattern.DOTALL))
  }

  @Test
  fun aggregateAndThrowIfAny_throwsItselfOnly() {
    val aggregateException = kotlin.runCatching {
      aggregateAndThrowIfAny {
        error("123")
      }
    }.exceptionOrNull()

    assertThat(aggregateException?.getThrowableText().orEmpty()).containsMatch("123")
  }
}