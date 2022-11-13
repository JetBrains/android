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
package com.android.tools.idea.streaming.emulator.dialogs

import com.android.annotations.concurrency.Slow
import com.android.emulator.control.SnapshotFilter
import com.android.emulator.control.SnapshotList
import com.android.emulator.snapshot.SnapshotOuterClass.Snapshot
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.streaming.emulator.EmptyStreamObserver
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.readKeyValueFile
import com.android.tools.idea.streaming.emulator.updateKeyValueFile
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.kotlin.konan.file.use
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

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
   * Obtains and returns a list of snapshots by querying the emulator.
   */
  @Slow
  fun fetchSnapshotList(): List<SnapshotInfo> {
    val snapshotsFuture = SettableFuture.create<List<SnapshotInfo>>()
    val snapshotFilter = SnapshotFilter.newBuilder().setStatusFilter(SnapshotFilter.LoadStatus.All).build()
    emulatorController.listSnapshots(snapshotFilter, object : EmptyStreamObserver<SnapshotList>() {
      override fun onNext(response: SnapshotList) {
        val snapshots = response.snapshotsList.map {
          SnapshotInfo(snapshotsFolder, it)
        }
        snapshotsFuture.set(snapshots)
      }

      override fun onError(t: Throwable) {
        snapshotsFuture.setException(t)
      }
    })

    try {
      return snapshotsFuture.get()
    }
    catch (_: ExecutionException) {
      // The error is already logged by EmulatorController.
    }
    catch (_: InterruptedException) {
    }
    catch (_: CancellationException) {
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
      return SnapshotInfo(snapshotFolder, snapshot, folderSize(snapshotFolder), isCompatible = true, isLoadedLast = false)
    }
    catch (_: NoSuchFileException) {
      // The "snapshot.pb" file is missing. Skip the incomplete snapshot.
    } catch (e: IOException) {
      thisLogger().warn("Error reading $snapshotProtoFile - ${e.localizedMessage}")
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
    } catch (e: IOException) {
      thisLogger().warn("Error writing $protoFile - ${e.localizedMessage}")
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
