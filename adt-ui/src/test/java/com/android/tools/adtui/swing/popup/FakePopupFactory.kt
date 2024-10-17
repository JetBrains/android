/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.swing.popup

import org.mockito.kotlin.mock
import java.awt.Component
import javax.swing.Popup
import javax.swing.PopupFactory

/**
 * A fake [PopupFactory] that creates a mock Popup and records information of how it was requested.
 */
class FakePopupFactory : PopupFactory() {
  var contents: Component? = null
  val mockPopup = mock<Popup>()

  override fun getPopup(owner: Component?, contents: Component?, x: Int, y: Int, isHeavyWeightPopup: Boolean): Popup {
    this.contents = contents
    return mockPopup
  }
}
