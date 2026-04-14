/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.recompositions

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.InspectorModel.SelectionListener
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ParameterGroupItem
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ParameterItem
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.RecomposeStateReadData
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.RecompositionDetails
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.RecompositionDetailsResult
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.RecompositionDetailsResult.RecompositionDetailsData
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.RecompositionDetailsResult.Waiting
import com.android.tools.idea.layoutinspector.properties.PropertyType
import com.android.tools.idea.layoutinspector.ui.LayoutInspectorRootPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import javax.swing.Icon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

private const val MAX_EXPRESSION_LENGTH = 80
private const val PARAMETER_CHANGES = "Parameter changes:"
private const val STATE_READ_START_LINE = "State read value: "
private const val STACK_TRACE_START_LINE = "    at "
private const val PREV_DESCRIPTION_KEY = "layout.inspector.recomposition.prev"
private const val NEXT_DESCRIPTION_KEY = "layout.inspector.recomposition.next"
private const val HIDE_DESCRIPTION_KEY = "layout.inspector.recomposition.hide"
private const val NO_STATE_READS_ID = "layout.inspector.recomposition.no-state-reads"
private const val NO_RECOMPOSITION_DETAILS = "layout.inspector.recomposition.no-details"

internal const val INVALIDATED = "<invalidated>"

/** Specifies the content to be displayed in the [RecompositionUiPanel]. */
data class RecompositionContent(
  /** Text with the current recomposition number to be shown in a central place in the panel */
  val recompositionText: String = "",
  /** Empty State text when no recomposition counts are available. */
  val emptyStateText: String = "",
  /** Text with the number of state reads for the current recomposition */
  val stateReadsText: String = "",
  /** Text with parameter changes and stack traces of all the state reads for this recomposition */
  val detailsText: String = "",
  /** Specifies which composable that we are showing state reads for. */
  val composableInspected: ComposableDefinition? = null,
  /** An update count. Only meant for tests */
  @TestOnly val updates: Int = 0,
)

/** Model for the [RecompositionUiPanel]. */
internal interface RecompositionUiModel {
  /** Show the State Read panel if the value is true, otherwise hide. */
  val show: StateFlow<Boolean>

  /** The content to show in the [RecompositionUiPanel]. */
  val content: StateFlow<RecompositionContent>

  /** The total number of recompositions of the composable being inspected. */
  val recompositions: StateFlow<Int>

  /** An action to navigate to the state reads for the previous recomposition */
  val prevAction: AnAction

  /** An action to navigate to the state reads for the next recomposition */
  val nextAction: AnAction

  /** An action to close the state read panel */
  val minimizeAction: AnAction
}

internal class RecompositionUiModelImpl(
  private val model: InspectorModel,
  parentScope: CoroutineScope,
  parentDisposable: Disposable,
  private val resultShown: () -> Unit,
) : RecompositionUiModel {
  private val scope = parentScope.createChildScope(parentDisposable = parentDisposable)
  private val lock = Any()
  @GuardedBy("lock") private var currentKey: RecompositionKey? = null
  private var hasStateReadsForPreviousRecomposition = false

  private val _show = MutableStateFlow(false)
  override val show = _show.asStateFlow()

  private val _content = MutableStateFlow(RecompositionContent())
  override val content = _content.asStateFlow()

  private val _recompositions = MutableStateFlow(0)
  override val recompositions = _recompositions.asStateFlow()

  override val prevAction = createAction(AllIcons.Actions.Play_back, PREV_DESCRIPTION_KEY, ::gotoPrevRecomposition, ::hasPrevComposition)

  override val nextAction = createAction(AllIcons.Actions.Play_forward, NEXT_DESCRIPTION_KEY, ::gotoNextRecomposition, ::hasNextComposition)

  override val minimizeAction =
    createAction(AllIcons.General.HideToolWindow, HIDE_DESCRIPTION_KEY, { model.recompositionModel.stopShowingRecompositionDetails() })

  private enum class InactiveState(private val messageId: String, private val detailsId: String) {
    NOTHING_SELECTED(
      messageId = "layout.inspector.recomposition.nothing.selected",
      detailsId = "layout.inspector.recomposition.nothing.selected.details",
    ),
    WAITING(messageId = "layout.inspector.recomposition.waiting", detailsId = "layout.inspector.recomposition.waiting.details"),
    VIEW(messageId = "layout.inspector.recomposition.view", detailsId = "layout.inspector.recomposition.view.details"),
    NOT_OBSERVED(
      messageId = "layout.inspector.recomposition.not.observed",
      detailsId = "layout.inspector.recomposition.not.observed.details",
    );

    fun message() = LayoutInspectorBundle.message(messageId)

    fun messageDetails() = LayoutInspectorBundle.message(detailsId)
  }

  private val listener = SelectionListener { _, view, _ -> updateStateOfSelection(view) }

  private val updateListener = InspectorModel.ModificationListener { _, _, _ -> recompositionUpdate() }

  init {
    model.addSelectionListener(listener)
    model.addModificationListener(updateListener)
    scope.launch {
      model.recompositionModel.recompositionDataRequested.collect { key ->
        if (key == null) {
          stopStateObservations()
        }
      }
    }
    scope.launch { model.recompositionModel.observedForRecompositions.collect { updateStateOfSelection(model.selection) } }
    scope.launch { model.recompositionModel.recompositionDetails.filterNotNull().collect { showResult(it) } }

    Disposer.register(parentDisposable) {
      model.removeSelectionListener(listener)
      model.removeModificationListener(updateListener)
    }
  }

  private fun recompositionUpdate() {
    _recompositions.value = model.selection?.recompositions?.count ?: 0
  }

  private fun updateStateOfSelection(view: ViewNode?) {
    val requested = model.recompositionModel.recompositionDataRequested.value
    if (requested != null) {
      when {
        view == null -> showInactiveState(InactiveState.NOTHING_SELECTED)
        view !is ComposeViewNode -> showInactiveState(InactiveState.VIEW)
        !model.recompositionModel.isNodeObserved(view) -> showInactiveState(InactiveState.NOT_OBSERVED)
        view.anchorHash == synchronized(lock) { currentKey?.composable?.anchorHash } -> {} // Keep current recomposition
        else -> loadRecompositionDetails(view)
      }
    }
  }

  private fun showResultFor(key: RecompositionKey, result: RecompositionDetailsResult) {
    when (result) {
      is Waiting -> showInactiveState(InactiveState.WAITING)
      is RecompositionDetailsData ->
        if (result.key == key) showRecompositionDetailsResult(result) else showInactiveState(InactiveState.WAITING)
    }
  }

  private fun showResult(result: RecompositionDetailsResult) {
    when (result) {
      is Waiting -> showInactiveState(InactiveState.WAITING)
      is RecompositionDetailsData -> showRecompositionDetailsResult(result)
    }
  }

  private fun showRecompositionDetailsResult(result: RecompositionDetailsData) {
    synchronized(lock) {
      currentKey = result.key
      val node = result.key.composable
      hasStateReadsForPreviousRecomposition = result.hasDataForPreviousRecomposition
      _show.value = true
      _recompositions.value = model.selection?.recompositions?.count ?: 0
      val hasData = result.details.parameterChanges.isNotEmpty() || result.details.reads.isNotEmpty()
      val emptyText =
        when {
          hasData -> ""
          StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_PARAMETER_CHANGES.get() -> LayoutInspectorBundle.message(NO_RECOMPOSITION_DETAILS)
          else -> LayoutInspectorBundle.message(NO_STATE_READS_ID)
        }
      _content.value =
        RecompositionContent(
          recompositionText = generateRecompositionText(result.key),
          stateReadsText = generateStateReadsText(result.details.reads.size),
          detailsText = generateDetailsText(result.details),
          composableInspected = ComposableDefinition(node.qualifiedName, node.composeFilename),
          emptyStateText = emptyText,
          updates = _content.value.updates + 1,
        )
    }
    resultShown()
  }

  private fun loadRecompositionDetails(composable: ComposeViewNode, recomposition: Int = composable.recompositions.count) {
    val key = RecompositionKey(composable, recomposition)
    if (model.recompositionModel.recompositionDataRequested.value != key) {
      model.recompositionModel.requestRecompositionDataFor(composable, recomposition)
    } else {
      // The user navigated back to an observable composable from either a View or a
      // non-observable composable. The result may still hold the state reads for the wanted composable
      // and recomposition.
      showResultFor(key, model.recompositionModel.recompositionDetails.value ?: Waiting)
    }
  }

  private fun showInactiveState(state: InactiveState, waitingFor: RecompositionKey? = null) {
    synchronized(lock) {
      if (waitingFor != null && currentKey == waitingFor) {
        // Do not show the waiting state if we are already displaying what we are waiting for.
        return
      }
      currentKey = null
      _show.value = true
      _recompositions.value = model.selection?.recompositions?.count ?: 0
      _content.value =
        RecompositionContent(
          recompositionText = state.message(),
          emptyStateText = state.messageDetails(),
          updates = _content.value.updates + 1,
        )
    }
  }

  private fun stopStateObservations() {
    clear()
  }

  private fun clear() {
    synchronized(lock) {
      currentKey = null
      _show.value = false
      _recompositions.value = 0
      _content.value = RecompositionContent()
    }
  }

  private fun gotoPrevRecomposition(event: AnActionEvent) {
    val key = synchronized(lock) { currentKey } ?: return
    loadRecompositionDetails(key.composable, key.recomposition - 1)
    LayoutInspectorRootPanel.get(event)?.currentClient?.stats?.prevRecompositionChosen()
  }

  private fun gotoNextRecomposition(event: AnActionEvent) {
    val key = synchronized(lock) { currentKey } ?: return
    loadRecompositionDetails(key.composable, key.recomposition + 1)
    LayoutInspectorRootPanel.get(event)?.currentClient?.stats?.nextRecompositionChosen()
  }

  private fun hasPrevComposition(): Boolean {
    synchronized(lock) { currentKey } ?: return false
    return hasStateReadsForPreviousRecomposition
  }

  private fun hasNextComposition(): Boolean {
    val key = synchronized(lock) { currentKey } ?: return false
    return key.recomposition < key.composable.recompositions.count
  }

  private fun generateRecompositionText(key: RecompositionKey): String {
    return LayoutInspectorBundle.message("layout.inspector.recomposition.number", key.recomposition.toString())
  }

  private fun generateStateReadsText(readCount: Int): String {
    return LayoutInspectorBundle.message("layout.inspector.state.read.count", readCount.toString())
  }

  private fun generateDetailsText(details: RecompositionDetails): String {
    val builder = StringBuilder()
    builder.appendParameterChanges(details.parameterChanges)
    builder.appendStackTraces(details.reads)
    return builder.toString()
  }

  private fun StringBuilder.appendParameterChanges(parameterChanges: List<ParameterItem>) {
    if (parameterChanges.isEmpty()) {
      return
    }
    appendLine(PARAMETER_CHANGES)
    parameterChanges.forEach { parameter ->
      append("- ${parameter.name}: ")
      val maxLengthReached = generateExpressionWithLengthLimit(parameter)
      appendLine()

      // Write the full value if we cut off the end of the value expression:
      if (maxLengthReached) {
        // Use the name: "value" such that the folding detector can find the generated value section
        generateValue(parameter, 0, "value")
        appendLine() // terminates the value
        appendLine() // add an empty line for easy folding recognition
      }
    }
    appendLine()
  }

  private fun StringBuilder.appendStackTraces(reads: List<RecomposeStateReadData>) {
    reads.forEach { data ->
      generateStateReadLine(data.value, data.invalidated)
      data.stacktrace.forEach { trace ->
        val fileName = trace.fileName.takeIf { it.isNotEmpty() } ?: "Unknown Source"
        appendLine("$STACK_TRACE_START_LINE${trace.declaringClass}.${trace.methodName}($fileName:${trace.lineNumber})")
      }
      appendLine()
    }
  }

  private fun StringBuilder.generateStateReadLine(item: ParameterItem, invalidated: Boolean) {
    val message = StringBuilder()
    message.append(STATE_READ_START_LINE)
    val maxLengthReached = message.generateExpressionWithLengthLimit(item)
    if (invalidated) {
      message.append(" $INVALIDATED")
    }
    var read = message.toString()
    LayoutInspectorRecompositionRewriter.EP_NAME.extensionList.forEach { read = it.rewriteStateRead(model.project, read) }
    appendLine(read)

    // Write the full value if we cut off the end of the value expression:
    if (maxLengthReached) {
      generateValue(item, 0)
      appendLine() // terminates the value
      appendLine() // generates an empty line before the stacktrace
    }
  }

  private fun StringBuilder.generateValue(item: ParameterItem, indent: Int, nameOverride: String? = null) {
    val spacing = "  ".repeat(indent)
    val children = (item as? ParameterGroupItem)?.children ?: emptyList()
    val isList = item.type == PropertyType.ITERABLE
    val value =
      when {
        item.name == "..." -> ""
        isList && children.isEmpty() -> "[]"
        isList -> ""
        else -> item.value
      }
    append("$spacing${nameOverride ?: item.name}: $value")
    if (children.isEmpty()) {
      return
    }
    append(if (isList) "[" else " {")
    var separator = ""
    children.forEach { child ->
      appendLine(separator)
      separator = ","
      generateValue(child, indent + 1)
    }
    appendLine()
    append("$spacing${if (isList) "]" else "}"}")
  }

  private fun StringBuilder.generateExpressionWithLengthLimit(item: ParameterItem): Boolean {
    val expression = StringBuilder()
    expression.generateExpression(item)
    var maxLengthReached = false
    if (expression.length > MAX_EXPRESSION_LENGTH) {
      // Limit the expression for the value:
      expression.delete(MAX_EXPRESSION_LENGTH, expression.length)
      expression.append("...")
      maxLengthReached = true
    }
    append(expression)
    return maxLengthReached
  }

  private fun StringBuilder.generateExpression(item: ParameterItem) {
    if (length > MAX_EXPRESSION_LENGTH) {
      return
    }
    val value = item.value ?: item.name.takeIf { it == "..." }.orEmpty()
    val isList = value.startsWith("List")
    if (!isList) {
      append(value)
    }
    val group = item as? ParameterGroupItem
    if (group != null) {
      append(if (isList) "[" else "(")
      var separator = ""
      group.children.forEach { element ->
        append(separator)
        generateExpression(element)
        separator = ", "
      }
      append(if (isList) "]" else ")")
    } else if (isList) {
      append("[]")
    }
  }

  private fun createAction(
    icon: Icon,
    descriptionKey: String,
    action: (AnActionEvent) -> Unit,
    enabled: () -> Boolean = { true },
  ): AnAction {
    val description = LayoutInspectorBundle.message(descriptionKey)
    return object : DumbAwareAction(description, null, icon) {
      override fun actionPerformed(event: AnActionEvent) {
        action(event)
      }

      override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = enabled()
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }
  }
}
