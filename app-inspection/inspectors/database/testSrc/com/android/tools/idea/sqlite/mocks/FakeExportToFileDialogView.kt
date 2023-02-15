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
package com.android.tools.idea.sqlite.mocks

import com.android.tools.idea.sqlite.ui.exportToFile.ExportToFileDialogView

class FakeExportToFileDialogView : ExportToFileDialogView {
  val listeners = mutableListOf<ExportToFileDialogView.Listener>()

  override fun show() {}

  override fun addListener(listener: ExportToFileDialogView.Listener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: ExportToFileDialogView.Listener) {
    listeners.remove(listener)
  }
}
