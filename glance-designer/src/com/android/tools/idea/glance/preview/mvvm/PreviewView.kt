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
package com.android.tools.idea.glance.preview.mvvm

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.stdui.ActionData
import com.android.tools.adtui.stdui.UrlData
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import javax.swing.JComponent

/** Preview View interface in the MVVM pattern. Intended to be accessed by the ViewModel. */
interface PreviewView {
  @UiThread
  fun showErrorMessage(message: String, recoveryUrl: UrlData?, actionToRecover: ActionData?)

  @UiThread fun showLoadingMessage(message: String)

  @UiThread fun showContent()

  @UiThread fun updateToolbar()

  val component: JComponent

  val surface: NlDesignSurface
}
