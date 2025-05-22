/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.variant.conflict

import com.android.test.testutils.TestUtils
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import org.jetbrains.android.AndroidTestCase

abstract class ConflictsTestCase : AndroidTestCase() {
  protected fun appModuleBuilder(
    appPath: String = ":app",
    selectedVariant: String = "debug",
    dependOnVariant: String? = "debug"
  ) = AndroidModuleModelBuilder(
    appPath,
    selectedVariant,
    AndroidProjectBuilder(androidModuleDependencyList = { listOf(AndroidModuleDependency(":lib", dependOnVariant)) })
  )

  protected fun libModuleBuilder(selectedVariant: String = "debug") =
    AndroidModuleModelBuilder(
      ":lib",
      selectedVariant,
      AndroidProjectBuilder(projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY })
    )

  override fun setUp() {
    super.setUp()
    AndroidGradleTests.setUpSdks(myFixture, TestUtils.getSdk().toFile())
  }
}