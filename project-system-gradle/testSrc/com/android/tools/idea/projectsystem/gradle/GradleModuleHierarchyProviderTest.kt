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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTestDef
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_MODELS_UPDATED_TOPIC
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findModuleByFullName
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import java.io.File
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleSettings

data class GradleModuleHierarchyProviderTest(
  override val name: String,
  override val testProject: TestProject,
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
  val test: (Project) -> Unit,
) : SyncedProjectTestDef {

  companion object {
    val tests: List<GradleModuleHierarchyProviderTest> =
      listOf(
        GradleModuleHierarchyProviderTest(name = "testCompositeStructure", TestProject.COMPOSITE_BUILD) { project ->
          val isPhasedSyncEnabled = StudioFlags.PHASED_SYNC_ENABLED.get()
          val projectGradleVersion =
            GradleSettings.getInstance(project).linkedProjectsSettings.single().let {
              GradleInstallationManager.guessGradleVersion(it) ?: GradleVersion.current()
            }
          val parentNameNeeded = GradleVersionUtil.isGradleAtLeast(projectGradleVersion, "8.0")
          val expectedModuleNames =
            listOf("project.app", "project.lib", "TestCompositeLib3") +
              if (isPhasedSyncEnabled) {
                listOf(
                  "includedLib1",
                  "TestCompositeLib2",
                  "TestCompositeLib4",
                  if (parentNameNeeded) "includedLib1.TestCompositeLibNested_1" else "TestCompositeLibNested_1",
                  if (parentNameNeeded) "TestCompositeLib3.TestCompositeLibNested_3" else "TestCompositeLibNested_3",
                )
              } else {
                listOf("TestCompositeLib1", "composite2", "composite4", "compositeNest", "com.test.compositeNest3.compositeNest")
              }
          val expectedModules = expectedModuleNames.map { project.findModuleByFullName(it) }
          val provider = GradleModuleHierarchyProvider(project)
          assertThat(provider.forProject.submodules).containsExactlyElementsIn(expectedModules)
        },
        GradleModuleHierarchyProviderTest(name = "testUsualStructure", TestProject.SIMPLE_APPLICATION) { project ->
          val provider = GradleModuleHierarchyProvider(project)
          val app = project.findAppModule()
          assertThat(provider.forProject.submodules).containsExactly(app)
        },
        GradleModuleHierarchyProviderTest(name = "testFirstSyncFailedStructure", TestProject.SIMPLE_APPLICATION_SYNC_FAILED) { project ->
          TruthJUnit.assume().that(ModuleManager.getInstance(project).modules).asList().hasSize(1)
          val provider = GradleModuleHierarchyProvider(project)
          // This case is handled by the AndroidViewProjectNode directly.
          assertThat(provider.forProject.submodules).isEmpty()
        },
        GradleModuleHierarchyProviderTest(name = "testCacheIsResetOnModelsUpdated", TestProject.SIMPLE_APPLICATION) { project ->
          val provider = GradleModuleHierarchyProvider.getInstance(project)
          val app = project.findAppModule()
          val unitTest = ModuleManager.getInstance(project).modules.find { it.name.endsWith(".unitTest") }!!

          // Initial state: unitTest is linked and thus NOT a submodule of app.
          assertThat(provider.createForModule(app).submodules).isEmpty()

          // Break linkage for unitTest
          val originalData = unitTest.getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP)
          unitTest.putUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP, null)

          // Fire topic - this should trigger reset and rebuild.
          project.messageBus.syncPublisher(PROJECT_SYSTEM_MODELS_UPDATED_TOPIC).androidModelsUpdated()

          // unitTest should now appear as a submodule of app (because it is no longer linked and shares the gradle path).
          assertThat(provider.createForModule(app).submodules).contains(unitTest)

          // Restore linkage
          unitTest.putUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP, originalData)

          // Fire topic again
          project.messageBus.syncPublisher(PROJECT_SYSTEM_MODELS_UPDATED_TOPIC).androidModelsUpdated()

          // unitTest should be excluded again.
          assertThat(provider.createForModule(app).submodules).isEmpty()
        },
      )
  }

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): SyncedProjectTestDef {
    return copy(agpVersion = agpVersion)
  }

  override fun runTest(root: File, project: Project) {
    test(project)
  }
}
