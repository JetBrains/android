/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.graphics

import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.decorator.WidgetDecorator

import javax.swing.Icon

/**
 * Icon that allows different representations based on the selected view mode (Blueprint or Screen).
 */
data class NlIcon(val screenModeIcon: Icon, val screenBlueprintIcon: Icon) {
  fun getSelectedIcon(context: SceneContext): Icon =
    if (context.colorSet.style == WidgetDecorator.BLUEPRINT_STYLE)
      screenBlueprintIcon
    else
      screenModeIcon
}
