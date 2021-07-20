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

import com.intellij.openapi.util.Key
import com.intellij.ui.content.Content

private val IS_SPLITTING_TAB_KEY = Key<Boolean>("IsSplittingTab")

/**
 * Mark a content so we can enable context menu actions on it
 */
internal fun Content.setIsSplittingTab() = putUserData(IS_SPLITTING_TAB_KEY, true)

/**
 * Returns true if this context is in a Splitting Tabs ToolWindow.
 */
internal fun Content.isSplittingTab() = getUserData(IS_SPLITTING_TAB_KEY) ?: false

/**
 * Convenience method that handles unlikely `manager == null` condition.
 */
internal fun Content.getPosition(): Int = manager?.getIndexOfContent(this) ?: -1