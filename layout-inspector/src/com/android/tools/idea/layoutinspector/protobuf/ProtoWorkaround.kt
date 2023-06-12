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
package com.android.tools.idea.layoutinspector.protobuf

import com.android.tools.idea.protobuf.CodedInputStream
import com.android.tools.idea.protobuf.GeneratedMessageV3
import com.android.tools.idea.protobuf.InvalidProtocolBufferException
import com.android.tools.idea.protobuf.Parser
import java.io.InputStream

// The file format chosen for snapshots contains multiple sections of protobufs.
// We want to read those sections with GeneratedMessageV3.parseDelimitedFrom(InputStream) since it allows partial reads of the InputStream.
// The implementation of GeneratedMessageV3.parseDelimitedFrom(InputStream) has a hardcoded protobuf recursion limit of 100.
// Our compose tree can easily get deeper than 100.
// This implementation of parseDelimitedFrom allows for a deeper recursion limit by copying parts of the implementation from the protobuf
// library.
//
// A bug has been filed for the protobuf team to make this easier: b/251824432

fun <T: GeneratedMessageV3> parseDelimitedFrom(input: InputStream, parser: Parser<T>): T? {
  val firstByte = input.read()
  if (firstByte == -1) {
    return null
  }
  val size = CodedInputStream.readRawVarint32(firstByte, input)
  val limitedInput: InputStream = LimitedInputStream(input, size)
  return parsePartialFrom(limitedInput, parser)
}

private fun <T: GeneratedMessageV3> parsePartialFrom(input: InputStream, parser: Parser<T>): T? {
  val codedInput = CodedInputStream.newInstance(input).apply { setRecursionLimit(Int.MAX_VALUE) }
  val message = parser.parsePartialFrom(codedInput)

  return try {
    codedInput.checkLastTagWas(0)
    message
  }
  catch (ex: InvalidProtocolBufferException) {
    throw ex.setUnfinishedMessage(message)
  }
}
