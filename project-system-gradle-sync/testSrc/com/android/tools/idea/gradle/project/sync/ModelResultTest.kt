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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.project.sync.ModelResult.Companion.ignoreExceptionsAndGet
import com.google.common.truth.Expect
import org.junit.Rule
import org.junit.Test

class ModelResultTest {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun `create when succeeds`() {
    val result = ModelResult.create {
      "abc"
    }
    expect.that(result.exceptions).isEmpty()
    expect.that(result.ignoreExceptionsAndGet()).isEqualTo("abc")
  }

  @Test
  fun `create when succeeds with exceptions`() {
    val result = ModelResult.create {
      recordException { error("123") }
      "abc"
    }
    expect.that(result.exceptions).hasSize(1)
    expect.that(result.exceptions.getOrNull(0)?.message).isEqualTo("123")
    expect.that(result.exceptions.getOrNull(0)?.stackTrace).isNotEmpty()
    expect.that(result.ignoreExceptionsAndGet()).isEqualTo("abc")
  }

  @Test
  fun `create when fails with exceptions`() {
    val result = ModelResult.create {
      error("123")
      @Suppress("UNREACHABLE_CODE")
      "this is just to infer types"
    }
    expect.that(result.exceptions).hasSize(1)
    expect.that(result.exceptions.getOrNull(0)?.message).isEqualTo("123")
    expect.that(result.exceptions.getOrNull(0)?.stackTrace).isNotEmpty()
    expect.that(result.ignoreExceptionsAndGet()).isNull()
  }

  @Test
  fun `create records exceptions from recordAndGet`() {
    val intermediateResult = ModelResult.create {
      recordException { error("123") }
      "abc"
    }
    val result = ModelResult.create {
      val intermediateValue = intermediateResult.recordAndGet()
      intermediateValue + "xyz"
    }
    expect.that(result.exceptions).hasSize(1)
    expect.that(result.exceptions.getOrNull(0)?.message).isEqualTo("123")
    expect.that(result.exceptions.getOrNull(0)?.stackTrace).isNotEmpty()
    expect.that(result.ignoreExceptionsAndGet()).isEqualTo("abcxyz")
  }

  @Test
  fun `create records exceptions from recordAndGet when intermediate step fails`() {
    val intermediateResult = ModelResult.create {
      error("123")
      @Suppress("UNREACHABLE_CODE")
      "this is just to infer types"
    }
    val result = ModelResult.create {
      val intermediateValue = intermediateResult.recordAndGet()
      (intermediateValue ?: "(was null)") + "xyz"
    }
    expect.that(result.exceptions).hasSize(1)
    expect.that(result.exceptions.getOrNull(0)?.message).isEqualTo("123")
    expect.that(result.exceptions.getOrNull(0)?.stackTrace).isNotEmpty()
    expect.that(result.ignoreExceptionsAndGet()).isEqualTo("(was null)xyz")
  }
}