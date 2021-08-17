/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.serverflags

import com.google.protobuf.Message

class ServerFlagServiceEmpty : ServerFlagService {
  override val initialized: Boolean = false
  override val configurationVersion: Long = -1
  override val names: List<String> = emptyList()
  override fun getString(name: String): String? {
    checkInternalDebugBuild()
    return null
  }

  override fun getInt(name: String): Int? {
    checkInternalDebugBuild()
    return null
  }

  override fun getFloat(name: String): Float? {
    checkInternalDebugBuild()
    return null
  }

  override fun getBoolean(name: String): Boolean? {
    checkInternalDebugBuild()
    return null
  }

  override fun <T : Message> getProtoOrNull(name: String, instance: T): T? {
    checkInternalDebugBuild()
    return null
  }

  private fun checkInternalDebugBuild() {
    if (java.lang.Boolean.getBoolean("idea.is.internal")) {
      // This exception indicates that the service has been accessed before it has been initialized.
      // Please reach out to the owners of this code to figure out how best to synchronize the calling
      // code with the service initialization.
      throw RuntimeException("call to ServerFlagService before initialization")
    }
  }
}
