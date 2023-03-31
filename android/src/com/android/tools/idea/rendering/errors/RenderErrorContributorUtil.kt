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

import com.android.tools.idea.rendering.HtmlBuilderHelper
import com.android.tools.idea.rendering.HtmlLinkManager
import com.android.tools.idea.rendering.ShowExceptionFix
import com.android.utils.HtmlBuilder
import com.intellij.openapi.project.Project

/**
 * Adds a build call action to the given {@link HtmlBuilder}.
 */
fun HtmlBuilder.addBuildAction(linkManager: HtmlLinkManager): HtmlBuilder {
  newlineIfNecessary()
    .newline()
    .addIcon(HtmlBuilderHelper.getRefreshIconPath())
    .addLink(null, "Build", " the project.",
             linkManager.createBuildProjectUrl()).newline()

  return this
}

/**
 * Adds "Show Exception" call action.
 */
fun HtmlBuilder.addShowException(linkManager: HtmlLinkManager, project: Project?, throwable: Throwable?): HtmlBuilder {
  throwable?.let {
    newline()
    addLink("Show Exception", linkManager.createRunnableLink(ShowExceptionFix(project, throwable)))
  }
  return this
}