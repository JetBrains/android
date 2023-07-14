/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices

import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings

/** Proxy used to avoid exposing the entire [LayoutInspectorSettings]. */
object EmbeddedLayoutInspectorSettingsProxy {
  var enableEmbeddedLayoutInspector: Boolean
    set(value) {
      LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled = value
    }
    get() = LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled
}

/**
 * Utility function used to enable and disable embedded Layout Inspector in tests. It takes care of
 * restoring the settings state at the end of the test, and provides
 * [EmbeddedLayoutInspectorSettingsProxy], a utility to enable and disable embedded Layout Inspector
 * during the test.
 */
fun withEmbeddedLayoutInspector(
  enabled: Boolean = true,
  block: EmbeddedLayoutInspectorSettingsProxy.() -> Unit
) {
  val settings = EmbeddedLayoutInspectorSettingsProxy
  val prev = settings.enableEmbeddedLayoutInspector
  settings.enableEmbeddedLayoutInspector = enabled
  block(settings)
  settings.enableEmbeddedLayoutInspector = prev
}
