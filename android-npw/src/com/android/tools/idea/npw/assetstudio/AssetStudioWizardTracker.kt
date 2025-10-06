/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio

import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AssetStudioWizardEvent
import com.intellij.openapi.project.Project

/** Tracks usage of the Asset Studio Wizard. */
interface AssetStudioWizardTracker {

  /** Logs that a monochrome icon was generated using the Asset Studio Wizard. */
  fun logMonochromeIconGenerated()
}

class AssetStudioWizardTrackerImpl(private val project: Project) : AssetStudioWizardTracker {

  /** Creates a log event for the Asset Studio. */
  private fun createEvent(
    project: Project?,
    type: AssetStudioWizardEvent.Type,
    action: AssetStudioWizardEvent.Action,
  ): AndroidStudioEvent.Builder =
    AndroidStudioEvent.newBuilder()
      .withProjectId(project)
      .setKind(AndroidStudioEvent.EventKind.ASSET_STUDIO_WIZARD_EVENT)
      .setAssetStudioWizardEvent(
        AssetStudioWizardEvent.newBuilder().setType(type).setAction(action).build()
      )

  /**
   * Logs an event for the Asset Studio.
   *
   * @param project The current project.
   * @param type The type of Asset Studio wizard event.
   * @param action The action performed within the Asset Studio wizard.
   */
  private fun log(
    project: Project?,
    type: AssetStudioWizardEvent.Type,
    action: AssetStudioWizardEvent.Action,
  ) = UsageTracker.log(createEvent(project, type, action))

  /**
   * Logs that a monochrome icon was generated using the Asset Studio.
   *
   * @param project The current project.
   */
  override fun logMonochromeIconGenerated() =
    log(
      project,
      AssetStudioWizardEvent.Type.ADAPTIVE_LAUNCHER_ICON,
      AssetStudioWizardEvent.Action.MONOCHROME_ICON_CREATED,
    )
}
