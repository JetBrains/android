package com.android.tools.idea.logcat.service

import com.android.adblib.AdbDeviceServices
import com.android.adblib.DeviceSelector
import com.android.adblib.shellAsText
import com.android.adblib.utils.LineBatchShellCollector
import com.android.ddmlib.logcat.LogCatMessage
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.adb.processnamemonitor.ProcessNameMonitor
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.devices.Device
import com.intellij.openapi.Disposable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import java.time.Duration

/**
 * Implementation of a [LogcatService]
 */
internal class LogcatServiceImpl(
  private val disposableParent: Disposable,
  private val deviceServicesFactory: () -> AdbDeviceServices,
  private val processNameMonitor: ProcessNameMonitor,
) : LogcatService {

  override suspend fun readLogcat(device: Device): Flow<List<LogCatMessage>> {
    val deviceSelector = DeviceSelector.fromSerialNumber(device.serialNumber)
    @Suppress("OPT_IN_USAGE")
    return channelFlow {
      val messageAssembler = LogcatMessageAssembler(disposableParent, device.serialNumber, channel, processNameMonitor, coroutineContext)
      deviceServicesFactory().shell(deviceSelector, buildLogcatCommand(device), LineBatchShellCollector()).collect {
        messageAssembler.processNewLines(it)
      }

      // If the Logcat process quit`s with an error, there will be a pending message still left in the MessageAssembler.
      // This pending message will contain the last legitimate message terminated by a `\n\n` followed by the error text.
      // Here we extract the error message if it exists and log it as a system message.
      val message = messageAssembler.getAndResetPendingMessage()
      if (message != null) {
        val split = message.message.split("\n\n", limit = 2)
        if (split.size == 1) {
          channel.send(listOf(message))
        }
        else {
          channel.send(
            listOf(
              LogCatMessage(message.header, split[0]),
              LogCatMessage(SYSTEM_HEADER, split[1])),
          )
        }
      }
    }
  }

  override suspend fun clearLogcat(device: Device) {
    deviceServicesFactory().shellAsText(
      DeviceSelector.fromSerialNumber(device.serialNumber),
      "logcat -c",
      commandTimeout = Duration.ofSeconds(2))
  }
}

private fun buildLogcatCommand(device: Device): String {
  val command = StringBuilder("logcat -v long")
  if (device.sdk >= AndroidVersion.VersionCodes.N) {
    command.append(" -v epoch")
  }
  return command.toString()
}

