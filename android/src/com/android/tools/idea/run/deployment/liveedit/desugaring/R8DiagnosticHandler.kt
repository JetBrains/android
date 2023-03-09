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
package com.android.tools.idea.run.deployment.liveedit.desugaring

import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.desugarFailure
import com.android.tools.r8.Diagnostic
import com.android.tools.r8.DiagnosticsHandler

class R8DiagnosticHandler(private val logger: DesugarLogger) : DiagnosticsHandler{
  override fun error(error: Diagnostic?) {
    super.error(error)
    error?.let {
      logger.log(it.diagnosticMessage)
      desugarFailure(it.diagnosticMessage)
    }
  }
}