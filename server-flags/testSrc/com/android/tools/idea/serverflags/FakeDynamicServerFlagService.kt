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
package com.android.tools.idea.serverflags

import com.google.protobuf.Message

open class FakeDynamicServerFlagService(private val flagProvider: () -> Map<String, Any>) :
  DynamicServerFlagService {
  override val configurationVersion = -1L
  override val flagAssignments = emptyMap<String, Int>()
  private val flags = mutableMapOf<String, Any>()

  fun registerFlag(name: String, value: Any) {
    flags[name] = value
  }

  override fun updateFlags() {
    flags.putAll(flagProvider())
  }

  override fun getBoolean(name: String) = flags[name] as? Boolean

  override fun getInt(name: String) = flags[name] as? Int

  override fun getFloat(name: String) = flags[name] as? Float

  override fun getString(name: String) = flags[name] as? String

  override fun <T : Message> getProto(name: String, defaultInstance: T): T {
    return getProtoOrNull(name, defaultInstance) ?: defaultInstance
  }

  override fun <T : Message> getProtoOrNull(name: String, instance: T): T? {
    return flags[name] as? T
  }
}
