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
package com.android.tools.idea.projectsystem

import com.android.tools.idea.concurrency.runWriteActionAndWait
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.TestAndroidModel
import com.android.tools.idea.projectsystem.ProjectApplicationIdsProvider.Companion.PROJECT_APPLICATION_IDS_CHANGED_TOPIC
import com.android.tools.idea.projectsystem.ProjectApplicationIdsProvider.ProjectApplicationIdsListener
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult.SUCCESS
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.modifyModules
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetConfiguration
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


  @Test
  fun initialize_loadsApplicationIds(): Unit = runBlocking {
    createFacet(projectRule.module, "app1")
    val module2 = runWriteActionAndWait {
      projectRule.project.modifyModules {
        newModule("", "module-type")
      }
    }
    createFacet(module2, "app2", "app3")

    val projectApplicationIdsProviderImpl = ProjectApplicationIdsProviderImpl(project)

    assertThat(projectApplicationIdsProviderImpl.getPackageNames()).containsExactly(
      "app1",
      "app2",
      "app3",
    )
  }

  @Test
  fun syncEnds_reloadsApplicationIds(): Unit = runBlocking {
    createFacet(projectRule.module, "app1")

    val projectApplicationIdsProviderImpl = ProjectApplicationIdsProviderImpl(project)

    val module2 = runWriteActionAndWait {
      projectRule.project.modifyModules {
        newModule("", "module-type")
      }
    }
    createFacet(module2, "app2", "app3")
    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SUCCESS)

    assertThat(projectApplicationIdsProviderImpl.getPackageNames()).containsExactly(
      "app1",
      "app2",
      "app3",
    )
  }

  @Test
  fun syncEnds_applicationIdsChange_notifies(): Unit = runBlocking  {
    createFacet(projectRule.module, "app1")

    ProjectApplicationIdsProviderImpl(project)
    var notified = false
    project.messageBus.connect(disposableRule.disposable).subscribe(PROJECT_APPLICATION_IDS_CHANGED_TOPIC, ProjectApplicationIdsListener {
      notified = true
    })

    val module2 = runWriteActionAndWait {
      projectRule.project.modifyModules {
        newModule("", "module-type")
      }
    }
    createFacet(module2, "app2")
    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SUCCESS)

    assertThat(notified).isTrue()
  }

  @Test
  fun syncEnds_applicationIdsDoNotChange_doesNotNotifies(): Unit = runBlocking  {
    createFacet(projectRule.module, "app1")
    ProjectApplicationIdsProviderImpl(project)
    var notified = false
    project.messageBus.connect(disposableRule.disposable).subscribe(PROJECT_APPLICATION_IDS_CHANGED_TOPIC, ProjectApplicationIdsListener {
      notified = true
    })

    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SUCCESS)

    assertThat(notified).isFalse()
  }

  private fun createFacet(module: Module, vararg applicationIds: String): AndroidFacet  = runBlocking {

    val facet = AndroidFacet(
      module,
      "ProjectApplicationIdsProviderImplTest:Facet",
      AndroidFacetConfiguration()
    )
    runWriteActionAndWait {
      FacetManager.getInstance(module).createModifiableModel().apply {
        addFacet(facet)
        commit()
      }
    }
    Disposer.register(disposableRule.disposable, facet)
    AndroidModel.setForTests(facet, TestAndroidModel(allApplicationIds = applicationIds.toSet()))
    facet
  }
}