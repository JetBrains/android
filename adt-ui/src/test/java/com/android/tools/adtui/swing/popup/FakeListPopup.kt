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
package com.android.tools.adtui.swing.popup

import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.ListPopupStep
import java.awt.event.InputEvent
import javax.swing.event.ListSelectionListener

/**
 * A fake implementation of [ListPopup] for tests.
 */
internal class FakeListPopup<T>(items: List<T>) : FakeJBPopup<T>(items), ListPopup {
  override fun getListStep(): ListPopupStep<*> {
    TODO("Not yet implemented")
  }

  override fun handleSelect(handleFinalChoices: Boolean) {
    TODO("Not yet implemented")
  }

  override fun handleSelect(handleFinalChoices: Boolean, e: InputEvent?) {
    TODO("Not yet implemented")
  }

  override fun setHandleAutoSelectionBeforeShow(autoHandle: Boolean) {
    TODO("Not yet implemented")
  }

  override fun addListSelectionListener(listSelectionListener: ListSelectionListener?) {
    TODO("Not yet implemented")
  }
}