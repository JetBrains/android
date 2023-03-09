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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE") // TODO: remove usage of sun.swing.ImageIconUIResource.
package com.android.tools.idea.testing

import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.navigator.AndroidProjectViewPane
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.GroupByTypeComparator
import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.ui.DeferredIconImpl
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.RowIcon
import com.intellij.ui.icons.CachedImageIcon
import sun.swing.ImageIconUIResource
import java.io.File
import javax.swing.Icon

fun <T : Any> Project.dumpAndroidProjectView(
  projectViewSettings: ProjectViewSettings = ProjectViewSettings(),
  initialState: T,
  filter: (element: AbstractTreeNode<*>, state: T) -> T?
): String {

  val androidSdk: File = IdeSdks.getInstance().androidSdkPath!!

  fun String.replaceVariableParts(): String {
    val userHomePath = System.getProperty("user.home")
    val androidSdkAbsolutePath = androidSdk.absolutePath
    val androidSdkUserRootedPath = androidSdk.absolutePath.replace(userHomePath, "~")
    // Some tools (for example ndk-build) follows symlinks created by bazel and write down canonical path of stuff inside the SDK.
    val androidSdkCanonicalPath = androidSdk.resolve("platform-tools/adb").canonicalFile.parentFile.parentFile.path
    return replace(androidSdkAbsolutePath, "<ANDROID_SDK>", ignoreCase = false)
      .replace(androidSdkUserRootedPath, "<ANDROID_SDK>", ignoreCase = false)
      .replace(androidSdkCanonicalPath, "<ANDROID_SDK>", ignoreCase = false)
      .replace(userHomePath, "<HOME>", ignoreCase = false)
      .replace(x64platformPattern, "<x64_PLATFORM>")
  }

  fun PresentationData.toTestText(): String {
    val icon = getIcon(false)

    fun Icon.toText(): String? = when {
      // After commit 2c486969f4 evaluate will throw an assertion failed exception if run under read action in NativeIconProvider
      // Most over uses require a read action, only the implementation class DeferredIconImpl knows this
      this is DeferredIconImpl<*> -> (if (!isNeedReadAction) executeOnPooledThread { evaluate() }.get() else evaluate()).toText()
      this is RetrievableIcon -> retrieveIcon().toText()
      this is RowIcon && allIcons.size == 1 -> getIcon(0)?.toText()
      this is CachedImageIcon -> originalPath ?: Regex("path=([^,]+)").find(toString())?.groups?.get(1)?.value ?: ""
      this is ImageIconUIResource -> description ?: "ImageIconUIResource(?)"
      this is LayeredIcon && allLayers.size == 1 ->  getIcon(0)?.toText()
      this is LayeredIcon -> "[${allLayers.joinToString(separator = ", ") { it.toText().orEmpty() }}]"
      this.javaClass.simpleName == "DummyIcon" -> this.toString()
      else -> "$this (${javaClass.simpleName})"
    }

    val iconText = icon?.toText()
    val nodeText =
      if (coloredText.isEmpty()) presentableText
      else coloredText.joinToString(separator = "") { it.text }

    return buildString {
      append(nodeText)
      if (iconText != null) append(" (icon: $iconText)")
    }
      .replaceVariableParts()
  }

  fun createAndDumpProjectView(): String {
    val viewPane = AndroidProjectViewPane(this)
    // We need to create a component to initialize the view pane.
    viewPane.createComponent()
    val treeStructure: AbstractTreeStructure? = viewPane.treeStructure
    val rootElement = treeStructure?.rootElement ?: return ""
    // In production sorting happens when the tree builder asynchronously populates the UI. It uses the following comparator, by default,
    // which, unfortunately, is not accessible via a public API.
    val comparator = GroupByTypeComparator(null,
                                           AndroidProjectViewPane.ID)

    return buildString {

      fun dump(element: AbstractTreeNode<*>, prefix: String = "", state: T) {
        val newState = filter(element, state) ?: return

        appendln("$prefix${element.presentation.toTestText()}")
        treeStructure
          .getChildElements(element)
          .map { it as AbstractTreeNode<*> }
          .apply { forEach { it.update() } }
          .sortedWith(comparator)
          .forEach { dump(it, "    $prefix", newState) }
      }

      dump(rootElement as AbstractTreeNode<*>, state = initialState)
    }
      // Trim the trailing line end since snapshots are loaded without it.
      .trimEnd()
  }

  fun applySettings(settings: ProjectViewSettings) {
    ProjectView.getInstance(this).apply {
      setHideEmptyPackages(AndroidProjectViewPane.ID, settings.hideEmptyPackages)
      (this as ProjectViewImpl).setFlattenPackages(
        AndroidProjectViewPane.ID, settings.flattenPackages)
    }
  }

  fun getCurrentSettings(): ProjectViewSettings = ProjectView.getInstance(
    this).let { view ->
    ProjectViewSettings(
      hideEmptyPackages = view.isHideEmptyMiddlePackages(AndroidProjectViewPane.ID),
      flattenPackages = view.isFlattenPackages(AndroidProjectViewPane.ID)
    )
  }

  val oldSettings = getCurrentSettings()
  applySettings(projectViewSettings)
  return try {
    createAndDumpProjectView()
  }
  finally {
    applySettings(oldSettings)
  }
}

private val x64platformPattern = Regex("darwin-x86_64|linux-x86_64")
