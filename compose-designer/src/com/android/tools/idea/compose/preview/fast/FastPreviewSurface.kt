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
package com.android.tools.idea.compose.preview.fast

import com.android.tools.idea.editors.fast.CompilationResult
import kotlinx.coroutines.Deferred

/** Interface to be implemented by surfaces (like the Preview) that support FastPreview. */
interface FastPreviewSurface {
  /**
   * Request a fast preview refresh. The result [Deferred] will contain the result of the
   * compilation or the method will return [CompilationResult.CompilationAborted] if the compilation
   * request could not be scheduled (e.g. the code has syntax errors).
   */
  fun requestFastPreviewRefreshAsync(): Deferred<CompilationResult>
}
