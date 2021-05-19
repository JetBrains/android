/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.adb.wireless

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.catching
import com.android.tools.idea.concurrency.transform
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.InvalidDataException
import com.intellij.util.LineSeparator
import java.awt.Color
import java.net.InetAddress
import java.util.concurrent.Executor

@UiThread
class AdbDevicePairingServiceImpl(private val randomProvider: RandomProvider,
                                  private val adbService: AdbServiceWrapper,
                                  taskExecutor: Executor) : AdbDevicePairingService {
  private val LOG = logger<AdbDevicePairingServiceImpl>()
  private val taskExecutor = FutureCallbackExecutor.wrap(taskExecutor)
  private val studioServiceNamePrefix = "studio-"


  override fun checkMdnsSupport(): ListenableFuture<MdnsSupportState> {
    // TODO: Investigate updating (then using) ddmlib instead of spawning an adb client command, so that
    //       we don't have to rely on parsing command line output
    LOG.info("Checking if mDNS is supported (`adb mdns check` command)")
    val futureResult = adbService.executeCommand(listOf("mdns", "check"))
    return futureResult.transform(taskExecutor) { result ->
      when {
        result.errorCode != 0 -> {
          LOG.warn("`adb mdns check` returned a non-zero error code (${result.errorCode})")
          val isUnknownCommand = result.stderr.any { line -> line.contains(Regex("unknown.*command")) }
          if (isUnknownCommand)
            MdnsSupportState.AdbVersionTooLow
          else
            MdnsSupportState.AdbInvocationError
        }
        result.stdout.isEmpty() -> {
          LOG.warn("`adb mdns check` returned an empty output (why?)")
          MdnsSupportState.AdbInvocationError
        }
        // See https://android-review.googlesource.com/c/platform/system/core/+/1274009/5/adb/client/transport_mdns.cpp#553
        result.stdout.any { it.contains("mdns daemon version") } -> {
          MdnsSupportState.Supported
        }
        else -> {
          MdnsSupportState.NotSupported
        }
      }
    }.catching(taskExecutor, Throwable::class.java) { t ->
      LOG.warn("Error executing `adb mdns check`", t)
      MdnsSupportState.AdbInvocationError
    }.transform(taskExecutor) { supportState ->
      // This `tansform` is just for logging purposes
      LOG.info("Checking if mDNS is supportState result: ${supportState}")
      supportState
    }
  }

  override fun generateQrCode(backgroundColor: Color, foregroundColor: Color): ListenableFuture<QrCodeImage> {
    return taskExecutor.executeAsync {
      val serviceName = studioServiceNamePrefix + createRandomString(10)
      val password = createRandomString(12)
      val pairingString = createPairingString(serviceName, password)
      val image = QrCodeGenerator.encodeQrCodeToImage(pairingString, backgroundColor, foregroundColor)
      QrCodeImage(serviceName, password, pairingString, image)
    }
  }

  override fun devices(): ListenableFuture<List<AdbDevice>> {
    return Futures.immediateFuture(emptyList())
  }

  override fun scanMdnsServices(): ListenableFuture<List<MdnsService>> {
    // TODO: Investigate updating (then using) ddmlib instead of spawning an adb client command, so that
    //       we don't have to rely on parsing command line output
    val futureResult = adbService.executeCommand(listOf("mdns", "services"))
    return futureResult.transform(taskExecutor) { result ->
      // Output example:
      //  List of discovered mdns services
      //  adb-939AX05XBZ-vWgJpq	_adb-tls-connect._tcp.	192.168.1.86:39149
      //  adb-939AX05XBZ-vWgJpq	_adb-tls-pairing._tcp.	192.168.1.86:37313
      // Regular expression
      //  adb-<everything-until-space><spaces>__adb-tls-pairing._tcp.<spaces><everything-until-colon>:<port>
      val lineRegex = Regex("([^\\t]+)\\t*_adb-tls-pairing._tcp.\\t*([^:]+):([0-9]+)")

      if (result.errorCode != 0) {
        throw AdbCommandException("Error discovering services", result.errorCode, result.stderr)
      }

      if (result.stdout.isEmpty()) {
        throw AdbCommandException("Empty output from \"adb mdns services\" command", -1, result.stderr)
      }

      return@transform result.stdout
        .drop(1)
        .mapNotNull { line ->
          val matchResult = lineRegex.find(line)
          matchResult?.let {
            try {
              val serviceName = it.groupValues[1]
              val ipAddress = InetAddress.getByName(it.groupValues[2])
              val port = it.groupValues[3].toInt()
              val serviceType = if (serviceName.startsWith(studioServiceNamePrefix)) ServiceType.QrCode else ServiceType.PinCode
              MdnsService(serviceName, serviceType, ipAddress, port)
            }
            catch (ignored: Exception) {
              LOG.warn("mDNS service entry ignored due do invalid characters: ${line}")
              null
            }
          }
        }
    }
  }

  override fun pairMdnsService(mdnsService: MdnsService, password: String): ListenableFuture<PairingResult> {
    LOG.info("Start mDNS pairing: ${mdnsService}")

    val deviceAddress = "${mdnsService.ipAddress.hostAddress}:${mdnsService.port}"
    // TODO: Update this when password can be passed as an argument
    val passwordInput = password + LineSeparator.getSystemLineSeparator().separatorString
    // TODO: Investigate updating (then using) ddmlib instead of spawning an adb client command, so that
    //       we don't have to rely on parsing command line output
    val futureResult = adbService.executeCommand(listOf("pair", deviceAddress), passwordInput)

    return futureResult.transform(taskExecutor) { result ->
      LOG.info("mDNS pairing exited with code ${result.errorCode}")
      result.stdout.take(5).forEachIndexed { index, line ->
        LOG.info("  stdout line #$index: $line") }

      if (result.errorCode != 0) {
        throw AdbCommandException("Error pairing device", result.errorCode, result.stderr)
      }

      if (result.stdout.isEmpty()) {
        throw AdbCommandException("Empty output from \"adb pair\" command", -1, result.stderr)
      }

      // Output example:
      //  Enter pairing code: Successfully paired to 192.168.1.86:41915 [guid=adb-939AX05XBZ-vWgJpq]
      // Regular expression
      //  <Prefix><everything-until-colon>:<port>[guid=<everything-until-close-bracket>]
      val lineRegex = Regex("Successfully paired to ([^:]*):([0-9]*) \\[guid=([^\\]]*)\\]")
      val matchResult = lineRegex.find(result.stdout[0])
      matchResult?.let {
        try {
          val ipAddress = InetAddress.getByName(it.groupValues[1])
          val port = it.groupValues[2].toInt()
          val serviceGuid = it.groupValues[3]
          PairingResult(ipAddress, port, serviceGuid)
        }
        catch (e: Exception) {
          throw InvalidDataException("Pairing result is invalid", e)
        }
      } ?: throw InvalidDataException("Pairing result is invalid")
    }
  }

  override fun waitForDevice(pairingResult: PairingResult): ListenableFuture<AdbOnlineDevice> {
    return adbService.waitForOnlineDevice(pairingResult)
  }

  /**
   * Format is "WIFI:T:ADB;S:service;P:password;;" (without the quotes)
   */
  private fun createPairingString(service: String, password: String): String {
    return "WIFI:T:ADB;S:${service};P:${password};;"
  }

  private fun createRandomString(charCount: Int): String {
    @Suppress("SpellCheckingInspection")
    val charSet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-+*/<>{}"
    val sb = StringBuilder()
    for (i in 1..charCount) {
      val char = charSet[randomProvider.nextInt(charSet.length)]
      sb.append(char)
    }
    return sb.toString()
  }
}
