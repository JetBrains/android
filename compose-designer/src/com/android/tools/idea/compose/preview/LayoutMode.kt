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
package com.android.tools.idea.compose.preview

/** Layout modes for different [PreviewMode]. */
sealed class LayoutMode {

  /** Default [LayoutMode] for most of the [PreviewMode]s. */
  object Default : LayoutMode()

  /**
   * Gallery [LayoutMode] for [PreviewMode.Gallery]. It adds extra tab component and rearrange
   * hierarchy of the components as well.
   */
  object Gallery : LayoutMode()
}
