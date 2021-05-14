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
package com.android.tools.idea.ui.wizard

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.io.FileUtil.filesEqual
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.PlatformIcons
import com.intellij.util.SmartList
import java.io.File
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import javax.swing.Icon
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import kotlin.streams.toList

/**
 * Default [Icon] that [ProposedFileTreeModel] marks non-directory [File]s
 * with when a proposedFileToIcon mapping isn't specified.
 */
val DEFAULT_ICON: Icon = AllIcons.FileTypes.Any_type

/**
 * The [Icon] that [ProposedFileTreeModel] uses to mark directories.
 */
val DIR_ICON: Icon = PlatformIcons.FOLDER_ICON

/**
 * A [TreeModel] representing the sub-tree of the file system relevant to a pre-determined set of
 * proposed new [File]s relative to a given root directory. The model keeps track of which of the
 * non-directory [File]s already existed **when the model was created**. If a path to a proposed
 * file contains non-existing intermediate directories, these will also be considered new files.
 *
 * The model marks each relevant file and directory with an [Icon] for rendering (see
 * [ProposedFileTreeCellRenderer]). By default, proposed files are marked with either [DIR_ICON]
 * for directories or [DEFAULT_ICON] for regular files. However, callers can specify the [Icon]
 * corresponding to each proposed [File] by passing a [Map] to the appropriate constructor.
 *
 * Directory contents preserve the iteration order of the [Set] or [Map] used to construct the model.
 * For example, if the model is built from the sorted map
 *
 * { File("root/sub1/file1"): icon1, File("root/sub2/file2"): icon2 }
 *
 * then *sub1* will appear before *sub2* in the model of *root*'s children, since it appeared first
 * when iterating over the map entries.
 */
class ProposedFileTreeModel private constructor(private val rootNode: Node) : TreeModel {
  constructor(rootDir: File, proposedFileToIcon: Map<File, Icon>)
    : this(Node.makeTree(rootDir, proposedFileToIcon.keys, proposedFileToIcon::get))

  constructor(rootDir: File, proposedFiles: Set<File>)
    : this(
    Node.makeTree(rootDir, proposedFiles) { null })

  /**
   * Returns true if any of the non-directory proposed [File]s in the tree already exist.
   */
  fun hasConflicts() = rootNode.hasConflicts()

  override fun getRoot() = rootNode

  override fun isLeaf(node: Any?) = (node as Node).isLeaf()

  override fun getChildCount(parent: Any?) = (parent as Node).getChildCount()

  override fun getIndexOfChild(parent: Any?, child: Any?) = (parent as Node).getIndexOfChild(child as Node)

  override fun getChild(parent: Any?, index: Int) = (parent as Node).getChild(index)

  override fun valueForPathChanged(path: TreePath?, newValue: Any?) {}

  override fun removeTreeModelListener(l: TreeModelListener?) {}

  override fun addTreeModelListener(l: TreeModelListener?) {}

  /**
   * Returns the conflicting files that are not overwritten by the new files.
   */
  fun getShadowConflictedFiles(): List<File> {
    return rootNode.getShadowConflictedFiles()
  }

  /**
   * A vertex in a [ProposedFileTreeModel]'s underlying tree structure. Each node corresponds either
   * to a directory, in which case it will also keep track of a list of nodes corresponding to that
   * directory's children, or to a proposed normal [File], in which case it records whether or not the
   * proposed [File] is in conflict with an existing file.
   *
   * By definition,[Node]s corresponding to directories are not conflicted (though they may represent
   * a [conflictedTree]), and [Node]s corresponding to normal (non-directory) files can have no children.
   *
   * @property file The proposed [File] corresponding to this node
   * @property conflictedFiles the list of existing files this proposed file conflicts with
   * @property icon The [Icon] with which the [File] should be marked
   * @property children If this node corresponds to a directory, a list of nodes corresponding
   *           to the directory's children. Otherwise, this list is empty.
   * @property conflictedTree true if this node or any of its descendants correspond to a proposed
   *           [File] that already exists as a normal (non-directory) file.
   */
  data class Node(val file: File,
                  val conflictedFiles: List<File>,
                  private var icon: Icon,
                  private val children: MutableList<Node> = mutableListOf(),
                  private var conflictedTree: Boolean = conflictedFiles.isNotEmpty()) {

    fun hasConflicts() = conflictedTree

    fun getIcon() = icon

    fun isLeaf() = children.isEmpty()

    fun getChildCount() = children.size

    fun getIndexOfChild(child: Node) = children.indexOf(child)

    fun getChild(index: Int) = children[index]

    private fun findChildByFile(childFile: File) = children.find { FileUtilRt.filesEqual(it.file, childFile) }

    private fun addChild(childNode: Node) {
      if (icon == DEFAULT_ICON) {
        // The file this node corresponds to was originally thought to be a regular file,
        // but now we know it's a directory. Change the node's icon to reflect this.
        icon = DIR_ICON
      }
      children.add(childNode)
    }

    /**
     * Given a node-relative path to a proposed [File] and an [Icon] with which to mark it,
     * this function recursively builds all the missing intermediate directory nodes
     * between this node and a newly-constructed leaf node corresponding to the proposed [File].
     *
     * @param relativePath a list of path segments pointing to the proposed [File]
     * @param icon the [Icon] with which the proposed [File] should be marked, or null if the
     *             proposed [File] should be marked with a default [Icon].
     */
    private fun addDescendant(relativePath: List<String>, icon: Icon?, conflictChecker: ConflictChecker?) {
      if (relativePath.isEmpty()) return

      val childFile = file.resolve(relativePath[0])
      var childNode = findChildByFile(childFile)

      if (relativePath.size == 1) {
        if (childNode != null) {
          // If a node for the descendant we're adding already exists, the descendant is an
          // intermediate directory that appeared in the path of another proposed file. Mark it
          // with the caller-specified icon, if one was given.
          if (icon != null) {
            childNode.icon = icon
          }
        }
        else {
          val nodeIcon = when {
            icon != null -> icon
            childFile.isDirectory -> DIR_ICON
            else -> DEFAULT_ICON
          }
          val conflicts = conflictChecker?.findConflictingFiles(childFile) ?: emptyList()
          childNode = Node(childFile, conflicts, nodeIcon)
          addChild(childNode)
        }
      }
      else {
        if (childNode == null) {
          // If a node for the intermediate directory doesn't exist yet, make one.
          childNode = Node(childFile, emptyList(), DIR_ICON)
          addChild(childNode)
        }

        childNode.addDescendant(relativePath.drop(1), icon, conflictChecker)
      }

      if (childNode.conflictedTree) {
        conflictedTree = true
      }
    }

    fun getShadowConflictedFiles(): List<File> {
      if (hasConflicts()) {
        val shadowConflicts = mutableListOf<File>()
        getShadowConflictedFiles(shadowConflicts)
        return shadowConflicts
      }
      return emptyList()
    }

    private fun getShadowConflictedFiles(conflictedFiles: MutableList<File>) {
      if (hasConflicts()) {
        for (conflict in this.conflictedFiles) {
          if (!filesEqual(conflict, file)) {
            conflictedFiles.add(conflict)
          }
        }
        for (child in children) {
          child.getShadowConflictedFiles(conflictedFiles)
        }
      }
    }

    companion object {
      /**
       * Constructs a tree rooted at [rootDir] that contains nodes corresponding to each proposed
       * file in [proposedFiles] and any intermediate directories.
       *
       * @param rootDir the directory the root node of the tree should correspond to
       * @param proposedFiles a set of proposed files. Each file must be a descendant of [rootDir].
       * @param getIconForFile a function which, given a file, returns either the icon that it
       *        should be marked with or null to use a default icon
       *
       * @throws IllegalArgumentException if any of the files in [proposedFiles] is not a descendant
       *         of [rootDir]
       */
      fun makeTree(rootDir: File, proposedFiles: Set<File>, getIconForFile: (File) -> Icon?): Node {
        val root = getCommonAncestor(rootDir, proposedFiles)
        val rootNode = Node(root, emptyList(), DIR_ICON)
        val conflictChecker = ConflictChecker(root)

        for (file in proposedFiles) {
          val icon = getIconForFile(file)
          val relativeFile = if (file.isAbsolute) file.relativeTo(root) else file.normalize()

          if (relativeFile.startsWith("..")) {
            throw IllegalArgumentException("$root is not an ancestor of $file")
          }
          rootNode.addDescendant(relativeFile.invariantSeparatorsPath.split("/"), icon, conflictChecker)
        }

        return rootNode
      }

      private fun getCommonAncestor(rootDir: File, files: Collection<File>): File {
        var root = rootDir.toPath()
        outer@ while (true) {
          for (file in files) {
            if (!file.toPath().startsWith(root)) {
              root = root.parent ?: break
              continue@outer
            }
          }
          return root.toFile()
        }
      }
    }
  }

  private class ConflictChecker(private val rootDir: File) {
    private val subdirectoryCache = mutableMapOf<File, List<File>>()

    fun findConflictingFiles(file: File): List<File> {
      val conflicts = SmartList<File>()
      if (file.isFile) {
        conflicts.add(file)
      }
      val parent = file.parentFile
      if (parent != null) {
        val grandParent = parent.parentFile
        if (filesEqual(grandParent?.parentFile, rootDir)) {
          val fileName = file.name
          val thisConfig = FolderConfiguration.getConfigForFolder(parent.name)
          if (thisConfig != null) {
            for (dir in getSubdirectories(grandParent)) {
              if (!filesEqual(dir, parent)) {
                val config = FolderConfiguration.getConfigForFolder(dir.name) ?: continue
                if (thisConfig.isMatchFor(config)) {
                  val potentialConflict = dir.resolve(fileName)
                  if (potentialConflict.isFile) {
                    conflicts.add(potentialConflict)
                  }
                }
              }
            }
          }
        }
      }
      return conflicts
    }

    fun getSubdirectories(dir: File): List<File> {
      return subdirectoryCache.computeIfAbsent(dir) {
        try {
          return@computeIfAbsent Files.list(dir.toPath()).use { stream ->
            stream
              .filter { Files.isDirectory(it) }
              .map { it.toFile() }
              .toList()
          }
        }
        catch (e: NoSuchFileException) {
          return@computeIfAbsent emptyList()
        }
      }
    }
  }
}