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
package com.android.tools.idea.emulator.actions.dialogs

import com.android.annotations.concurrency.Slow
import com.android.emulator.control.SnapshotList
import com.android.emulator.snapshot.SnapshotOuterClass.Snapshot
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.emulator.EmptyStreamObserver
import com.android.tools.idea.emulator.EmulatorController
import com.android.tools.idea.emulator.logger
import com.android.tools.idea.emulator.readKeyValueFile
import com.android.tools.idea.emulator.updateKeyValueFile
import com.intellij.util.text.nullize
import org.jetbrains.kotlin.konan.file.use
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.streams.asSequence

/**
 * Manages emulator snapshots and boot mode.
 */
class SnapshotManager(val emulatorController: EmulatorController) {

  val snapshotsFolder: Path = avdFolder.resolve("snapshots")

  val avdFolder: Path
    get() = emulatorController.emulatorId.avdFolder
  private val avdId: String
    get() = emulatorController.emulatorId.avdId

  /**
   * Fetches and returns a list of snapshots by reading the "snapshots" subfolder of the AVD folder.
   *
   * @param excludeQuickBoot if true, the quick boot snapshot is not included in the returned list
   */
  @Slow
  fun fetchSnapshotList(excludeQuickBoot: Boolean = false): List<SnapshotInfo> {
    val validSnapshotsReady = CountDownLatch(1)
    val validSnapshotIds = hashSetOf<String>()
    emulatorController.listSnapshots(object : EmptyStreamObserver<SnapshotList>() {
      override fun onNext(response: SnapshotList) {
        for (snapshot in response.snapshotsList) {
          validSnapshotIds.add(snapshot.snapshotId)
        }
      }

      override fun onError(t: Throwable) {
        validSnapshotsReady.countDown()
      }

      override fun onCompleted() {
        validSnapshotsReady.countDown()
      }
    })

    try {
      val snapshots = Files.list(snapshotsFolder).use { stream ->
        stream.asSequence()
          .mapNotNull { folder ->
            if (excludeQuickBoot && folder.fileName.toString() == QUICK_BOOT_SNAPSHOT_ID) null else readSnapshotInfo(folder)
          }
          .toList()
      }

      validSnapshotsReady.await()
      for (snapshot in snapshots) {
        snapshot.isValid = validSnapshotIds.contains(snapshot.snapshotId) || snapshot.isQuickBoot && !snapshot.isCreated
      }
      return snapshots
    }
    catch (_: NoSuchFileException) {
      // The "snapshots" folder hasn't been created yet - ignore to return an empty snapshot list.
    }
    catch (e: IOException) {
      logger.warn("Error reading ${snapshotsFolder} - ${e.localizedMessage}")
    }
    return emptyList()
  }

  @Slow
  private fun readSnapshotInfo(snapshotFolder: Path): SnapshotInfo? {
    val snapshotProtoFile = snapshotFolder.resolve(SNAPSHOT_PROTO_FILE)
    try {
      val snapshot = Files.newInputStream(snapshotProtoFile).use {
        Snapshot.parseFrom(it)
      }
      return SnapshotInfo(snapshotFolder, snapshot, folderSize(snapshotFolder))
    }
    catch (_: NoSuchFileException) {
      // The "snapshot.pb" file is missing. Skip the incomplete snapshot.
    }
    catch (e: IOException) {
      logger.warn("Error reading ${snapshotProtoFile} - ${e.localizedMessage}")
    }
    return null
  }

  /**
   * Reads and returns information for the given snapshot. Returns null in case of errors.
   */
  @Slow
  fun readSnapshotInfo(snapshotFolderName: String): SnapshotInfo? {
    return readSnapshotInfo(snapshotsFolder.resolve(snapshotFolderName))
  }

  /*
   * Writes the given snapshot proto to the "snapshot.pb" file.
   */
  @Slow
  fun saveSnapshotProto(snapshotFolder: Path, snapshotProto: Snapshot) {
    val protoFile = snapshotFolder.resolve(SNAPSHOT_PROTO_FILE)
    try {
      Files.newOutputStream(protoFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { stream->
        snapshotProto.writeTo(stream)
      }
    }
    catch (e: IOException) {
      logger.warn("Error writing ${protoFile} - ${e.localizedMessage}")
    }
  }

  private fun folderSize(folder: Path): Long {
    var size = 0L
    Files.list(folder).use { stream ->
      stream.forEach { file ->
        try {
          size += if (Files.isDirectory(file)) folderSize(file) else Files.size(file)
        }
        catch (_: IOException) {
          // Ignore I/O errors.
        }
      }
    }
    return size
  }

  /**
   * Returns the boot options obtained by reading the "config.ini" file in the AVD folder.
   */
  @Slow
  fun readBootMode(): BootMode? {
    val keysToExtract = setOf("fastboot.chosenSnapshotFile", "fastboot.forceChosenSnapshotBoot",
                              "fastboot.forceColdBoot", "fastboot.forceFastBoot")
    val map = readKeyValueFile(avdFolder.resolve("config.ini"), keysToExtract) ?: return null
    val bootType = when {
      map["fastboot.forceFastBoot"] == "yes" -> BootType.QUICK
      map["fastboot.forceChosenSnapshotBoot"] == "yes" -> BootType.SNAPSHOT
      else -> BootType.COLD
    }
    return BootMode(bootType, map["fastboot.chosenSnapshotFile"])
  }

  /**
   * Saves the boot options by updating the "config.ini" file in the AVD directory.
   */
  @Slow
  fun saveBootMode(bootMode: BootMode) {
    val updates = mapOf(
      "fastboot.forceColdBoot" to toYesNo(bootMode.bootType == BootType.COLD),
      "fastboot.forceFastBoot" to toYesNo(bootMode.bootType == BootType.QUICK),
      "fastboot.forceChosenSnapshotBoot" to toYesNo(bootMode.bootType == BootType.SNAPSHOT),
      "fastboot.chosenSnapshotFile" to bootMode.bootSnapshotId
    )
    updateKeyValueFile(avdFolder.resolve("config.ini"), updates)

    // Update the cached AVD information in the AVD manager.
    val avdManagerConnection = AvdManagerConnection.getDefaultAvdManagerConnection()
    avdManagerConnection.reloadAvd(avdId)
  }

  private fun toYesNo(value: Boolean) =
    if (value) "yes" else "no"
}

/**
 * Information about an Emulator snapshot.
 */
class SnapshotInfo(val snapshotFolder: Path, val snapshot: Snapshot, val sizeOnDisk: Long) {

  /**
   * Creates a placeholder for a non-existent snapshot.
   */
  constructor(snapshotFolder: Path) : this(snapshotFolder, Snapshot.getDefaultInstance(), 0)

  /**
   * The ID of the snapshot.
   */
  val snapshotId = snapshotFolder.fileName.toString()

  /**
   * True if the snapshot was created automatically when the emulator shut down.
   */
  val isQuickBoot = snapshotId == QUICK_BOOT_SNAPSHOT_ID

  /**
   * The name of the snapshot to be used in the UI. May be different from the name of the snapshot folder.
   */
  val displayName: String
    get() = if (isQuickBoot) "Quickboot (auto-saved)" else snapshot.logicalName.nullize() ?: snapshotId

  /**
   * The screenshot file containing an image of the device screen when the snapshot was taken.
   */
  val screenshotFile: Path
    get() = snapshotFolder.resolve("screenshot.png")

  /**
   * Returns the creation time in milliseconds since epoch.
   */
  val creationTime: Long
    get() = TimeUnit.SECONDS.toMillis(snapshot.creationTime)

  /**
   * Returns the description of the snapshot.
   */
  val description: String
    get() = snapshot.description

  /**
   * True if the snapshot is compatible with the current emulator configuration.
   */
  val isCreated: Boolean
    get() = snapshot.creationTime != 0L

  /**
   * True if the snapshot is compatible with the current emulator configuration.
   */
  var isValid: Boolean = true


  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SnapshotInfo

    if (snapshotFolder != other.snapshotFolder) return false

    return true
  }

  override fun hashCode(): Int {
    return snapshotFolder.hashCode()
  }
}

/**
 * Creates a [BootMode] corresponding to the given boot snapshot. A null [bootSnapshot] value implies cold boot.
 */
fun createBootMode(bootSnapshot: SnapshotInfo?): BootMode {
  return when (bootSnapshot?.snapshotId) {
    null -> BootMode(BootType.COLD, null)
    QUICK_BOOT_SNAPSHOT_ID -> BootMode(BootType.QUICK, null)
    else -> BootMode(BootType.SNAPSHOT, bootSnapshot.snapshotId)
  }
}

/**
 * Describes the snapshot, if any, used to start the Emulator.
 */
data class BootMode(val bootType: BootType, val bootSnapshotId: String?)

enum class BootType { COLD, QUICK, SNAPSHOT }

const val QUICK_BOOT_SNAPSHOT_ID = "default_boot"

private const val SNAPSHOT_PROTO_FILE = "snapshot.pb"
