/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.performance

import com.android.tools.datastore.database.NetworkTable
import com.android.tools.profiler.proto.NetworkProfiler

import java.sql.Connection
import java.util.Random

class NetworkGenerator(connection: Connection) : DataGenerator(connection) {

  private val myTable = NetworkTable()

  init {
    myTable.initialize(connection)
  }

  override fun generate(timestamp: Long, properties: GeneratorProperties) {
    generateDataPacket(timestamp, properties)
    if (isWithinProbability(0.3)) {
      generateHttpRequestData(timestamp, properties)
    }

  }

  private fun generateDataPacket(timestamp: Long, properties: GeneratorProperties) {
    val data = NetworkProfiler.NetworkProfilerData.newBuilder()
      .setEndTimestamp(timestamp)
      .setSpeedData(NetworkProfiler.SpeedData.newBuilder()
                      .setReceived(Math.abs(random.nextLong()))
                      .setSent(Math.abs(random.nextLong())))
      .setConnectionData(NetworkProfiler.ConnectionData.newBuilder()
                           .setConnectionNumber(random.nextInt()))
      .build()
    myTable.insert(properties.session, data)
  }

  private fun generateHttpRequestData(timestamp: Long, properties: GeneratorProperties) {
    val defaultData = NetworkProfiler.HttpDetailsResponse.newBuilder()
      .setRequest(NetworkProfiler.HttpDetailsResponse.Request.newBuilder()
                    .setFields("HEADER")
                    .setMethod("POST")
                    .setTraceId("0")
                    .setUrl("http://Some.long.http/url/for/the/test"))
      .build()
    val connection = NetworkProfiler.HttpConnectionData.newBuilder()
      .setStartTimestamp(timestamp - 1)
      .setEndTimestamp(timestamp)
      .setConnId(random.nextLong())
      .build()
    myTable.insertOrReplace(properties.session, defaultData, defaultData, defaultData, defaultData, defaultData, connection)
  }
}