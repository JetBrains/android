/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.model.stdui

/**
 * A model for a CommonBorder.
 *
 * Controls the display of error and placeholder variations of the border.
 */
interface CommonBorderModel {

  /**
   * When a component is in an error state the border should be red.
   */
  val hasError: Boolean

  /**
   * When a placeholder is shown, the border should be a little faint.
   */
  val hasPlaceHolder: Boolean
}
