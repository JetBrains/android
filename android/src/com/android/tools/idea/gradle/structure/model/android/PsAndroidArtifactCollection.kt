/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android

import com.android.ide.common.gradle.model.IdeAndroidArtifact
import com.android.ide.common.gradle.model.IdeBaseArtifact
import com.android.ide.common.gradle.model.IdeJavaArtifact
import com.android.tools.idea.gradle.structure.model.PsModelCollection
import java.util.function.Consumer

class PsAndroidArtifactCollection internal constructor(val parent: PsVariant) : PsModelCollection<PsAndroidArtifact> {
  private val artifactsByName = mutableMapOf<String, PsAndroidArtifact>()

  init {
    val variant = this.parent.resolvedModel
    if (variant != null) {
      addArtifact(variant.mainArtifact)
      for (androidArtifact in variant.extraAndroidArtifacts) {
        if (androidArtifact != null) {
          addArtifact(androidArtifact as IdeAndroidArtifact)
        }
      }
      for (javaArtifact in variant.extraJavaArtifacts) {
        if (javaArtifact != null) {
          addArtifact(javaArtifact as IdeJavaArtifact)
        }
      }
    }
  }

  private fun addArtifacts(artifacts: Collection<IdeBaseArtifact>) {
    artifacts.forEach(Consumer { this.addArtifact(it) })
  }

  private fun addArtifact(artifact: IdeBaseArtifact) {
    artifactsByName[artifact.name] = PsAndroidArtifact(parent, artifact.name, artifact)
  }

  fun findElement(name: String): PsAndroidArtifact? {
    return artifactsByName[name]
  }

  override fun forEach(consumer: Consumer<PsAndroidArtifact>) {
    artifactsByName.values.forEach(consumer)
  }
}
