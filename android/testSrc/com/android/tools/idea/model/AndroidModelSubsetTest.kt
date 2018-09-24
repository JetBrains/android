/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.model

import com.android.ide.common.util.PathString
import com.android.projectmodel.ARTIFACT_NAME_ANDROID_TEST
import com.android.projectmodel.ARTIFACT_NAME_MAIN
import com.android.projectmodel.ARTIFACT_NAME_UNIT_TEST
import com.android.projectmodel.AndroidPathType
import com.android.projectmodel.AndroidSubmodule
import com.android.projectmodel.ArtifactDependency
import com.android.projectmodel.Config
import com.android.projectmodel.ExternalLibrary
import com.android.projectmodel.ProjectType
import com.android.projectmodel.SourceSet
import com.android.projectmodel.buildTable
import com.android.projectmodel.configTableSchemaWith
import com.android.projectmodel.configTableWith
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId.ANDROIDX_DATA_BINDING_LIB
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId.DATA_BINDING_LIB
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Test cases for [AndroidModelSubset].
 */
class AndroidModelSubsetTest {
  private val databindingLib = ArtifactDependency(library = ExternalLibrary(address = "foo.bar", classJars = listOf(PathString("/bin"))),
                                                  requestedMavenCoordinate = ANDROIDX_DATA_BINDING_LIB.getCoordinate("+"),
                                                  resolvedMavenCoordinate = ANDROIDX_DATA_BINDING_LIB.getCoordinate(""))

  private fun simpleConfig(onlyPath: String) = Config(sources = SourceSet(mapOf(AndroidPathType.JAVA to listOf(PathString(onlyPath)))))

  private val typicalGradleConfigTable =
    configTableSchemaWith(
      "buildType" to listOf("debug", "release")
    ).buildTable(
      null to simpleConfig("main").copy(compileDeps = listOf(databindingLib)),
      "debug" to simpleConfig("debug"),
      "release" to simpleConfig("release"),
      ARTIFACT_NAME_MAIN to simpleConfig("app")
    )

  private val typicalGradleModel = com.android.projectmodel.AndroidModel(listOf(
    AndroidSubmodule(
      name = "myProject",
      type = ProjectType.APP,
      variants = typicalGradleConfigTable.generateVariants(),
      configTable = typicalGradleConfigTable
    )
  ))

  private fun typicalBlazeTargetModel(targetName: String, extraCompileDeps: List<ArtifactDependency> = listOf()): AndroidSubmodule {
    val configTable = configTableWith(simpleConfig("src").copy(compileDeps = extraCompileDeps, applicationIdSuffix = targetName))
    return AndroidSubmodule(
      name = targetName,
      type = ProjectType.APP
    ).withVariantsGeneratedBy(configTable)
  }

  private val typicalBlazeModel = com.android.projectmodel.AndroidModel(listOf(
    typicalBlazeTargetModel("app", listOf(databindingLib)),
    typicalBlazeTargetModel("tests"),
    typicalBlazeTargetModel("resources")
  ))

  @Test
  fun testGradleModelOneVariantSelected() {
    val selection = selectVariant(typicalGradleModel, "release")

    assertThat(selection.firstMainArtifact()!!.artifact.resolved.sources.javaDirectories).containsExactly(PathString("main"),
                                                                                                          PathString("release"),
                                                                                                          PathString("app"))
    assertThat(selection.selectedArtifacts().toList().map { it.artifact.name }).containsExactly(ARTIFACT_NAME_MAIN, ARTIFACT_NAME_UNIT_TEST,
                                                                                                ARTIFACT_NAME_ANDROID_TEST)
    assertThat(selection.selectedArtifacts(ARTIFACT_NAME_MAIN).toList().map { it.artifact.name }).containsExactly(ARTIFACT_NAME_MAIN)
    assertThat(selection.selectedConfigs(ARTIFACT_NAME_UNIT_TEST).toList().map { it.path.simpleName }).containsExactly("main", "release")
    assertThat(selection.selectedConfigs().toList().map { it.path.simpleName }).containsExactly("main", "release", ARTIFACT_NAME_MAIN)
    assertThat(selection.selectedVariants().toList().map { it.variant.configPath.simpleName }).containsExactly("release")
    assertThat(selection.dependsOn(ANDROIDX_DATA_BINDING_LIB.getCoordinate("+"))).isTrue()
    assertThat(selection.dependsOn(ANDROIDX_DATA_BINDING_LIB.getCoordinate(""))).isTrue()
    assertThat(selection.dependsOn(DATA_BINDING_LIB.getCoordinate("+"))).isFalse()
  }

  @Test
  fun testBlazeModelAllVariantsSelected() {
    val selection = selectAllVariants(typicalBlazeModel)

    assertThat(selection.firstMainArtifact()!!.artifact.resolved.sources.javaDirectories).containsExactly(PathString("src"))
    assertThat(selection.selectedArtifacts(ARTIFACT_NAME_MAIN).toList().map { it.submodule.name })
      .containsExactly("app", "tests", "resources")
    assertThat(selection.selectedConfigs(ARTIFACT_NAME_UNIT_TEST).toList().map { it.path.simpleName }).isEmpty()
    assertThat(selection.selectedConfigs().toList().map { it.config.applicationIdSuffix }).containsExactly("app", "tests", "resources")
    assertThat(selection.selectedVariants().toList().map { it.variant.mainArtifact.resolved.applicationIdSuffix })
      .containsExactly("app", "tests", "resources")
    assertThat(selection.dependsOn(ANDROIDX_DATA_BINDING_LIB.getCoordinate("+"))).isTrue()
    assertThat(selection.dependsOn(DATA_BINDING_LIB.getCoordinate("+"))).isFalse()
  }
}