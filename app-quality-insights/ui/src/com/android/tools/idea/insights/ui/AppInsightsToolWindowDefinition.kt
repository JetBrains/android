/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.intellij.openapi.Disposable
import com.intellij.util.ui.JBUI
import javax.swing.Icon

class AppInsightsToolWindowDefinition(
  title: String,
  icon: Icon,
  name: String,
  factory: (Disposable) -> ToolContent<AppInsightsToolWindowContext>,
) :
  ToolWindowDefinition<AppInsightsToolWindowContext>(
    title,
    icon,
    name,
    Side.RIGHT,
    Split.TOP,
    AutoHide.DOCKED,
    JBUI.scale(400),
    DEFAULT_BUTTON_SIZE,
    ALLOW_BASICS,
    factory,
  )
