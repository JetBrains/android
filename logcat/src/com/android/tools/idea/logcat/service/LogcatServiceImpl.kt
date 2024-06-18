package com.android.tools.idea.logcat.service

import com.android.adblib.DeviceSelector
import com.android.adblib.LineBatchShellCollector
import com.android.adblib.shellAsText
import com.android.adblib.shellCommand
import com.android.processmonitor.monitor.ProcessNameMonitor
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.message.LogcatHeaderParser.LogcatFormat.EPOCH_FORMAT
import com.android.tools.idea.logcat.message.LogcatHeaderParser.LogcatFormat.STANDARD_FORMAT
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.util.LOGGER
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.io.EOFException
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.jetbrains.annotations.VisibleForTesting

/**
 * Last message in batch will be posted after a delay, to allow for more log lines if another batch
 * is pending.
 */
private const val LOGCAT_IDLE_TIMEOUT_MILLIS = 100L

/** Implementation of a [LogcatService] */
internal class LogcatServiceImpl
@VisibleForTesting
constructor(project: Project, private val lastMessageDelayMs: Long = LOGCAT_IDLE_TIMEOUT_MILLIS) :
  LogcatService {
  @Suppress("unused") // Used by XML registration
  constructor(project: Project) : this(project, LOGCAT_IDLE_TIMEOUT_MILLIS)

  private val deviceServices = AdbLibService.getSession(project).deviceServices
  private val processNameMonitor: ProcessNameMonitor =
    project.getService(ProcessNameMonitor::class.java)

  override suspend fun readLogcat(
    serialNumber: String,
    sdk: Int,
    duration: Duration,
    newMessagesOnly: Boolean,
  ): Flow<List<LogcatMessage>> {
    return when (sdk >= 35 && StudioFlags.LOGCAT_PROTOBUF_ENABLED.get()) {
      true -> readLogcatProtobuf(serialNumber, duration, newMessagesOnly)
      false -> readLogcatText(serialNumber, sdk, duration, newMessagesOnly)
    }
  }

  private suspend fun readLogcatText(
    serialNumber: String,
    sdk: Int,
    duration: Duration,
    newMessagesOnly: Boolean,
  ): Flow<List<LogcatMessage>> {
    val deviceSelector = DeviceSelector.fromSerialNumber(serialNumber)
    return channelFlow {
      val logcatFormat = logcatFormat(sdk)
      val cutoffTimeSupported = sdk >= 21

      /** [AndroidVersion.VersionCodes.LOLLIPOP] */
      val command = buildString {
        append("logcat -v long")
        if (logcatFormat == EPOCH_FORMAT) {
          append(" -v epoch")
        }
        if (cutoffTimeSupported && newMessagesOnly) {
          append(" -T 1")
        }
      }

      val cutoffTime =
        when {
          newMessagesOnly && !cutoffTimeSupported ->
            deviceServices
              .shellAsText(deviceSelector, "date +%s", commandTimeout = Duration.ofMillis(500))
              .stdout
              .trimEnd()
              .toLongOrNull()
          else -> null
        }

      val messageAssembler =
        LogcatMessageAssembler(
          serialNumber,
          logcatFormat,
          channel,
          processNameMonitor,
          coroutineContext,
          lastMessageDelayMs,
          cutoffTime,
        )
      try {
        try {
          deviceServices
            .shell(deviceSelector, command, LineBatchShellCollector(), commandTimeout = duration)
            .collect { messageAssembler.processNewLines(it) }
        } catch (e: TimeoutException) {
          LOGGER.debug { "Done collecting Logcat from device $serialNumber after $duration" }
          channel.close()
          return@channelFlow
        }

        // If the Logcat process quit`s with an error, there will be a pending message still left in
        // the MessageAssembler.
        // This pending message will contain the last legitimate message terminated by a `\n\n`
        // followed by the error text.
        // Here we extract the error message if it exists and log it as a system message.
        val message = messageAssembler.getAndResetLastMessage()
        if (message != null) {
          val split = message.message.split("\n\n", limit = 2)
          if (split.size == 1) {
            channel.send(listOf(message))
          } else {
            channel.send(
              listOf(
                LogcatMessage(message.header, split[0]),
                LogcatMessage(SYSTEM_HEADER, split[1]),
              )
            )
          }
        }
      } finally {
        Disposer.dispose(messageAssembler)
      }
    }
  }

  private suspend fun readLogcatProtobuf(
    serialNumber: String,
    duration: Duration,
    newMessagesOnly: Boolean,
  ): Flow<List<LogcatMessage>> {
    return channelFlow {
      val command = buildString {
        append("logcat --proto")
        if (newMessagesOnly) {
          append(" -T 1")
        }
      }

      val deviceSelector = DeviceSelector.fromSerialNumber(serialNumber)
      try {
        deviceServices
          .shellCommand(deviceSelector, command)
          .withCollector(LogcatProtoShellCollector(serialNumber, processNameMonitor))
          .execute()
          .collect { send(it) }
      } catch (e: EOFException) {
        LOGGER.debug { "Done collecting Logcat from device $serialNumber" }
        channel.close()
        return@channelFlow
      } catch (e: TimeoutException) {
        LOGGER.debug { "Done collecting Logcat from device $serialNumber after $duration" }
        channel.close()
        return@channelFlow
      }
    }
  }

  override suspend fun clearLogcat(serialNumber: String) {
    deviceServices.shellAsText(
      DeviceSelector.fromSerialNumber(serialNumber),
      "logcat -c",
      commandTimeout = Duration.ofSeconds(2),
    )
  }
}

private fun logcatFormat(sdk: Int) =
  if (sdk >= 24
  /** [AndroidVersion.VersionCodes.N] */
  ) EPOCH_FORMAT
  else STANDARD_FORMAT
