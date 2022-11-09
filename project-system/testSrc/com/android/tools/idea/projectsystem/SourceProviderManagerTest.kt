/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runWriteActionAndWait
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.roots.invalidateProjectRoots
import org.junit.Rule
import org.junit.Test

class SourceProviderManagerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun selfDisposesOnProjectRootsChange() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val sourceProviderManagerBeforeNotification = facet.sourceProviders
    runWriteActionAndWait {
      projectRule.project.invalidateProjectRoots()
    }
    val sourceProviderManagerAfterNotification = facet.sourceProviders
    assertThat(sourceProviderManagerAfterNotification).isNotSameAs(sourceProviderManagerBeforeNotification)
  }
}

