/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.producers

import com.google.idea.blaze.base.dependencies.TargetInfo
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

/** Used to recognize blaze binary contexts, providing all the information required by blaze run configuration producers. */
interface BinaryContextProvider {

  /** A context related to a blaze binary target, used to configure a run configuration. */
  data class BinaryRunContext(val sourceElement: PsiElement, val target: TargetInfo) {
    companion object {
      @JvmStatic
      fun create(sourceElement: PsiElement, target: TargetInfo): BinaryRunContext {
        return BinaryRunContext(sourceElement, target)
      }
    }
  }

  /**
   * Returns the [BinaryRunContext] corresponding to the given [ConfigurationContext], if relevant and recognized by this provider.
   *
   * This is called frequently on the EDT, via the `RunConfigurationProducer` API, so must be efficient.
   */
  fun getRunContext(context: ConfigurationContext): BinaryRunContext?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<BinaryContextProvider> = ExtensionPointName.create("com.google.idea.blaze.BinaryContextProvider")
  }
}
