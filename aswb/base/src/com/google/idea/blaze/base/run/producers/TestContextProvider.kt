/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.extensions.ExtensionPointName

/** Used to recognize test contexts, providing all the information required by blaze run configuration producers. */
interface TestContextProvider {

  /**
   * Returns the [RunConfigurationContext] corresponding to the given [ConfigurationContext], if relevant and recognized by this provider.
   *
   * This is called frequently on the EDT, via the `RunConfigurationProducer` API, so must be efficient.
   */
  fun getTestContext(context: ConfigurationContext): RunConfigurationContext?

  companion object {
    @JvmField val EP_NAME: ExtensionPointName<TestContextProvider> = ExtensionPointName.create("com.google.idea.blaze.TestContextProvider")
  }
}
