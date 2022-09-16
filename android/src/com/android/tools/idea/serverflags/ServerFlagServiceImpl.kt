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

import com.android.tools.idea.serverflags.protos.ServerFlag
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Message

class ServerFlagServiceImpl : ServerFlagService {
  override val configurationVersion: Long
  val flags: Map<String, ServerFlag>

  init {
    val data = initializer()
    configurationVersion = data.configurationVersion
    flags = data.flags
  }

  override val names = flags.keys.toList()

  override fun getString(name: String): String? {
    val flag = flags[name] ?: return null
    return when {
      (flag.hasStringValue()) -> flag.stringValue
      else -> null
    }
  }

  override fun getInt(name: String): Int? {
    val flag = flags[name] ?: return null
    return when {
      (flag.hasIntValue()) -> flag.intValue
      else -> null
    }
  }

  override fun getFloat(name: String): Float? {
    val flag = flags[name] ?: return null
    return when {
      (flag.hasFloatValue()) -> flag.floatValue
      else -> null
    }
  }

  override fun getBoolean(name: String): Boolean? {
    val flag = flags[name] ?: return null
    return when {
      (flag.hasBooleanValue()) -> flag.booleanValue
      else -> null
    }
  }

  override fun <T : Message> getProtoOrNull(name: String, instance: T): T? {
    val flag = flags[name] ?: return null
    if (!flag.hasProtoValue()) {
      return null
    }

    val any = flag.protoValue ?: return null

    return try {
      @Suppress("UNCHECKED_CAST")
      instance.parserForType.parseFrom(any.value) as T
    }
    catch (e: InvalidProtocolBufferException) {
      null
    }
  }

  override fun toString(): String {
    if (flags.isEmpty()) {
      return "No server flags are enabled."
    }

    val sb = StringBuilder()
    for ((name, flag) in flags.entries.sortedBy { it.key }) {
      sb.append("Name: $name\nPercentEnabled: ${flag.percentEnabled}\nValue: ${flag.Value}\n\n")
    }

    return sb.toString()
  }

  companion object {
    var initializer: () -> ServerFlagInitializationData = { ServerFlagInitializer.initializeService() }
  }
}

private val ServerFlag.Value: String
  get() {
    return when {
      this.hasStringValue() -> this.stringValue
      this.hasBooleanValue() -> this.booleanValue.toString()
      this.hasFloatValue() -> this.floatValue.toString()
      this.hasIntValue() -> this.intValue.toString()
      this.hasProtoValue() -> "custom proto"
      else -> "No value specified"
    }
  }


