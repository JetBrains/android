/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.runsIndexingWithGradlePhasedSync

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.entities.GradleAndroidModelEntity
import com.android.tools.idea.gradle.project.entities.GradleModuleModelEntity
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject.Companion.openTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.flags.overrideForTest
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.utils.combineAsCamelCase
import com.intellij.java.impl.dependencySubstitution.LibraryMavenCoordinateEntity
import com.intellij.java.impl.dependencySubstitution.ModuleMavenCoordinateEntity
import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.java.workspace.entities.JavaProjectSettingsEntity
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.TestModulePropertiesEntity
import com.intellij.platform.workspace.jps.entities.contentRoot
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.impl.VersionedStorageChangeInternal
import com.intellij.util.messages.MessageBusConnection
import com.intellij.workspaceModel.ide.toPath
import kotlin.io.path.Path
import kotlin.io.path.relativeToOrSelf
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A snapshot test that captures and compares the [WorkspaceModel] changes that occur during a Gradle phased sync by listening to
 * [WorkspaceModelTopics.CHANGED] events and associates each change with its phase.
 *
 * These changes are undesired in a re-sync causing performance issues and excessive re-processing. This test mainly exists to detect these
 * undesired changes and reduce the number of them to avoid needless processing.
 *
 * This test is expected to be brittle around platform changes but the entire point of its existence to avoid regressing the state captured
 * by it.
 */
@Suppress("UnstableApiUsage")
@RunWith(Parameterized::class)
class WorkspaceModelChangesPerPhaseSnapshotTest(val testProject: TestProject) : SnapshotComparisonTest {
  @get:Rule val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/wsmChangedEvents/resync"

  override fun getName() = testProject.name.lowercase().split("_").combineAsCamelCase()

  @Before
  fun disableDependencyResolutionPhase() { // Disable this for now as we're not optimizing dependency resolution yet
    StudioFlags.PHASED_SYNC_DEPENDENCY_RESOLUTION_ENABLED.overrideForTest(false, projectRule.testRootDisposable)
  }

  sealed class Change {
    data class WorkspaceModelChange(val event: VersionedStorageChange) : Change()

    data class RootsChanged(val event: ModuleRootEvent) : Change()
  }

  @Test
  fun wsmChangedEventsForResync() {
    val workspaceModelChangeEvents = mutableListOf<Pair<Change, GradleSyncPhase?>>()
    projectRule.openTestProject(testProject) { project ->
      setUpPhaseAndWorkspaceModelListener(project.messageBus.connect(), workspaceModelChangeEvents)
      project.requestSyncAndWait()
      assertIsEqualToSnapshot(dump(workspaceModelChangeEvents))
    }
  }

  private fun dump(workspaceModelChangeEvents: MutableList<Pair<Change, GradleSyncPhase?>>): String {
    var previousRootsChangedEvent: Change? = null
    var wsmIndex = 1
    return workspaceModelChangeEvents
      .joinToString("\n") { eventWithPhase ->
        val (event, phase) = eventWithPhase
        buildString {
          when (event) {
            // Roots change events must come before a WSM change event.
            is Change.RootsChanged -> previousRootsChangedEvent = event
            is Change.WorkspaceModelChange -> {
              // First dump all the known classes
              appendLine("Workspace model changed #${wsmIndex++}, phase: ${phase?.name ?: "N/A"}")
              if (previousRootsChangedEvent is Change.RootsChanged) {
                append("    Roots changed")
                if ((event.event as VersionedStorageChangeInternal).getAllChanges().toList().isEmpty()) {
                  appendLine(" (special batch for previous workspace model change)")
                } else {
                  appendLine()
                }
                previousRootsChangedEvent = null
              }
              entityDumpers.forEach {
                val dump = it.dump(event.event, testProject)
                if (dump.isNotBlank()) {
                  appendLine(dump)
                }
              }
              // Then the remaining unknown classes.
              dumpUnknownClasses(event.event)
            }
          }
        }
      }
      .apply {
        check(previousRootsChangedEvent == null) { "Unexpected state, roots change events shouldn't be the final entry in events." }
      }
      .lines()
      .filter { it.isNotEmpty() }
      .joinToString("\n")
  }

  private fun StringBuilder.dumpUnknownClasses(event: VersionedStorageChange) {
    // Need to use internal API to be able to get the unknown classes, but it's crucial for this test
    (event as VersionedStorageChangeInternal)
      .getAllChanges()
      .map { change ->
        val entity =
          when (change) {
            is EntityChange.Removed -> change.oldEntity
            is EntityChange.Replaced -> change.oldEntity
            is EntityChange.Added -> change.newEntity
          }
        change to entity.getEntityInterface()
      }
      .sortedBy { (change, entityClazz) -> change.toString() }
      .filter { (change, entityClazz) -> !knownClasses.contains(entityClazz) }
      .forEach { (change, entityClazz) -> appendLine("    $change") }
  }

  private fun setUpPhaseAndWorkspaceModelListener(
    connection: MessageBusConnection,
    workspaceModelChangeEvents: MutableList<Pair<Change, GradleSyncPhase?>>,
  ) {
    var currentPhase: GradleSyncPhase? = null
    GradleSyncExtension.EP_NAME.point.registerExtension(
      object : GradleSyncExtension {
        override fun updateProjectModel(
          context: ProjectResolverContext,
          syncStorage: MutableEntityStorage,
          projectStorage: MutableEntityStorage,
          phase: GradleSyncPhase,
        ) {
          currentPhase = phase
        }
      },
      projectRule.testRootDisposable,
    )
    connection.subscribe(
      WorkspaceModelTopics.CHANGED,
      object : WorkspaceModelChangeListener {
        override fun changed(event: VersionedStorageChange) {
          workspaceModelChangeEvents.add(Change.WorkspaceModelChange(event) to currentPhase)
          currentPhase = null // There is only one workspace update per phase
        }
      },
    )
    connection.subscribe(
      ModuleRootListener.TOPIC,
      object : ModuleRootListener {
        override fun rootsChanged(event: ModuleRootEvent) {
          workspaceModelChangeEvents.add(Change.RootsChanged(event) to currentPhase)
        }
      },
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testParameters(): Collection<*> =
      listOf(
        TestProject.SIMPLE_APPLICATION,
        TestProject.PURE_JAVA_PROJECT,
        TestProject.KOTLIN_KAPT,
        TestProject.WITH_GRADLE_METADATA,
        TestProject.BUILD_CONFIG_AS_BYTECODE_ENABLED,
        TestProject.MULTI_FLAVOR,
        TestProject.COMPOSITE_BUILD,
      )

    private val entityDumpers =
      listOf(
        EntityChangeDumper(ModuleEntity::class.java) {
          if (it.exModuleOptions?.externalSystem == GradleConstants.SYSTEM_ID.id) {
            EntityChangeDumper.entityToString(it)
          } else {
            "ModuleEntity for non-Gradle module, name: <omitted_for_snapshot_consistency>"
          }
        },
        EntityChangeDumper(ContentRootEntity::class.java) { contentRootEntity ->
          if (contentRootEntity.module.exModuleOptions?.externalSystem == GradleConstants.SYSTEM_ID.id) {
            // Try to relativize the URL to the root project path
            val url =
              contentRootEntity.module.exModuleOptions?.rootProjectPath?.let { contentRootEntity.url.toPath().relativeToOrSelf(Path(it)) }
                ?: contentRootEntity.url.toPath()
            if (url.toString().isEmpty()) {
              "ContentRootEntity url: <empty>"
            } else {
              "ContentRootEntity url: $url"
            }
          } else {
            "ContentRootEntity for non-Gradle module url: <omitted_for_snapshot_consistency>"
          }
        },
        EntityChangeDumper(ExcludeUrlEntity::class.java) { excludeUrlEntity ->
          if (excludeUrlEntity.contentRoot?.module?.exModuleOptions?.externalSystem == GradleConstants.SYSTEM_ID.id) {
            // Try to relativize the URL to the root project path
            val url =
              excludeUrlEntity.contentRoot?.module?.exModuleOptions?.rootProjectPath?.let {
                excludeUrlEntity.url.toPath().relativeToOrSelf(Path(it))
              } ?: excludeUrlEntity.url.toPath()
            if (url.toString().isEmpty()) {
              "ExcludeUrlEntity url: <empty>"
            } else {
              "ExcludeUrlEntity url: $url"
            }
          } else {
            "ExcludeUrlEntity for non-Gradle module, url: <omitted_for_snapshot_consistency>"
          }
        },
        EntityChangeDumper(LibraryEntity::class.java) {
          buildString {
            append("LibraryEntity: ${it.name}")
            when (val tableId = it.tableId) {
              is LibraryTableId.ModuleLibraryTableId -> append(" (module level library for ${tableId.moduleId})")

              is LibraryTableId.GlobalLibraryTableId -> append(" (global library)")
              else -> {}
            }
          }
        },
        EntityChangeDumper(
          GradleAndroidModelEntity::class.java,
          entityToString = { "GradleAndroidModelEntity for ${it.module.name}" },
          // TODO(b/384022658): In this project specifically, two different gradle projects can end up with the same module name
          //  (depends on the execution ordering of something TBD), meaning a re-sync might end up shuffling the GradleAndroidModel
          //  entity around. Filtering this case out to avoid test flakiness.
          projectFilter = { it != TestProject.COMPOSITE_BUILD },
        ),
        EntityChangeDumper(FacetEntity::class.java),
        EntityChangeDumper(TestModulePropertiesEntity::class.java) { "TestModulePropertiesEntity for ${it.module.name}" },
        EntityChangeDumper(ModuleMavenCoordinateEntity::class.java) { "ModuleMavenCoordinateEntity for ${it.module.name}" },
        EntityChangeDumper(JavaModuleSettingsEntity::class.java) { "JavaModuleSettingsEntity for ${it.module.name}" },
        EntityChangeDumper(GradleModuleModelEntity::class.java) { "GradleModuleModelEntity for ${it.module.name}" },
        EntityChangeDumper(LibraryMavenCoordinateEntity::class.java) { "LibraryMavenCoordinateEntity for ${it.library.name}" },
        EntityChangeDumper(JavaProjectSettingsEntity::class.java) { "JavaProjectSettingsEntity for the IDE project" },
      )
    private val knownClasses = entityDumpers.map { it.clazz }.toSet()
  }
}

/** Wrapper class for conveniently dumping various types of entity changes. */
private class EntityChangeDumper<out T : WorkspaceEntity>(
  val clazz: Class<out T>,
  private val projectFilter: (TestProject) -> Boolean = { true },
  private val entityToString: (T) -> String = Companion.entityToString,
) {
  fun dump(event: VersionedStorageChange, testProject: TestProject): String {
    return event.getChanges(clazz).filter { projectFilter(testProject) }.sortedBy(::toString).joinToString("\n", transform = ::toString)
  }

  private fun toString(change: EntityChange<out T>): String =
    when (change) {
      is EntityChange.Removed -> "    Removed ${entityToString(change.oldEntity)}"
      is EntityChange.Replaced ->
        """
        |    Replaced: ${entityToString(change.oldEntity)}
        |    With:     ${entityToString(change.newEntity)}
        """
          .trimMargin()
      is EntityChange.Added -> "    Added ${entityToString(change.newEntity)}"
    }

  companion object {
    val entityToString: (WorkspaceEntity) -> String = {
      buildString {
        append(it.getEntityInterface().simpleName)
        if (it is WorkspaceEntityWithSymbolicId) {
          append(" for ${it.symbolicId}")
        }
        appendLine()
      }
    }
  }
}
