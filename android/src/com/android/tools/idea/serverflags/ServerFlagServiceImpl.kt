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

import com.android.tools.analytics.CommonMetricsData
import com.android.tools.idea.analytics.currentIdeBrand
import com.android.tools.idea.serverflags.protos.AndroidSdkSupportConfiguration
import com.android.tools.idea.serverflags.protos.FlagValue
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Message
import com.google.protobuf.TextFormat

class ServerFlagServiceImpl : ServerFlagService {
  override val configurationVersion: Long
  private val flags: Map<String, ServerFlagValueData>

  init {
    val data = initializer()
    configurationVersion = data.configurationVersion
    flags = data.flags
  }

  override val flagAssignments = flags.entries.associate { it.key to it.value.index }

  override fun getString(name: String): String? {
    val flag = flags[name] ?: return null
    return when {
      (flag.value.hasStringValue()) -> flag.value.stringValue
      else -> null
    }
  }

  override fun getInt(name: String): Int? {
    val flag = flags[name] ?: return null
    return when {
      (flag.value.hasIntValue()) -> flag.value.intValue
      else -> null
    }
  }

  override fun getFloat(name: String): Float? {
    val flag = flags[name] ?: return null
    return when {
      (flag.value.hasFloatValue()) -> flag.value.floatValue
      else -> null
    }
  }

  override fun getBoolean(name: String): Boolean? {
    val flag = flags[name] ?: return null
    return when {
      (flag.value.hasBooleanValue()) -> flag.value.booleanValue
      else -> null
    }
  }

  override fun <T : Message> getProtoOrNull(name: String, instance: T): T? {
    val flag = flags[name] ?: return null
    if (!flag.value.hasProtoValue()) {
      return null
    }

    val any = flag.value.protoValue ?: return null

    return try {
      @Suppress("UNCHECKED_CAST")
      instance.parserForType.parseFrom(any.value) as T
    } catch (e: InvalidProtocolBufferException) {
      null
    }
  }

  override fun toString(): String {
    if (flags.isEmpty()) {
      return "No server flags are enabled."
    }

    val sb = StringBuilder()
    for ((name, flag) in flags.entries.sortedBy { it.key }) {
      sb.append(
        "Name: $name\nPercentEnabled: ${flag.value.percentEnabled}\nValue: ${prettyPrint(name, flag.value)}\n\n"
      )
    }

    return sb.toString()
  }

  private fun prettyPrint(name: String, flag: FlagValue): String =
    when {
      flag.hasStringValue() -> flag.stringValue
      flag.hasBooleanValue() -> flag.booleanValue.toString()
      flag.hasFloatValue() -> flag.floatValue.toString()
      flag.hasIntValue() -> flag.intValue.toString()
      flag.hasProtoValue() -> prettyPrintProto(name)
      else -> "No value specified"
    }

  private fun prettyPrintProto(name: String): String {
    val type =
      when (name) {
        "feature/studio_api_level_support" -> AndroidSdkSupportConfiguration.getDefaultInstance()
        else -> return "custom proto"
      }
    val proto = getProtoOrNull(name, type) ?: return "null"
    return TextFormat.printer().printToString(proto)
  }

  companion object {
    private const val ENABLED_OVERRIDE_KEY = "studio.server.flags.enabled.override"

    var initializer: () -> ServerFlagInitializationData = {
      val overrideParser = OverridePropertyParserImpl(SUPPORTS_MULTI_VALUE_FLAGS)
      val overriddenFlags =
        System.getProperty(ENABLED_OVERRIDE_KEY)?.let { overrideParser.parseProperty(it) }
          ?: emptyMap()
      ServerFlagInitializer.initializeService(
        localCacheDirectory,
        flagsVersion,
        CommonMetricsData.osName,
        currentIdeBrand(),
        overriddenFlags,
        SUPPORTS_MULTI_VALUE_FLAGS,
      )
    }
  }
}
