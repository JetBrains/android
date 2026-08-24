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
package com.android.tools.idea.transport

import com.android.tools.idea.protobuf.ByteString
import com.android.tools.profiler.proto.Transport
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files

object TransportServiceUtils {
  private val log = Logger.getInstance(TransportServiceUtils::class.java)

  /**
   * Aggregates a stream of byte chunks received from a gRPC call into a single ByteString.
   *
   * @param streamSupplier A supplier providing the iterator for BytesInChunksResponse messages.
   * @return A ByteString containing the aggregated byte chunks, or ByteString.EMPTY on error.
   */
  fun aggregateByteChunks(streamSupplier: () -> Iterator<Transport.BytesInChunksResponse>): ByteString {
    return try {
      val output = ByteString.newOutput()
      streamSupplier().forEach { response -> response.chunk?.writeTo(output) }
      output.toByteString()
    } catch (e: Exception) {
      log.warn("Failed to aggregate byte chunks", e)
      ByteString.EMPTY
    }
  }

  /**
   * Streams chunks directly to a newly created temporary file. Cleans up and returns null if writing fails or results in a 0-byte file.
   *
   * @param prefix Prefix for the temporary file.
   * @param suffix Suffix for the temporary file.
   * @param streamSupplier A supplier providing the iterator for BytesInChunksResponse messages.
   * @return The File containing the streamed bytes, or null if the stream is empty or fails.
   */
  fun streamChunksToTempFile(
    prefix: String,
    suffix: String,
    streamSupplier: () -> Iterator<Transport.BytesInChunksResponse>,
  ): File? {
    var tempFile: File? = null
    return try {
      val file = FileUtil.createTempFile(prefix, suffix, true).also { tempFile = it }
      val byteResponses = streamSupplier()
      FileOutputStream(file).use { stream ->
        byteResponses.forEach { response -> response.chunk?.writeTo(stream) }
      }
      if (file.length() == 0L) {
        FileUtil.delete(file)
        null
      } else {
        file
      }
    } catch (e: Exception) {
      log.warn("Failed to write byte chunks to temp file", e)
      tempFile?.let { FileUtil.delete(it) }
      null
    }
  }

  /** Creates a temporary file and writes the given ByteString content to it. Returns null if content is empty or on failure. */
  fun createTempFileFromBytes(prefix: String, suffix: String, content: ByteString): File? {
    if (content.isEmpty) {
      return null
    }
    var tempFile: File? = null
    return try {
      val file = FileUtil.createTempFile(prefix, suffix, true).also { tempFile = it }
      FileOutputStream(file).use { stream -> content.writeTo(stream) }
      file
    } catch (e: Exception) {
      log.warn("Failed to save preprocessed content to temp file", e)
      tempFile?.let { FileUtil.delete(it) }
      null
    }
  }

  /** Creates a temporary file with the given content. The file will be deleted on exit. */
  @JvmStatic
  @Throws(IOException::class)
  fun createTempFile(prefix: String, suffix: String, content: ByteString): File {
    val file = File.createTempFile(prefix, suffix)
    file.deleteOnExit()
    Files.write(file.toPath(), content.toByteArray())
    return file
  }
}
