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
package com.android.tools.idea.projectsystem

import com.intellij.openapi.extensions.ExtensionPointName

interface PseudoLocalesToken : Token {

  enum class PseudoLocalesState {
    /** Generation of pseudolocales is enabled for all variants that have the given application's application ID */
    ENABLED,
    /** Generation of pseudolocales is disabled for all variants that have the given application's application ID */
    DISABLED,
    /** Generation of pseudolocales is known to be enabled in some variants that have the given application ID, and disabled in others */
    BOTH,
    /** The project system either doesn't support returning this, or the given application is not found. */
    UNKNOWN,
  }

  companion object {
    val EP_NAME: ExtensionPointName<PseudoLocalesToken> =
      ExtensionPointName("com.android.tools.idea.projectsystem.pseudoLocalesToken")

    @JvmStatic
    fun AndroidProjectSystem.isPseudoLocalesEnabled(applicationProjectContext: ApplicationProjectContext): PseudoLocalesState =
      getTokenOrNull(EP_NAME)?.isPseudoLocalesEnabled(applicationProjectContext) ?: PseudoLocalesState.UNKNOWN
  }

  fun isPseudoLocalesEnabled(applicationProjectContext: ApplicationProjectContext): PseudoLocalesState
}
