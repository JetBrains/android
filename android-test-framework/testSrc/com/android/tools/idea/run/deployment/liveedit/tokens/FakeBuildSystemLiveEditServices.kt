/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit.tokens

import com.android.tools.idea.project.FacetBasedApplicationProjectContext
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.projectsystem.TestApplicationProjectContext
import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices.ApplicationLiveEditServicesForTests
import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.util.asSafely

class FakeBuildSystemLiveEditServices : BuildSystemLiveEditServices<AndroidProjectSystem, ApplicationProjectContext> {
  var testApplicationLiveEditServices: ApplicationLiveEditServices? = null

  override fun isApplicable(applicationProjectContext: ApplicationProjectContext): Boolean {
    return testApplicationLiveEditServices != null ||
      applicationProjectContext is FacetBasedApplicationProjectContext ||
      applicationProjectContext is TestApplicationProjectContext
  }

  override fun isApplicable(projectSystem: AndroidProjectSystem): Boolean = true

  override fun getApplicationServices(applicationProjectContext: ApplicationProjectContext): ApplicationLiveEditServices {
    val testApplicationLiveEditServices = testApplicationLiveEditServices
    return when {
      testApplicationLiveEditServices != null -> testApplicationLiveEditServices
      applicationProjectContext is FacetBasedApplicationProjectContext -> ApplicationLiveEditServices.LegacyForTests(applicationProjectContext.project)
      applicationProjectContext is TestApplicationProjectContext -> ApplicationLiveEditServicesForTests(classFiles = mapOf())
      else -> error("Unexpected application project context: $applicationProjectContext")
    }
  }

  /**
   * Registers this fake implementation for the lifespan of [parentDisposable] for all project systems.
   */
  fun register(parentDisposable: Disposable) {
    ExtensionTestUtil.maskExtensions(BuildSystemLiveEditServices.EP_NAME, listOf(this), parentDisposable)
  }
}