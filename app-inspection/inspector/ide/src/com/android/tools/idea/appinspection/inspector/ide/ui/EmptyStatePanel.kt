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
package com.android.tools.idea.appinspection.inspector.ide.ui

import com.android.tools.adtui.stdui.EmptyStatePanel
import com.android.tools.adtui.stdui.UrlData
import com.android.tools.idea.appinspection.inspector.ide.bundle.AppInspectorBundle

/** Convenience constructor that sets up an empty message with a learn more URL (if present). */
fun EmptyStatePanel(reason: String, learnMoreUrl: String?) =
  EmptyStatePanel(
    reason,
    learnMoreUrl?.let { url -> UrlData(AppInspectorBundle.message("learn.more"), url) }
  )
