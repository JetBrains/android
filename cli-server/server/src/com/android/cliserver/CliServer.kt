/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.cliserver

import com.google.common.net.HttpHeaders
import io.grpc.Metadata
import io.grpc.Server
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

class CliServer(private val serverRegistry: CliServerRegistry, private val serverInfoProvider: ServerInfoProvider) {
  private val logger = Logger.getLogger(CliServer::class.java.name)
  var server: Server? = null
  private var sessionToken: String? = null

  private val commandHandlers = ConcurrentHashMap<Int, (CommandRequest) -> CommandResponse?>()

  fun registerCommandHandler(type: Int, handler: (CommandRequest) -> CommandResponse?) {
    if (commandHandlers.containsKey(type)) throw IllegalStateException("handler is already registered for $type")
    commandHandlers[type] = handler
  }

  fun start() {
    val loopback = InetAddress.getLoopbackAddress()
    server =
      NettyServerBuilder.forAddress(InetSocketAddress(loopback, 0)).addService(CliService()).intercept(AuthInterceptor()).build().apply {
        start()
        val port = port
        sessionToken = serverRegistry.register(port)
        logger.info("Server started, listening on $loopback:$port")
      }
  }

  fun stop() {
    server?.shutdown()
    serverRegistry.unregister()
    server = null
  }

  internal inner class CliService : StudioCliServiceGrpcKt.StudioCliServiceCoroutineImplBase() {
    override suspend fun executeCommand(request: CommandRequest): CommandResponse {
      val handler = commandHandlers[request.type]
      return handler?.let { handler(request) } ?: commandResponse { error = genericError { message = "${request.type} not implemented" } }
    }

    override suspend fun checkStatus(request: CheckStatusRequest): CheckStatusResponse {
      val serverInfo = serverInfoProvider.status()
      return checkStatusResponse {
        pid = ProcessHandle.current().pid()
        version = serverInfo.version
        projectStatus.addAll(
          serverInfo.projects.map { project ->
            projectStatus {
              name = project.name
              path = project.path
              status =
                when (project.status) {
                  ServerInfoProvider.ProjectStatus.UNKNOWN -> ProjectStatus.Status.UNKNOWN
                  ServerInfoProvider.ProjectStatus.NOT_READY -> ProjectStatus.Status.NOT_READY
                  ServerInfoProvider.ProjectStatus.READY -> ProjectStatus.Status.READY
                }
            }
          }
        )
      }
    }
  }

  private val headerKey = Metadata.Key.of(HttpHeaders.AUTHORIZATION, Metadata.ASCII_STRING_MARSHALLER)

  private inner class AuthInterceptor : ServerInterceptor {
    override fun <ReqT, RespT> interceptCall(
      call: ServerCall<ReqT, RespT>,
      headers: Metadata,
      next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
      val token = headers.get(headerKey)

      if (token == null || token != "Bearer $sessionToken") {
        call.close(Status.UNAUTHENTICATED.withDescription("Invalid or missing token"), Metadata())
        return object : ServerCall.Listener<ReqT>() {}
      }
      return next.startCall(call, headers)
    }
  }
}
