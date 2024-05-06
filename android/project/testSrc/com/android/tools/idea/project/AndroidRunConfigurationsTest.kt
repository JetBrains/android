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
package com.android.tools.idea.project

import com.android.tools.deployer.model.component.ComponentType
import com.android.tools.idea.execution.common.DeployableToDevice
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.getHolderModule
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.configuration.AndroidComplicationConfigurationType
import com.android.tools.idea.run.configuration.AndroidTileConfigurationType
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfigurationType
import com.android.tools.idea.run.configuration.AndroidWearConfiguration
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.tools.idea.testing.writeChild
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.dom.manifest.UsesFeature
import org.junit.After
import org.junit.Rule
import org.junit.Test

class AndroidRunConfigurationsTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @After
  fun tearDown() {
    StudioFlags.WEAR_RUN_CONFIGS_AUTOCREATE_ENABLED.clearOverride()
    StudioFlags.WEAR_RUN_CONFIGS_AUTOCREATE_MAX_TOTAL_RUN_CONFIGS.clearOverride()
  }

  @Test
  fun `set default activity launch for simple app`() {
    val preparedProject = projectRule.prepareTestProject(testProject = AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      project.requestSyncAndWait()
      val configurationFactory = AndroidRunConfigurationType.getInstance().factory

      val configurations = RunManager.getInstance(project).getConfigurationsList(configurationFactory.type)

      assertThat(configurations).hasSize(1)
      assertThat(configurations[0]).isInstanceOf(AndroidRunConfiguration::class.java)
      assertThat((configurations[0] as AndroidRunConfiguration).MODE).isEqualTo(AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY)
    }
  }

  @Test
  fun `set default activity launch for app with activity declared not in the main module`() {
    val preparedProject = projectRule.prepareTestProject(testProject = AndroidCoreTestProject.APP_WITH_ACTIVITY_IN_LIB)
    preparedProject.open { project ->
      project.requestSyncAndWait()
      val configurationFactory = AndroidRunConfigurationType.getInstance().factory

      val configurations = RunManager.getInstance(project).getConfigurationsList(configurationFactory.type)

      assertThat(configurations).hasSize(1)
      assertThat(configurations[0]).isInstanceOf(AndroidRunConfiguration::class.java)
      assertThat((configurations[0] as AndroidRunConfiguration).MODE).isEqualTo(AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY)
    }
  }

  @Test
  fun `run configuration deploys to local device`() {
    val preparedProject = projectRule.prepareTestProject(testProject = AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      project.requestSyncAndWait()
      val configurationFactory = AndroidRunConfigurationType.getInstance().factory

      val configurations = RunManager.getInstance(project).getConfigurationsList(configurationFactory.type)

      assertThat(configurations).hasSize(1)
      assertThat(configurations[0]).isInstanceOf(AndroidRunConfiguration::class.java)
      assertThat(DeployableToDevice.deploysToLocalDevice(configurations[0])).isTrue()
    }
  }

  @Test
  fun `wear configurations get added`() {
    StudioFlags.WEAR_RUN_CONFIGS_AUTOCREATE_ENABLED.override(true)
    val preparedProject = projectRule.prepareTestProject(testProject = AndroidCoreTestProject.WEAR_WITH_TILE_COMPLICATION_AND_WATCHFACE)
    preparedProject.open { project ->
      project.requestSyncAndWait()

      val runManager = RunManager.getInstance(project)

      val watchFaceConfigurations = runManager.getConfigurationsList(AndroidWatchFaceConfigurationType())
      assertThat(watchFaceConfigurations).hasSize(1)
      watchFaceConfigurations[0].let { configuration ->
        assertThat(watchFaceConfigurations[0]).isInstanceOf(AndroidWearConfiguration::class.java)
        assertThat(configuration.name).isEqualTo("app.MyWatchFace")
        val componentLaunchOptions = (configuration as AndroidWearConfiguration).componentLaunchOptions
        assertThat(componentLaunchOptions.componentName).isEqualTo("com.example.myface.MyWatchFace")
        assertThat(componentLaunchOptions.componentType).isEqualTo(ComponentType.WATCH_FACE)
        assertThat(configuration.module).isEqualTo(project.findAppModule().getHolderModule())
      }

      val tileConfigurations = runManager.getConfigurationsList(AndroidTileConfigurationType())
      assertThat(tileConfigurations).hasSize(1)
      tileConfigurations[0].let { configuration ->
        assertThat(tileConfigurations[0]).isInstanceOf(AndroidWearConfiguration::class.java)
        assertThat(configuration.name).isEqualTo("app.MyTileService")
        val componentLaunchOptions = (configuration as AndroidWearConfiguration).componentLaunchOptions
        assertThat(componentLaunchOptions.componentName).isEqualTo("com.example.tile.MyTileService")
        assertThat(componentLaunchOptions.componentType).isEqualTo(ComponentType.TILE)
        assertThat(configuration.module).isEqualTo(project.findAppModule().getHolderModule())
      }

      val complicationConfigurations = runManager.getConfigurationsList(AndroidComplicationConfigurationType())
      assertThat(complicationConfigurations).hasSize(1)
      complicationConfigurations[0].let { configuration ->
        assertThat(complicationConfigurations[0]).isInstanceOf(AndroidWearConfiguration::class.java)
        assertThat(configuration.name).isEqualTo("app.MyComplicationService")
        val componentLaunchOptions = (configuration as AndroidWearConfiguration).componentLaunchOptions
        assertThat(componentLaunchOptions.componentName).isEqualTo("com.example.complication.MyComplicationService")
        assertThat(componentLaunchOptions.componentType).isEqualTo(ComponentType.COMPLICATION)
        assertThat(configuration.module).isEqualTo(project.findAppModule().getHolderModule())
      }
    }
  }

  @Test
  fun `wear configurations do not get added if their component is not declared in the manifest`() {
    StudioFlags.WEAR_RUN_CONFIGS_AUTOCREATE_ENABLED.override(true)
    val preparedProject = projectRule.prepareTestProject(testProject = AndroidCoreTestProject.WEAR_WITH_TILE_COMPLICATION_AND_WATCHFACE)
    preparedProject.open { project ->
      project.projectFile?.writeChild(
        "src/com/example/tile/TileServiceNotInManifest.kt",
        """
            package com.example.tile

            import androidx.wear.tiles.TileService

            class TileServiceNotInManifest : TileService()
      """.trimIndent())

      project.projectFile?.writeChild(
        "src/com/example/myface/WatchFaceNotInManifest.kt",
        """
          package com.example.myface

          import androidx.wear.watchface.WatchFaceService

          class WatchFaceNotInManifest : WatchFaceService()
      """.trimIndent())

      project.projectFile?.writeChild(
        "src/com/example/complication/ComplicationNotInManifest.kt",
        """
          package com.example.complication

          import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService

          class ComplicationNotInManifest : ComplicationDataSourceService()
      """.trimIndent())

      project.requestSyncAndWait()
      val wearComponentNames = RunManager.getInstance(project).allConfigurationsList
        .filterIsInstance<AndroidWearConfiguration>()
        .mapNotNull { it.componentLaunchOptions.componentName }
      assertThat(wearComponentNames).doesNotContain("com.example.tile.TileServiceNotInManifest")
      assertThat(wearComponentNames).doesNotContain("com.example.myface.WatchFaceNotInManifest")
      assertThat(wearComponentNames).doesNotContain("com.example.complication.ComplicationNotInManifest")
    }
  }

  @Test
  fun `wear configurations do not get added if the component is already used in a configuration`() {
    StudioFlags.WEAR_RUN_CONFIGS_AUTOCREATE_ENABLED.override(true)
    val preparedProject = projectRule.prepareTestProject(testProject = AndroidCoreTestProject.WEAR_WITH_TILE_COMPLICATION_AND_WATCHFACE)
    preparedProject.open { project ->
      val runManager = RunManager.getInstance(project)
      runManager.removeExistingRunConfigurations()
      runManager.addWearConfigurationWithComponentName<AndroidTileConfigurationType>(
        name = "ExistingTileConfig",
        componentName = "com.example.tile.MyTileService"
      )
      runManager.addWearConfigurationWithComponentName<AndroidComplicationConfigurationType>(
        name = "ExistingComplicationConfig",
        componentName = "com.example.complication.MyComplicationService"
      )
      runManager.addWearConfigurationWithComponentName<AndroidWatchFaceConfigurationType>(
        name = "ExistingWatchFaceConfig",
        componentName = "com.example.myface.MyWatchFace"
      )

      project.requestSyncAndWait()

      val configurations = runManager.allConfigurationsList.filterIsInstance<AndroidWearConfiguration>()
      assertThat(configurations.map { it.name }).containsExactly(
        "ExistingTileConfig",
        "ExistingComplicationConfig",
        "ExistingWatchFaceConfig"
      )
    }
  }

  @Test
  fun `wear configurations do not get added when flag is disabled`() {
    StudioFlags.WEAR_RUN_CONFIGS_AUTOCREATE_ENABLED.override(false)
    val preparedProject = projectRule.prepareTestProject(testProject = AndroidCoreTestProject.WEAR_WITH_TILE_COMPLICATION_AND_WATCHFACE)
    preparedProject.open { project ->
      project.requestSyncAndWait()

      assertThat(RunManager.getInstance(project).allConfigurationsList.filterIsInstance<AndroidWearConfiguration>()).isEmpty()
    }
  }

  @Test
  fun `wear configurations do not get added if they breach maximum limit`() {
    StudioFlags.WEAR_RUN_CONFIGS_AUTOCREATE_MAX_TOTAL_RUN_CONFIGS.override(2)
    val preparedProject = projectRule.prepareTestProject(testProject = AndroidCoreTestProject.WEAR_WITH_TILE_COMPLICATION_AND_WATCHFACE)
    preparedProject.open { project ->
      project.requestSyncAndWait()

      assertThat(RunManager.getInstance(project).allConfigurationsList.filterIsInstance<AndroidWearConfiguration>()).isEmpty()
    }
  }

  @Test
  fun `wear configurations do not get added if there is no required watch feature`() {
    StudioFlags.WEAR_RUN_CONFIGS_AUTOCREATE_ENABLED.override(true)
    val preparedProject = projectRule.prepareTestProject(testProject = AndroidCoreTestProject.WEAR_WITH_TILE_COMPLICATION_AND_WATCHFACE)
    preparedProject.open { project ->
      val runManager = RunManager.getInstance(project)

      removeWatchFeatureRequirement(project)
      runManager.removeExistingRunConfigurations()
      project.requestSyncAndWait()

      assertThat(runManager.allConfigurationsList.filterIsInstance<AndroidWearConfiguration>()).isEmpty()
    }
  }

  private fun removeWatchFeatureRequirement(project: Project) {
    runWriteCommandAction(project) {
      project.getAndroidFacets().forEach { facet ->
        val watchFeature = Manifest.getMainManifest(facet)?.usesFeatures?.find { it.name.value == UsesFeature.HARDWARE_TYPE_WATCH }
        watchFeature?.required?.stringValue = "false"
      }
    }
    runInEdtAndWait {
      FileDocumentManager.getInstance().saveAllDocuments()
    }
  }

  private fun RunManager.removeExistingRunConfigurations() =
    allConfigurationsList.forEach {
      removeConfiguration(findSettings(it))
    }

  private inline fun <reified T: ConfigurationType> RunManager.addWearConfigurationWithComponentName(
    name: String,
    componentName: String
  ) = createConfiguration(name, T::class.java).also { settings ->
    val configuration = settings.configuration as AndroidWearConfiguration
    configuration.componentLaunchOptions.componentName = componentName
    addConfiguration(settings)
  }
}