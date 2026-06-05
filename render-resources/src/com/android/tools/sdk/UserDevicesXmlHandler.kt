// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.sdk

import com.android.SdkConstants
import com.android.dvlib.DeviceSchema
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.utils.ILogger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object UserDevicesXmlHandler {
  private val USER_DEVICES_SCHEMA_VERSION = Regex("""http://schemas\.android\.com/sdk/devices/(\d+)""")

  private const val UNSUPPORTED_USER_DEVICES_XML_SUFFIX = ".unsupported-schema"

  /**
   * Moves the user devices file aside when its schema version is newer than this sdklib build
   * supports, so [DeviceManager] never parses it.
   *
   * [DeviceManager.initUserDevices] recovers from malformed XML by backing the file up and
   * continuing, but for an unsupported schema version it rethrows the [AssertionError] from
   * [DeviceSchema.getXsdStream]. That error aborts the whole AVD scan, so no AVDs appear
   * (RIDER-139412). With the file removed before the lazy parse, the user devices end up empty and
   * the rest still loads.
   */
  fun backupUnsupportedUserDevicesXml(sdkHandler: AndroidSdkHandler, logger: ILogger) {
    val androidFolder = sdkHandler.androidFolder ?: return
    val devicesXml = androidFolder.resolve(SdkConstants.FN_DEVICES_XML)
    if (!Files.isRegularFile(devicesXml)) {
      return
    }
    val schemaVersion = readUserDevicesSchemaVersion(devicesXml, logger) ?: return
    if (schemaVersion <= DeviceSchema.NS_LATEST_VERSION) {
      return
    }

    try {
      // The file still holds the user's device profiles, just in a schema this build can't read, so
      // we move it aside (never delete it) for a later Studio/SDK to recover.
      val existingBackup = identicalUnsupportedUserDevicesBackup(androidFolder, devicesXml)
      if (existingBackup != null) {
        // This exact content is already backed up. Drop the duplicate so backups don't pile up on
        // every restart while the schema stays unsupported.
        Files.deleteIfExists(devicesXml)
        logger.warning(
          "Ignoring user Android device definitions: schema version %d is newer than the supported version %d. Already backed up at %s",
          schemaVersion,
          DeviceSchema.NS_LATEST_VERSION,
          existingBackup,
        )
        return
      }
      val backupPath = freeUnsupportedUserDevicesBackupPath(androidFolder)
      Files.move(devicesXml, backupPath)
      logger.warning(
        "Ignoring user Android device definitions: schema version %d is newer than the supported version %d. Backed up to %s",
        schemaVersion,
        DeviceSchema.NS_LATEST_VERSION,
        backupPath,
      )
    }
    catch (e: IOException) {
      logger.warning("Failed to back up user Android device definitions from %s: %s", devicesXml, e.message ?: e.javaClass.name)
    }
    catch (e: SecurityException) {
      logger.warning("Failed to back up user Android device definitions from %s: %s", devicesXml, e.message ?: e.javaClass.name)
    }
  }

  /**
   * Returns an existing backup whose contents match [devicesXml] byte for byte, or `null` if none
   * does. The caller uses this to skip making a fresh backup when the content is already saved.
   */
  private fun identicalUnsupportedUserDevicesBackup(androidFolder: Path, devicesXml: Path): Path? {
    val base = SdkConstants.FN_DEVICES_XML + UNSUPPORTED_USER_DEVICES_XML_SUFFIX
    var candidate = androidFolder.resolve(base)
    var index = 1
    while (Files.exists(candidate)) {
      if (Files.isRegularFile(candidate) && Files.mismatch(devicesXml, candidate) == -1L) {
        return candidate
      }
      candidate = androidFolder.resolve("$base.$index")
      index++
    }
    return null
  }

  /**
   * Returns the first unused backup path, mirroring how sdklib renames unparseable files: the plain
   * suffix first, then `.1`, `.2`, and so on. This preserves every distinct backed-up version instead
   * of clobbering an earlier one.
   */
  private fun freeUnsupportedUserDevicesBackupPath(androidFolder: Path): Path {
    val base = SdkConstants.FN_DEVICES_XML + UNSUPPORTED_USER_DEVICES_XML_SUFFIX
    var candidate = androidFolder.resolve(base)
    var index = 1
    while (Files.exists(candidate)) {
      candidate = androidFolder.resolve("$base.$index")
      index++
    }
    return candidate
  }

  private fun readUserDevicesSchemaVersion(devicesXml: Path, logger: ILogger): Int? {
    val content = try {
      Files.readString(devicesXml)
    }
    catch (e: IOException) {
      logger.warning("Failed to read user Android device definitions from %s: %s", devicesXml, e.message ?: e.javaClass.name)
      return null
    }
    catch (e: SecurityException) {
      logger.warning("Failed to read user Android device definitions from %s: %s", devicesXml, e.message ?: e.javaClass.name)
      return null
    }
    return USER_DEVICES_SCHEMA_VERSION.find(content)?.groupValues?.getOrNull(1)?.toIntOrNull()
  }
}
