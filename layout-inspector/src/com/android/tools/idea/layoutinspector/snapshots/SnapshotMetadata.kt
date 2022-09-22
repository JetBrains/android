/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.snapshots

import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSnapshotInfo
import com.intellij.openapi.application.ApplicationInfo
import layoutinspector.snapshots.Metadata
import java.awt.Dimension

/**
 * Metadata about a layout inspector snapshot that's included in the snapshot itself. Can be used for metrics logging.
 */
class SnapshotMetadata(
    val snapshotVersion: ProtocolVersion,
    val apiLevel: Int? = null,
    val processName: String? = null,
    var containsCompose: Boolean? = null,
    val liveDuringCapture: Boolean? = null,
    val source: Metadata.Source? = null,
    val sourceVersion: String? = null,
    var saveDuration: Long? = null,
    var loadDuration: Long? = null,
    var dpi: Int? = null,
    var fontScale: Float? = null,
    var screenDimension: Dimension? = null
) {
  /**
   * Convert to the proto used by metrics.
   */
  fun toSnapshotInfo(): DynamicLayoutInspectorSnapshotInfo? =
    DynamicLayoutInspectorSnapshotInfo.newBuilder().apply {
      snapshotVersion = this@SnapshotMetadata.snapshotVersion.toInt()

      saveSource = when (source) {
        Metadata.Source.STUDIO -> DynamicLayoutInspectorSnapshotInfo.SaveSource.STUDIO
        else -> DynamicLayoutInspectorSnapshotInfo.SaveSource.UNKNOWN
      }
      sourceVersion?.let { saveVersion = it }
      liveDuringCapture?.let { liveWhenSaved = it }
      saveDuration?.let { saveDurationMs = it.toInt() }
      loadDuration?.let { loadDurationMs = it.toInt() }
    }.build()

  /**
   * Convert to the proto saved in a snapshot.
   */
  fun toProto(): Metadata =
    Metadata.newBuilder().apply {
      this@SnapshotMetadata.apiLevel?.let { apiLevel = it }
      this@SnapshotMetadata.processName?.let { processName = it }
      this@SnapshotMetadata.containsCompose?.let { containsCompose = it }
      this@SnapshotMetadata.liveDuringCapture?.let { liveDuringCapture = it }
      source = this@SnapshotMetadata.source
      sourceVersion = ApplicationInfo.getInstance().fullVersion
      this@SnapshotMetadata.dpi?.let { dpi = it }
      this@SnapshotMetadata.fontScale?.let { fontScale = it }
      this@SnapshotMetadata.screenDimension?.let {
        screenWidth = it.width
        screenHeight = it.height
      }
    }.build()
}

/**
 * Convert from the proto saved in a snapshot to a normal object.
 */
fun Metadata.convert(version: ProtocolVersion) = SnapshotMetadata(
  version,
  apiLevel,
  processName,
  containsCompose,
  liveDuringCapture,
  source,
  sourceVersion,
  dpi = dpi,
  fontScale = fontScale,
  screenDimension = if (screenWidth > 0 && screenHeight > 0) Dimension(screenWidth, screenHeight) else null
)

fun ProtocolVersion.toInt() = when(this) {
  ProtocolVersion.Version1 -> 1
  ProtocolVersion.Version2 -> 2
  ProtocolVersion.Version3 -> 3
  ProtocolVersion.Version4 -> 4
}