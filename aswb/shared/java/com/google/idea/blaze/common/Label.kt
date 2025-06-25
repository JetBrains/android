/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common

import com.google.idea.blaze.common.TargetPattern.ScopeStatus
import com.google.idea.blaze.common.TargetPattern.ScopeStatus.EXCLUDED
import com.google.idea.blaze.common.TargetPattern.ScopeStatus.INCLUDED
import com.google.idea.blaze.common.TargetPattern.ScopeStatus.NOT_IN_SCOPE
import java.nio.file.Path


/**
 * Represents an absolute build target label.
 *
 * <p>This class is a simple wrapper around a string and should be used in place of {@link String}
 * whenever appropriate.
 *
 * <p>It can be considered equivalent to <a href="https://bazel.build/rules/lib/Label>Label</a> in
 * bazel.
 *
 * <p>Note that this class only supports labels in the current workspace, i.e. not labels of the
 * form {@code @repo//pkg/foo:abc}.
 */
data class Label(val workspace: String, val buildPackage: String, val name: String) {

  companion object {
    @JvmField
    val ROOT_WORKSPACE = "";

    @JvmStatic
    fun of(label: String): Label {
      return parseLabel(label)
    }

    /**
     * Parse a string as a Bazel label.
     *
     * When parsing the resulting label is not validated fully. This is to avoid performance penalty when processing BEP output.
     */
    fun parseLabel(label: String, allowRelativeLabels: Boolean = false): Label {
      require(!label.isBlank()) { "Empty label" }
      val workspacePosition = if (label.startsWith("@")) (if (label.startsWith("@@")) 2 else 1) else 0
      val (workspaceEnd, buildPackagePosition) = label.indexOf("//", workspacePosition)
        .let { if (it < 0 && allowRelativeLabels) 0 to 0 else it to it + 2 }
      require(workspaceEnd >= workspacePosition) { "Invalid label: $label" }
      val (buildPackageEnd, namePosition) = label.indexOf(":", buildPackagePosition)
        .let {
          if (it >= 0) it to it + 1 else label.length to label.lastIndexOf('/') + 1
        }

      val workspace = label.substring(workspacePosition, workspaceEnd)
      // Bazel 8 states that repo names may contain only A-Z, a-z, 0-9, '-', '_', '.' and '+', but this is not a stable specification
      // as just a version ago it allowed ~ as well. Make sure the name does not include any dangerous characters.
      require(!workspace.contains('/') && !workspace.contains(':')) { "Invalid workspace: $workspace" }

      val buildPackage = label.substring(buildPackagePosition, buildPackageEnd)
      val name = label.substring(namePosition)

      return Label(
        workspace = Interners.STRING.intern(workspace),
        buildPackage = Interners.STRING.intern(buildPackage),
        name = Interners.STRING.intern(name)
      )
    }

    @JvmStatic
    fun fromWorkspacePackageAndName(workspace: String, packagePath: Path, name: Path): Label {
      return if (workspace.isEmpty())
        Label.of("//$packagePath:$name")
      else Label.of("@@$workspace//$packagePath:$name")
    }

    @JvmStatic
    fun fromWorkspacePackageAndName(workspace: String, packagePath: Path, name: String): Label {
      return fromWorkspacePackageAndName(workspace, packagePath, Path.of(name));
    }
  }

  fun getBuildPackagePath(): Path = Path.of(buildPackage)
  fun getNamePath(): Path = Path.of(name)

  fun siblingWithName(name: String): Label = fromWorkspacePackageAndName(workspace, getBuildPackagePath(), name);

  fun siblingWithPathAndName(pathAndName: String): Label {
    val colonPos: Int = pathAndName.indexOf(':')
    require(colonPos > 0) { pathAndName }
    return fromWorkspacePackageAndName(
      workspace = workspace,
      packagePath = getBuildPackagePath().resolve(pathAndName.substring(0, colonPos)),
      name = pathAndName.substring(colonPos + 1)
    )
  }

  /** When this label refers to a source file, returns the workspace relative path to that file. */
  fun toFilePath(): Path = getBuildPackagePath().resolve(getNamePath())

  override fun toString(): String {
    return buildString(capacity = 5 + workspace.length + buildPackage.length + name.length) {
      if (!workspace.isEmpty()) {
        append("@@")
        append(workspace)
      }
      append("//")
      append(buildPackage)
      append(":")
      append(name)
    }
  }
}

private val rootPath = Path.of("/")

data class TargetPattern(
  internal val workspace: String,
  internal val buildPackageAbsolutePath: Path,
  internal val includesSubpackages: Boolean,
  internal val targetName: String?,
  private val negative: Boolean,
) {
  enum class ScopeStatus { NOT_IN_SCOPE, INCLUDED, EXCLUDED }

  fun inScope(target: Label): ScopeStatus {
    if (workspace != target.workspace) return NOT_IN_SCOPE
    val targetBuildPackagePath = rootPath.resolve(target.getBuildPackagePath())
    if (buildPackageAbsolutePath != targetBuildPackagePath && !(includesSubpackages && targetBuildPackagePath.startsWith(buildPackageAbsolutePath))) {
      return NOT_IN_SCOPE
    }
    return when (targetName == null || targetName == target.name) {
      true -> if (negative) EXCLUDED else INCLUDED
      false -> NOT_IN_SCOPE
    }
  }

  override fun toString(): String {
    return buildString(capacity = 9 + workspace.length + buildPackageAbsolutePath.toString().length + (targetName?.length ?: 0)) {
      if (negative) {
        append("-")
      }
      if (workspace != "") {
        append("@@")
        append(workspace)
      }
      append("/")
      append(buildPackageAbsolutePath)
      if (targetName != null) {
        append(":")
        append(targetName)
      } else {
        if (includesSubpackages) {
          append("/...")
        }
        else {
          append(":*")
        }
      }
    }
  }

  companion object {
    private val threeDotsPath = Path.of("...")
    private val wildcardTargets = setOf("*", "all", "all-targets")

    @JvmStatic
    fun parse(pattern: String): TargetPattern {
      val (negative, patternLabel) = if (pattern.startsWith('-')) true to pattern.substring(1) else false to pattern
      val parsedAsLabel = Label.parseLabel(patternLabel, allowRelativeLabels = true)
      val parsedBuildPackagePath = rootPath.resolve(parsedAsLabel.getBuildPackagePath())
      return if (parsedBuildPackagePath.endsWith(threeDotsPath))
        TargetPattern(
          parsedAsLabel.workspace,
          parsedBuildPackagePath.parent,
          includesSubpackages = true,
          targetName = null,
          negative = negative,
        )
      else TargetPattern(
        parsedAsLabel.workspace,
        parsedBuildPackagePath,
        includesSubpackages = false,
        targetName = parsedAsLabel.name.takeUnless { it in wildcardTargets },
        negative = negative,
      )
    }
  }
}

class TargetPatternCollection(private val root: Node, private val rootScope: ScopeStatus) {
  class Node(val key: String, var children: MutableMap<String, Node> = mutableMapOf(), var scope: ((Label) -> ScopeStatus)? = null)

  fun inScope(target: Label): ScopeStatus {
    var node = root
    var lastScope: ((Label) -> ScopeStatus) = { rootScope }
    for (key in target.structuralKeys()) {
      node = node.children[key] ?: break
      lastScope = node.scope ?: lastScope
    }
    return lastScope(target)
  }

  companion object {
    @JvmStatic
    fun create(patterns: List<TargetPattern>): TargetPatternCollection {
      if (patterns.isEmpty()) {
        // The empty list means everything is included.
        return TargetPatternCollection(Node(key = ""), rootScope = INCLUDED)
      }
      val root = Node(key = "")

      fun add(pattern: TargetPattern) {
        var node = root
        for (key in pattern.structuralKeys()) {
          node = node.children.getOrPut(key) { Node(key) }
        }
        node.children.clear() // Override any previously configured patterns underneath.
        node.scope = pattern::inScope
      }

      for (pattern in patterns) {
        add(pattern)
      }
      return TargetPatternCollection(root, rootScope = NOT_IN_SCOPE)
    }
  }
}

private fun TargetPattern.structuralKeys(): List<String> = buildList(3 + buildPackageAbsolutePath.nameCount) {
  add(workspace)
  addAll(buildPackageAbsolutePath.map { it.toString() })
  if (!includesSubpackages) {
    add("*") // A dedicated key for all targets under the package.
    if (targetName != null) {
      add(targetName)
    }
  }
}

private fun Label.structuralKeys(): List<String> = buildList(3 + getBuildPackagePath().nameCount) {
  add(workspace)
  addAll(getBuildPackagePath().map {it.toString()})
  add("*") // A dedicated key for all targets under the package.
  add(name)
}

