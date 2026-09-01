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
package trebuchet.extras

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InputStreamAdapterTest {

  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun `reads stream content until EOF and subsequent next returns null`() {
    val content = "hello world stream".toByteArray()
    val inputStream = ByteArrayInputStream(content)
    val adapter = InputStreamAdapter(inputStream, totalSize = content.size.toLong())

    val slice = adapter.next()
    assertThat(slice).isNotNull()
    assertThat(String(slice!!.buffer, slice.startIndex, slice.length)).isEqualTo("hello world stream")
    assertThat(adapter.totalRead).isEqualTo(content.size.toLong())

    // First EOF read sets hitEof and returns null
    assertThat(adapter.next()).isNull()
    assertThat(adapter.hitEof).isTrue()

    // Subsequent call hits `if (hitEof) return null` (Line 33)
    assertThat(adapter.next()).isNull()
  }

  @Test
  fun `progress callback is notified during read`() {
    val content = "test progress".toByteArray()
    var progressCalled = false
    var reportedRead: Long = 0
    var reportedTotal: Long = 0

    val adapter =
      InputStreamAdapter(
        ByteArrayInputStream(content),
        totalSize = content.size.toLong(),
        progressCallback = { read, total ->
          progressCalled = true
          reportedRead = read
          reportedTotal = total
        },
      )

    val slice = adapter.next()
    assertThat(slice).isNotNull()
    assertThat(progressCalled).isTrue()
    assertThat(reportedRead).isEqualTo(content.size.toLong())
    assertThat(reportedTotal).isEqualTo(content.size.toLong())
  }

  @Test
  fun `close sets hitEof and subsequent next returns null`() {
    val content = "test close".toByteArray()
    val adapter = InputStreamAdapter(ByteArrayInputStream(content))

    adapter.close()
    assertThat(adapter.hitEof).isTrue()
    assertThat(adapter.next()).isNull()
  }

  @Test
  fun `file constructor reads file content correctly`() {
    val tempFile = tempFolder.newFile("test_stream.txt")
    tempFile.writeText("file stream content")

    val adapter = InputStreamAdapter(tempFile)
    val slice = adapter.next()
    assertThat(slice).isNotNull()
    assertThat(String(slice!!.buffer, slice.startIndex, slice.length)).isEqualTo("file stream content")
    adapter.close()
  }
}
