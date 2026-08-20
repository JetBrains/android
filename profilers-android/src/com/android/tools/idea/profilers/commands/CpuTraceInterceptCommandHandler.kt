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

import androidx.tracing.perfetto.handshake.PerfettoSdkHandshake
import androidx.tracing.perfetto.handshake.protocol.ResponseResultCodes.RESULT_CODE_ALREADY_ENABLED
import androidx.tracing.perfetto.handshake.protocol.ResponseResultCodes.RESULT_CODE_ERROR_BINARY_MISSING
import androidx.tracing.perfetto.handshake.protocol.ResponseResultCodes.RESULT_CODE_ERROR_BINARY_VERIFICATION_ERROR
import androidx.tracing.perfetto.handshake.protocol.ResponseResultCodes.RESULT_CODE_ERROR_BINARY_VERSION_MISMATCH
import androidx.tracing.perfetto.handshake.protocol.ResponseResultCodes.RESULT_CODE_ERROR_OTHER
import androidx.tracing.perfetto.handshake.protocol.ResponseResultCodes.RESULT_CODE_SUCCESS
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.ide.common.repository.GMAVEN_BASE_URL
import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.deviceToDeviceInfo
import com.android.tools.idea.io.IdeFileService
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Trace
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.android.tools.profilers.cpu.TraceMerger
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.google.common.annotations.VisibleForTesting
import com.google.gson.stream.JsonReader
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.PerfettoSdkHandshakeMetadata
import com.google.wireless.android.sdk.stats.PerfettoSdkHandshakeMetadata.HandshakeResult
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtilRt
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.TimeUnit

// Helper class to format artifact to maven url.
data class Artifact(val groupId: String, val artifactId: String, val version: String?) {

  val fileName = "${artifactId}-${version}.aar"

  fun toGMavenUrl() = URL("${GMAVEN_BASE_URL}/${groupId.replace('.', '/')}/${artifactId}/${version}/${fileName}")
}

// Command handler that triggers on perfetto traces. This enables the tracing of apps that use the perfetto SDK.
class CpuTraceInterceptCommandHandler(val device: IDevice, private val transportStub: TransportServiceGrpc.TransportServiceBlockingStub) :
  TransportProxy.ProxyCommandHandler {

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
      Commands.Command.CommandType.START_TRACE -> {
        TraceType.from(command.startTrace.configuration) == TraceType.PERFETTO
      }
      // The overhead of enabling the SDK tracing is minimal, we do not need to issue
      // a broadcast to disable it.
      else -> false
    }
  }

  override fun execute(command: Commands.Command): Transport.ExecuteResponse {
    assert(command.type == Commands.Command.CommandType.START_TRACE)
    setUpComposeTracing(command)
    return transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(command).build())
  }

  companion object {
    private val SAFE_PACKAGE = Regex("[a-zA-Z0-9._]+")
    private val SAFE_VERSION = Regex("[A-Za-z0-9._+\\-]{1,64}")
  }

  private fun setUpComposeTracing(command: Commands.Command) {
    val appName = command.startTrace.configuration.appName
    if (appName == null) {
      log.warn("Skipping Perfetto-SDK handshake: appName is null")
      return
    }

    if (!SAFE_PACKAGE.matches(appName)) {
      log.warn("Skipping Perfetto-SDK handshake: appName is not a valid package name")
      return
    }

    var handshakeResult = HandshakeResult.UNKNOWN_RESULT

    /*
     * --- TRACING 2.0 START BROADCAST ---
     * Try to initiate Tracing 2.0 by sending the START broadcast to the target app.
     * If this succeeds (resultCode == 1), the app will initialize its internal buffers and start recording
     * Compose traces directly to its local memory, completely bypassing the OS-level Perfetto daemon.
     * In this case, we set an active session flag so CpuTraceStopCommandHandler knows to pull the traces later,
     * and we immediately return to skip the legacy Tracing 1.0 handshake.
     * If unhandled (resultCode == 0), the app does NOT have the androidx.tracing.profiler library linked,
     * so we gracefully fall back to the legacy Tracing 1.0 setup.
     * If failed (resultCode < 0), we still set the active flag to attempt to pull partial data if possible,
     * and we show an explicit UI warning balloon to the developer.
     */
    try {
      val startBroadcastCommand =
        "am broadcast -a androidx.tracing.profiler.action.START -n $appName/androidx.tracing.profiler.ConnectedProfilerTracingReceiver"
      val receiver = CollectingOutputReceiver()
      device.executeShellCommand(startBroadcastCommand, receiver, 5, TimeUnit.SECONDS)

      val output = receiver.output
      val resultRegex = Regex("result=(-?\\d+)")
      val match = resultRegex.find(output)
      val resultCodeStr = match?.groupValues?.get(1)
      val resultCode = resultCodeStr?.toIntOrNull()

      if (resultCode != null) {
        when (resultCode) {
          1 -> {
            log.info("Tracing 2.0 START successful for $appName.")
            TraceMerger.markAsTracingV2(command.pid.toLong(), appName)
            return
          }
          0 -> {
            log.info("Tracing 2.0 START unhandled (result 0) for $appName. Falling back to Tracing 1.0.")
          }
          else -> {
            log.warn("Tracing 2.0 START returned error code $resultCode for $appName. Still setting active flag to pull partial data.")
            NotificationGroupManager.getInstance()
              .getNotificationGroup("Android Notification Group")
              ?.createNotification(
                "Compose Tracing Error",
                "Tracing initialization returned $resultCode. Final merged trace might contain partial or no Compose data.",
                NotificationType.WARNING,
              )
              ?.notify(null)
            TraceMerger.markAsTracingV2(command.pid.toLong(), appName)
            return
          }
        }
      } else {
        log.warn("Tracing 2.0 START returned unparsable output: $output. Skipping Compose Tracing setup.")
        return
      }
    } catch (e: Exception) {
      log.warn("Exception during Tracing 2.0 START broadcast. Skipping Compose Tracing setup.", e)
      return
    }
    // --- END TRACING 2.0 START BROADCAST ---

    try {
      val handshake =
        PerfettoSdkHandshake(
          targetPackage = appName,
          // Kotlin doesn't have a native json parser. As such a handler needs to be created to
          // map the broadcast output to a key/value pair for the library.
          parseJsonMap = { jsonString: String ->
            sequence {
              JsonReader(StringReader(jsonString)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) yield(reader.nextName() to reader.nextString())
                reader.endObject()
              }
            }
              .toMap()
          },
          // The library doesn't have details about communicating with a device.
          // This callback is used to issue commands to the device and capture the output.
          executeShellCommand = {
            val receiver = CollectingOutputReceiver()
            device.executeShellCommand(it, receiver, 5, TimeUnit.SECONDS)
            receiver.output
          },
        )
      // Try to enable the perfetto library using the app's built-in binary
      // If that fails try to download the requested version and
      // enable tracing using that library
      val isStartupTracing = command.startTrace.configuration.initiationType == Trace.TraceInitiationType.INITIATED_BY_STARTUP
      var response = enableTracing(handshake, isStartupTracing, null)
      if (response.resultCode == RESULT_CODE_ERROR_BINARY_MISSING) {
        // note: requiredVersion (below) is guaranteed to be non-null for resultCode == RESULT_CODE_ERROR_BINARY_MISSING
        val path = resolveArtifact(checkNotNull(response.requiredVersion))
        if (path != null) {
          val baseApk = File(path.toUri())
          val libraryProvider =
            PerfettoSdkHandshake.LibrarySource.aarLibrarySource(baseApk, File(FileUtilRt.getTempDirectory())) { tmpFile, dstFile ->
              device.pushFile(tmpFile.absolutePath, dstFile.absolutePath)
            }
          // provide binaries and retry
          response = enableTracing(handshake, isStartupTracing, libraryProvider)
        }
      }

      // process the response
      lastResponseCode = response.resultCode
      val error =
        when (response.resultCode) {
          0 ->
            "The broadcast to enable tracing was not received. This most likely means " +
              "that the app does not contain the `androidx.tracing.tracing-perfetto` " +
              "library as its dependency."
          RESULT_CODE_SUCCESS -> null
          RESULT_CODE_ALREADY_ENABLED -> "Perfetto SDK already enabled."
          RESULT_CODE_ERROR_BINARY_MISSING ->
            "Perfetto SDK binary dependencies missing. " + "Required version: ${response.requiredVersion}. " + "Error: ${response.message}."
          RESULT_CODE_ERROR_BINARY_VERSION_MISMATCH ->
            "Perfetto SDK binary mismatch. " + "Required version: ${response.requiredVersion}. " + "Error: ${response.message}."
          RESULT_CODE_ERROR_BINARY_VERIFICATION_ERROR ->
            "Perfetto SDK binary verification failed. " +
              "Required version: ${response.requiredVersion}. " +
              "Error: ${response.message}. " +
              "If working with an unreleased snapshot, ensure all modules are built " +
              "against the same snapshot (e.g. clear caches and rebuild)."
          RESULT_CODE_ERROR_OTHER -> "Error: ${response.message}."
          else -> throw RuntimeException("Unrecognized exit code: ${response.resultCode}.")
        }
      if (error != null) {
        log.warn(error)
      }

      when (response.resultCode) {
        0 -> handshakeResult = HandshakeResult.UNSUPPORTED
        RESULT_CODE_SUCCESS -> handshakeResult = HandshakeResult.SUCCESS
        RESULT_CODE_ALREADY_ENABLED -> handshakeResult = HandshakeResult.ALREADY_ENABLED
        RESULT_CODE_ERROR_BINARY_MISSING -> handshakeResult = HandshakeResult.ERROR_BINARY_UNAVAILABLE
        RESULT_CODE_ERROR_BINARY_VERSION_MISMATCH -> handshakeResult = HandshakeResult.ERROR_BINARY_VERSION_MISMATCH
        RESULT_CODE_ERROR_BINARY_VERIFICATION_ERROR -> handshakeResult = HandshakeResult.ERROR_BINARY_VERIFICATION_ERROR
        RESULT_CODE_ERROR_OTHER -> handshakeResult = HandshakeResult.ERROR_OTHER
      }
    } catch (t: Throwable) {
      // Due to a bug in the current library an exception may be thrown that is a no-op.
      if (t.message == null || !t.message!!.contains("result=0")) {
        log.warn(t)
      }
    } finally {
      UsageTracker.log(
        AndroidStudioEvent.newBuilder()
          .setKind(AndroidStudioEvent.EventKind.ANDROID_PROFILER)
          .setDeviceInfo(deviceToDeviceInfo(device))
          .setAndroidProfilerEvent(
            AndroidProfilerEvent.newBuilder()
              .setType(AndroidProfilerEvent.Type.PERFETTO_SDK_HANDSHAKE)
              .setPerfettoSdkHandshakeMetadata(PerfettoSdkHandshakeMetadata.newBuilder().setHandshakeResult(handshakeResult))
          )
          .also { lastMetricsEvent = it }
      )
    }
  }

  private fun enableTracing(
    handshake: PerfettoSdkHandshake,
    isStartupTracing: Boolean,
    libraryProvider: PerfettoSdkHandshake.LibrarySource?,
  ) =
    when {
      isStartupTracing -> handshake.enableTracingColdStart(persistent = false, libraryProvider)
      else -> handshake.enableTracingImmediate(libraryProvider)
    }

  private fun resolveArtifact(artifactVersion: String): Path? {
    if (!SAFE_VERSION.matches(artifactVersion)) {
      log.warn("Rejecting Perfetto-SDK requiredVersion from device: '$artifactVersion'")
      return null
    }
    val artifact = Artifact("androidx.tracing", "tracing-perfetto-binary", artifactVersion)
    return try {
      val tmpDir = IdeFileService("profiler-artifacts").getOrCreateTempDir("http-tmp").normalize()
      val tmpFile = tmpDir.resolve(artifact.fileName).normalize()
      require(tmpFile.startsWith(tmpDir)) { "perfetto-binary download path escaped $tmpDir: ${artifact.fileName}" }
      log.debug("StudioDownloader downloading: ${artifact.fileName}")
      StudioDownloader()
        .downloadFullyWithCaching(
          artifact.toGMavenUrl(),
          tmpFile,
          null,
          StudioLoggerProgressIndicator(CpuTraceInterceptCommandHandler::class.java),
        )
      tmpFile
    } catch (e: IOException) {
      log.warn("Error downloading Perfetto-SDK binary:", e)
      null
    } catch (e: IllegalArgumentException) {
      log.warn("Error resolving Perfetto-SDK binary path:", e)
      null
    }
  }
}
