/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.ui.save

import com.android.tools.idea.ui.AndroidAdbUiBundle.message
import com.intellij.ide.actions.RevealFileAction

/** Defines what happens after a screenshot or a screen recording is saved to a file. */
internal enum class PostSaveAction {
  NONE, SHOW_IN_FOLDER, OPEN;

  val isSupported: Boolean
    get() = this != SHOW_IN_FOLDER || RevealFileAction.isSupported()

  override fun toString(): String {
    return when (this) {
      NONE -> message("post.save.action.do.nothing")
      SHOW_IN_FOLDER -> message("post.save.action.show.in", RevealFileAction.getFileManagerName())
      OPEN -> message("post.save.action.open")
    }
  }
}