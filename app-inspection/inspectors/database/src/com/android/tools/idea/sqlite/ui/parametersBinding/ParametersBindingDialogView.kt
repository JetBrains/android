/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.sqlite.ui.parametersBinding

import com.android.tools.idea.sqlite.controllers.SqliteParameter
import com.android.tools.idea.sqlite.controllers.SqliteParameterValue
import com.android.tools.idea.sqlite.ui.parametersBinding.ParametersBindingDialogView.Listener

/**
 * Abstraction used by [ParametersBindingController] to avoid direct dependency on the UI
 * implementation.
 *
 * A dialog that allows the user to assign values to templates in a SQLite statement.
 *
 * @see [Listener] for the listener interface.
 */
interface ParametersBindingDialogView {
  fun show()
  fun showNamedParameters(parameters: Set<SqliteParameter>)

  fun addListener(listener: Listener)
  fun removeListener(listener: Listener)

  interface Listener {
    /**
     * This method is called when the user has assigned a value to each parameter.
     *
     * @param parameters A map where each name of a parameter is mapped to the value assigned to it.
     */
    fun bindingCompletedInvoked(parameters: Map<SqliteParameter, SqliteParameterValue>)
  }
}
