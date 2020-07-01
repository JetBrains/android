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
import com.google.common.util.concurrent.ListenableFuture
import java.awt.Color
import java.awt.image.BufferedImage
import java.net.InetAddress

/**
 * Service to expose and pair wireless devices. All entry points run asynchronously and
 * return [ListenableFuture] for completion.
 */
@UiThread
interface AdbDevicePairingService {
  /**
   * Generates a new [QrCodeImage] instance, with a new random service name and password
   */
  fun generateQrCode(backgroundColor: Color, foregroundColor: Color) : ListenableFuture<QrCodeImage>

  /**
   * Returns a snapshot of the list of [AdbDevice] currently known
   */
  fun devices() : ListenableFuture<List<AdbDevice>>

  /**
   * Look up the list of mDNS services currently seen by the underlying adb implementation
   */
  fun scanMdnsServices() : ListenableFuture<List<MdnsService>>

  /**
   * Pair a device through an mDNS service
   */
  fun pairMdnsService(mdnsService: MdnsService, password: String) : ListenableFuture<PairingResult>

  /**
   * Wait for device to be available from the underlying adb implementation
   */
  fun waitForDevice(pairingResult: PairingResult): ListenableFuture<AdbOnlineDevice>
}

/**
 * Result of pairing a device through "adb pair"
 */
data class PairingResult(
  /**
   * The IP address of the device that was paired
   */
  val ipAddress: InetAddress,
  /**
   * The TCP port that was used for pairing
   */
  val port: Int,
  /**
   * The ID of the mDNS service representing the device that was paired (e.g. "adb-939AX05XBZ-vWgJpq")
   */
  val mdnsServiceId: String) {
  /**
   * A user friendly string representation of the device
   */
  val displayString : String
    get() {
      return ipAddress.hostAddress + ":" + port
    }
}

/**
 * A device/service as exposed by the "adb mdns services" command
 */
data class MdnsService(val serviceName: String, val ipAddress: InetAddress, val port: Int) {
  fun isPairing(): Boolean {
    return serviceName.contains("pairing")
  }

  fun isConnect() : Boolean {
    return serviceName.contains("connect")
  }

  /**
   * A user friendly string representation of the device
   */
  val displayString : String
    get() {
      return ipAddress.hostAddress + ":" + port
    }
}

/**
 * Abstraction over an bitmap representation of a QrCode
 */
data class QrCodeImage(
  /**
   * The service name of this QR Code. This name will be exposed by ADB when the phone is ready to pair.
   */
  val serviceName: String,
  /**
   * The password of this QR Code. This password will be used when pairing the device.
   */
  val password: String,
  /**
   * The full pairing string, i.e. the string that is encoded in the [image]
   */
  val pairingString: String,
  /**
   * The QR Code [BufferedImage] representing [pairingString].
   *
   * The image has a white background and a black pixel for each QR Code "dot". There is also a white border of a few pixel
   * wide around the image to allow for cameras to frame the QR Code.
   */
  val image: BufferedImage)
