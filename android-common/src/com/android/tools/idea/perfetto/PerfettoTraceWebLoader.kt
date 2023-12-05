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
package com.android.tools.idea.perfetto

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.apache.http.HttpStatus
import org.apache.http.config.SocketConfig
import org.apache.http.entity.ContentType
import org.apache.http.entity.FileEntity
import org.apache.http.impl.bootstrap.ServerBootstrap
import org.jetbrains.ide.PooledThreadExecutor
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Kotlin equivalent of https://github.com/google/perfetto/blob/49ef5c5916fc1304549b681a1129a7a85c82db9f/tools/open_trace_in_ui
 */

// We reuse the HTTP+RPC port because it's the only one allowed by the CSP
private const val port = 9001
private const val origin = "https://ui.perfetto.dev"

/**
 * Opens a trace in Perfetto Web UI (in the browser).
 *
 * Spins up a temporary web server to provide the trace to Perfetto Web UI.
 * See [https://github.com/google/perfetto/blob/49ef5c5916fc1304549b681a1129a7a85c82db9f/tools/open_trace_in_ui] for more information.
 */
object PerfettoTraceWebLoader {
  const val FEATURE_REGISTRY_KEY = "profiler.trace.open.mode.web" // determines whether the feature is enabled via Registry.is(<this key>)
  const val TRACE_HANDLED_CAPTION = "The trace has been handed over to the Perfetto Web UI (ui.perfetto.dev)"

  private val taskExecutor = PooledThreadExecutor.INSTANCE
  private val requestQueue = Channel<File>(capacity = Channel.UNLIMITED)
  private var server: HttpServer? = null

  private val logger = Logger.getInstance(PerfettoTraceWebLoader::class.java)

  val handledTraceIds = mutableSetOf<Long>()

  init {
    CoroutineScope(taskExecutor.asCoroutineDispatcher()).launch {
      while (true) {
        // launch a transient web server to provide the trace file to ui.perfetto.dev (file://... is not supported, it must be http://...)
        val traceFile = requestQueue.receive()
        val requestReceivedLatch = Job() // allows us to wait until the request starts getting processed by the web server
        server = HttpServer(traceFile, requestReceivedLatch).also { it.start() }

        // open ui.perfetto.dev in the browser (with trace file url passed as an url parameter)
        val urlEncodedFileName = URLEncoder.encode(traceFile.name, Charsets.UTF_8)
        BrowserLauncher.instance.browse(
          URI.create("$origin/#!/?url=http://127.0.0.1:$port/$urlEncodedFileName&referrer=android_studio_desktop"))

        // Wait until the request has been received and order the server to gracefully shut down (requests being processed will be allowed
        // to finish before the server is completely stopped)
        requestReceivedLatch.join()
        server?.stop()
        server = null
      }
    }
  }

  fun loadTrace(traceFile: File, traceId: Long? = null) {
    if (traceId != null) handledTraceIds.add(traceId)

    val result = requestQueue.trySend(traceFile)
    if (!result.isSuccess) logger.error("Failed to load a trace file ${traceFile.name}", result.exceptionOrNull())
  }
}

private class HttpServer(traceFile: File, requestReceivedLatch: CompletableJob) {
  private var server = ServerBootstrap
    .bootstrap()
    .setListenerPort(port)
    .setSocketConfig(SocketConfig.custom().setSoReuseAddress(true).setSoKeepAlive(true).build())
    .registerHandler("/${traceFile.name}") { _, response, _ ->
      response.setStatusCode(HttpStatus.SC_OK)
      response.setHeader("Access-Control-Allow-Origin", origin)
      response.setHeader("Cache-Control", "no-cache")
      response.entity = FileEntity(traceFile, ContentType.DEFAULT_BINARY)
      requestReceivedLatch.complete() // mark the request as now received and being processed by the server
    }.create()

  fun start() = server.start()

  fun stop() = server.shutdown(10, SECONDS) // grace period to finish any currently running downloads if any are present
}