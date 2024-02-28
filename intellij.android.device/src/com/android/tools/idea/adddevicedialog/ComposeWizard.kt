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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import javax.swing.Action
import javax.swing.JComponent
import kotlinx.coroutines.launch
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.enableNewSwingCompositing

/**
 * A wizard dialog whose steps are implemented in Compose.
 *
 * Pages are implemented by composable functions of the type `@Composable WizardPageScope.() ->
 * Unit`.
 */
internal class ComposeWizard(
  val project: Project?,
  title: String,
  initialPage: @Composable WizardPageScope.() -> Unit,
) : DialogWrapper(project) {

  val dialogScope =
    AndroidCoroutineScope(
      disposable,
      AndroidDispatchers.uiThread(ModalityState.stateForComponent(rootPane)),
    )

  private val pageStack = mutableStateListOf<@Composable WizardPageScope.() -> Unit>(initialPage)
  private val currentPage
    get() = pageStack.last()

  private val wizardDialogScope =
    object : WizardDialogScope {
      override fun pushPage(page: @Composable WizardPageScope.() -> Unit) {
        pageStack.add(page)
      }

      override fun popPage() {
        pageStack.removeLast()
      }

      override fun close() {
        close(OK_EXIT_CODE)
      }
    }

  private val prevButton = WizardButton("Previous")
  private val nextButton = WizardButton("Next")
  private val finishButton = WizardButton("Finish")

  private val wizardPageScope =
    object : WizardPageScope {
      override var nextActionName by nextButton::name
      override var finishActionName by finishButton::name
      override var nextAction by nextButton::action
      override var finishAction by finishButton::action
    }

  private inner class WizardButton(name: String) : DialogWrapper.DialogWrapperAction(name) {
    var name by mutableStateOf(name)
    var action by mutableStateOf(WizardAction.Disabled)

    init {
      dialogScope.launch { snapshotFlow(::name).collect { putValue(Action.NAME, it) } }
      dialogScope.launch { snapshotFlow(::action).collect { isEnabled = it.enabled } }
    }

    override fun doAction(e: ActionEvent?) {
      action.action?.let { wizardDialogScope.apply(it) }
    }
  }

  init {
    this.title = title
    init()
  }

  override fun createActions(): Array<Action> {
    return arrayOf(cancelAction, prevButton, nextButton, finishButton)
  }

  override fun createCenterPanel(): JComponent {
    @OptIn(ExperimentalJewelApi::class) (enableNewSwingCompositing())
    val component = JewelComposePanel {
      CompositionLocalProvider(LocalProject provides project) {
        prevButton.action =
          if (pageStack.size > 1) WizardAction { pageStack.removeLast() } else WizardAction.Disabled
        wizardPageScope.apply { currentPage() }
      }
    }
    component.preferredSize = DEFAULT_PREFERRED_SIZE
    component.minimumSize = DEFAULT_MIN_SIZE

    return component
  }
}

/** Scope providing access to operations allowed in WizardActions. */
interface WizardDialogScope {
  fun pushPage(page: @Composable WizardPageScope.() -> Unit)

  fun popPage()

  fun close()
}

/** The action state of a wizard button: whether it is enabled, and if so, its behavior. */
class WizardAction(val action: (WizardDialogScope.() -> Unit)?) {
  val enabled: Boolean
    get() = action != null

  companion object {
    val Disabled = WizardAction(null)
  }
}

/**
 * Scope providing access to wizard buttons, allowing pages to enable / disable buttons and define
 * their behavior.
 */
interface WizardPageScope {
  var nextActionName: String
  var finishActionName: String
  var nextAction: WizardAction
  var finishAction: WizardAction
}

internal val LocalFileSystem = staticCompositionLocalOf<FileSystem> { FileSystems.getDefault() }
internal val LocalProject = staticCompositionLocalOf<Project?> { throw AssertionError() }

private val DEFAULT_PREFERRED_SIZE: Dimension = JBUI.size(900, 650)
private val DEFAULT_MIN_SIZE: Dimension = JBUI.size(600, 350)
