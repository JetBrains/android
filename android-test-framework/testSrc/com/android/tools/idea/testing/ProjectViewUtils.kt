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
package com.android.tools.idea.testing

import com.android.tools.idea.navigator.AndroidProjectViewPane
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.GroupByTypeComparator
import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.DeferredIcon
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RowIcon
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
    return replace(androidSdkAbsolutePath, "<ANDROID_SDK>", ignoreCase = false)
      .replace(androidSdkUserRootedPath, "<ANDROID_SDK>", ignoreCase = false)
      .replace(userHomePath, "<HOME>", ignoreCase = false)
      .replace(x64platformPattern, "<x64_PLATFORM>")
  }

  fun Icon.getIconText(): Icon? {
    var icon: Icon? = this
    do {
      val previous = icon
      icon = if (icon is DeferredIcon) icon.evaluate() else icon
      icon = if (icon is RowIcon && icon.allIcons.size == 1) icon.getIcon(0) else icon
      icon = if (icon is LayeredIcon && icon.allLayers.size == 1) icon.getIcon(0) else icon
    }
    while (previous != icon)
    return icon
  }

  fun PresentationData.toTestText(): String {
    val icon = getIcon(false)?.getIconText()
    val iconText =
      (icon as? IconLoader.CachedImageIcon)?.originalPath
      ?: (icon as? ImageIconUIResource)?.let { it.description ?: "ImageIconUIResource(?)" }
      ?: icon?.let { "$it (${it.javaClass.simpleName})" }
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
