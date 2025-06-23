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
        append("@@");
        append(workspace);
      }
      append("//");
      append(buildPackage);
      append(":");
      append(name);
    }
  }
}

data class TargetPattern(
  private val workspace: String,
  private val buildPackagePath: Path,
  private val includesSubpackages: Boolean,
  private val targetName: String?,
) {
  fun includes(target: Label): Boolean {
    if (workspace != target.workspace) return false
    val targetBuildPackagePath = target.getBuildPackagePath()
    if (buildPackagePath != targetBuildPackagePath && !(includesSubpackages && targetBuildPackagePath.startsWith(buildPackagePath))) {
      return false
    }
    return targetName == null || targetName == target.name
  }

  companion object {
    private val threeDotsPath = Path.of("...")
    private val wildcardTargets = setOf("*", "all", "all-targets")

    fun parse(pattern: String): TargetPattern {
      val parsedAsLabel = Label.parseLabel(pattern, allowRelativeLabels = true)
      val parsedBuildPackagePath = parsedAsLabel.getBuildPackagePath()
      return if (parsedBuildPackagePath.endsWith(threeDotsPath))
        TargetPattern(
          parsedAsLabel.workspace,
          parsedBuildPackagePath.subpath(0, parsedBuildPackagePath.nameCount - 1),
          includesSubpackages = true,
          targetName = null
        )
      else TargetPattern(
        parsedAsLabel.workspace,
        parsedBuildPackagePath.subpath(0, parsedBuildPackagePath.nameCount),
        includesSubpackages = false,
        targetName = parsedAsLabel.name.takeUnless { it in wildcardTargets }
      )
    }
  }
}