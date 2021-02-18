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
package org.jetbrains.android.facet

import com.android.tools.idea.projectsystem.sourceProviders
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import com.google.common.truth.Truth.assertThat
import com.intellij.ProjectTopics
import com.intellij.facet.FacetManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.module.impl.ModuleEx
import com.intellij.openapi.roots.impl.ModuleRootEventImpl
import org.junit.Rule
import org.junit.Test

class SourceProviderManagerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun selfDisposesOnFacetConfigurationChange() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val sourceProviderManagerBeforeNotification = facet.sourceProviders
    invokeAndWaitIfNeeded {
      (projectRule.module as ModuleEx).deprecatedModuleLevelMessageBus.syncPublisher(FacetManager.FACETS_TOPIC).facetConfigurationChanged(facet)
    }
    val sourceProviderManagerAfterNotification = facet.sourceProviders
    assertThat(sourceProviderManagerAfterNotification).isNotSameAs(sourceProviderManagerBeforeNotification)
  }

  @Test
  fun selfDisposesOnProjectRootsChange() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val sourceProviderManagerBeforeNotification = facet.sourceProviders
    invokeAndWaitIfNeeded {
      val publisher = projectRule.project.messageBus.syncPublisher(ProjectTopics.PROJECT_ROOTS)
      publisher.beforeRootsChange(ModuleRootEventImpl(projectRule.project, false))
      publisher.rootsChanged(ModuleRootEventImpl(projectRule.project, false))
    }
    val sourceProviderManagerAfterNotification = facet.sourceProviders
    assertThat(sourceProviderManagerAfterNotification).isNotSameAs(sourceProviderManagerBeforeNotification)
  }
}

class GradleSourceProviderManagerTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Test
  fun selfDisposesOnFacetConfigurationChange() {
    projectRule.load(TestProjectPaths.SIMPLE_APPLICATION)
    val facet = AndroidFacet.getInstance(projectRule.project.findAppModule() )!!
    val sourceProviderManagerBeforeNotification = facet.sourceProviders
    projectRule.requestSyncAndWait()
    val sourceProviderManagerAfterNotification = facet.sourceProviders
    assertThat(sourceProviderManagerAfterNotification).isNotSameAs(sourceProviderManagerBeforeNotification)
  }
}