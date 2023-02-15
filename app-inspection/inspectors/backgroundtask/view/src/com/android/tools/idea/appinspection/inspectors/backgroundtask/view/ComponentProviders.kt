/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.view

import androidx.work.inspection.WorkManagerInspectorProtocol.CallStack
import androidx.work.inspection.WorkManagerInspectorProtocol.Constraints
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.JobInfo
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorTracker
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.BackgroundTaskEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WorkEntry
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Interface for converting some model input into a UI component. */
interface ComponentProvider<T> {
  fun convert(data: T): JComponent
}

/**
 * A basic provider which converts any data to its a label containing the data's `toString`
 * representation, useful as a default provider for simple cases.
 */
class ToStringProvider<T> : ComponentProvider<T> {
  override fun convert(data: T) = JBLabel(data.toString())
}

/** Provides a component that represents a class name which can be navigated to. */
class ClassNameProvider(
  private val ideServices: AppInspectionIdeServices,
  private val scope: CoroutineScope,
  private val tracker: BackgroundTaskInspectorTracker
) : ComponentProvider<String> {
  override fun convert(fqcn: String): JComponent {
    return ActionLink(fqcn) {
      scope.launch {
        ideServices.navigateTo(AppInspectionIdeServices.CodeLocation.forClass(fqcn))
        tracker.trackJumpedToSource()
      }
    }
  }
}

/** Provides a component that represents a timestamp in a human readable format. */
object TimeProvider : ComponentProvider<Long> {
  override fun convert(timestamp: Long) = JBLabel(timestamp.toFormattedTimeString())
}

/** Provides a component that represents a state text with icon. */
object StateProvider : ComponentProvider<BackgroundTaskEntry> {
  override fun convert(entry: BackgroundTaskEntry): JComponent {
    return JBLabel(entry.status.capitalizedName()).apply { icon = entry.icon() }
  }
}

/**
 * Provides a component that displays the location a worker was enqueued at, with the ability to
 * click on that location to navigate into the code.
 */
class EnqueuedAtProvider(
  private val ideServices: AppInspectionIdeServices,
  private val scope: CoroutineScope,
  private val tracker: BackgroundTaskInspectorTracker
) : ComponentProvider<CallStack> {
  override fun convert(stack: CallStack): JComponent {
    return if (stack.framesCount == 0) {
      JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply {
        add(JBLabel("Unavailable"))
        add(Box.createHorizontalStrut(5))
        val icon = JLabel(StudioIcons.Common.HELP)
        HelpTooltip()
          .setDescription(
            "Enqueue location is only known for workers started after opening the inspector."
          )
          .installOn(icon)
        add(icon)
      }
    } else {
      val frame0 = stack.getFrames(0)
      ActionLink("${frame0.fileName} (${frame0.lineNumber})") {
        scope.launch {
          ideServices.navigateTo(
            AppInspectionIdeServices.CodeLocation.forFile(frame0.fileName, frame0.lineNumber)
          )
          tracker.trackJumpedToSource()
        }
      }
    }
  }
}

/** Provides a component that displays a list of string values. */
object StringListProvider : ComponentProvider<List<String>> {
  override fun convert(strings: List<String>): JComponent {
    return if (strings.isNotEmpty()) {
      JPanel(VerticalFlowLayout(0, 0)).apply { strings.forEach { str -> add(JBLabel("\"$str\"")) } }
    } else {
      createEmptyContentLabel()
    }
  }
}

/**
 * Provides a component that displays an BackgroundTaskEntry with navigation support.
 *
 * @param selectEntry A callback which can be triggered to select some target entry.
 */
class EntryIdProvider(private val selectEntry: (BackgroundTaskEntry) -> Unit) :
  ComponentProvider<BackgroundTaskEntry> {
  override fun convert(data: BackgroundTaskEntry): JComponent {
    return ActionLink(data.className) { selectEntry(data) }.apply { icon = data.icon() }
  }
}

/**
 * Provides a component that displays a list of workers, each which, when clicked, select that
 * worker in a source table.
 *
 * @param selectWork A callback which can be triggered to select some target worker.
 */
class IdListProvider(
  private val client: BackgroundTaskInspectorClient,
  private val currentWork: WorkInfo,
  private val selectWork: (WorkEntry) -> Unit
) : ComponentProvider<List<String>> {
  override fun convert(ids: List<String>): JComponent {
    val currId = currentWork.id
    return if (ids.isNotEmpty()) {
      JPanel(VerticalFlowLayout(0, 0)).apply {
        ids.forEach { id ->
          val entry = client.getEntry(id)
          if (entry != null) {
            val work = (entry as WorkEntry).getWorkInfo()
            val mixedLabel = JPanel(HorizontalLayout(2))
            val actionLink =
              ActionLink(id) { selectWork(entry) }.apply {
                icon = entry.icon()
                if (work.tagsCount > 0) {
                  toolTipText =
                    "<html><b>Tags</b><br>${work.tagsList.joinToString("<br>") { "\"$it\"" }}</html>"
                }
              }
            mixedLabel.add(actionLink)
            if (id == currId) {
              mixedLabel.add(JLabel("(Current)"))
            }
            add(mixedLabel)
          } else {
            add(JBLabel(id))
          }
        }
      }
    } else {
      createEmptyContentLabel()
    }
  }
}

/** Provides a component that displays a list of constraint descriptions for some target worker. */
object WorkConstraintProvider : ComponentProvider<Constraints> {
  override fun convert(constraint: Constraints): JComponent {
    val constraintDescs = mutableListOf<String>()
    when (constraint.requiredNetworkType) {
      Constraints.NetworkType.CONNECTED -> constraintDescs.add("Network must be connected")
      Constraints.NetworkType.UNMETERED -> constraintDescs.add("Network must be unmetered")
      Constraints.NetworkType.NOT_ROAMING -> constraintDescs.add("Network must not be roaming")
      Constraints.NetworkType.METERED -> constraintDescs.add("Network must be metered")
      Constraints.NetworkType.UNRECOGNIZED -> constraintDescs.add("Network must be recognized")
      else -> {}
    }

    if (constraint.requiresCharging) {
      constraintDescs.add("Requires charging")
    }
    if (constraint.requiresBatteryNotLow) {
      constraintDescs.add("Requires battery not low")
    }
    if (constraint.requiresDeviceIdle) {
      constraintDescs.add("Requires idle device")
    }
    if (constraint.requiresStorageNotLow) {
      constraintDescs.add("Requires storage not low")
    }

    return if (constraintDescs.isNotEmpty()) {
      JPanel(VerticalFlowLayout(0, 0)).apply {
        constraintDescs.forEach { desc -> add(JBLabel(desc)) }
      }
    } else {
      createEmptyContentLabel()
    }
  }
}

/** Provides a component that displays a list of constraint descriptions for some target worker. */
object JobConstraintProvider : ComponentProvider<JobInfo> {
  override fun convert(job: JobInfo): JComponent {
    val constraintDescs = mutableListOf<String>()
    when (job.networkType) {
      JobInfo.NetworkType.NETWORK_TYPE_METERED -> constraintDescs.add("Network must be metered")
      JobInfo.NetworkType.NETWORK_TYPE_UNMETERED -> constraintDescs.add("Network must be unmetered")
      JobInfo.NetworkType.NETWORK_TYPE_NOT_ROAMING ->
        constraintDescs.add("Network must not be roaming")
      else -> {}
    }

    if (job.isRequireCharging) {
      constraintDescs.add("Requires charging")
    }
    if (job.isRequireBatteryNotLow) {
      constraintDescs.add("Requires battery not low")
    }
    if (job.isRequireDeviceIdle) {
      constraintDescs.add("Requires idle device")
    }
    if (job.isRequireStorageNotLow) {
      constraintDescs.add("Requires storage not low")
    }

    return if (constraintDescs.isNotEmpty()) {
      JPanel(VerticalFlowLayout(0, 0)).apply {
        constraintDescs.forEach { desc -> add(JBLabel(desc)) }
      }
    } else {
      createEmptyContentLabel()
    }
  }
}

/** Provides a component which displays all the key/value pairs in a worker's output data. */
object OutputDataProvider : ComponentProvider<WorkInfo> {
  override fun convert(workInfo: WorkInfo): JComponent {
    val data = workInfo.data
    return if (data.entriesList.isNotEmpty()) {
      val panel =
        JPanel(VerticalFlowLayout(0, 0)).apply {
          data.entriesList.forEach { pair ->
            val pairPanel = JPanel(HorizontalLayout(0))
            pairPanel.add(JLabel("${pair.key} = "))
            pairPanel.add(
              JLabel("\"${pair.value}\"").apply {
                foreground = BackgroundTaskInspectorColors.DATA_VALUE_TEXT_COLOR
              }
            )
            add(pairPanel)
          }
        }
      HideablePanel.Builder("Data", panel)
        .setPanelBorder(JBUI.Borders.empty())
        .setContentBorder(JBUI.Borders.empty(0, 20, 0, 0))
        .build()
    } else {
      val state = workInfo.state
      JBLabel().apply {
        if (state.isFinished()) {
          text = BackgroundTaskInspectorBundle.message("table.data.null")
          foreground = BackgroundTaskInspectorColors.DATA_TEXT_NULL_COLOR
        } else {
          text = BackgroundTaskInspectorBundle.message("table.data.awaiting")
          foreground = BackgroundTaskInspectorColors.DATA_TEXT_AWAITING_COLOR
        }
      }
    }
  }
}

private fun createEmptyContentLabel() =
  JBLabel(BackgroundTaskInspectorBundle.message("detail.content.none")).apply {
    foreground = BackgroundTaskInspectorColors.EMPTY_CONTENT_COLOR
  }
