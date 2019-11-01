/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui

import com.android.tools.idea.flags.StudioFlags
import com.intellij.ide.util.PropertiesComponent

private const val PREFERENCE_KEY = "live.layout.inspector.enabled"

var enableLiveLayoutInspector
  get() = PropertiesComponent.getInstance().getBoolean(PREFERENCE_KEY, false) ||
          StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_LEGACY_DEVICE_SUPPORT.get()
  set(value) = PropertiesComponent.getInstance().setValue(PREFERENCE_KEY, value)


