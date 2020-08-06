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
@file:JvmName("DesignerDataKeys")

package com.android.tools.idea.actions

import com.android.tools.idea.common.editor.DesignSurfaceNotificationManager
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.surface.LayoutScannerControl
import com.intellij.openapi.actionSystem.DataKey

/**
 * Data key for the actions work in Design Editor. This includes DesignSurface and ActionToolBar, but **exclude** all attached ToolWindows.
 * Attached ToolWindows take the responsibility of handling shortcuts and key events. For example, when focusing Palette, typing means
 * search the widget.
 */
@JvmField
val DESIGN_SURFACE: DataKey<DesignSurface> = DataKey.create(DesignSurface::class.qualifiedName!!)

/**
 * Data key to retrieve [LayoutScannerControl]. The control allows layout validator to be turned
 * on or off.
 */
@JvmField
val LAYOUT_SCANNER_KEY = DataKey.create<LayoutScannerControl>(LayoutScannerControl::class.java.name)

/**
 * Data key to retrieve [DesignSurfaceNotificationManager] which controls the notification status of [NlDesignSurface]
 */
@JvmField
val NOTIFICATION_KEY = DataKey.create<DesignSurfaceNotificationManager>(DesignSurfaceNotificationManager::class.java.name)