/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.qsync

import com.google.idea.blaze.android.manifest.ManifestParser
import com.google.idea.blaze.base.qsync.ProjectProtoTransformProvider
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.java.AarPackageNameExtractor
import com.google.idea.blaze.qsync.java.AddAndroidResPackages
import com.google.idea.blaze.qsync.java.AddDependencyAars
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdateOperation

/** A [ProjectProtoTransform] that adds android specific information to the project proto.  */
object AndroidProjectProtoTransform {
  /**
   * Provides a [ProjectProtoTransform] that adds android specific information to the project
   * proto.
   */
  class Provider : ProjectProtoTransformProvider {
    override fun createTransforms(projectDef: ProjectDefinition): List<ProjectProtoUpdateOperation> {
      return listOf(
        AddDependencyAars(
          projectDef,
          AarPackageNameExtractor { ManifestParser.parseManifestFromInputStream(it)?.packageName ?: throw BuildException("Failed to parse manifest") }
        ),
        AddAndroidResPackages()
      )
    }
  }
}
