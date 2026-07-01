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
package com.android.tools.preview

/**
 * Strip any leading <html> in the string. This is done so downstream Swing labels can never auto-render injected code. For example, preview
 * `name`/`group` may originate from a library-supplied MultiPreview annotation class, where this vulnerability can be explored.
 */
fun String?.neuterHtml(): String? = this?.let { if (it.trimStart().startsWith("<")) "\u200B$it" else it }
