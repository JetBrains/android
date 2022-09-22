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
package com.android.tools.idea.logcat

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.logcat.ProjectApplicationIdsProvider.Companion.PROJECT_APPLICATION_IDS_CHANGED_TOPIC
import com.android.tools.idea.logcat.ProjectApplicationIdsProvider.ProjectApplicationIdsListener
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.TestAndroidModel
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult.SUCCESS
import com.android.tools.idea.testing.registerServiceInstance
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.ProjectFacetManager
import com.intellij.mock.MockModule
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetConfiguration
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [ProjectApplicationIdsProviderImpl]
 */
class ProjectApplicationIdsProviderImplTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule = RuleChain(projectRule, disposableRule)

  private val project get() = projectRule.project
  private val mockProjectFacetManager = mock<ProjectFacetManager>()

  @Before
  fun setUp() {
    project.registerServiceInstance(ProjectFacetManager::class.java, mockProjectFacetManager, disposableRule.disposable)
  }

  @Test
  fun initialize_loadsApplicationIds() {
    val facet1 = mockFacet("app1")
    val facet2 = mockFacet("app2", "app3")
    whenever(mockProjectFacetManager.getFacets(AndroidFacet.ID)).thenReturn(listOf(facet1, facet2))

    val projectApplicationIdsProviderImpl = ProjectApplicationIdsProviderImpl(project)

    assertThat(projectApplicationIdsProviderImpl.getPackageNames()).containsExactly(
      "app1",
      "app2",
      "app3",
    )
  }

  @Test
  fun syncEnds_reloadsApplicationIds() {
    val facet1 = mockFacet("app1")
    whenever(mockProjectFacetManager.getFacets(AndroidFacet.ID)).thenReturn(listOf(facet1))
    val projectApplicationIdsProviderImpl = ProjectApplicationIdsProviderImpl(project)

    val facet2 = mockFacet("app2", "app3")
    whenever(mockProjectFacetManager.getFacets(AndroidFacet.ID)).thenReturn(listOf(facet1, facet2))
    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SUCCESS)

    assertThat(projectApplicationIdsProviderImpl.getPackageNames()).containsExactly(
      "app1",
      "app2",
      "app3",
    )
  }

  @Test
  fun syncEnds_applicationIdsChange_notifies() {
    val facet1 = mockFacet("app1")
    whenever(mockProjectFacetManager.getFacets(AndroidFacet.ID)).thenReturn(listOf(facet1))
    ProjectApplicationIdsProviderImpl(project)
    var notified = false
    project.messageBus.connect(disposableRule.disposable).subscribe(PROJECT_APPLICATION_IDS_CHANGED_TOPIC, ProjectApplicationIdsListener {
      notified = true
    })

    val facet2 = mockFacet("app2")
    whenever(mockProjectFacetManager.getFacets(AndroidFacet.ID)).thenReturn(listOf(facet2))
    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SUCCESS)

    assertThat(notified).isTrue()
  }

  @Test
  fun syncEnds_applicationIdsDoNotChange_doesNotNotifies() {
    val facet1 = mockFacet("app1")
    whenever(mockProjectFacetManager.getFacets(AndroidFacet.ID)).thenReturn(listOf(facet1))
    ProjectApplicationIdsProviderImpl(project)
    var notified = false
    project.messageBus.connect(disposableRule.disposable).subscribe(PROJECT_APPLICATION_IDS_CHANGED_TOPIC, ProjectApplicationIdsListener {
      notified = true
    })

    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SUCCESS)

    assertThat(notified).isFalse()
  }

  private fun mockFacet(vararg applicationIds: String): AndroidFacet {
    val facet = AndroidFacet(
      MockModule(disposableRule.disposable),
      "ProjectApplicationIdsProviderImplTest:Facet",
      AndroidFacetConfiguration()
    )
    Disposer.register(disposableRule.disposable, facet)
    AndroidModel.set(facet, TestAndroidModel(allApplicationIds = applicationIds.toSet()))
    return facet
  }
}