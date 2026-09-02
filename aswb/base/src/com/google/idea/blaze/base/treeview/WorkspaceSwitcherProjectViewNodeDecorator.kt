/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.treeview

import com.google.idea.blaze.base.workspace.WorkspaceSwitchManager
import com.google.idea.blaze.base.workspace.WorkspaceSwitchManager.getWorkspacePath
import com.google.idea.blaze.base.workspace.WorkspaceSwitchManager.getWorkspaceTarget
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Path
import org.jetbrains.annotations.VisibleForTesting

/**
 * Transforms virtual workspace switcher directory prefixes into '<project>' across Project and File tree views when
 * [WorkspaceSwitchManager.WORKSPACE_SWITCHER_ENABLED] is active.
 */
class WorkspaceSwitcherProjectViewNodeDecorator : ProjectViewNodeDecorator {

  override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
    val project = node.project ?: return
    if (project.isDisposed) return
    if (!WorkspaceSwitchManager.WORKSPACE_SWITCHER_ENABLED.getValue()) return
    if (project.getWorkspaceTarget() == null) return

    val workspacePath = project.getWorkspacePath()
    transformPresentation(project, workspacePath, node, data)
  }

  @VisibleForTesting
  internal fun transformPresentation(
    project: Project,
    workspacePath: Path,
    node: ProjectViewNode<*>,
    data: PresentationData,
  ) {
    val prefixes = getOrCreatePrefixes(project, workspacePath)

    fun replacePrefix(text: String): String {
      // Fast path: tree nodes are overwhelmingly simple file/folder names (e.g. 'MainActivity.kt', 'src').
      // Only paths (starting with '/' or '~') or the project location hash can match.
      if (text != project.locationHash && !text.startsWith('/') && !text.startsWith('~')) {
        return text
      }

      val normalized = FileUtil.toSystemIndependentName(text)
      return when {
        normalized == prefixes.homeRelativePrefix || normalized == prefixes.absolutePrefix -> "<project>"
        normalized.startsWith(prefixes.homeRelativePrefixWithSlash) ->
          "<project>/${normalized.substring(prefixes.homeRelativePrefixWithSlash.length)}"
        normalized.startsWith(prefixes.absolutePrefixWithSlash) ->
          "<project>/${normalized.substring(prefixes.absolutePrefixWithSlash.length)}"
        normalized == project.locationHash && isWorkspaceRoot(node, prefixes.absolutePrefix) -> "<project>"
        else -> text
      }
    }

    fun updatePresentableText() {
      val currentText = data.presentableText ?: return
      val newText = replacePrefix(currentText)
      if (newText != currentText) {
        data.presentableText = newText
      }
    }

    fun updateColoredText() {
      val fragments = data.coloredText
      if (fragments.isEmpty() || fragments.none { f -> f.text?.let { replacePrefix(it) != it } == true }) {
        return
      }
      val updatedFragments = fragments.map { fragment ->
        val originalText = fragment.text
        val substituted = originalText?.let { replacePrefix(it) }
        if (substituted != null && substituted != originalText) {
          PresentableNodeDescriptor.ColoredFragment(substituted, fragment.attributes)
        } else {
          fragment
        }
      }
      data.clearText()
      for (f in updatedFragments) {
        data.addText(f)
      }
    }

    fun updateLocationString() {
      val loc = data.locationString ?: return
      if (!loc.contains('/') && !loc.contains('~')) return
      val normalizedLoc = FileUtil.toSystemIndependentName(loc)
      val newLoc =
        when {
          prefixes.homeRelativePattern.containsMatchIn(normalizedLoc) ->
            prefixes.homeRelativePattern.replace(normalizedLoc) { "${it.groupValues[1]}<project>" }
          prefixes.absolutePattern.containsMatchIn(normalizedLoc) ->
            prefixes.absolutePattern.replace(normalizedLoc) { "${it.groupValues[1]}<project>" }
          else -> null
        }
      if (newLoc != null) {
        data.locationString = newLoc
      }
    }

    updatePresentableText()
    updateColoredText()
    updateLocationString()
  }

  private fun isWorkspaceRoot(node: ProjectViewNode<*>, absolutePrefix: String): Boolean {
    val vf = node.virtualFile ?: return false
    return vf.path.trimEnd('/') == absolutePrefix
  }

  companion object {
    private val WORKSPACE_PATH_PREFIXES_KEY = Key.create<WorkspacePathPrefixes>("aswb.workspace.path.prefixes")

    private data class WorkspacePathPrefixes(
      val workspacePath: Path,
      val absolutePrefix: String,
      val homeRelativePrefix: String,
      val absolutePrefixWithSlash: String = "$absolutePrefix/",
      val homeRelativePrefixWithSlash: String = "$homeRelativePrefix/",
      val homeRelativePattern: Regex = createLocationPattern(homeRelativePrefix),
      val absolutePattern: Regex = createLocationPattern(absolutePrefix),
    )

    private fun createLocationPattern(prefix: String): Regex {
      return Regex("""(^|[(\[\s])${Regex.escape(prefix)}(?=[/)\s\]]|$)""")
    }

    private fun getOrCreatePrefixes(project: Project, workspacePath: Path): WorkspacePathPrefixes {
      val cached = project.getUserData(WORKSPACE_PATH_PREFIXES_KEY)
      if (cached != null && cached.workspacePath == workspacePath) {
        return cached
      }
      val absolutePrefix = FileUtil.toSystemIndependentName(workspacePath.toString()).trimEnd('/')
      val homeRelativePrefix = FileUtil.toSystemIndependentName(FileUtil.getLocationRelativeToUserHome(absolutePrefix)).trimEnd('/')
      val prefixes = WorkspacePathPrefixes(workspacePath, absolutePrefix, homeRelativePrefix)
      project.putUserData(WORKSPACE_PATH_PREFIXES_KEY, prefixes)
      return prefixes
    }
  }
}
