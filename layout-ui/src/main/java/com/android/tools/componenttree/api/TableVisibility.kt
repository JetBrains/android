/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.componenttree.api

/** Interactions on a component tree after it is created. */
interface TableVisibility {

  /** Control the visibility of the header in the component tree. */
  fun setHeaderVisibility(visible: Boolean)

  /** Control the visibility of the columns in the component tree. */
  fun setColumnVisibility(columnIndex: Int, visible: Boolean)
}

/** A [TableVisibility] implementation that does nothing. */
class NoOpTableVisibility : TableVisibility {
  override fun setHeaderVisibility(visible: Boolean) {}

  override fun setColumnVisibility(columnIndex: Int, visible: Boolean) {}
}
