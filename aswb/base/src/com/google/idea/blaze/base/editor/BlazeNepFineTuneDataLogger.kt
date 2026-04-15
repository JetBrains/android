/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.google.idea.blaze.base.editor

import com.android.tools.idea.gemini.NepFineTuneData
import com.android.tools.idea.gemini.NepFineTuneDataListener
import com.google.idea.blaze.base.logging.EventLoggingService
import com.google.idea.blaze.base.logging.GenericEvent
import com.google.idea.common.experiments.FeatureRolloutExperiment
import com.intellij.openapi.project.Project

class BlazeNepFineTuneDataLogger : NepFineTuneDataListener {
  val nepFineTuneDataCaptureExperiment = FeatureRolloutExperiment("aiplugin.editor.predictionservices.nep.fine.tune.data.capture")

  override fun onData(project: Project, data: NepFineTuneData) {
    if (!nepFineTuneDataCaptureExperiment.isEnabled) return
    EventLoggingService.getInstance().log(GenericEvent(project, data.caller, data.type, data.values, data.durationNanos))
  }
}
