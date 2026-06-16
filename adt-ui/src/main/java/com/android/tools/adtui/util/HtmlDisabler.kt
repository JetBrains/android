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
package com.android.tools.adtui.util

import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel

fun JLabel.disableHtml(): JLabel = disableHtmlInternal()

fun JButton.disableHtml(): JButton = disableHtmlInternal()

/**
 * Set `html.disable` for any component.
 *
 * This is private because not all components support it. Individual components should add functions as needed.
 */
private inline fun <reified T : JComponent> JComponent.disableHtmlInternal(): T = apply { putClientProperty("html.disable", true) } as T
