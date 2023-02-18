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
package com.android.tools.idea.appinspection.inspector.ide

import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import javax.swing.JComponent

/**
 * An inspector tab wraps one or more related app inspectors which are then used together to present
 * a cohesive UI to the user, allowing them to surface runtime information about their app.
 */
interface AppInspectorTab {
  val messengers: Iterable<AppInspectorMessenger>
  val component: JComponent
}

/** Convenience class for the common case where a tab is associated with a single messenger only */
abstract class SingleAppInspectorTab(val messenger: AppInspectorMessenger) : AppInspectorTab {
  final override val messengers = listOf(messenger)
}
