/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.adtui.validation.Validator
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ModuleValidatorTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels(
    JavaModuleModelBuilder.rootModuleBuilder,
    AndroidModuleModelBuilder(":app", "debug", AndroidProjectBuilder()),
    JavaModuleModelBuilder(":libs", buildable = false),
    AndroidModuleModelBuilder(":libs:lib", "debug", AndroidProjectBuilder()),
    AndroidModuleModelBuilder(":libs:lib2", "debug", AndroidProjectBuilder())
  )
  private lateinit var moduleValidator: ModuleValidator

  @Before
  fun createModuleValidator() {
    moduleValidator = ModuleValidator(projectRule.project)
  }

  @Test
  fun testIsValidModuleName() {
    assertNotOkModuleName("app")
    assertNotOkModuleName(":app")
    assertNotOkModuleName("libs:lib")
    assertNotOkModuleName(":libs:lib2")
    assertOkModuleName("lib")
    assertOkModuleName(":lib")
    assertOkModuleName("lib_LIB0")
    assertOkModuleName("lib-LIB1")
    assertOkModuleName(":libs:lib1") // Module in sub folder
  }

  @Test
  fun testNotRecommendedModuleName() {
    assertWarningModuleName(":libs:lib 1")
  }

  @Test
  fun testInvalidModuleName() {
    assertErrorModuleName("")
    assertErrorModuleName("..")
    assertErrorModuleName("123!456")
    assertErrorModuleName("a\\b")
    assertErrorModuleName("a/b")
    assertErrorModuleName("a'b")
    assertErrorModuleName("'")
    assertErrorModuleName("'''")
  }

  @Test
  fun testIsInvalidWindowsModuleName() {
    val invalidWindowsFilenames = arrayOf("con", "prn", "aux", "clock$", "nul", "\$boot")
    for (s in invalidWindowsFilenames) {
      assertNotOkModuleName(s)
    }
  }

  private fun assertOkModuleName(name: String) {
    val result = moduleValidator.validate(name)
    assertSame(result.message, Validator.Severity.OK, result.severity)
  }

  private fun assertNotOkModuleName(name: String) {
    val result = moduleValidator.validate(name)
    assertNotSame(Validator.Severity.OK, result.severity)
  }

  private fun assertErrorModuleName(name: String) {
    val result = moduleValidator.validate(name)
    assertSame(result.message, Validator.Severity.ERROR, result.severity)
  }

  private fun assertWarningModuleName(name: String) {
    val result = moduleValidator.validate(name)
    assertSame(result.message, Validator.Severity.WARNING, result.severity)
  }
}