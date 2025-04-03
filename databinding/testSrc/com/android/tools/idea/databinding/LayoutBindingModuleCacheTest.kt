/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.databinding

import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.Module
import com.intellij.testFramework.ExtensionTestUtil
import kotlin.test.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LayoutBindingModuleCacheTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun ensureSearchScopeDoesNotRecurseInfinitely() {
    // Regression test for b/408194178
    val recursiveBindingLayoutToken =
      object : BindingLayoutToken<AndroidProjectSystem> {
        override fun isTestModule(projectSystem: AndroidProjectSystem, module: Module) = false

        override fun additionalModulesForLightBindingScope(
          projectSystem: AndroidProjectSystem,
          module: Module,
        ): List<Module> = listOf(module)

        override fun isApplicable(projectSystem: AndroidProjectSystem) = true
      }

    ExtensionTestUtil.maskExtensions(
      BindingLayoutToken.EP_NAME,
      listOf(recursiveBindingLayoutToken),
      projectRule.testRootDisposable,
    )

    val androidFacet = assertNotNull(projectRule.module.androidFacet)
    val moduleCache = LayoutBindingModuleCache.getInstance(androidFacet)

    // Simply invoking `lightBindingClassSearchScope` is enough to ensure it doesn't enter an
    // infinite recursion.
    assertThat(moduleCache.lightBindingClassSearchScope).isNotNull()
  }
}
