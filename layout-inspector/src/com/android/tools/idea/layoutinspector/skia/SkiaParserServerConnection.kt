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
package com.android.tools.idea.layoutinspector.skia

import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.layoutinspector.proto.SkiaParser.GetViewTreeRequest
import com.android.tools.idea.layoutinspector.proto.SkiaParser.GetViewTreeResponse
import com.android.tools.idea.layoutinspector.proto.SkiaParser.InspectorView
import com.android.tools.idea.layoutinspector.proto.SkiaParser.RequestedNodeInfo
import com.android.tools.idea.layoutinspector.proto.SkiaParserServiceGrpc
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.protobuf.Empty
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.util.net.NetUtils
import com.android.tools.idea.io.grpc.ManagedChannel
import com.android.tools.idea.io.grpc.Status
import com.android.tools.idea.io.grpc.StatusRuntimeException
import com.android.tools.idea.io.grpc.netty.NettyChannelBuilder
import com.android.tools.idea.io.grpc.stub.StreamObserver
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.min

private const val INITIAL_DELAY_MILLI_SECONDS = 10L
private const val MAX_DELAY_MILLI_SECONDS = 1000L
private const val MAX_TIMES_TO_RETRY = 10
// TODO: optimize
private const val CHUNK_SIZE = 1024 * 1024

class SkiaParserServerConnection(private val serverPath: Path) {
  lateinit var client: SkiaParserServiceGrpc.SkiaParserServiceStub
  private lateinit var channel: ManagedChannel
  private var handler: OSProcessHandler? = null

  /**
   * Start the server if it isn't already running. This must be run before any other operations on this object.
   *
   * If the server is killed by another process we detect it with process.isAlive.
   * Note that this will be a sub process of Android Studio and will terminate when
   * Android Studio process is terminated.
   */
  fun runServer() {
    val localPort = createGrpcClient()
    handler = OSProcessHandler.Silent(GeneralCommandLine(serverPath.toAbsolutePath().toString(), localPort.toString())).apply {
      addProcessListener(object : ProcessAdapter() {
        override fun processTerminated(event: ProcessEvent) {
          if (event.exitCode == 0) {
            Logger.getInstance(SkiaParserServerConnection::class.java).info("SkiaServer terminated successfully")
          }
          else {
            Logger.getInstance(SkiaParserServerConnection::class.java)
              .warn("SkiaServer terminated exitCode: ${event.exitCode}  text: ${event.text}")
          }
        }

        override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
          Logger.getInstance(SkiaParserServerConnection::class.java).debug("SkiaServer willTerminate")
        }

        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
          Logger.getInstance(SkiaParserServerConnection::class.java).info("SkiaServer Message: ${event.text}")
        }
      })
      startNotify()
    }
  }

  @VisibleForTesting
  @WorkerThread
  fun createGrpcClient() : Int {
    // TODO: actually find and (re-)launch the server, and reconnect here if necessary.
    val localPort = NetUtils.findAvailableSocketPort()
    if (localPort < 0) {
      throw Exception("Unable to find available socket port")
    }

    channel = NettyChannelBuilder.forAddress("localhost", localPort)
      .usePlaintext()
      // TODO: chunk incoming images
      .maxInboundMessageSize(512 * 1024 * 1024 - 1)
      .build()
    client = SkiaParserServiceGrpc.newStub(channel).withWaitForReady()
    return localPort
  }

  @Slow
  fun shutdown() {
    val lock = CountDownLatch(1)
    client.shutdown(Empty.getDefaultInstance(), object: StreamObserver<Empty> {
      override fun onNext(ignore: Empty?) {
        lock.countDown()
      }

      override fun onError(error: Throwable?) {}
      override fun onCompleted() {}
    })
    if (!lock.await(10, TimeUnit.SECONDS)) {
      Logger.getInstance(SkiaParserServerConnection::class.java).warn(
        "Timed out waiting for skia parser shutdown. A skia-grpc-server process could have been orphaned.")
    }
    channel.shutdownNow()
    channel.awaitTermination(1, TimeUnit.SECONDS)
    handler?.destroyProcess()
  }


  @Slow
  @Throws(ParsingFailedException::class, UnsupportedPictureVersionException::class)
  fun getViewTree(data: ByteArray, requestedNodes: Iterable<RequestedNodeInfo>, scale: Double): Pair<InspectorView, Map<Int, ByteString>> {
    ping()
    return getViewTreeImpl(data, requestedNodes, scale)
  }

  @Slow
  fun ping() {
    var tries = 0
    var delay = INITIAL_DELAY_MILLI_SECONDS
    var lastException: Throwable? = null
    while (tries < MAX_TIMES_TO_RETRY) {
      try {
        val lock = CountDownLatch(1)
        client.ping(Empty.getDefaultInstance(), object: StreamObserver<Empty> {
          override fun onNext(ignore: Empty?) {
            lock.countDown()
          }

          override fun onError(error: Throwable?) {
            error?.printStackTrace()
            lastException = error
          }
          override fun onCompleted() {}
        })

        if (lock.await(1, TimeUnit.SECONDS)) {
          return
        }
      }
      catch (ignore: TimeoutException) {
        // try again
        lastException = ignore
      }
      catch (ex: StatusRuntimeException) {
        if (ex.status.code != Status.Code.UNAVAILABLE) {
          throw ex
        }
        lastException = ex
      }
      Thread.sleep(delay)
      tries++
      delay = min(2 * delay, MAX_DELAY_MILLI_SECONDS)
    }
    throw lastException!!
  }

  private fun getViewTreeImpl(
    data: ByteArray,
    requestedNodes: Iterable<RequestedNodeInfo>,
    scale: Double
  ): Pair<InspectorView, Map<Int, ByteString>> {
    val responseFuture = CompletableFuture<InspectorView>()
    val images = mutableMapOf<Int, ByteString>()

    val requestObserver = client.getViewTree2(object: StreamObserver<GetViewTreeResponse> {
      private var lastResponse: GetViewTreeResponse? = null

      override fun onNext(response: GetViewTreeResponse) {
        lastResponse = response
        if (response.imageId > 0) {
          images[response.imageId] = response.image
        }
      }

      override fun onError(error: Throwable?) {
        error?.printStackTrace()
        responseFuture.completeExceptionally(error)
      }

      override fun onCompleted() {
        val root = lastResponse?.root
        if (root != null) {
          responseFuture.complete(root)
        }
        else {
          responseFuture.completeExceptionally(ParsingFailedException())
        }
      }
    })!!

    for (offset in data.indices step CHUNK_SIZE) {
      val size = min(CHUNK_SIZE, data.size - offset)
      val requestBuilder = GetViewTreeRequest.newBuilder()
        .setVersion(2)
        .setTotalSize(data.size)
        .setSkp(ByteString.copyFrom(data, offset, size))
      if (offset + size == data.size) {
        // this is the last request, add the rest of the data
        requestBuilder.addAllRequestedNodes(requestedNodes).scale = scale.toFloat()
        requestObserver.onNext(requestBuilder.build())
        requestObserver.onCompleted()
      }
      else {
        requestObserver.onNext(requestBuilder.build())
      }
    }

    val root = try {
      responseFuture.get()
    }
    catch (executionException: ExecutionException) {
      throw executionException.cause ?: executionException
    }
    return Pair(root, images)
  }
}