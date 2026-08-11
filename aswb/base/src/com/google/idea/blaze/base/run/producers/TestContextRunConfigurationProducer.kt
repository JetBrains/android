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

import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import org.jetbrains.annotations.TestOnly

/** Produces run configurations via [TestContextProvider]. */
class TestContextRunConfigurationProducer :
  BlazeRunConfigurationProducer<RunConfigurationContext>(BlazeCommandRunConfigurationType.getInstance()) {

  override fun findContext(context: ConfigurationContext): RunConfigurationContext? {
    if (SmRunnerUtils.getSelectedSmRunnerTreeElements(context).isNotEmpty()) {
      // handled by a different producer
      return null
    }
    return TestContextProvider.EP_NAME.extensions.firstNotNullOfOrNull { it.getTestContext(context) }
  }

  companion object {
    @TestOnly
    @JvmStatic
    fun getInstance(): TestContextRunConfigurationProducer {
      return RunConfigurationProducer.getInstance(TestContextRunConfigurationProducer::class.java)
    }
  }
}
