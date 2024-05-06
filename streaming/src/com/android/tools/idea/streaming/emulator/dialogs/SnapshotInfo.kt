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

import com.android.emulator.control.SnapshotDetails
import com.android.emulator.snapshot.SnapshotOuterClass.Snapshot
import com.intellij.util.text.nullize
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Information about an Emulator snapshot.
 */
class SnapshotInfo(val snapshotFolder: Path,
                   val snapshot: Snapshot,
                   val sizeOnDisk: Long,
                   val isCompatible: Boolean,
                   var isLoadedLast: Boolean) {

  /**
   * Creates a [SnapshotInfo] from the given [prototype] with the given display name and description.
   */
  constructor(prototype: SnapshotInfo, displayName: String, description: String)
      : this(prototype.snapshotFolder,
             prototype.snapshot.withLogicalNameAndDescription(if (displayName == prototype.snapshotId) "" else displayName, description),
             prototype.sizeOnDisk,
             prototype.isCompatible,
             prototype.isLoadedLast)

  /**
   * Creates a placeholder for a non-existent snapshot.
   */
  constructor(snapshotFolder: Path) : this(snapshotFolder, Snapshot.getDefaultInstance(), 0, isCompatible = true, isLoadedLast = false)

  /**
   * Creates a [SnapshotInfo] for the given [SnapshotDetails] proto message.
   */
  constructor(snapshotsFolder: Path, snapshotDetails: SnapshotDetails)
      : this(snapshotsFolder.resolve(snapshotDetails.snapshotId),
             snapshotDetails.details,
             snapshotDetails.size,
             isCompatible = snapshotDetails.status != SnapshotDetails.LoadStatus.Incompatible,
             isLoadedLast = snapshotDetails.status == SnapshotDetails.LoadStatus.Loaded)

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
   * True if the snapshot physically exists.
   */
  val isCreated: Boolean
    get() = snapshot.creationTime != 0L


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

  override fun toString(): String {
    val buf = StringBuilder("Snapshot $snapshotId")
    if (displayName != snapshotId) {
      buf.append(" ($displayName)")
    }
    if (!isCompatible) {
      buf.append(" incompatible")
    }
    if (!isCreated) {
      buf.append(" not created yet")
    }
    return buf.toString()
  }
}

private fun Snapshot.withLogicalNameAndDescription(logicalName: String, description: String): Snapshot {
  return toBuilder().apply {
    this.logicalName = logicalName
    this.description = description
  }.build()
}