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
package com.google.idea.blaze.qsync.deps

import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.project.DependencyGraphProvider
import com.google.idea.blaze.qsync.project.Target
import com.google.idea.blaze.qsync.project.TargetStatus

class OverrideDependencyGraphProvider(
  private val delegate: DependencyGraphProvider,
  private val targetOverride: (Label, DependencyGraphProvider) -> Target?,
) : DependencyGraphProvider {

  override fun getTarget(label: Label): Target {
    return targetOverride(label, this) ?: delegate.getTarget(label)
  }
}

fun JavaOutputInfoDependencyGraphProvider(outputInfo: OutputInfo, delegate: DependencyGraphProvider): DependencyGraphProvider {
  return OverrideDependencyGraphProvider(delegate) { label, provider ->
    val javaArtifacts = outputInfo.javaArtifactInfo[label]
    if (javaArtifacts != null) {
      object : Target {
        override val label: Label = label
        override val status: TargetStatus =
          if (javaArtifacts.isExternalDependency) {
            TargetStatus.EXTERNAL_TO_PROJECT_SCOPE
          } else {
            TargetStatus.IN_PROJECT_SCOPE
          }
        override val dependencies: Collection<Target>
          get() = outputInfo.getDependencies(label).map { provider.getTarget(it) }

        override fun equals(other: Any?): Boolean = (other as? Target)?.label == label

        override fun hashCode(): Int = label.hashCode()
      }
    } else {
      null
    }
  }
}
