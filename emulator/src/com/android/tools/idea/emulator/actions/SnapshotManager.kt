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
package com.android.tools.idea.emulator.actions

import com.android.annotations.concurrency.Slow
import com.android.emulator.snapshot.SnapshotOuterClass.Snapshot
import com.android.tools.idea.emulator.logger
import com.android.tools.idea.emulator.readKeyValueFile
import com.android.tools.idea.emulator.updateKeyValueFile
import com.intellij.util.text.nullize
import org.jetbrains.kotlin.konan.file.use
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.streams.asSequence

/**
 * Manages emulator snapshots and boot mode.
 */
class SnapshotManager(val avdFolder: Path) {
  /**
   * Fetches and returns a list of snapshots by reading the "snapshots" subfolder of the AVD folder.
   */
  @Slow
  fun fetchSnapshotList(): List<SnapshotInfo> {
    val snapshotsFolder = avdFolder.resolve("snapshots")
    return Files.list(snapshotsFolder).use { stream ->
      stream.asSequence()
        .map {
          val snapshotDirName = it.fileName.toString()
          if (snapshotDirName != "default_boot") {
            val snapshotProtoFile = it.resolve("snapshot.pb")
            try {
              val snapshot = Files.newInputStream(snapshotProtoFile).use {
                Snapshot.parseFrom(it)
              }
              if (snapshot.imagesCount == 0) {
                return@map null // Incomplete snapshot.
              }
              return@map SnapshotInfo(it, snapshot)
            }
            catch (ignore: NoSuchFileException) {
            }
            catch (e: IOException) {
              logger.warn("Error reading ${snapshotProtoFile}")
            }
          }
          return@map null
        }
        .filterNotNull()
        .sortedBy(SnapshotInfo::displayName)
        .toList()
    }
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
      "fastboot.chosenSnapshotFile" to bootMode.bootSnapshot
    )
    updateKeyValueFile(avdFolder.resolve("config.ini"), updates)
  }

  private fun toYesNo(value: Boolean) =
    if (value) "yes" else "no"
}

/**
 * Information about an Emulator snapshot.
 */
class SnapshotInfo(val snapshotFolder: Path, val snapshot: Snapshot) {
  /**
   * The ID of the snapshot.
   */
  val snapshotId = snapshotFolder.fileName.toString()

  /**
   * The name of the snapshot to be used in the UI. May be different from the name of the snapshot folder.
   */
  val displayName: String
    get() = snapshot.logicalName.nullize() ?: snapshotId

  /**
   * The screenshot file containing an image of the device screen when the snapshot was taken.
   */
  val screenshotFile: Path
    get() = snapshotFolder.resolve("screenshot.png")

  /**
   * Indicates that the last attempt to load the snapshot was unsuccessful.
   */
  val failedToLoad: Boolean
    get() = snapshot.failedToLoadReasonCode != 0L
}

/**
 * Describes the snapshot, if any, used to start the Emulator.
 */
data class BootMode(val bootType: BootType, val bootSnapshot: String?)

enum class BootType { COLD, QUICK, SNAPSHOT }