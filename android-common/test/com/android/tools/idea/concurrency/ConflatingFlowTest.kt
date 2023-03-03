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
package com.android.tools.idea.concurrency

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import kotlin.random.Random

class ConflatingFlowTest {
  /**
   * Test that ensures that conflateLatest does receive every single element and that
   * no single item is lost.
   */
  @Test
  fun `every element is delivered`() = runBlocking {
    val startNumber = 0
    val endNumber = 100

    val flow = flow {
      for (i in startNumber..endNumber) {
        delay(Random.nextLong(0, 10))
        emit(listOf(i))
      }
    }

    var expected = startNumber
    flow.conflateLatest { waiting, new ->
      delay(Random.nextLong(0, 10))
      waiting + new
    }
      .collect {
        delay(Random.nextLong(0, 100))
        it.forEach { received ->
          assertEquals(expected, received)
          expected = received + 1
        }
      }

    assertEquals("Not all numbers were received", 101, expected)
  }

  @Test
  fun `element batching`() = runBlocking {
    val startNumber = 0
    val endNumber = 20
    // Element that will take a longer time to process
    val slowElement = 5
    // Send one permit. This will allow to control the input flow so we know
    // the speed. Initially, it will have 1 permit and 1 more will be given after
    // the element has been processed, this ensures the elements are received 1 by 1.
    val permits = Channel<Unit>(Channel.UNLIMITED)

    permits.send(Unit)

    val flow = flow {
      for (i in startNumber..endNumber) {
        permits.receive()
        emit(listOf(i))
      }
    }

    var expected = startNumber
    val outputBuffer = StringBuffer()
    flow.conflateLatest { waiting, new ->
      waiting + new
    }
      .collect {
        delay(100)
        if (it.contains(slowElement)) {
          // Add a bunch 4 more permits in one go. This will generate a bunch of inputs
          // very quickly and will allow a batch of 5 to form.
          repeat(4) {
            permits.send(Unit)
          }
          // Simulate very slow processing of element 10
          delay(100)
        }
        outputBuffer.appendLine(it.toString())
        it.forEach { received ->
          assertEquals(expected, received)
          expected = received + 1
        }

        // Next element is allowed to come in now
        permits.send(Unit)
      }

    assertEquals("""
      [0]
      [1]
      [2]
      [3]
      [4]
      [5]
      [6]
      [7, 8, 9, 10]
      [11]
      [12]
      [13]
      [14]
      [15]
      [16]
      [17]
      [18]
      [19]
      [20]
    """.trimIndent(), outputBuffer.trim())
  }
}