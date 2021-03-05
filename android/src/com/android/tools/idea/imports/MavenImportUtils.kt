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
package com.android.tools.idea.imports

import com.android.ide.common.repository.GradleVersion
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AutoImportEvent

/**
 * Tracks user interaction with suggested import support.
 *
 * @param artifactId GMaven coordinate of the corresponding added dependency due to the invocation of `suggested import`.
 */
internal fun trackSuggestedImport(artifactId: String) {
  if (!StudioFlags.ENABLE_SUGGESTED_IMPORT.get()) return

  val autoImportEvent = AutoImportEvent.newBuilder()
    .setArtifactId(artifactId)

  AndroidStudioEvent.newBuilder()
    .setKind(AndroidStudioEvent.EventKind.AUTO_IMPORT_EVENT) //TODO: rename to suggested import.
    .setAutoImportEvent(autoImportEvent)
    .let { UsageTracker.log(it) }
}

/**
 * Displays the preview type (alpha, beta...) if applicable, or just the original [artifact].
 */
fun flagPreview(artifact: String, version: String?): String {
  version ?: return artifact

  val previewType = GradleVersion.tryParse(version)?.previewType ?: return artifact
  return "$artifact ($previewType)"
}