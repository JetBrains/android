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
package com.android.tools.idea.project.hyperlink

import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.project.Project
import javax.swing.event.HyperlinkEvent

/**
 * A fragment of the final message rendered from a [com.android.tools.idea.project.messages.SyncMessage].
 *
 *
 * A fragment usually holds both visual representation of an action(s) and the action(s) themselves in a form of [executeHandler].
 * However, the visual part may be omitted if the fragment is used to complement already existing visual representation stored as
 * the `text` property of a [com.android.tools.idea.project.messages.SyncMessage].
 */
interface SyncMessageFragment {
  val urls: Collection<String>

  fun executeHandler(project: Project, event: HyperlinkEvent)

  fun executeIfClicked(project: Project, event: HyperlinkEvent): Boolean {
    if (urls.contains(event.description)) {
      executeHandler(project, event)
      return true
    }
    return false
  }

  fun toHtml(): String

  val quickFixIds: List<AndroidStudioEvent.GradleSyncQuickFix>
}