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
package com.android.tools.profilers.cpu.perfetto

import com.intellij.ide.browsers.BrowserLauncher
import org.apache.http.HttpStatus
import org.apache.http.config.SocketConfig
import org.apache.http.entity.ContentType
import org.apache.http.entity.FileEntity
import org.apache.http.impl.bootstrap.ServerBootstrap
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

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

  private var server: HttpServer? = null
  fun loadTrace(traceFile: File) {
    server?.stop() // TODO(297379481): close the server automatically once it fully serves one request
    server = HttpServer(traceFile).also { it.start() }
    val urlEncodedFileName = URLEncoder.encode(traceFile.name, Charsets.UTF_8)
    BrowserLauncher.instance.browse(URI.create("$origin/#!/?url=http://127.0.0.1:$port/$urlEncodedFileName")) }
}

private class HttpServer(traceFile: File) {
  private var server = ServerBootstrap
    .bootstrap()
    .setListenerPort(port)
    .setSocketConfig(SocketConfig.custom().setSoReuseAddress(true).setSoKeepAlive(true).build())
    .registerHandler("/${traceFile.name}") { _, response, _ ->
      response.setStatusCode(HttpStatus.SC_OK)
      response.setHeader("Access-Control-Allow-Origin", origin)
      response.setHeader("Cache-Control", "no-cache")
      response.entity = FileEntity(traceFile, ContentType.DEFAULT_BINARY)
    }.create()

  fun start() = server.start()

  fun stop() = server.shutdown(5, TimeUnit.SECONDS) // grace period to finish any currently running downloads if any are present
}