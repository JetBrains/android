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
package com.android.tools.idea.res

import com.android.testutils.waitForCondition
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleGroupPathEntity
import com.intellij.platform.workspace.jps.entities.groupPath
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleGroupPathEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.android.facet.ResourceFolderManager
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

@RunWith(JUnit4::class)
class AndroidProjectRootListenerTest {
  @get:Rule val androidProjectRule = AndroidProjectRule.inMemory()

  private val project by lazy { androidProjectRule.project }
  private val module by lazy { androidProjectRule.fixture.module }

  private var checkForChangesInvocations = 0

  @Before
  fun setUp() {
    val mockResourceFolderManager: ResourceFolderManager = mock {
      on { checkForChanges() } doAnswer
        {
          checkForChangesInvocations++
          Unit
        }
    }
    module.registerOrReplaceServiceInstance(
      ResourceFolderManager::class.java,
      mockResourceFolderManager,
      androidProjectRule.testRootDisposable,
    )
  }

  @Test
  fun updateAfterSync() {
    // Register AndroidProjectRootListener and ensure it doesn't trigger updates as it starts.
    AndroidProjectRootListener.ensureSubscribed(project)
    assertThat(checkForChangesInvocations).isEqualTo(0)

    // Simulate sync finishing.
    ApplicationManager.getApplication().invokeAndWait {
      project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(ProjectSystemSyncManager.SyncResult.SUCCESS)
    }

    // Wait for the event queue to clear out, and verify the ResourceFolderManager was updated.
    waitForCondition(10.seconds) { checkForChangesInvocations == 1 }
  }

  @Test
  fun updateAfterProjectRootsChange() {
    // Register AndroidProjectRootListener and ensure it doesn't trigger updates as it starts.
    AndroidProjectRootListener.ensureSubscribed(project)
    assertThat(checkForChangesInvocations).isEqualTo(0)

    // Simulate roots changing.
    ApplicationManager.getApplication().invokeAndWait {
      // Replacing the current AndroidProjectSystem with itself is enough to trigger the relevant
      // events for this test.
      val projectSystemService = ProjectSystemService.getInstance(project)
      projectSystemService.replaceProjectSystemForTests(projectSystemService.projectSystem)
    }

    // Wait for the event queue to clear out, and verify the ResourceFolderManager was updated.
    waitForCondition(10.seconds) { checkForChangesInvocations == 1 }
  }

  @Test
  fun updateAfterContentRootAdded() {
    // JetBrains: a new content root must still update the resources after the entity type check (RIDER-144420).
    // Register AndroidProjectRootListener and ensure it doesn't trigger updates as it starts.
    AndroidProjectRootListener.ensureSubscribed(project)
    assertThat(checkForChangesInvocations).isEqualTo(0)

    // Add a content root. The workspace model gets a new ContentRootEntity.
    val contentRoot = androidProjectRule.fixture.tempDirFixture.findOrCreateDir("extraContentRoot")
    ApplicationManager.getApplication().invokeAndWait { PsiTestUtil.addContentRoot(module, contentRoot) }

    // Wait for the event queue to clear out, and verify the ResourceFolderManager was updated.
    waitForCondition(10.seconds) { checkForChangesInvocations == 1 }
  }

  @Test
  fun noUpdateAfterUnrelatedWorkspaceModelChange() {
    // JetBrains: reproduces RIDER-144420. A change of an entity that the resources do not read must not update them.
    // Rider registers a WorkspaceFileIndexContributor for its project model entities, so each change of them fires rootsChanged.
    // A contributor for ModuleGroupPathEntity makes a change of the group path fire rootsChanged in the same way.
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(
      object : WorkspaceFileIndexContributor<ModuleGroupPathEntity> {
        override val entityClass: Class<ModuleGroupPathEntity> = ModuleGroupPathEntity::class.java

        override fun registerFileSets(entity: ModuleGroupPathEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) = Unit
      },
      androidProjectRule.testRootDisposable,
    )
    AndroidProjectRootListener.ensureSubscribed(project)

    // Add a group path to the module. This also changes the ModuleEntity, so one update is expected.
    updateProjectModel("AndroidProjectRootListenerTest: add group path") { builder ->
      val moduleEntity = module.findModuleEntity(builder)!!
      builder.modifyModuleEntity(moduleEntity) { groupPath = ModuleGroupPathEntity(listOf("group"), moduleEntity.entitySource) }
    }
    waitForCondition(10.seconds) { checkForChangesInvocations == 1 }

    // Change only the group path. The ModuleEntity stays the same, so no update must come within 2 seconds.
    updateProjectModel("AndroidProjectRootListenerTest: change group path") { builder ->
      val groupPathEntity = module.findModuleEntity(builder)!!.groupPath!!
      builder.modifyModuleGroupPathEntity(groupPathEntity) { path = mutableListOf("otherGroup") }
    }
    assertThrows(TimeoutException::class.java) { waitForCondition(2.seconds) { checkForChangesInvocations > 1 } }
  }

  private fun updateProjectModel(description: String, updater: (MutableEntityStorage) -> Unit) {
    WriteAction.runAndWait<Throwable> { WorkspaceModel.getInstance(project).updateProjectModel(description, updater) }
  }
}
