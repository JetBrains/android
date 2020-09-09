/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager

import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.devices.DeviceParser
import com.android.sdklib.devices.DeviceWriter
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.sdk.AndroidSdks
import com.android.utils.ILogger
import com.google.common.base.Predicate
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.ContainerUtil
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactoryConfigurationError

/**
 * A wrapper class which manages a [DeviceManager] instance and provides convenience functions for working with [Device]s.
 */
class DeviceManagerConnection(private val sdkPath: File?) {
  private var ourDeviceManager: DeviceManager? = null

  /**
   * Setup our static instances if required. If the instance already exists, then this is a no-op.
   */
  private fun initIfNecessary(): Boolean {
    if (ourDeviceManager == null) {
      if (sdkPath == null) {
        IJ_LOG.error("No installed SDK found!")
        return false
      }
      ourDeviceManager = DeviceManager.createInstance(sdkPath, SDK_LOG)
    }
    return true
  }

  /**
   * @return a list of Devices currently present on the system.
   */
  val devices: List<Device>
    get() = if (!initIfNecessary()) listOf()
    else ourDeviceManager!!.getDevices(DeviceManager.ALL_DEVICES).toList()

  /**
   * @return the device identified by device name and manufacturer or null if not found.
   */
  fun getDevice(id: String, manufacturer: String): Device? =
    if (!initIfNecessary()) null else ourDeviceManager!!.getDevice(id, manufacturer)

  /**
   * Calculate an ID for a device (optionally from a given ID) which does not clash
   * with any existing IDs.
   */
  fun getUniqueId(id: String?): String {
    val baseId = id ?: "New Device"
    if (!initIfNecessary()) {
      return baseId
    }
    val devices = ourDeviceManager!!.getDevices(DeviceManager.DeviceFilter.USER)
    var candidate = baseId
    var i = 1
    while (anyIdMatches(candidate, devices)) {
      candidate = "$baseId $i"
      i++
    }
    return candidate
  }

  /**
   * Delete the given device if it exists.
   */
  fun deleteDevice(info: Device?) {
    if (info != null) {
      if (!initIfNecessary()) {
        return
      }
      ourDeviceManager!!.removeUserDevice(info)
      ourDeviceManager!!.saveUserDevices()
    }
  }

  /**
   * Edit the given device, overwriting existing data, or creating it if it does not exist.
   */
  fun createOrEditDevice(device: Device) {
    if (!initIfNecessary()) {
      return
    }
    ourDeviceManager!!.replaceUserDevice(device)
    ourDeviceManager!!.saveUserDevices()
  }

  /**
   * Create the given devices
   */
  fun createDevices(devices: List<Device>) {
    if (!initIfNecessary()) {
      return
    }
    for (device in devices) {
      // Find a unique ID for this new device
      val deviceIdBase = device.id
      val deviceNameBase = device.displayName
      val i = 2
      var device = device
      while (isUserDevice(device)) {
        val id = "${deviceIdBase}_$i"
        val name = "${deviceNameBase}_$i"
        device = cloneDeviceWithNewIdAndName(device, id, name)
      }
      ourDeviceManager!!.addUserDevice(device)
    }
    ourDeviceManager!!.saveUserDevices()
  }

  /**
   * Return true iff the given device matches one of the user declared devices.
   */
  fun isUserDevice(device: Device): Boolean = if (!initIfNecessary()) false
  else ourDeviceManager!!.getDevices(DeviceManager.DeviceFilter.USER).any { input -> device.id.equals(input?.id, ignoreCase = true) }

  companion object {
    private val IJ_LOG: Logger get() = logger<AvdManagerConnection>()
    private val SDK_LOG: ILogger = LogWrapper(IJ_LOG).alwaysLogAsDebug(true).allowVerbose(false)
    private val NULL_CONNECTION = DeviceManagerConnection(null)
    private val ourCache: MutableMap<File, DeviceManagerConnection> = ContainerUtil.createWeakMap()

    fun getDeviceManagerConnection(sdkPath: File): DeviceManagerConnection {
      if (!ourCache.containsKey(sdkPath)) {
        ourCache[sdkPath] = DeviceManagerConnection(sdkPath)
      }
      return ourCache[sdkPath]!!
    }

    @JvmStatic
    val defaultDeviceManagerConnection: DeviceManagerConnection
      get() = AndroidSdks.getInstance().tryToChooseSdkHandler().location?.let { getDeviceManagerConnection(it) } ?: NULL_CONNECTION

    private fun anyIdMatches(id: String, devices: Collection<Device>): Boolean = devices.any { id.equals(it.id, ignoreCase = true) }

    private fun cloneDeviceWithNewIdAndName(device: Device, id: String, name: String): Device {
      val builder = Device.Builder(device)
      builder.setId(id)
      builder.setName(name)
      return builder.build()
    }

    fun getDevicesFromFile(xmlFile: File): List<Device> {
      var stream: InputStream? = null
      val list: MutableList<Device> = Lists.newArrayList()
      try {
        stream = FileInputStream(xmlFile)
        list.addAll(DeviceParser.parse(stream).values())
      }
      catch (e: IllegalStateException) {
        // The device builders can throw IllegalStateExceptions if
        // build gets called before everything is properly setup
        IJ_LOG.error(e)
      }
      catch (e: Exception) {
        IJ_LOG.error("Error reading devices", e)
      }
      finally {
        if (stream != null) {
          try {
            stream.close()
          }
          catch (ignore: IOException) {
          }
        }
      }
      return list
    }

    fun writeDevicesToFile(devices: List<Device?>, file: File) {
      if (devices.isNotEmpty()) {
        var stream: FileOutputStream? = null
        try {
          stream = FileOutputStream(file)
          DeviceWriter.writeToXml(stream, devices)
        }
        catch (e: Exception) {
          IJ_LOG.warn("Error handling file: ${e.message}")
        }
        finally {
          try {
            stream?.close()
          }
          catch (e: IOException) {
            IJ_LOG.warn("Error closing file: ${e.message}")
          }
        }
      }
    }
  }
}