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
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.time.Clock

private const val CACHE_DURATION_MILLIS = 5 * 60 * 1000 /* 5 minutes */

/** Implementation of [DynamicServerFlagService] that updates flags with a call to [updateFlags] */
class DynamicServerFlagServiceImpl(
  private val clock: Clock = Clock.systemDefaultZone(),
  private var serverFlagService: ServerFlagService = ServerFlagServiceEmpty,
) : ServerFlagService, DynamicServerFlagService {

  private var timestamp = -1L

  @RequiresBackgroundThread
  override fun updateFlags() {
    val currentTime = clock.millis()
    if (timestamp + CACHE_DURATION_MILLIS > currentTime) {
      return
    }
    timestamp = currentTime
    downloadNewFlagsAndCreateNewService()
  }

  override val configurationVersion: Long
    get() = serverFlagService.configurationVersion

  override val flagAssignments: Map<String, Int>
    get() = serverFlagService.flagAssignments

  override fun getBoolean(name: String): Boolean? {
    return serverFlagService.getBoolean(name)
  }

  override fun getBoolean(name: String, defaultValue: Boolean): Boolean {
    return serverFlagService.getBoolean(name, defaultValue)
  }

  override fun getInt(name: String): Int? {
    return serverFlagService.getInt(name)
  }

  override fun getInt(name: String, defaultValue: Int): Int {
    return serverFlagService.getInt(name, defaultValue)
  }

  override fun getFloat(name: String): Float? {
    return serverFlagService.getFloat(name)
  }

  override fun getFloat(name: String, defaultValue: Float): Float {
    return serverFlagService.getFloat(name, defaultValue)
  }

  override fun getString(name: String): String? {
    return serverFlagService.getString(name)
  }

  override fun getString(name: String, defaultValue: String): String {
    return serverFlagService.getString(name, defaultValue)
  }

  override fun <T : Message> getProto(name: String, defaultInstance: T): T {
    return serverFlagService.getProto(name, defaultInstance)
  }

  override fun <T : Message> getProtoOrNull(name: String, instance: T): T? {
    return serverFlagService.getProtoOrNull(name, instance)
  }

  private fun downloadNewFlagsAndCreateNewService() {
    ServerFlagDownloader.downloadServerFlagList()
    serverFlagService = ServerFlagServiceImpl()
  }
}
