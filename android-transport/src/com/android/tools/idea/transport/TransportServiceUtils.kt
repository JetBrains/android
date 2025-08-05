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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files

object TransportServiceUtils {

  /**
   * Aggregates a stream of byte chunks received from a gRPC call into a single ByteString.
   *
   * @param byteResponses An iterator providing the sequence of Bytes2Response messages containing byte chunks.
   * @return A ByteString containing the aggregated byte chunks.
   */
  fun aggregateByteChunks(byteResponses: Iterator<Transport.BytesInChunksResponse>): ByteString {
    val outputStream = ByteArrayOutputStream()
    byteResponses.forEach { response ->
      response.chunk?.let { outputStream.write(it.toByteArray()) }
    }
    return ByteString.copyFrom(outputStream.toByteArray())
  }
  /**
   * Creates a temporary file with the given content. The file will be deleted on exit.
   */
  @JvmStatic
  @Throws(IOException::class)
  fun createTempFile(prefix: String, suffix: String, content: ByteString): File {
    val file = File.createTempFile(prefix, suffix)
    file.deleteOnExit()
    Files.write(file.toPath(), content.toByteArray())
    return file
  }
}