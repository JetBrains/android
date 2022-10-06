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
package com.android.tools.idea.profilers.commands

import androidx.tracing.perfetto.PerfettoHandshake
import androidx.tracing.perfetto.PerfettoHandshake.ResponseExitCodes.RESULT_CODE_ALREADY_ENABLED
import androidx.tracing.perfetto.PerfettoHandshake.ResponseExitCodes.RESULT_CODE_ERROR_BINARY_MISSING
import androidx.tracing.perfetto.PerfettoHandshake.ResponseExitCodes.RESULT_CODE_ERROR_BINARY_VERIFICATION_ERROR
import androidx.tracing.perfetto.PerfettoHandshake.ResponseExitCodes.RESULT_CODE_ERROR_BINARY_VERSION_MISMATCH
import androidx.tracing.perfetto.PerfettoHandshake.ResponseExitCodes.RESULT_CODE_ERROR_OTHER
import androidx.tracing.perfetto.PerfettoHandshake.ResponseExitCodes.RESULT_CODE_SUCCESS
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.ide.common.repository.GMAVEN_BASE_URL
import com.android.repository.api.ConsoleProgressIndicator
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.io.IdeFileService
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.stats.AndroidStudioUsageTracker
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.google.gson.stream.JsonReader
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.PerfettoSdkHandshakeMetadata
import com.google.wireless.android.sdk.stats.PerfettoSdkHandshakeMetadata.HandshakeResult
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtilRt
import org.assertj.core.util.VisibleForTesting
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Helper class to format artifact to maven url.
data class Artifact(
  val groupId: String,
  val artifactId: String,
  val version: String?,
) {

  val fileName = "${artifactId}-${version}.aar"

  fun toGMavenUrl() = URL(
    "${GMAVEN_BASE_URL}/${groupId.replace('.', '/')}/${artifactId}/${version}/${fileName}")
}

// Command handler that triggers on perfetto traces. This enables the tracing of apps that use the perfetto SDK.
class CpuTraceInterceptCommandHandler(val device: IDevice,
                                      private val transportStub: TransportServiceGrpc.TransportServiceBlockingStub)
  : TransportProxy.ProxyCommandHandler {

  // Field exists solely for testing (not used for any other reason)
  @VisibleForTesting
  var lastResponseCode: Int = -1
    private set

  // Field exists solely for testing (not used for any other reason)
  @VisibleForTesting
  var lastMetricsEvent: AndroidStudioEvent.Builder? = null
    private set

  private val log = Logger.getInstance(CpuTraceInterceptCommandHandler::class.java)

  override fun shouldHandle(command: Commands.Command): Boolean {
    // We only check perfetto traces.
    return when (command.type) {
      Commands.Command.CommandType.START_CPU_TRACE -> {
        command.startCpuTrace.configuration.userOptions.traceType == Cpu.CpuTraceType.PERFETTO
      }
      // The overhead of enabling the SDK tracing is minimal, we do not need to issue
      // a broadcast to disable it.
      else -> false
    }
  }

  override fun execute(command: Commands.Command): Transport.ExecuteResponse {
    assert(command.type == Commands.Command.CommandType.START_CPU_TRACE)
    enableTrackingCompose(command)
    return transportStub.execute(Transport.ExecuteRequest.newBuilder()
                                   .setCommand(command)
                                   .build())
  }

  private fun enableTrackingCompose(command: Commands.Command) {
    var handshakeResult = HandshakeResult.UNKNOWN_RESULT

    try {
      val handshake = PerfettoHandshake(
        targetPackage = command.startCpuTrace.configuration.appName,
        // Kotlin doesn't have a native json parser. As such a handler needs to be created to
        // map the broadcast output to a key/value pair for the library.
        parseJsonMap = { jsonString: String ->
          sequence {
            JsonReader(StringReader(jsonString)).use { reader ->
              reader.beginObject()
              while (reader.hasNext()) yield(reader.nextName() to reader.nextString())
              reader.endObject()
            }
          }.toMap()
        },
        // The library doesn't have details about communicating with a device.
        // This callback is used to issue commands to the device and capture the output.
        executeShellCommand = {
          val latch = CountDownLatch(1)
          val receiver = CollectingOutputReceiver(latch)
          device.executeShellCommand(it, receiver)
          latch.await(5, TimeUnit.SECONDS)
          receiver.output
        })
      // Try to enable the perfetto library using a built-in binary
      // If that fails try to download the requested version and
      // enable tracing using that library
      val response = handshake.enableTracing(null).let {
        if (it.exitCode == RESULT_CODE_ERROR_BINARY_MISSING) {
          val path = resolveArtifact(it.requiredVersion)
          if (path == null) {
            handshakeResult = HandshakeResult.ERROR_BINARY_UNAVAILABLE
            log.warn("Failed to download tracing-perfetto-binary ")
            return
          }
          val baseApk = File(path.toUri())
          val libraryProvider = PerfettoHandshake.ExternalLibraryProvider(baseApk, File(FileUtilRt.getTempDirectory())
          ) { tmpFile, dstFile ->
            device.pushFile(tmpFile.absolutePath, dstFile.absolutePath)
          }
          handshake.enableTracing(libraryProvider)
        } // provide binaries and retry
        else
          it // no retry
      }

      // process the response
      lastResponseCode = response.exitCode
      val error = when (response.exitCode) {
        0 -> "The broadcast to enable tracing was not received. This most likely means " +
             "that the app does not contain the `androidx.tracing.tracing-perfetto` " +
             "library as its dependency."
        RESULT_CODE_SUCCESS -> null
        RESULT_CODE_ALREADY_ENABLED -> "Perfetto SDK already enabled."
        RESULT_CODE_ERROR_BINARY_MISSING ->
          "Perfetto SDK binary dependencies missing. " +
          "Required version: ${response.requiredVersion}. " +
          "Error: ${response.message}."
        RESULT_CODE_ERROR_BINARY_VERSION_MISMATCH ->
          "Perfetto SDK binary mismatch. " +
          "Required version: ${response.requiredVersion}. " +
          "Error: ${response.message}."
        RESULT_CODE_ERROR_BINARY_VERIFICATION_ERROR ->
          "Perfetto SDK binary verification failed. " +
          "Required version: ${response.requiredVersion}. " +
          "Error: ${response.message}. " +
          "If working with an unreleased snapshot, ensure all modules are built " +
          "against the same snapshot (e.g. clear caches and rebuild)."
        RESULT_CODE_ERROR_OTHER -> "Error: ${response.message}."
        else -> throw RuntimeException("Unrecognized exit code: ${response.exitCode}.")
      }
      if (error != null) {
        log.warn(error)
      }

      when (response.exitCode) {
        0 -> handshakeResult = HandshakeResult.UNSUPPORTED
        RESULT_CODE_SUCCESS -> handshakeResult = HandshakeResult.SUCCESS
        RESULT_CODE_ALREADY_ENABLED -> handshakeResult = HandshakeResult.ALREADY_ENABLED
        RESULT_CODE_ERROR_BINARY_VERSION_MISMATCH -> handshakeResult = HandshakeResult.ERROR_BINARY_VERSION_MISMATCH
        RESULT_CODE_ERROR_BINARY_VERIFICATION_ERROR -> handshakeResult = HandshakeResult.ERROR_BINARY_VERIFICATION_ERROR
        RESULT_CODE_ERROR_OTHER -> handshakeResult = HandshakeResult.ERROR_OTHER
      }
    }
    catch (t: Throwable) {
      // Due to a bug in the current library an exception may be thrown that is a no-op.
      if (t.message == null || !t.message!!.contains("result=0")) {
        log.warn(t)
      }
    } finally {
      UsageTracker.log(
        AndroidStudioEvent.newBuilder()
          .setKind(AndroidStudioEvent.EventKind.ANDROID_PROFILER)
          .setDeviceInfo(AndroidStudioUsageTracker.deviceToDeviceInfo(device))
          .setAndroidProfilerEvent(
            AndroidProfilerEvent.newBuilder()
              .setType(AndroidProfilerEvent.Type.PERFETTO_SDK_HANDSHAKE)
              .setPerfettoSdkHandshakeMetadata(PerfettoSdkHandshakeMetadata.newBuilder().setHandshakeResult(handshakeResult))
          ).also { lastMetricsEvent = it }
      )
    }
  }

  private fun resolveArtifact(artifactVersion: String): Path? {
    val artifact = Artifact("androidx.tracing",
                            "tracing-perfetto-binary",
                            artifactVersion)
    return try {
      val tmpDir = IdeFileService("profiler-artifacts").getOrCreateTempDir("http-tmp")
      val tmpFile = tmpDir.resolve(artifact.fileName)
      log.debug("StudioDownloader downloading: ${artifact.fileName}")
      StudioDownloader().downloadFullyWithCaching(artifact.toGMavenUrl(),
                                                  tmpFile,
                                                  null,
                                                  ConsoleProgressIndicator())
      tmpFile
    }
    catch (e: IOException) {
      null
    }
  }
}