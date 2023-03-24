/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.logcat.actions

import com.android.tools.idea.explainer.IssueExplainer
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.LOGCAT_MESSAGE_KEY
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import org.jetbrains.annotations.VisibleForTesting
import kotlin.math.min

internal class ExplainLogcatCrashAction :
  DumbAwareAction(IssueExplainer.get().getFixLabel("line")) {

  override fun update(e: AnActionEvent) {
    val message = getMessage(e)
    when (message.count()) {
      0 -> e.presentation.isVisible = false
      else -> {
        val label = if (message.any { isCrashFrame(it) }) "crash" else "log entry"
        e.presentation.isVisible = true
        e.presentation.text = IssueExplainer.get().getFixLabel(label)
      }
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val message = getMessage(e).joinToString("\n", postfix = "\n")
    IssueExplainer.get().explain(project, message, IssueExplainer.RequestKind.LOGCAT)

    ApplicationManager.getApplication().invokeLater {
      ToolWindowManagerEx.getInstanceEx(project).hideToolWindow("Logcat", false)
    }
  }

  /**
   * Try to compute the most relevant items from the selected lines. If there is no selection, and
   * the entry is part of a stacktrace, try to collect the whole stacktrace and summarize it.
   */
  private fun getMessage(e: AnActionEvent): List<String> {
    val editor = e.getData(CommonDataKeys.EDITOR) as EditorEx? ?: return emptyList()
    val selectionModel = editor.selectionModel
    return buildList {
      editor.document.processRangeMarkersOverlappingWith(
        selectionModel.selectionStart,
        selectionModel.selectionEnd
      ) {
        val message = it.getUserData(LOGCAT_MESSAGE_KEY)
        if (message != null && it.startOffset != selectionModel.selectionEnd) {
          add(message.extract())
        }
        true
      }
    }
  }
}

/** Number of stack frames to include in summary */
private const val TOP_FRAME_COUNT = 5

private fun isCrashFrame(line: String): Boolean {
  return line.contains("\tat ")
}

@VisibleForTesting
fun extractRelevant(message: String): String {
  if (isCrashFrame(message)) {
    val stack = pickStack(message)
    val frames = stack.lines()
    val top = frames.subList(0, min(frames.size, TOP_FRAME_COUNT))
    return top.joinToString("\n") { it.trim() }
  }

  return message.trim()
}

/**
 * If the logcat message represents a crash, it can have a long stack trace with multiple nested
 * "Caused by" exceptions.
 *
 * We want to pick the best one.
 *
 * Rethrows makes this a little tricky. If an app is rethrowing an exception, it's the caused-by
 * exception that's interesting. But we don't necessarily just want to go the very innermost
 * caused by.
 *
 * This will find the *first* exception where the first (innermost) stack frame appears to be in
 * the system.
 */
private fun pickStack(message: String): String {
  if (!isCrashFrame(message)) {
    return message
  }
  val chains = message.split("Caused by: ")
  // Pick the first stack caused-by where the first line is from the framework
  for (trace in chains) {
    val stack = trace.indexOf("\tat ")
    if (stack != -1) {
      val start = stack + 4
      if (
        trace.startsWith("java.", start) ||
        trace.startsWith("android.", start) ||
        trace.startsWith("org.apache.", start) ||
        trace.startsWith("org.json.", start) ||
        trace.startsWith("com.google.", start) ||
        trace.startsWith("com.android.internal", start)
      ) {
        return trace
      }
    }
  }
  return message
}

/**
 * Given a logcat message, extracts the most relevant information as a string. This will skip the
 * header, and for crashes, will focus on the root cause of the exception in order to make it
 * shorter (and will skip various stack traces too.)
 */
private fun LogcatMessage.extract(): String {
  return extractRelevant(message) + " with tag " + header.tag
}
