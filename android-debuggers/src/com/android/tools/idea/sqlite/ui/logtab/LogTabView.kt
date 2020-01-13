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
package com.android.tools.idea.sqlite.ui.logtab

import com.android.annotations.concurrency.UiThread
import javax.swing.JComponent

/**
 * Abstraction over the UI to display logs.
 * Used by [com.android.tools.idea.sqlite.controllers.LogTabController] to avoid direct dependency on the UI implementation.
 */
@UiThread
interface LogTabView {
  /**
   * The JComponent containing the view's UI.
   */
  val component: JComponent

  fun log(log: String)
  fun logError(log: String)
}