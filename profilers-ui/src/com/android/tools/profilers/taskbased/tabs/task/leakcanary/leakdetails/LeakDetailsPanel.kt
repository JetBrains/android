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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary.leakdetails
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.tools.leakcanarylib.data.Leak
import com.android.tools.leakcanarylib.data.Node
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_CLOSE
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_GO_TO_DECLARATION
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_LEAK_DETAIL_EMPTY_INITIAL_MESSAGE
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_NO_DECLARATION_FOUND
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_NO_DECLARATION_FOUND_TOOLTIP
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_NO_LEAK_FOUND_MESSAGE
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_OPEN
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.START_TASK_SELECTION_ERROR_ICON_DESC
import com.android.tools.profilers.taskbased.common.text.EllipsisText
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskbars.notifications.NotificationWithTooltip
import icons.StudioIconsCompose
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.util.concurrent.CompletableFuture

/**
 * Composable function that renders the leak details panel.
 * It displays either a placeholder message when no leak is selected or the leak trace details with a scrollbar when a leak is available.
 *
 * @param selectedLeak The currently selected leak, if any.
 * @param gotoDeclaration GotoDeclaration action for the given node.
 */
@Composable
fun LeakDetailsPanel(selectedLeak: Leak?,
                     gotoDeclaration: (Node) -> Unit,
                     isRecording: Boolean,
                     isDeclarationAvailableAsync: (Node) -> CompletableFuture<Boolean>) {
  val emptyLeakMessage = if (isRecording) LEAKCANARY_LEAK_DETAIL_EMPTY_INITIAL_MESSAGE else LEAKCANARY_NO_LEAK_FOUND_MESSAGE
  if (selectedLeak == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      EllipsisText(text = emptyLeakMessage, maxLines = 3)
    }
  }
  else {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(10.dp)) {
        // If displayedLeakTrace is empty, use empty list for the leak nodes.
        val traceNodes = if (selectedLeak.displayedLeakTrace.isNotEmpty()) selectedLeak.displayedLeakTrace[0].nodes else listOf()
        var openStates by remember(selectedLeak) { mutableStateOf(List(traceNodes.size) { false }) }
        traceNodes.forEachIndexed { index, currNode ->
          LeakTraceNodeView(
            node = currNode,
            previousNode = if (index > 0) traceNodes[index - 1] else null,
            gotoDeclaration = gotoDeclaration,
            nextNode = if (index + 1 < traceNodes.size) traceNodes[index + 1] else null,
            isOpen = openStates[index],
            onClickNode = {
              openStates = openStates.toMutableList().apply { this[index] = !this[index] }
            },
            isDeclarationAvailableAsync = isDeclarationAvailableAsync
          )
        }
      }
      VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
      )
    }
  }
}

/**
 * Composable function that renders a single node in the leak trace. It provides an expandable/collapsible view to show node details and
 * actions.
 *
 * @param node The current node to display.
 * @param previousNode The previous node in the trace, if any.
 * @param gotoDeclaration GotoDeclaration action for the given node.
 * @param nextNode The next node in the trace, if any.
 */
@Composable
fun LeakTraceNodeView(node: Node,
                      previousNode: Node?,
                      gotoDeclaration: (Node) -> Unit,
                      nextNode: Node?,
                      isOpen: Boolean = false,
                      onClickNode: () -> Unit,
                      isDeclarationAvailableAsync: (Node) -> CompletableFuture<Boolean>) {
  val rowClickableModifier = Modifier
    .clickable(onClick = { onClickNode() }, indication = null, interactionSource = remember { MutableInteractionSource() })
    .pointerHoverIcon(PointerIcon.Hand)
  var isDeclarationFound by remember(node) { mutableStateOf<Boolean>(true) }

  LaunchedEffect(node) {
    isDeclarationAvailableAsync(node).thenAccept { isAvailable ->
      isDeclarationFound = isAvailable
    }
  }
  Column(modifier = Modifier.height(IntrinsicSize.Min)) {
    Row {
      Row(modifier = rowClickableModifier.testTag(node.className)) {
        if (isOpen) {
          Icon(AllIconsKeys.General.ArrowDown, LEAKCANARY_OPEN)
        }
        else {
          Icon(AllIconsKeys.General.ArrowRight, LEAKCANARY_CLOSE)
        }
        Spacer(Modifier.padding(2.5.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          LeakIcon(node.leakingStatus)
          if (nextNode != null) {
            VerticalLeakStatusLine(nextNode.leakingStatus)
          }
        }
      }
      Spacer(Modifier.padding(20.dp))
      Column(horizontalAlignment = Alignment.Start) {
        Row(modifier = rowClickableModifier.padding(top = 2.dp, end = 5.dp)) {
          Text(
            text = buildAnnotatedString {
              appendClassAndStatusText(previousNode, node)
            },
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Visible
          )
          if (isOpen) {
            if (isDeclarationFound) {
              Link(text = LEAKCANARY_GO_TO_DECLARATION, onClick = { gotoDeclaration(node) })
            }
            else {
              NotificationWithTooltip(notificationText = LEAKCANARY_NO_DECLARATION_FOUND,
                                      tooltipMainText = LEAKCANARY_NO_DECLARATION_FOUND_TOOLTIP,
                                      tooltipSubText = null,
                                      iconKey = StudioIconsCompose.AppQualityInsights.NonFatal,
                                      iconDescription = START_TASK_SELECTION_ERROR_ICON_DESC)
            }
          }
        }
        if (isOpen) {
          LeakNodeDetails(node)
        }
      }
    }
  }
}
/**
 * Helper function to get the display name of the referring object.
 * It extracts and formats the relevant information from the previous node's referencing field.
 *
 * @param previousNode The previous node in the trace, if any.
 * @param node The current node.
 * @return The formatted display name of the referring object.
 */
private fun getReferringDisplayName(previousNode: Node?, node: Node): String {
  val referringField = previousNode?.referencingField ?: return ""
  val cleanedField = referringField.toString()
    .replace("│", "")
    .replace("↓", "")
    .trim()
    .split("\n")[0]
    .trim()
  val className = node.className.split(".").last()
  return "($cleanedField:$className)"
}
/**
 * Extension function on AnnotatedString.Builder to append class name and status text with formatting.
 *
 * @param previousNode The previous node in the trace, if any.
 * @param node The current node.
 */
private fun AnnotatedString.Builder.appendClassAndStatusText(previousNode: Node?, node: Node) {
  val referringDisplayName = getReferringDisplayName(previousNode, node)
  val referenceDisplaySplitIndex = referringDisplayName.lastIndexOf(":")
  val firstSection = if (referenceDisplaySplitIndex > 0)
    referringDisplayName.substring(0, referenceDisplaySplitIndex) else referringDisplayName
  val lastSection = if (referenceDisplaySplitIndex > 0) "$"+
                                                        referringDisplayName.substring(referenceDisplaySplitIndex+1,
                                                                                       referringDisplayName.length) else ""
  append(AnnotatedString(text = node.className))
  append(AnnotatedString(" ${node.nodeType} \n"))
  append(AnnotatedString(firstSection))
  append(AnnotatedString(lastSection, spanStyle = SpanStyle(color = getLeakStatusColor(node.leakingStatus))))
}