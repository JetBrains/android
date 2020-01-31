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

import com.android.tools.adtui.validation.Validator.Result
import com.android.tools.idea.observable.core.StringValueProperty
import com.google.common.io.Files
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.io.File

class ModuleValidatorTest {
  private var moduleValidator: ModuleValidator? = null
  private var tmpDir: File? = null
  @Before
  fun createModuleValidator() {
    tmpDir = Files.createTempDir()
    moduleValidator = ModuleValidator(StringValueProperty(tmpDir!!.absolutePath))
  }

  @After
  fun cleanModuleValidator() {
    tmpDir!!.delete()
  }

  @Test
  fun testIsValidModuleName() {
    assertValidModuleName("app")
    assertValidModuleName("lib")
    assertValidModuleName("lib_LIB0")
    assertValidModuleName("lib-LIB1")
    assertValidModuleName(":libs:lib1") // Module in sub folder
  }

  @Test
  fun testInvalidModuleName() {
    assertInvalidModuleName("")
    assertInvalidModuleName("..")
    assertInvalidModuleName("123!456")
    assertInvalidModuleName("a\\b")
    assertInvalidModuleName("a/b")
    assertInvalidModuleName("a'b")
    assertInvalidModuleName("'")
    assertInvalidModuleName("'''")
  }

  @Test
  fun testIsInvalidWindowsModuleName() {
    val invalidWindowsFilenames = arrayOf("con", "prn", "aux", "clock$", "nul", "\$boot")
    for (s in invalidWindowsFilenames) {
      assertInvalidModuleName(s)
    }
  }

  private fun assertValidModuleName(name: String) {
    val result = moduleValidator!!.validate(name)
    assertSame(result.message, Result.OK, result)
  }

  private fun assertInvalidModuleName(name: String) {
    val result = moduleValidator!!.validate(name)
    assertNotSame(result.message, Result.OK, result)
  }
}