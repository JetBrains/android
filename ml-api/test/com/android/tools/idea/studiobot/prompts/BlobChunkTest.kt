/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.studiobot.prompts

import com.android.tools.idea.studiobot.MimeType
import kotlin.test.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BlobChunkTest {
  // These tests are needed because BlobChunk is a data class that contains a ByteArray,
  // and that requires equals and hashCode to be implemented explicitly to avoid surprises
  // since by default arrays' equals and hashCode don't check their contents.

  @Test
  fun `should mark two structurally identical instances as equals`() {
    val first = Prompt.Message.BlobChunk(MimeType.JPEG, emptyList(), "MyByteArray".toByteArray())
    val second = Prompt.Message.BlobChunk(MimeType.JPEG, emptyList(), "MyByteArray".toByteArray())

    assertEquals(second, first)
  }

  @Test
  fun `should mark two not structurally identical instances as not equals`() {
    val first = Prompt.Message.BlobChunk(MimeType.JPEG, emptyList(), "MyByteArray2".toByteArray())
    val second = Prompt.Message.BlobChunk(MimeType.JPEG, emptyList(), "MyByteArray".toByteArray())

    assertNotEquals(second, first)
  }
}
