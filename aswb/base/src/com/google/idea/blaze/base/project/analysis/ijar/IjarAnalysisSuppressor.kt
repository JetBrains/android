/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.base.project.analysis.ijar

import com.google.idea.blaze.qsync.deps.ArtifactDirectories
import com.google.idea.common.experiments.BoolExperiment
import com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisSuppressor
import com.intellij.openapi.vfs.VirtualFile

class IjarAnalysisSuppressor: BytecodeAnalysisSuppressor {
  private val prefix = ArtifactDirectories.JAVADEPS.relativePath().toString()


  override fun shouldSuppress(file: VirtualFile): Boolean {
    val filePath = file.path
    val javaDepsIndex = filePath.indexOf(prefix)
    // In general any `.jar` files under `.bazel/javadeps` are normally expected to contains class headers and therefore should not be
    // analysed to infer their contracts. However, there might be some prepackaged core library files and such analysis could be still
    // helpful so we skip only files than explicitly end with `-ijar.jar` or `-hjar.jar`. We may need to adjust this in the future.
    // Note: Analyzing the content of the .jar may be more precise but this this method is supposed to work fast so while it works with
    // this solution.
    return enabled.value && javaDepsIndex > 0 ||
           (filePath.indexOf("-ijar.jar!/", javaDepsIndex + prefix.length) > 0 &&
            filePath.indexOf("-hjar.jar!/", javaDepsIndex + prefix.length) > 0);
  }

  companion object {
    @JvmField
    val enabled = BoolExperiment("header.jar.analysis.suppression.enabled", true)
  }
}
