/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.toolwindow.splittingtabs

import com.intellij.ui.content.Content

/**
 * Returns true if this context is in a Splitting Tabs ToolWindow.
 */
internal fun Content.isSplittingTab() = findFirstSplitter() != null

/**
 * Returns the "first" [SplittingPanel] child of this content or null if this content is not a Splitting Tabs ToolWindow.
 */
internal fun Content.findFirstSplitter(): SplittingPanel? = SplittingPanel.findFirstSplitter(component)

/**
 * Convenience method that handles unlikely `manager == null` condition.
 */
internal fun Content.getPosition(): Int = manager?.getIndexOfContent(this) ?: -1