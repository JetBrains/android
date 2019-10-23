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

import com.android.tools.profiler.proto.Common
import org.junit.Assert.assertNotNull

import java.sql.Connection
import java.util.ArrayList
import java.util.Random

/**
 * This class is responsible for managing generator properties, as well as
 * triggering each generator to generate data.
 */
class DataGeneratorManager(connection: Connection, performantDb: Boolean) {
  private var myProperties: GeneratorProperties? = null
  private var myGenerators: MutableList<DataGenerator> = ArrayList()

  init {
    if (performantDb) {
      myGenerators.add(MemoryLiveAllocationGenerator(connection))
    }
    else {
      myGenerators.add(EventsGenerator(connection))
      myGenerators.add(EnergyGenerator(connection))
      myGenerators.add(CpuGenerator(connection))
      myGenerators.add(NetworkGenerator(connection))
      myGenerators.add(MemoryGenerator(connection))
    }
  }

  /**
   * Should be called before {@link :generateData} and {@link :endSession}.
   * This function is responsible for generating the properties used in data
   * generation as well as setting up the session.
   */
  fun beginSession(seed: Long) {
    val random = Random(seed)
    val pid = random.nextInt()
    myProperties = GeneratorProperties.Builder(Common.Session.newBuilder()
                                                 .setStreamId(random.nextLong())
                                                 .setPid(pid)
                                                 .setSessionId(random.nextInt().toLong())
                                                 .build())
      .setPid(pid)
      .build()
  }

  /**
   * Triggers data generation for a given timestamp.
   */
  fun generateData(timestamp: Long) {
    for (generator in myGenerators) {
      generator.generate(timestamp, myProperties!!)
    }
  }

  /**
   * Ends the current session and returns the session object used in data generation.
   */
  fun endSession(): Common.Session {
    val session = myProperties!!.session
    myProperties = null
    return session
  }
}
