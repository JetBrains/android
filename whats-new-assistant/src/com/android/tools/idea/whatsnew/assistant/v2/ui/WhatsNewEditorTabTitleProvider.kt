/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant.v2.ui

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.TabTitle
import com.intellij.openapi.util.NlsContexts.Tooltip
import com.intellij.openapi.vfs.VirtualFile

internal class WhatsNewEditorTabTitleProvider private constructor() : EditorTabTitleProvider {
  override fun getEditorTabTitle(project: Project, file: VirtualFile): @TabTitle String? = null

  override fun getEditorTabTooltipText(project: Project, virtualFile: VirtualFile): @Tooltip String? =
    if (virtualFile is WhatsNewVirtualFile) "" else null
}
