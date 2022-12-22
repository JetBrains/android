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
package com.android.tools.idea.streaming.device

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Test
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets.UTF_8

/**
 * Tests for [SuspendingServerSocketChannel] and [SuspendingSocketChannel].
 */
class SuspendingNetworkChannelTest {
  // TODO: add tests for
  //     - timeouts passed to various read/write methods work as expected
  //     - cancellation cancels all pending operations and closes the underlying channel
  private val coroutineScope = CoroutineScope(Dispatchers.IO)

  @After
  fun tearDown() {
    coroutineScope.cancel(null)
  }

  @Test
  fun testBasicFunctions() {
    val inputBuffer = ByteBuffer.allocate(20)
    val steps = Array(5) { CountDownLatch(1) }
    val done = CountDownLatch(1)

    val serverChannel = SuspendingServerSocketChannel(AsynchronousServerSocketChannel.open().bind(InetSocketAddress("localhost", 0)))
    coroutineScope.launch {
      serverChannel.use {
        serverChannel.accept().use { socketChannel ->
          steps[0].countDown()
          socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
          socketChannel.read(inputBuffer)
          assertThat(inputBuffer.position()).isEqualTo(8)
          steps[1].countDown()
          socketChannel.readFully(inputBuffer)
          steps[2].countDown()
          val buf = ByteBuffer.wrap("12345678".toByteArray(UTF_8))
          socketChannel.write(buf)
          assertThat(buf.position()).isEqualTo(8)
          steps[3].countDown()
          socketChannel.writeFully(ByteBuffer.wrap("abcdefghijkl".toByteArray(UTF_8)))
          steps[4].countDown()
          done.await(20, TimeUnit.SECONDS)
        }
      }
    }

    assertThat(steps[0].await(200, TimeUnit.MILLISECONDS)).isFalse() // serverChannel.accept() should not return yet.
    val channel = SocketChannel.open(serverChannel.localAddress)
    assertThat(steps[0].await(200, TimeUnit.MILLISECONDS)).isTrue() // serverChannel.accept() should return.
    val out = channel.socket().getOutputStream()
    assertThat(steps[1].await(200, TimeUnit.MILLISECONDS)).isFalse() // socketChannel.read(buffer) should not return yet.
    out.writeAndFlush("12345678".toByteArray(UTF_8))
    assertThat(steps[1].await(200, TimeUnit.MILLISECONDS)).isTrue() // socketChannel.read(buffer) should return.
    assertThat(inputBuffer.position()).isEqualTo(8)
    out.writeAndFlush("abcdefgh".toByteArray(UTF_8))
    assertThat(steps[2].await(200, TimeUnit.MILLISECONDS)).isFalse() // socketChannel.readFully(buffer) should not return yet.
    out.writeAndFlush("ijkl".toByteArray(UTF_8))
    assertThat(steps[2].await(200, TimeUnit.MILLISECONDS)).isTrue() // socketChannel.readFully(buffer) should return.
    assertThat(inputBuffer.hasRemaining()).isFalse()
    assertThat(inputBuffer.array().toString(UTF_8)).isEqualTo("12345678abcdefghijkl")

    val buf = ByteArray(20)
    channel.readFully(ByteBuffer.wrap(buf, 0, 8))
    assertThat(steps[3].await(200, TimeUnit.MILLISECONDS)).isTrue() // socketChannel.write(outputBuffer) should return.
    channel.readFully(ByteBuffer.wrap(buf, 8, 8))
    channel.readFully(ByteBuffer.wrap(buf, 16, 4))
    assertThat(steps[4].await(200, TimeUnit.MILLISECONDS)).isTrue() // socketChannel.writeFully(outputBuffer) should return.
    assertThat(buf.toString(UTF_8)).isEqualTo("12345678abcdefghijkl")
    done.countDown() // Allow the socket channel to close.
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