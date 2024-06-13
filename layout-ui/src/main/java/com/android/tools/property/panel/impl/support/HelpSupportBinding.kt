/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.property.panel.impl.support

import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionShortCutSet
import com.android.tools.adtui.stdui.registerAnActionKey
import com.android.tools.property.panel.api.PropertyItem
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import javax.swing.JComponent

/** Helper object for binding the 3 standard help operation to standard keys. */
object HelpSupportBinding {

  fun registerHelpKeyActions(
    component: JComponent,
    getProperty: () -> PropertyItem?,
    condition: Int = JComponent.WHEN_FOCUSED,
  ) {
    component.registerAnActionKey(
      { getProperty()?.helpSupport?.help },
      KeyStrokes.F1,
      "help",
      condition,
    )
    component.registerAnActionKey(
      { getProperty()?.helpSupport?.secondaryHelp },
      KeyStrokes.SHIFT_F1,
      "help2",
      condition,
    )
    ActionManager.getInstance()?.getAction(IdeActions.ACTION_GOTO_DECLARATION)?.let {
      component.registerActionShortCutSet({ getProperty()?.helpSupport?.browse() }, it.shortcutSet)
    }
  }
}
