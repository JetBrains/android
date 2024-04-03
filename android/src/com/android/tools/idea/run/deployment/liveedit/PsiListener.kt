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
package com.android.tools.idea.run.deployment.liveedit

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction

/**
 * @param file: Where the file event originated
 * @param origin: The most narrow PSI Element where the edit event occurred.
 * @param parentGroup: A list of all functions that encapsulate the origin of the event in the source code ordered by nesting level, from
 * innermost to outermost. This will be used to determine which compose groups to invalidate on the given change.
 */
data class EditEvent(val file: PsiFile,
                     val origin: KtElement? = null,
                     val parentGroup: List<KtFunction> = emptyList(),
                     var unsupportedPsiEvents: ArrayList<UnsupportedPsiEvent> = ArrayList()) {

  constructor(file: PsiFile,
              origin: KtElement?,
              parentGroup: List<KtFunction> = emptyList(),
              unsupportedPsiEvent: UnsupportedPsiEvent) : this(file, origin, parentGroup) {
              unsupportedPsiEvents.add(unsupportedPsiEvent)
  }
}