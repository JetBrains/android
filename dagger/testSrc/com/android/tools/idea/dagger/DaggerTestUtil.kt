/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.dagger

import com.android.test.testutils.TestUtils
import com.intellij.openapi.module.Module
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

private const val DAGGER_VERSION = "2.52"
private const val HILT_VERSION = "2.44.2"
private const val JAVAX_INJECT_VERSION = "1"

private val DAGGER_AND_HILT_LIBRARIES =
  listOf(
    "com/google/dagger/dagger/$DAGGER_VERSION/dagger-$DAGGER_VERSION.jar",
    "com/google/dagger/hilt-core/$HILT_VERSION/hilt-core-$HILT_VERSION.jar",
    "javax/inject/javax.inject/$JAVAX_INJECT_VERSION/javax.inject-$JAVAX_INJECT_VERSION.jar",
  )

@Deprecated(
  "Use addDaggerAndHiltClasses(module: Module) instead",
  ReplaceWith("addDaggerAndHiltClasses(fixture.module)"),
)
fun addDaggerAndHiltClasses(fixture: CodeInsightTestFixture) {
  addDaggerAndHiltClasses(fixture.module)
}

fun addDaggerAndHiltClasses(module: Module) {
  for (path in DAGGER_AND_HILT_LIBRARIES) {
    val pathString = TestUtils.getLocalMavenRepoFile(path).toString()
    PsiTestUtil.addLibrary(module, pathString)
  }
}
