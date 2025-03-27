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
package com.android.tools.adtui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.Component
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.JPanel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class TestComposeWizard(initialPage: @Composable WizardPageScope.() -> Unit) :
  InternalWizardDialogScope, WizardPageScope() {

  private val pageStack = mutableStateListOf<@Composable WizardPageScope.() -> Unit>(initialPage)

  @Composable
  fun Content() {
    prevAction =
      if (pageStack.size > 1) WizardAction { pageStack.removeLast() } else WizardAction.Disabled
    WizardPageScaffold(this, pageStack.last())
  }

  override val component: Component = JPanel()

  override fun pushPage(page: @Composable WizardPageScope.() -> Unit) {
    pageStack.add(page)
  }

  override fun popPage() {
    pageStack.removeLast()
  }

  override fun pageStackSize(): Int = pageStack.size

  private val closeLatch = CountDownLatch(1)

  override fun close() {
    closeLatch.countDown()
  }

  override fun cancel() {
    closeLatch.countDown()
  }

  fun awaitClose(timeout: Duration = 30.seconds) {
    if (!closeLatch.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
      throw TimeoutException("Close did not occur after $timeout")
    }
  }

  override var nextAction by mutableStateOf(WizardAction.Disabled)
  override var finishAction by mutableStateOf(WizardAction.Disabled)

  var prevAction: WizardAction = WizardAction.Disabled

  fun performAction(action: WizardAction) {
    checkNotNull(action.action) { "Action is disabled" }
    action.action!!.invoke(this)
  }
}
