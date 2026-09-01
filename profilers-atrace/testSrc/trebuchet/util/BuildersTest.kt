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
package trebuchet.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildersTest {

  class TestPayload(var value: String = "")

  @Test
  fun `end returns null when stack is empty`() {
    val builder = StartEndBuilder<String, TestPayload>(new = { TestPayload() })
    val result = builder.end { value }
    assertThat(result).isNull()
  }

  @Test
  fun `start and end lifecycle works correctly`() {
    val builder = StartEndBuilder<String, TestPayload>(new = { TestPayload() })
    builder.start {
      value = "hello"
    }

    val result = builder.end { value.uppercase() }
    assertThat(result).isEqualTo("HELLO")
  }

  @Test
  fun `nested start and end works in LIFO order`() {
    val builder = StartEndBuilder<String, TestPayload>(new = { TestPayload() })
    builder.start { value = "outer" }
    builder.start { value = "inner" }

    val innerResult = builder.end { value }
    assertThat(innerResult).isEqualTo("inner")

    val outerResult = builder.end { value }
    assertThat(outerResult).isEqualTo("outer")

    assertThat(builder.end { value }).isNull()
  }

  @Test
  fun `recycles objects into garbage on end and reuses in start`() {
    var createCount = 0
    val builder =
      StartEndBuilder<String, TestPayload>(
        new = {
          createCount++
          TestPayload()
        },
        reset = {
          it.value = ""
          it
        },
      )

    builder.start { value = "first" }
    assertThat(createCount).isEqualTo(1)
    val first = builder.end { value }
    assertThat(first).isEqualTo("first")

    // Next start should reuse the recycled object rather than creating a new instance
    builder.start {
      assertThat(value).isEmpty()
      value = "reused"
    }
    assertThat(createCount).isEqualTo(1)
    val second = builder.end { value }
    assertThat(second).isEqualTo("reused")
  }

  @Test
  fun `companion make creates builder instance`() {
    val builder = StartEndBuilder.make<Int, TestPayload>()
    builder.start { value = "42" }
    val result = builder.end { value.toInt() }
    assertThat(result).isEqualTo(42)
  }
}
