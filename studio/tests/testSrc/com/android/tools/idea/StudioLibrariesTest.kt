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
package com.android.tools.idea


import com.android.tools.idea.io.grpc.Status
import com.android.tools.idea.io.grpc.StatusException
import com.android.tools.idea.io.grpc.protobuf.StatusProto
import org.junit.Test

/**
 * Tests related to libraries used by studio, especially if some testable logic goes into constructing those libraries.
 */
class StudioLibrariesTest {
  /**
   * Test that protos used by grpc are appropriately jarjar'd. See issue 243979004
   */
  @Test
  fun testGrpcProtoJarJar() {
    // If proto-google-common-protos is not included in studio-grpc.jar, the below code will result
    // in attempting to use a com.google.protobuf.Message as a com.android.tools.idea.Message.
    StatusProto.fromThrowable(
      StatusProto.toStatusRuntimeException(StatusProto.fromThrowable(StatusException(Status.ABORTED))))
  }
}