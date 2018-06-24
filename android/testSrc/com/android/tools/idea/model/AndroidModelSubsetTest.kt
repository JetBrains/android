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
import com.android.projectmodel.*
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId.ANDROIDX_DATA_BINDING_LIB
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId.DATA_BINDING_LIB
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Test cases for [AndroidModelSubset].
 */
class AndroidModelSubsetTest {
  private val databindingLib = ArtifactDependency(library = JavaLibrary("foo.bar", PathString("/bin")),
                                                  requestedMavenCoordinate = ANDROIDX_DATA_BINDING_LIB.getCoordinate("+"),
                                                  resolvedMavenCoordinate = ANDROIDX_DATA_BINDING_LIB.getCoordinate(""))

  private fun simpleConfig(onlyPath: String) = Config(sources = SourceSet(mapOf(AndroidPathType.JAVA to listOf(PathString(onlyPath)))))

  private val typicalGradleConfigTable = configTableWith(
    configTableSchemaWith(
      "buildType" to listOf("debug", "release")
    ),
    mapOf(null to simpleConfig("main").copy(compileDeps = listOf(databindingLib)),
          "debug" to simpleConfig("debug"),
          "release" to simpleConfig("release"),
          ARTIFACT_NAME_MAIN to simpleConfig("app"))
  )

  private val typicalGradleModel = com.android.projectmodel.AndroidModel(listOf(
    AndroidProject(
      name = "myProject",
      type = ProjectType.APP,
      variants = typicalGradleConfigTable.generateVariants(),
      configTable = typicalGradleConfigTable
    )
  ))

  private fun typicalBlazeConfigTable(targetName: String, extraCompileDeps: List<ArtifactDependency>): ConfigTable =
    configTableWith(
      configTableSchemaWith("target" to listOf(targetName)),
      mapOf(targetName to simpleConfig("src").copy(compileDeps = extraCompileDeps))
    )

  private fun typicalBlazeProjectModel(targetName: String, extraCompileDeps: List<ArtifactDependency> = listOf()): AndroidProject {
    val configTable = typicalBlazeConfigTable(targetName, extraCompileDeps)
    return AndroidProject(
      name = targetName,
      type = ProjectType.APP,
      variants = configTable.generateVariants(),
      configTable = configTable
    )
  }

  private val typicalBlazeModel = com.android.projectmodel.AndroidModel(listOf(
    typicalBlazeProjectModel("app", listOf(databindingLib)),
    typicalBlazeProjectModel("tests"),
    typicalBlazeProjectModel("resources")
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
    assertThat(selection.selectedArtifacts(ARTIFACT_NAME_MAIN).toList().map { it.variant.configPath.simpleName })
      .containsExactly("app", "tests", "resources")
    assertThat(selection.selectedConfigs(ARTIFACT_NAME_UNIT_TEST).toList().map { it.path.simpleName })
      .containsExactly("app", "tests", "resources")
    assertThat(selection.selectedConfigs().toList().map { it.path.simpleName }).containsExactly("app", "tests", "resources")
    assertThat(selection.selectedVariants().toList().map { it.variant.configPath.simpleName }).containsExactly("app", "tests", "resources")
    assertThat(selection.dependsOn(ANDROIDX_DATA_BINDING_LIB.getCoordinate("+"))).isTrue()
    assertThat(selection.dependsOn(DATA_BINDING_LIB.getCoordinate("+"))).isFalse()
  }
}