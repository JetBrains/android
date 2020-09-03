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
package com.android.tools.idea.appinspection.inspectors.workmanager.view

import com.intellij.ui.JBColor

object WorkManagerInspectorColors {
  // TODO (b/166077440) Match text colors with the spec
  val DATA_VALUE_TEXT_COLOR = JBColor(0x58AB5C, 0x65BB69)
  val DATA_TEXT_NULL_COLOR = JBColor(0x002FA6, 0x2B7DA2)
  val DATA_TEXT_AWAITING_COLOR = JBColor(0x787878, 0xC8C8C8);
  val EMPTY_CONTENT_COLOR = JBColor(0x787878, 0xC8C8C8);
}
