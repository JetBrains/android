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
package com.android.tools.idea.rendering.errors.ui

import com.android.utils.HtmlBuilder
import javax.swing.Icon

/**
 * Defines the types of messages that could be shown on the bottom of the [IssuePanel]
 * @param icon the icon to be shown aside of the message
 * @param htmlText the message in html format (it could also include links).
 */
data class MessageTip(val icon: Icon?, val htmlText: String) {
  constructor(icon: Icon?, htmlBuilder: HtmlBuilder) : this(
    icon,
    htmlBuilder.stringBuilder.toString(),
  )
}

