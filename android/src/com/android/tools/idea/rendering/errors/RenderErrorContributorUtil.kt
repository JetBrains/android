/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.rendering.errors

import com.android.tools.idea.actions.SendFeedbackAction
import com.android.tools.idea.rendering.ShowExceptionFix
import com.android.tools.idea.rendering.errors.ui.MessageTip
import com.android.tools.rendering.HtmlLinkManager
import com.android.utils.HtmlBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project

/**
 * Create a [MessageTip] to build the project with a link to its action
 *
 * @param linkManager the manager with the action to take
 * @param textBefore the text to append before the message
 */
fun createBuildTheProjectMessage(linkManager: HtmlLinkManager, textBefore: String? = null): MessageTip {
  val builder = HtmlBuilder()
  textBefore?.let {
    builder.add(it).newline()
  }
  return MessageTip(
    AllIcons.General.Information,
    builder.addLink(
        "Tip: ",
        "Build",
        " the project.",
        linkManager.createBuildProjectUrl()
      )
  )
}

/**
 * Create a [MessageTip] to build the module with a link to its action
 *
 * @param linkManager the manager with the action to take
 */
fun createBuildTheModuleMessage(linkManager: HtmlLinkManager): MessageTip {
  return MessageTip(
    AllIcons.General.Information,
    HtmlBuilder()
      .addLink("Tip: ", "Build", " the module.", linkManager.createBuildModuleUrl())
  )
}

/**
 * Create a [MessageTip] to build and refresh the layout with a link to its action
 *
 * @param linkManager the manager with the action to take
 */
fun createBuildAndRefreshLayoutMessage(linkManager: HtmlLinkManager): MessageTip {
  return MessageTip(
    AllIcons.General.Information,
    HtmlBuilder()
      .addLink(
        "Tip: ",
        "Build & Refresh",
        " the layout.",
        linkManager.createRefreshRenderUrl()
      )
  )
}

/**
 * Adds "Show Exception" call action to the html builder.
 *
 * @param linkManager the manager with the action to take
 * @param project the project on which you want to report the bug
 * @param throwable the exception on which you want to show the action
 */
fun HtmlBuilder.addExceptionMessage(linkManager: HtmlLinkManager, project: Project?, throwable: Throwable?): HtmlBuilder {
  throwable?.let {
    addLink(
      "Show Exception",
      linkManager.createRunnableLink(ShowExceptionFix(project, throwable))
    )
  }
  return this
}

/**
 * Create a [MessageTip] with a link to the bug report page
 *
 * @param project the project on which you want to report the bug
 * @param linkManager the manager with the action to take
*/
fun createAddReportBugMessage(project: Project, linkManager: HtmlLinkManager, textBefore: String? = null): MessageTip {
  val builder = HtmlBuilder()
  textBefore?.let {
    builder.add(it).newline()
  }
  return MessageTip(
    AllIcons.General.Information,
    builder.addLink(
      null,
      "Report Bug",
      ".",
      linkManager.createRunnableLink { SendFeedbackAction.submit(project) })
  )
}