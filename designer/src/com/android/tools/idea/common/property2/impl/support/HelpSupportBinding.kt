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
package com.android.tools.idea.common.property2.impl.support

import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.adtui.stdui.registerAnActionKey
import com.android.tools.idea.common.property2.api.PropertyItem
import com.android.tools.idea.common.property2.impl.model.KeyStrokes
import javax.swing.JComponent

/**
 * Helper object for binding the 3 standard help operation to standard keys.
 */
object HelpSupportBinding {

  fun registerHelpKeyActions(component: JComponent, getProperty: () -> PropertyItem?, condition: Int = JComponent.WHEN_FOCUSED) {
    component.registerAnActionKey({ getProperty()?.helpSupport?.help }, KeyStrokes.f1, "help", condition)
    component.registerAnActionKey({ getProperty()?.helpSupport?.secondaryHelp }, KeyStrokes.shiftF1, "help2", condition)
    component.registerActionKey({ getProperty()?.helpSupport?.browse() }, KeyStrokes.browse, "browse")
  }
}
