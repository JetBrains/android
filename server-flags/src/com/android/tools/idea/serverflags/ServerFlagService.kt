/*
 * Copyright (C) 2021 The Android Open Source Project
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

interface ServerFlagService {
  val initialized: Boolean
  val configurationVersion: Long
  val names: List<String>
  fun getString(name: String): String?
  fun getInt(name: String): Int?
  fun getFloat(name: String): Float?
  fun getBoolean(name: String): Boolean?
  fun <T : Message> getProtoOrNull(name: String, instance: T): T?
  fun getString(name: String, defaultValue: String): String = getString(name) ?: defaultValue
  fun getInt(name: String, defaultValue: Int): Int = getInt(name) ?: defaultValue
  fun getFloat(name: String, defaultValue: Float): Float = getFloat(name) ?: defaultValue
  fun getBoolean(name: String, defaultValue: Boolean): Boolean = getBoolean(name) ?: defaultValue
  fun <T : Message> getProto(name: String, defaultInstance: T) =
    getProtoOrNull(name, defaultInstance) ?: defaultInstance

  companion object {
    var instance: ServerFlagService = ServerFlagServiceEmpty()
  }
}
