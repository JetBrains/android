/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.client

import com.google.protobuf.Timestamp
import java.time.Instant

// reference:
// https://source.corp.google.com/piper///depot/google3/java/com/google/protobuf/util/JavaTimeConversions.java;rcl=431825270;l=55

fun Instant.toProtoTimestamp(): Timestamp {
  return Timestamp.newBuilder().setSeconds(epochSecond).setNanos(nano).build()
}

fun Timestamp.toJavaInstant(): Instant {
  return Instant.ofEpochSecond(this.seconds, this.nanos.toLong())
}
