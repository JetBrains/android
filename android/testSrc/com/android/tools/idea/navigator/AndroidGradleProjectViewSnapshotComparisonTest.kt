/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.navigator

import com.android.tools.idea.Projects
import com.android.tools.idea.navigator.nodes.ndk.includes.view.IncludesViewNode
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.impl.GroupByTypeComparator
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DeferredIcon
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RowIcon
import java.io.File
import javax.swing.Icon

class AndroidGradleProjectViewSnapshotComparisonTest : AndroidGradleTestCase(), SnapshotComparisonTest {
  override val snapshotDirectoryName = "projectViewSnapshots"

  fun testSimpleApplication() {
    val text = importSyncAndDumpProject(TestProjectPaths.SIMPLE_APPLICATION)
    assertIsEqualToSnapshot(text)
  }

  fun testNestedProjects() {
    val text = importSyncAndDumpProject(TestProjectPaths.PSD_SAMPLE)
    assertIsEqualToSnapshot(text)
  }

  fun testDependentNativeModules() {
    val text = importSyncAndDumpProject(TestProjectPaths.DEPENDENT_NATIVE_MODULES, initialState = false) { element, state ->
      // Drop any file nodes under IncludesViewNode node.
      when {
        element is IncludesViewNode -> true
        state && element is PsiFileNode -> null
        else -> state
      }
    }
    assertIsEqualToSnapshot(text)
  }

  fun testJpsWithQualifiedNames() {
    // Prepare project in a different directory (_jps) to avoid closing the currently opened project.
    val projectPath =
      prepareProjectCoreForImport(
        File(myFixture.testDataPath, toSystemDependentName(TestProjectPaths.JPS_WITH_QUALIFIED_NAMES)),
        File(toSystemDependentName(project.basePath + "_jps"))) { /* Do nothing. */ }

    val project = ProjectUtil.openProject(projectPath.absolutePath, null, false)!!
    val text = project.dumpAndroidProjectView()
    ProjectUtil.closeAndDispose(project)

    assertIsEqualToSnapshot(text)
  }

  private fun importSyncAndDumpProject(
    projectDir: String,
    patch: ((projectRootPath: File) -> Unit)? = null
  ): String =
    importSyncAndDumpProject(projectDir, patch, Unit, { _, _ -> Unit })

  private fun <T : Any> importSyncAndDumpProject(
    projectDir: String,
    patch: ((projectRootPath: File) -> Unit)? = null,
    initialState: T,
    filter: (element: AbstractTreeNode<*>, state: T) -> T?
  ): String {
    val projectRootPath = prepareProjectForImport(projectDir)
    patch?.invoke(projectRootPath)
    val project = this.project!!
    importProject(project.name, Projects.getBaseDirPath(project))

    return project.dumpAndroidProjectView(initialState, filter)
  }

  private fun Project.dumpAndroidProjectView(): String = dumpAndroidProjectView(Unit, { _, _ -> Unit })

  private fun <T : Any> Project.dumpAndroidProjectView(initialState: T, filter: (element: AbstractTreeNode<*>, state: T) -> T?): String {
    val viewPane = AndroidProjectViewPane(this)
    // We need to create a component to initialize the view pane.
    viewPane.createComponent()
    val treeStructure: AbstractTreeStructure? = viewPane.treeStructure
    val rootElement = treeStructure?.rootElement ?: return ""
    // In production sorting happens when the tree builder asynchronously populates the UI. It uses the following comparator, by default,
    // which, unfortunately, is not accessible via a public API.
    val comparator = GroupByTypeComparator(null, "android")

    return buildString {

      val androidSdk: File = IdeSdks.getInstance().androidSdkPath!!

      fun String.replaceVariableParts(): String {
        val userHomePath = System.getProperty("user.home")
        val androidSdkAbsolutePath = androidSdk.absolutePath
        val androidSdkUserRootedPath = androidSdk.absolutePath.replace(userHomePath, "~")
        return replace(androidSdkAbsolutePath, "<ANDROID_SDK>", ignoreCase = false)
          .replace(androidSdkUserRootedPath, "<ANDROID_SDK>", ignoreCase = false)
          .replace(userHomePath, "<HOME>", ignoreCase = false)
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
        val iconText = (icon as? IconLoader.CachedImageIcon)?.originalPath ?: icon?.let { "$it (${it.javaClass.simpleName})" }
        val nodeText =
          if (coloredText.isEmpty()) presentableText
          else coloredText.joinToString(separator = "") { it.text }

        return buildString {
          append(nodeText)
          if (iconText != null) append(" (icon: $iconText)")
        }
          .replaceVariableParts()
      }

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
}

