/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.groups

import com.android.tools.idea.preview.PreviewBundle.message
import javax.swing.Icon

/** Class representing groups available for selection in the [ComposePreviewManager]. */
sealed interface PreviewGroup {
  val displayName: String
  val icon: Icon?
  val name: String?

  /**
   * A [PreviewGroup] defined by the user. These can only be created as part of [PreviewElement]s.
   */
  data class Named(
    override val displayName: String,
    override val icon: Icon?,
    override val name: String?,
  ) : PreviewGroup

  /** [PreviewGroup] to be used when no filtering is to be applied to the preview. */
  object All : PreviewGroup {
    override val displayName: String = message("group.switch.all")
    override val icon: Icon? = null
    override val name: String? = null

    override fun toString(): String = "All Group"
  }

  companion object {
    fun namedGroup(displayName: String, icon: Icon? = null, name: String = displayName): Named =
      Named(displayName, icon, name)
  }
}
