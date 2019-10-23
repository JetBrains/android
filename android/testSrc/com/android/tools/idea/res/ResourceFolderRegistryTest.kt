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
package com.android.tools.idea.res

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

class ResourceFolderRegistryTest : AndroidGradleTestCase() {
  fun testDisposal() {
    loadSimpleApplication()
    val repo = ResourceRepositoryManager.getModuleResources(myAndroidFacet)
      .leafResourceRepositories
      .firstIsInstance<ResourceFolderRepository>()

    runWriteAction {
      project.guessProjectDir()!!.findFileByRelativePath("app/src/main/res")!!.delete(this)
    }
    UIUtil.dispatchAllInvocationEvents()

    assertThat(Disposer.isDisposed(repo)).named("resource folder repository is disposed").isTrue()
  }
}