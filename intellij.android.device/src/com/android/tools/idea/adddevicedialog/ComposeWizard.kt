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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.compose.StudioComposePanel
import com.android.tools.adtui.compose.catchAndShowErrors
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import javax.swing.Action
import javax.swing.JComponent
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.enableNewSwingCompositing
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

/**
 * A wizard dialog whose steps are implemented in Compose.
 *
 * Pages are implemented by composable functions of the type `@Composable WizardPageScope.() ->
 * Unit`.
 *
 * TODO(b/361168375): Consider making this more broadly available. If parts of Studio unrelated to
 *   the Add Device Dialog want to use this, it should be moved to adt.ui.compose.
 */
class ComposeWizard(
  val project: Project?,
  title: String,
  private val minimumSize: Dimension = DEFAULT_MIN_SIZE,
  private val preferredSize: Dimension = DEFAULT_PREFERRED_SIZE,
  initialPage: @Composable WizardPageScope.() -> Unit,
) : DialogWrapper(project) {

  private val pageStack = mutableStateListOf<@Composable WizardPageScope.() -> Unit>(initialPage)
  private val currentPage
    get() = pageStack.last()

  private val wizardDialogScope =
    object : InternalWizardDialogScope {
      override val component: Component
        get() = window

      override fun pushPage(page: @Composable WizardPageScope.() -> Unit) {
        pageStack.add(page)
      }

      override fun popPage() {
        pageStack.removeLast()
      }

      override fun pageStackSize(): Int = pageStack.size

      override fun close() {
        close(OK_EXIT_CODE)
      }

      override fun cancel() {
        close(CANCEL_EXIT_CODE)
      }
    }

  private val prevButton = WizardButton("Previous")
  private val nextButton = WizardButton("Next")
  private val finishButton = WizardButton("Finish")

  private val wizardPageScope =
    object : WizardPageScope() {
      override var nextAction by nextButton::action
      override var finishAction by finishButton::action
    }

  init {
    this.title = title
    init()
  }

  override fun createActions(): Array<Action> {
    return arrayOf()
  }

  // Don't include the default border; our banners need to span the entire width
  override fun createContentPaneBorder() = null

  // Don't include the bottom panel; we'll make buttons ourselves
  override fun createSouthPanel(): JComponent? = null

  override fun createCenterPanel(): JComponent {
    @OptIn(ExperimentalJewelApi::class) (enableNewSwingCompositing())
    val component = StudioComposePanel {
      CompositionLocalProvider(LocalProject provides project) {
        prevButton.action =
          if (pageStack.size > 1) WizardAction { pageStack.removeLast() } else WizardAction.Disabled
        wizardPageScope.apply { WizardPageScaffold(wizardDialogScope, currentPage) }
      }
    }
    component.preferredSize = preferredSize
    component.minimumSize = minimumSize

    return component
  }
}

@Composable
internal fun WizardPageScope.WizardPageScaffold(
  wizardDialogScope: InternalWizardDialogScope,
  content: @Composable WizardPageScope.() -> Unit,
) {
  Column {
    Box(Modifier.weight(1f)) { content() }
    Divider(Orientation.Horizontal)
    WizardButtonBar(
      wizardDialogScope,
      modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
    )
  }
}

@Composable
internal fun WizardPageScope.WizardButtonBar(
  wizardDialogScope: InternalWizardDialogScope,
  modifier: Modifier = Modifier,
) {
  with(wizardDialogScope) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      for (button in leftSideButtons) {
        OutlinedButton(onClick = { with(button.action) { invoke() } }) { Text(button.name) }
      }
      Spacer(Modifier.weight(1f))
      OutlinedButton(onClick = { cancel() }) { Text("Cancel") }
      OutlinedButton(onClick = { popPage() }, enabled = pageStackSize() > 1) { Text("Previous") }
      OutlinedButton(onClick = { with(nextAction) { invoke() } }, enabled = nextAction.enabled) {
        Text("Next")
      }
      DefaultButton(onClick = { with(finishAction) { invoke() } }, enabled = finishAction.enabled) {
        Text("Finish")
      }
    }
  }
}

/** Scope providing access to operations allowed in WizardActions. */
interface WizardDialogScope {
  /** Adds a new page to the page stack, generally in response to pressing Next. */
  fun pushPage(page: @Composable WizardPageScope.() -> Unit)

  /** Returns to the previous page in the page stack, generally in response to pressing Previous. */
  fun popPage()

  /** The number of pages currently on the page stack. */
  fun pageStackSize(): Int

  /** Causes the wizard to exit, returning [DialogWrapper.OK_EXIT_CODE]. */
  fun close()

  /** Causes the wizard to exit, returning [DialogWrapper.CANCEL_EXIT_CODE]. */
  fun cancel()
}

internal interface InternalWizardDialogScope : WizardDialogScope {
  /** A component to use as the parent for showing modal dialogs. */
  val component: Component
}

class WizardButton(name: String, action: WizardAction = WizardAction.Disabled) {
  var name by mutableStateOf(name)
  var action by mutableStateOf(action)
}

/** The action state of a wizard button: whether it is enabled, and if so, its behavior. */
class WizardAction(val action: (WizardDialogScope.() -> Unit)?) {
  val enabled: Boolean
    get() = action != null

  internal fun InternalWizardDialogScope.invoke() {
    catchAndShowErrors<ComposeWizard>(parent = component) { action?.invoke(this) }
  }

  companion object {
    val Disabled = WizardAction(null)
  }
}

/**
 * Scope providing access to wizard buttons, allowing pages to enable / disable buttons and define
 * their behavior.
 */
abstract class WizardPageScope {
  abstract var nextAction: WizardAction
  abstract var finishAction: WizardAction

  var leftSideButtons by mutableStateOf(emptyList<WizardButton>())

  private val state = mutableStateMapOf<Any, Any>()

  @Suppress("UNCHECKED_CAST")
  fun <T : Any> getOrCreateState(key: Class<T>, defaultState: () -> T): T =
    state.computeIfAbsent(key) { defaultState() } as T

  /**
   * Retrieves wizard-scoped state of the given type, or creates it if it has not yet been created
   * in this wizard.
   */
  inline fun <reified T : Any> getOrCreateState(noinline defaultState: () -> T): T =
    getOrCreateState(T::class.java, defaultState)
}

val LocalFileSystem = staticCompositionLocalOf<FileSystem> { FileSystems.getDefault() }
val LocalProject = staticCompositionLocalOf<Project?> { throw AssertionError() }

private val DEFAULT_PREFERRED_SIZE: Dimension = JBUI.size(900, 650)
private val DEFAULT_MIN_SIZE: Dimension = JBUI.size(600, 350)
