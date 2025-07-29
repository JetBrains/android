/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.stateinspection

import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.InspectorModel.StateReadsNodeListener
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ParameterGroupItem
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ParameterItem
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.RecomposeStateReadData
import com.android.tools.idea.layoutinspector.properties.PropertyType
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import icons.StudioIcons.LayoutEditor.Motion.NEXT_TICK
import icons.StudioIcons.LayoutEditor.Motion.PREVIOUS_TICK
import javax.swing.Icon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val MAX_EXPRESSION_LENGTH = 80
private const val STATE_READ_START_LINE = "State read value: "
private const val STACK_TRACE_START_LINE = "    at "
private const val PREV_DESCRIPTION_KEY = "layout.inspector.recomposition.prev"
private const val NEXT_DESCRIPTION_KEY = "layout.inspector.recomposition.next"
private const val HIDE_DESCRIPTION_KEY = "layout.inspector.recomposition.hide"

/** Model for the [StateInspectionPanel]. */
interface StateInspectionModel {
  /** Show the State Read panel if the value is true, otherwise hide. */
  val show: StateFlow<Boolean>

  /** An action to navigate to the state reads for the previous recomposition */
  val prevAction: AnAction

  /** Text with the current recomposition number to be shown in a central place in the panel */
  val recompositionText: StateFlow<String>

  /** An action to navigate to the state reads for the next recomposition */
  val nextAction: AnAction

  /** An action to close the state read panel */
  val minimizeAction: AnAction

  /** Text with the number of state reads for the current recomposition */
  val stateReadsText: StateFlow<String>

  /** Text with the stack traces of all the state reads for this recomposition */
  val stackTraceText: StateFlow<String>

  /** An update count. Can be used to indicate action mode changes, and for tests */
  val updates: StateFlow<Int>
}

class StateInspectionModelImpl(
  private val model: InspectorModel,
  private val scope: CoroutineScope,
  private val treeSettings: TreeSettings,
  private val client: () -> InspectorClient,
  parentDisposable: Disposable,
) : StateInspectionModel {
  private var currentNode: ComposeViewNode? = null
  private var currentRecomposition = 0
  private var firstRecomposition = 0

  private val _show = MutableStateFlow(false)
  override val show = _show.asStateFlow()

  // TODO(b/441353152): Request an icon for this feature:
  override val prevAction =
    createAction(PREVIOUS_TICK, PREV_DESCRIPTION_KEY, ::gotoPrevRecomposition, ::hasPrevComposition)

  private val _recompositionText = MutableStateFlow("")
  override val recompositionText = _recompositionText.asStateFlow()

  // TODO(b/441353152): Request an icon for this feature:
  override val nextAction =
    createAction(NEXT_TICK, NEXT_DESCRIPTION_KEY, ::gotoNextRecomposition, ::hasNextComposition)

  override val minimizeAction =
    createAction(
      AllIcons.General.HideToolWindow,
      HIDE_DESCRIPTION_KEY,
      {
        _show.value = false
        stopStateObservations()
      },
    )

  private val _stateReadsText = MutableStateFlow("")
  override val stateReadsText = _stateReadsText.asStateFlow()

  private val _stackTraceText = MutableStateFlow("")
  override val stackTraceText = _stackTraceText.asStateFlow()

  private val _updates = MutableStateFlow(0)
  override val updates = _updates.asStateFlow()

  private val listener = StateReadsNodeListener { view ->
    val composable = view as? ComposeViewNode
    _show.value = (composable != null)
    if (composable != null) {
      loadRecompositionStateReads(composable, composable.recompositions.count)
    } else {
      stopStateObservations()
    }
  }

  private val updateListener =
    InspectorModel.ModificationListener { _, _, _ -> _updates.value += 1 }

  init {
    model.addStateReadsNodeListener(listener)
    model.addModificationListener(updateListener)
    Disposer.register(parentDisposable) {
      model.removeStateReadsNodeListener(listener)
      model.removeModificationListener(updateListener)
    }
  }

  private fun loadRecompositionStateReads(composable: ComposeViewNode, recomposition: Int) {
    if (composable.anchorHash != currentNode?.anchorHash && !treeSettings.observeStateReadsForAll) {
      currentNode = null
      _recompositionText.value =
        LayoutInspectorBundle.message("layout.inspector.recomposition.waiting")
      _stateReadsText.value = ""
      _stackTraceText.value = ""
      _updates.value += 1
    }
    scope.launch {
      val result =
        client().getRecompositionStateReadsFromCache(composable, recomposition) ?: return@launch
      currentNode = result.node
      currentRecomposition = result.recomposition
      firstRecomposition = result.firstObservedRecomposition
      _recompositionText.value = generateRecompositionText()
      _stateReadsText.value = generateStateReadsText(result.reads.size)
      _stackTraceText.value = generateStackTraces(result.reads)
      _updates.value += 1
    }
  }

  private fun stopStateObservations() {
    val node = currentNode ?: return
    if (!treeSettings.observeStateReadsForAll) {
      scope.launch { client().getRecompositionStateReadsFromCache(node, 0) }
    }
    currentNode = null
    currentRecomposition = 0
    _recompositionText.value = ""
    _stateReadsText.value = ""
    _stackTraceText.value = ""
    _updates.value += 1
  }

  private fun gotoPrevRecomposition() {
    val node = currentNode ?: return
    loadRecompositionStateReads(node, currentRecomposition - 1)
  }

  private fun gotoNextRecomposition() {
    val node = currentNode ?: return
    loadRecompositionStateReads(node, currentRecomposition + 1)
  }

  private fun hasPrevComposition(): Boolean {
    val node = currentNode ?: return false
    return currentRecomposition > firstRecomposition && node.recompositions.count > 0
  }

  private fun hasNextComposition(): Boolean {
    val node = currentNode ?: return false
    return currentRecomposition < node.recompositions.count
  }

  private fun generateRecompositionText(): String {
    return LayoutInspectorBundle.message(
      "layout.inspector.recomposition.number",
      currentRecomposition.toString(),
    )
  }

  private fun generateStateReadsText(readCount: Int): String {
    return LayoutInspectorBundle.message("layout.inspector.state.read.count", readCount.toString())
  }

  private fun generateStackTraces(reads: List<RecomposeStateReadData>): String {
    val builder = StringBuilder()
    var index = 0
    reads.forEach { data ->
      index++
      generateStateReadLine(builder, data.value, data.invalidated)
      data.stacktrace.forEach { trace ->
        val fileName = trace.fileName.takeIf { it.isNotEmpty() } ?: "Unknown Source"
        builder.appendLine(
          "$STACK_TRACE_START_LINE${trace.declaringClass}.${trace.methodName}($fileName:${trace.lineNumber})"
        )
      }
      builder.appendLine()
    }
    return builder.toString()
  }

  private fun generateStateReadLine(
    builder: StringBuilder,
    item: ParameterItem,
    invalidated: Boolean,
  ) {
    val message = StringBuilder()
    message.append(STATE_READ_START_LINE)
    generateExpression(message, item)
    var maxLengthReached = false
    if (message.length > MAX_EXPRESSION_LENGTH) {
      // Limit the expression for the value:
      message.delete(MAX_EXPRESSION_LENGTH, message.length)
      message.append("...")
      maxLengthReached = true
    }
    if (invalidated) {
      message.append(" \uD83D\uDFE2")
    }
    builder.appendLine(message.toString())

    // Write the full value if we cut off the end of the value expression:
    if (maxLengthReached) {
      generateValue(builder, item, 0)
      builder.appendLine() // terminates the value
      builder.appendLine() // generates an empty line before the stacktrace
    }
  }

  private fun generateValue(builder: StringBuilder, item: ParameterItem, indent: Int) {
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
    builder.append("$spacing${item.name}: $value")
    if (children.isEmpty()) {
      return
    }
    builder.append(if (isList) "[" else " {")
    var separator = ""
    children.forEach { child ->
      builder.appendLine(separator)
      separator = ","
      generateValue(builder, child, indent + 1)
    }
    builder.appendLine()
    builder.append("$spacing${if (isList) "]" else "}"}")
  }

  private fun generateExpression(builder: StringBuilder, item: ParameterItem) {
    if (builder.length > MAX_EXPRESSION_LENGTH) {
      return
    }
    val value = item.value ?: item.name.takeIf { it == "..." }.orEmpty()
    val isList = value.startsWith("List")
    if (!isList) {
      builder.append(value)
    }
    val group = item as? ParameterGroupItem
    if (group != null) {
      builder.append(if (isList) "[" else "(")
      var separator = ""
      group.children.forEach { element ->
        builder.append(separator)
        generateExpression(builder, element)
        separator = ", "
      }
      builder.append(if (isList) "]" else ")")
    } else if (isList) {
      builder.append("[]")
    }
  }

  private fun createAction(
    icon: Icon,
    descriptionKey: String,
    action: () -> Unit,
    enabled: () -> Boolean = { true },
  ): AnAction {
    val description = LayoutInspectorBundle.message(descriptionKey)
    return object : DumbAwareAction(description, null, icon) {
      override fun actionPerformed(event: AnActionEvent) {
        action()
      }

      override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = enabled()
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }
  }
}
