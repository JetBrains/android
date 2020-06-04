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
package com.android.tools.idea.testing

import com.android.testutils.TestUtils
import com.android.tools.idea.navigator.AndroidProjectViewPane
import com.android.tools.idea.sdk.IdeSdks
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.GroupByTypeComparator
import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.io.FileUtil.sanitizeFileName
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.DeferredIcon
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RowIcon
import org.jetbrains.android.AndroidTestBase
import sun.swing.ImageIconUIResource
import java.io.File
import javax.swing.Icon

/**
 * See implementing classes for usage examples.
 */
interface SnapshotComparisonTest {
  /**
   * The name of the property which should be set to activate "update snapshots" test execution mode.
   */
  val updateSnapshotsJvmProperty: String get() = "UPDATE_TEST_SNAPSHOTS"

  /**
   * A testData subdirectory name where to look for snapshots.
   */
  val snapshotDirectoryWorkspaceRelativePath: String

  /**
   * The list of file name suffixes applicable to the currently running test.
   */
  val snapshotSuffixes: List<String> get() = listOf("")

  /**
   * Assumed to be matched by [UsefulTestCase.getName].
   */
  fun getName(): String
}

fun SnapshotComparisonTest.assertIsEqualToSnapshot(text: String, snapshotTestSuffix: String = "") {
  val (_, expectedText) = getAndMaybeUpdateSnapshot(snapshotTestSuffix, text)
  assertThat(text).isEqualTo(expectedText)
}

fun SnapshotComparisonTest.assertAreEqualToSnapshots(vararg checks: Pair<String, String>) {
  val (actual, expected) =
    checks
      .map { (actual, suffix) ->
        val (fullName, expected) = getAndMaybeUpdateSnapshot(suffix, actual)
        val header = "\n####################### ${fullName} #######################\n"
        header + actual to header + expected
      }
      .unzip()
      .let {
        it.first.joinToString(separator = "\n") to it.second.joinToString(separator = "\n")
      }

  assertThat(actual).isEqualTo(expected)
}

private fun SnapshotComparisonTest.getAndMaybeUpdateSnapshot(
  snapshotTestSuffix: String,
  text: String
): Pair<String, String> {
  val fullSnapshotName = sanitizeFileName(UsefulTestCase.getTestName(getName(), true)) + snapshotTestSuffix
  val expectedText = getExpectedTextFor(fullSnapshotName)

  if (System.getProperty(updateSnapshotsJvmProperty) != null) {
    updateSnapshotFile(fullSnapshotName, text)
  }
  return fullSnapshotName to expectedText
}

private fun SnapshotComparisonTest.getCandidateSnapshotFiles(project: String): List<File> =
  snapshotSuffixes
    .map { File("${TestUtils.getWorkspaceFile(snapshotDirectoryWorkspaceRelativePath)}/${project.substringAfter("projects/")}$it.txt") }

private fun SnapshotComparisonTest.updateSnapshotFile(snapshotName: String, text: String) {
  getCandidateSnapshotFiles(snapshotName)
    .let { candidates -> candidates.firstOrNull { it.exists() } ?: candidates.last() }
    .run {
      println("Writing to: ${this.absolutePath}")
      writeText(text)
    }
}

private fun SnapshotComparisonTest.getExpectedTextFor(project: String): String =
  getCandidateSnapshotFiles(project)
    .let { candidateFiles ->
      candidateFiles
        .firstOrNull { it.exists() }
        ?.let {
          println("Comparing with: ${it.relativeTo(File(AndroidTestBase.getTestDataPath()))}")
          it.readText().trimIndent()
        }
      ?: candidateFiles
        .joinToString(separator = "\n", prefix = "No snapshot files found. Candidates considered:\n\n") {
          it.relativeTo(File(AndroidTestBase.getTestDataPath())).toString()
        }
    }

data class ProjectViewSettings(
  val hideEmptyPackages: Boolean = true,
  val flattenPackages: Boolean = false
)

fun Project.dumpAndroidProjectView(): String = dumpAndroidProjectView(initialState = Unit) { _, _ -> Unit }

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
    val comparator = GroupByTypeComparator(null, AndroidProjectViewPane.ID)

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
      (this as ProjectViewImpl).setFlattenPackages(AndroidProjectViewPane.ID, settings.flattenPackages)
    }
  }

  fun getCurrentSettings(): ProjectViewSettings = ProjectView.getInstance(this).let { view ->
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

