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
package com.android.tools.idea.flags

import com.intellij.ide.AboutPopupDescriptionProvider
import com.intellij.openapi.util.NlsContexts

/** Provider to attach extra debug information to the about box */
class StudioFlagsAboutProvider: AboutPopupDescriptionProvider {
  // Don't show directly to the user in the about box.
  override fun getDescription(): @NlsContexts.DetailedDescription String?  = null

  // Shown in the copied text if not null.
  override fun getExtendedDescription(): @NlsContexts.DetailedDescription String = StudioFlags.FLAGS.toString("StudioFlags")
}