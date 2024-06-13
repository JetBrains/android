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
package com.android.tools.idea.adddevicedialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class TestComposeWizard(initialPage: @Composable WizardPageScope.() -> Unit) :
  WizardDialogScope, WizardPageScope {

  private val pageStack = mutableStateListOf<@Composable WizardPageScope.() -> Unit>(initialPage)

  @Composable
  fun Content() {
    pageStack.last()()
  }

  override fun pushPage(page: @Composable WizardPageScope.() -> Unit) {
    pageStack.add(page)
  }

  override fun popPage() {
    pageStack.removeLast()
  }

  override fun close() {}

  override var nextActionName by mutableStateOf("Next")
  override var finishActionName by mutableStateOf("Finish")

  override var nextAction by mutableStateOf(WizardAction.Disabled)
  override var finishAction by mutableStateOf(WizardAction.Disabled)
}
