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
package com.android.tools.compose.debug.recomposition

import com.android.tools.compose.ComposeBundle
import com.intellij.icons.AllIcons
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace

/**
 *  A [XNamedValue] shown instead of a [ComposeStateNode] when some error has occurred
 */
internal class ErrorNode(val errorText: String) : XNamedValue(ComposeBundle.message("recomposition.state.label")) {
  override fun computePresentation(node: XValueNode, place: XValuePlace) {
    node.setPresentation(AllIcons.General.Error, null, errorText, false)
  }
}
