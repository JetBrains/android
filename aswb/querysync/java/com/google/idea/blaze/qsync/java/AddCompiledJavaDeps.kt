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
package com.google.idea.blaze.qsync.java

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.qsync.deps.ArtifactDirectories
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdateOperation
import com.google.idea.blaze.qsync.project.ProjectPath
import kotlin.jvm.optionals.getOrNull

/** Adds compiled jars from dependencies to the project.  */
class AddCompiledJavaDeps(private val emptyJarDigests: Set<String>) : ProjectProtoUpdateOperation {
  override fun update(update: ProjectProtoUpdate, artifactState: ArtifactTracker.State, context: Context<*>) {
    update.artifactDirectory(ArtifactDirectories.JAVADEPS) {
      val skipped: MutableSet<String> = hashSetOf()
      val seen: MutableSet<String> = hashSetOf()
      val libNameToJars: MutableMap<Label, MutableSet<ProjectPath>> = hashMapOf()
      val outputJarToTarget: Map<String, Label> =
        artifactState.targets()
          .flatMap { it.javaInfo().getOrNull()?.outputJars().orEmpty() }
          .associate { it.digest() to it.target() }
      var emptySkipped = 0
      for (target in artifactState.targets()) {
        val javaInfo = target.javaInfo().getOrNull() ?: continue

        val jarsToAdd =
          javaInfo
            .jars()
            .filter { jar ->
              val emptyJar = emptyJarDigests.contains(jar.digest())
              if (emptyJar) {
                emptySkipped++
              }
              !emptyJar
            }
            .filter { jar ->
              val targetLabelByDigest = outputJarToTarget[jar.digest()]
              // Unknown or directly produced target.
              targetLabelByDigest == null || target.label() == targetLabelByDigest
            }
            .filter { jar ->
              val duplicateJar = seen.contains(jar.digest())
              if (duplicateJar) {
                skipped.add(jar.artifactPath().toString())
              }
              !duplicateJar
            }
            .toList()
        val jars = jarsToAdd.map { jar ->
          seen.add(jar.digest())
          addIfNewer(jar.artifactPath(), jar, target.buildContext())
          ArtifactDirectories.JAVADEPS.resolveChild(jar.artifactPath())
        }
        if (jars.isNotEmpty()) {
          libNameToJars.getOrPut(target.label()) { hashSetOf() } += jars
        }
      }
      context.output(PrintOutput.output("Skipped ${skipped.size} duplicate jars"))
      context.output(PrintOutput.output("Skipped $emptySkipped empty jars"))
      updateProjectProtoUpdateOneTargetToOneLibrary(libNameToJars, update)
    }
  }

  private fun updateProjectProtoUpdateOneTargetToOneLibrary(
    libNameToJars: Map<Label, Set<ProjectPath>>, update: ProjectProtoUpdate
  ) {
    libNameToJars.forEach { (name, jars) ->
      update
        .library(name) {
          addClassJars(jars)
        }
    }
  }
}
