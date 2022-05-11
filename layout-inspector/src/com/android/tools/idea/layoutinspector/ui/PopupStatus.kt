/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.ui.popup.Balloon

val DEVICE_VIEW_POPUP_STATUS = DataKey.create<PopupStatus>(PopupStatus::class.qualifiedName!!)

/**
 * Holds status of the popup that can be opened with the Layout Inspector.
 */
class PopupStatus {
  /** The [Balloon] for the recomposition count highlight color chooser */
  var highlightColorBalloon: Balloon? = null
}
