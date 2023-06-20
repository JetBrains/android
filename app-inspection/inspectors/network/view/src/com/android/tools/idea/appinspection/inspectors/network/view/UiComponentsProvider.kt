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
package com.android.tools.idea.appinspection.inspectors.network.view

import com.android.tools.adtui.stdui.ContentType
import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel
import com.android.tools.inspectors.common.ui.dataviewer.DataViewer
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceGroup
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceView

/** Factory class for the various custom UI components that are used in network inspector. */
interface UiComponentsProvider {
  fun createStackView(model: StackTraceModel): StackTraceView {
    // Delegate to StackTraceGroup to simply create a stack trace view of group size 1
    return createStackGroup().createStackView(model)
  }

  /**
   * Creates a UI component that displays some data (which may be text or binary). The view uses the
   * data's content type, if known, to render it correctly.
   *
   * @param styleHint A style which the viewer will attempt to apply; however, this may fail in some
   *   cases, so you are encouraged to check [DataViewer.getStyle] if you need to confirm the style
   *   was actually accepted.
   */
  fun createDataViewer(
    bytes: ByteArray,
    contentType: ContentType,
    styleHint: DataViewer.Style,
    formatted: Boolean
  ): DataViewer

  /** Creates a stack trace group that represents a list of stack trace views. */
  fun createStackGroup(): StackTraceGroup
}
