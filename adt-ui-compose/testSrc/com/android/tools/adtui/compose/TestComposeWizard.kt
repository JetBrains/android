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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.jewel.foundation.LocalComponent

class TestComposeWizard(initialPage: @Composable WizardPageScope.() -> Unit) : WizardDialogScope {

  override val coroutineScope = CoroutineScope(SupervisorJob())

  private val state: SnapshotStateMap<Any, Any> = mutableStateMapOf()
  private val pageStack = mutableStateListOf(WizardPage(WizardPageScope(coroutineScope, state), initialPage))
  private val currentPageScope
    get() = pageStack.last().pageScope

  private val currentPage
    get() = pageStack.last().content

  @Composable
  fun Content() {
    CompositionLocalProvider(LocalComponent provides component) {
      with(currentPageScope) { WizardPageScaffold(this@TestComposeWizard, currentPage) }
    }
  }

  override val component: JComponent = JPanel()

  override fun pushPage(page: @Composable (WizardPageScope.() -> Unit)) {
    pageStack.add(WizardPage(WizardPageScope(coroutineScope, state), page))
  }

  override fun popPage() {
    pageStack.removeLast()
  }

  override fun pageStackSize(): Int = pageStack.size

  private val closeLatch = CountDownLatch(1)

  override fun close() {
    closeLatch.countDown()
    coroutineScope.cancel()
  }

  override fun cancel() {
    close()
  }

  fun awaitClose(timeout: Duration = 30.seconds) {
    if (!closeLatch.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
      throw TimeoutException("Close did not occur after $timeout")
    }
  }

  var nextAction: WizardAction
    get() = currentPageScope.nextAction
    set(value) {
      currentPageScope.nextAction = value
    }

  fun performAction(action: WizardAction) {
    checkNotNull(action.action) { "Action is disabled" }
    action.action.invoke(this)
  }
}
