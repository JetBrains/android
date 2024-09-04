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
package com.android.tools.rendering

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.utils.SdkUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import java.io.File
import java.net.MalformedURLException

interface HtmlLinkManager {
  /** Handles a click in the [HtmlLinkManager]. */
  fun interface Action {
    /**
     * Called when the action is clicked. If available, the [module] will be passed as a parameter.
     */
    fun actionPerformed(module: Module?)
  }

  fun showNotification(content: String) = Unit

  fun handleUrl(
    url: String,
    module: Module,
    file: PsiFile,
    hasRenderResult: Boolean,
    surface: RefreshableSurface,
  )

  fun createBuildModuleUrl(): String = URL_BUILD_FOR_RENDERING

  fun createBuildProjectUrl(): String = URL_BUILD

  fun createSyncProjectUrl(): String = URL_SYNC

  fun createEditClassPathUrl(): String = URL_EDIT_CLASSPATH

  fun createOpenClassUrl(className: String): String = "$URL_OPEN_CLASS$className"

  fun createCommandLink(command: CommandLink): String

  fun createActionLink(action: Action): String

  fun createShowTagUrl(tag: String): String = "$URL_SHOW_TAG$tag"

  fun createNewClassUrl(className: String): String = "$URL_CREATE_CLASS$className"

  fun createOpenStackUrl(
    className: String,
    methodName: String,
    fileName: String,
    lineNumber: Int,
  ): String = "$URL_OPEN$className#$methodName;$fileName:$lineNumber"

  fun createReplaceTagsUrl(from: String, to: String): String = "$URL_REPLACE_TAGS$from/$to"

  fun createEditAttributeUrl(attribute: String, value: String): String =
    "$URL_EDIT_ATTRIBUTE$attribute/$value"

  fun createDisableSandboxUrl(): String = URL_DISABLE_SANDBOX

  fun createRefreshRenderUrl(): String = URL_REFRESH_RENDER

  fun createClearCacheUrl(): String = URL_CLEAR_CACHE_AND_NOTIFY

  fun createAddDependencyUrl(artifactId: GoogleMavenArtifactId): String =
    "$URL_ADD_DEPENDENCY$artifactId"

  fun createAddDebugDependencyUrl(artifactId: GoogleMavenArtifactId): String =
    "$URL_ADD_DEBUG_DEPENDENCY$artifactId"

  fun createReplaceAttributeValueUrl(
    attribute: String,
    oldValue: String,
    newValue: String,
  ): String = "$URL_REPLACE_ATTRIBUTE_VALUE$attribute/$oldValue/$newValue"

  fun createIgnoreFragmentsUrl(): String = URL_ACTION_IGNORE_FRAGMENTS

  fun createAssignFragmentUrl(id: String?): String = "$URL_ASSIGN_FRAGMENT_URL${(id ?: "")}"

  fun createPickLayoutUrl(activityName: String): String = "$URL_ASSIGN_LAYOUT_URL$activityName"

  fun createAssignLayoutUrl(activityName: String, layout: String): String =
    "$URL_ASSIGN_LAYOUT_URL$activityName:$layout"

  companion object {
    /**
     * Creates a file url for the given file and line position
     *
     * @param file the file
     * @param line the line, or -1 if not known
     * @param column the column, or 0 if not known
     * @return a URL which points to a given position in a file
     */
    @JvmStatic
    fun createFilePositionUrl(file: File, line: Int, column: Int): String? {
      return try {
        val fileUrl = SdkUtils.fileToUrlString(file)
        if (line != -1) {
          return if (column > 0) {
            "$fileUrl:$line:$column"
          } else {
            "$fileUrl:$line"
          }
        }
        fileUrl
      } catch (e: MalformedURLException) {
        // Ignore
        Logger.getInstance(HtmlLinkManager::class.java).error(e)
        null
      }
    }

    @JvmField
    val NOOP_LINK_MANAGER =
      object : HtmlLinkManager {
        override fun handleUrl(
          url: String,
          module: Module,
          file: PsiFile,
          hasRenderResult: Boolean,
          surface: RefreshableSurface,
        ) {}

        override fun createCommandLink(command: CommandLink): String = ""

        override fun createActionLink(action: Action): String = ""
      }

    @JvmField
    val NOOP_SURFACE =
      object : RefreshableSurface {
        override fun handleRefreshRenderUrl() {}

        override fun requestRender() {}
      }
  }

  interface RefreshableSurface {
    fun handleRefreshRenderUrl()

    fun requestRender()
  }

  interface CommandLink : Runnable {
    fun executeCommand()
  }
}

const val URL_BUILD = "action:build"
const val URL_BUILD_FOR_RENDERING = "action:buildForRendering"
const val URL_SYNC = "action:sync"
const val URL_EDIT_CLASSPATH = "action:classpath"
const val URL_OPEN_CLASS = "openClass:"
const val URL_REPLACE_TAGS = "replaceTags:"
const val URL_SHOW_TAG = "showTag:"
const val URL_CREATE_CLASS = "createClass:"
const val URL_OPEN = "open:"
const val URL_EDIT_ATTRIBUTE = "editAttribute:"
const val URL_DISABLE_SANDBOX = "disableSandbox:"
const val URL_REFRESH_RENDER = "refreshRender"
const val URL_CLEAR_CACHE_AND_NOTIFY = "clearCacheAndNotify"
const val URL_ADD_DEPENDENCY = "addDependency:"
const val URL_ADD_DEBUG_DEPENDENCY = "addDebugDependency:"
const val URL_ACTION_IGNORE_FRAGMENTS = "action:ignoreFragment"
const val URL_ASSIGN_FRAGMENT_URL = "assignFragmentUrl:"
const val URL_ASSIGN_LAYOUT_URL = "assignLayoutUrl:"
const val URL_REPLACE_ATTRIBUTE_VALUE = "replaceAttributeValue:"
