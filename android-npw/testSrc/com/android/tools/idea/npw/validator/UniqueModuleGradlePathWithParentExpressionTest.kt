/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.npw.validator

import com.android.tools.idea.npw.module.UniqueModuleGradlePathWithParentExpression
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class UniqueModuleGradlePathWithParentExpressionTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels(
    JavaModuleModelBuilder.rootModuleBuilder,
    AndroidModuleModelBuilder(":app", "debug", AndroidProjectBuilder()),
    JavaModuleModelBuilder(":libs", buildable = false),
    AndroidModuleModelBuilder(":libs:lib", "debug", AndroidProjectBuilder()),
    AndroidModuleModelBuilder(":libs:lib2", "debug", AndroidProjectBuilder())
  )

  @Test
  fun testFindUniqueName() {
    assertEquals("mylib", getValidatorValue("My Lib", ""))
    assertEquals("app:mylib", getValidatorValue("My Lib", "app"))
    assertEquals(":app:mylib", getValidatorValue("My Lib", ":app"))
    assertEquals(":app:mylib", getValidatorValue("My Lib", ":app:"))
    assertEquals("app2", getValidatorValue("app", ""))
    assertEquals("app2", getValidatorValue("app", ":"))
    assertEquals("libs2", getValidatorValue("libs", ""))
    assertEquals(":libs:lib3", getValidatorValue("lib", ":libs"))

    assertEquals("invalid'", getValidatorValue("invalid'", "")) // b/172268020
    assertEquals("invalid'", getValidatorValue("Invalid '", ""))
    assertEquals(":libs:invalid'", getValidatorValue("Invalid '", ":libs"))
  }

  private fun getValidatorValue(applicationName: String, moduleParent: String) : String =
    UniqueModuleGradlePathWithParentExpression(projectRule.project, StringValueProperty(applicationName), moduleParent).get()
}