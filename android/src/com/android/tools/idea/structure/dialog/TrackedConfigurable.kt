/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.structure.dialog

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.structure.configurables.ui.ModelPanel
import com.android.tools.idea.stats.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project

/**
 * Identification of a configurable which is is supposed to be recorded in [PSDEvent].
 *
 * See:  [com.android.tools.analytics.UsageTracker]
 */
interface TrackedConfigurable {
  val leftConfigurable: PSDEvent.PSDLeftConfigurable? get() = null
  val topConfigurable: PSDEvent.PSDTopTab? get() = null

  fun copyIdFieldsTo(builder: PSDEvent.Builder) {
    leftConfigurable?.let { builder.setLeftConfigurable(it) }
    topConfigurable?.let { builder.setTopTab(it) }
  }

  fun copyEditedFieldsTo(builder: PSDEvent.Builder) = Unit
}

fun Project.logUsageLeftNavigateTo(toSelect: Configurable) {
  if (toSelect is TrackedConfigurable) {
    val psdEvent = PSDEvent
      .newBuilder()
      .setGeneration(PSDEvent.PSDGeneration.PROJECT_STRUCTURE_DIALOG_GENERATION_002)
    toSelect.copyIdFieldsTo(psdEvent)
    UsageTracker.log(
      AndroidStudioEvent
        .newBuilder()
        .setCategory(AndroidStudioEvent.EventCategory.PROJECT_STRUCTURE_DIALOG)
        .setKind(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_LEFT_NAV_CLICK)
        .setPsdEvent(psdEvent)
        .withProjectId(this))
  }
}

fun Project.logUsagePsdAction(eventKind: AndroidStudioEvent.EventKind) {
    val psdEvent = PSDEvent
      .newBuilder()
      .setGeneration(PSDEvent.PSDGeneration.PROJECT_STRUCTURE_DIALOG_GENERATION_002)
    UsageTracker.log(
      AndroidStudioEvent
        .newBuilder()
        .setCategory(AndroidStudioEvent.EventCategory.PROJECT_STRUCTURE_DIALOG)
        .setKind(eventKind)
        .setPsdEvent(psdEvent)
        .withProjectId(this))
}

fun Project.logUsageTopNavigateTo(toSelect: ModelPanel<*>) {
  val psdEvent = PSDEvent
    .newBuilder()
    .setGeneration(PSDEvent.PSDGeneration.PROJECT_STRUCTURE_DIALOG_GENERATION_002)
  toSelect.copyIdFieldsTo(psdEvent)
  UsageTracker.log(
    AndroidStudioEvent
      .newBuilder()
      .setCategory(AndroidStudioEvent.EventCategory.PROJECT_STRUCTURE_DIALOG)
      .setKind(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_TOP_TAB_CLICK)
      .setPsdEvent(psdEvent)
      .withProjectId(this))
}

