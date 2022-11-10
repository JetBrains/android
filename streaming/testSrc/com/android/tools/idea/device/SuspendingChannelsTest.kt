/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.UsefulTestCase.assertThrows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.InterruptedByTimeoutException
import java.nio.channels.SocketChannel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets.UTF_8

/**
 * Tests for the [newOutputStream] and [newInputStream] functions defined in `SuspendingChannels.kt`.
 */
class SuspendingChannelsTest {
  private val coroutineScope = CoroutineScope(Dispatchers.IO)

  @After
  fun tearDown() {
    coroutineScope.cancel(null)
  }

  @Test
  fun testInputStream() {
    val buffer = ByteArray(20)
    val steps = Array(4) { CountDownLatch(1) }
    var exception: Throwable? = null

    val serverChannel = SuspendingServerSocketChannel(AsynchronousServerSocketChannel.open().bind(InetSocketAddress("127.0.0.1", 0)))
    coroutineScope.launch {
      serverChannel.use {
        serverChannel.accept().use { socketChannel ->
          socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
          val stream = newInputStream(socketChannel, 20)
          stream.readNBytes(buffer, 0, 4)
          steps[0].countDown()
          stream.waitForData(8)
          assertThat(stream.available()).isAtLeast(8)
          steps[1].countDown()
          stream.readNBytes(buffer, 4, 16)
          steps[2].countDown()
          try {
            exception = null
            stream.waitForData(1, 10, TimeUnit.MILLISECONDS)
          }
          catch (e: Throwable) {
            exception = e
          }
          finally {
            steps[3].countDown()
          }
        }
      }
    }

    val channel = SocketChannel.open(serverChannel.localAddress)
    val out = channel.socket().getOutputStream()
    assertThat(steps[0].await(200, TimeUnit.MILLISECONDS)).isFalse() // stream.readNBytes(buffer, 0, 4) should not return yet.
    out.writeAndFlush("12345678".toByteArray(UTF_8))
    assertThat(steps[0].await(200, TimeUnit.MILLISECONDS)).isTrue() // stream.readNBytes(buffer, 0, 4) should return.
    out.writeAndFlush("ab".toByteArray(UTF_8))
    assertThat(steps[1].await(200, TimeUnit.MILLISECONDS)).isFalse() // stream.waitForData(8) should not return yet.
    out.writeAndFlush("cdefgh".toByteArray(UTF_8))
    assertThat(steps[1].await(200, TimeUnit.MILLISECONDS)).isTrue() // stream.waitForData(8) should return.
    assertThat(steps[2].await(200, TimeUnit.MILLISECONDS)).isFalse() // stream.readNBytes(buffer, 4, 16) should not return yet.
    out.writeAndFlush("ijkl".toByteArray(UTF_8))
    assertThat(steps[2].await(200, TimeUnit.MILLISECONDS)).isTrue() // stream.readNBytes(buffer, 4, 16) should return.
    assertThat(buffer.toString(UTF_8)).isEqualTo("12345678abcdefghijkl")
    assertThat(steps[3].await(500, TimeUnit.MILLISECONDS)).isTrue() // stream.waitForData(1, 10, TimeUnit.MILLISECONDS) should throw.
    assertThat(exception).isInstanceOf(InterruptedByTimeoutException::class.java)
  }

  @Test
  fun testOutputStream() {
    val steps = Array(2) { CountDownLatch(1) }

    val serverChannel = SuspendingServerSocketChannel(AsynchronousServerSocketChannel.open().bind(InetSocketAddress("127.0.0.1", 0)))
    coroutineScope.launch {
      serverChannel.use {
        serverChannel.accept().use { socketChannel ->
          socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
          val stream = newOutputStream(socketChannel, 8)
          stream.write("1234".toByteArray(UTF_8))
          assertThat(steps[0].await(200, TimeUnit.MILLISECONDS)).isFalse() // Nothing should be written to the channel yet.
          stream.write("5678abcdef".toByteArray(UTF_8))
          assertThat(steps[0].await(200, TimeUnit.MILLISECONDS)).isTrue() // The first 8 bytes should be written to the channel.
          assertThat(steps[1].await(200, TimeUnit.MILLISECONDS)).isFalse() // The second buffer is not written yet.
          stream.writeAndFlush("ghijkl".toByteArray(UTF_8))
          assertThat(steps[1].await(200, TimeUnit.MILLISECONDS)).isTrue() // All data is written to the channel.
        }
      }
    }

    val channel = SocketChannel.open(serverChannel.localAddress)
    val buf = ByteArray(20)
    channel.readFully(ByteBuffer.wrap(buf, 0, 8))
    steps[0].countDown()
    channel.readFully(ByteBuffer.wrap(buf, 8, 12))
    steps[1].countDown()
  }

  @Test
  fun testConnectAndPeerClosing() {
    val connected = CountDownLatch(1)
    val closed = CountDownLatch(1)

    val serverChannel = SuspendingServerSocketChannel(AsynchronousServerSocketChannel.open().bind(InetSocketAddress("127.0.0.1", 0)))
    coroutineScope.launch {
      serverChannel.use {
        serverChannel.accept().use { socketChannel ->
          connected.countDown()
          socketChannel.close()
          closed.countDown()
        }
      }
    }
    runBlocking {
      SuspendingSocketChannel.open().use { channel ->
        channel.connect(serverChannel.localAddress)
        assertThat(connected.await(20, TimeUnit.MILLISECONDS)).isTrue()

        assertThat(closed.await(20, TimeUnit.MILLISECONDS)).isTrue()
        assertThrows(IOException::class.java) {
          runBlocking {
            while (true) {
              channel.writeFully(ByteBuffer.wrap("knock knock".toByteArray()))
            }
          }
        }
      }
    }
  }

  @Test
  fun testPeerClosing() {
    val connected = CountDownLatch(1)

    val serverChannel = SuspendingServerSocketChannel(AsynchronousServerSocketChannel.open().bind(InetSocketAddress("127.0.0.1", 0)))
    coroutineScope.launch {
      serverChannel.use {
        serverChannel.accept().use {
          connected.countDown()
        }
      }
    }
    runBlocking {
      SuspendingSocketChannel.open().use { channel ->
        channel.connect(serverChannel.localAddress)
      }
    }
    assertThat(connected.await(200, TimeUnit.MILLISECONDS)).isTrue()
  }

  private fun OutputStream.writeAndFlush(bytes: ByteArray) {
    write(bytes)
    flush()
  }

  private fun SocketChannel.readFully(buf: ByteBuffer) {
    while (buf.hasRemaining()) {
      if (read(buf) < 0) {
        throw IOException("Premature end of channel")
      }
    }
  }
}