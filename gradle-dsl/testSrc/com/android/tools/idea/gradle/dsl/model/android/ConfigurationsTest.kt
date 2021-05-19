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
package com.android.tools.idea.gradle.dsl.model.android

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.BOOLEAN
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.annotations.SystemDependent
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ConfigurationsTest : GradleFileModelTestCase() {
  @Test
  fun testParseConfigs() {
    writeToBuildFile(TestFile.PARSE_CONFIGS)

    val buildModel = gradleBuildModel

    val configsModel = buildModel.configurations()
    val configs = configsModel.all()
    assertSize(3, configs)
    val first = configs[0]
    val second = configs[1]
    val third = configs[2]


    assertThat("goodConfig", equalTo(first.name()))
    assertMissingProperty(first.visible())
    assertMissingProperty(first.transitive())

    assertThat("compile", equalTo(second.name()))
    assertMissingProperty(second.visible())
    verifyPropertyModel(second.transitive(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0)

    assertThat("newConfig", equalTo(third.name()))
    verifyPropertyModel(third.visible(), BOOLEAN_TYPE, false, BOOLEAN, REGULAR, 0)
    assertMissingProperty(third.transitive())
  }

  @Test
  fun testParseQualifiedConfigs() {
    writeToBuildFile(TestFile.PARSE_QUALIFIED_CONFIGS)

    val buildModel = gradleBuildModel

    val configsModel = buildModel.configurations()
    val configs = configsModel.all()
    assertSize(4, configs)
    val first = configs[0]
    val second = configs[1]
    val third = configs[2]
    val fourth = configs[3]

    assertThat("badConfig", equalTo(first.name()))
    assertMissingProperty(first.transitive())
    assertMissingProperty(first.visible())

    assertThat("otherBadConfig", equalTo(second.name()))
    assertMissingProperty(second.transitive())
    verifyPropertyModel(second.visible(), BOOLEAN_TYPE, false, BOOLEAN, REGULAR, 0)

    assertThat("superBadConfig", equalTo(third.name()))
    assertMissingProperty(third.transitive())
    verifyPropertyModel(third.visible(), BOOLEAN_TYPE, false, BOOLEAN, REGULAR, 0)

    assertThat("evenWorseConfig", equalTo(fourth.name()))
    verifyPropertyModel(fourth.transitive(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0)
    assertMissingProperty(fourth.visible())
  }

  @Test
  fun testAddNewConfigFromEmpty() {
    writeToBuildFile(TestFile.ADD_NEW_CONFIG_FROM_EMPTY)

    val buildModel = gradleBuildModel

    val configsModel = buildModel.configurations()
    configsModel.addConfiguration("newConfig")
    configsModel.addConfiguration("otherNewConfig").transitive().setValue(true)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_NEW_CONFIG_FROM_EMPTY_EXPECTED)

    run {
      val configs = buildModel.configurations().all()
      assertSize(2, configs)
      val first = configs[0]
      val second = configs[1]

      assertThat("newConfig", equalTo(first.name()))
      assertMissingProperty(first.transitive())
      assertMissingProperty(first.visible())

      assertThat("otherNewConfig", equalTo(second.name()))
      verifyPropertyModel(second.transitive(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0)
      assertMissingProperty(second.visible())
    }
  }

  @Test
  fun testAddNewConfig() {
    writeToBuildFile(TestFile.ADD_NEW_CONFIG)

    val buildModel = gradleBuildModel

    run {
      val configModel = buildModel.configurations()
      val newConfig = configModel.addConfiguration("otherNewConfig")
      ReferenceTo.createReferenceFromText("var1", newConfig.visible())?.let { newConfig.visible().setValue(it) }
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_NEW_CONFIG_EXPECTED)

    run {
      val configs = buildModel.configurations().all()
      assertSize(2, configs)
      val first = configs[0]
      val second = configs[1]

      assertThat("newConfig", equalTo(first.name()))
      assertMissingProperty(first.transitive())
      assertMissingProperty(first.visible())

      assertThat("otherNewConfig", equalTo(second.name()))
      assertMissingProperty(second.transitive())
      verifyPropertyModel(second.visible(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 1)
    }

  }

  @Test
  fun testRemoveConfig() {
    writeToBuildFile(TestFile.REMOVE_CONFIG)

    val buildModel = gradleBuildModel

    run {
      val configModel = buildModel.configurations()
      val configs = configModel.all()
      assertThat(configs.map { it.name() }.toSet(), equalTo(setOf("badConfig", "worseConfig", "worstConfig")))

      assertThat("badConfig", equalTo(configs[0].name()))
      verifyPropertyModel(configs[0].transitive(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0)
      assertMissingProperty(configs[0].visible())

      assertThat("worseConfig", equalTo(configs[1].name()))
      assertMissingProperty(configs[1].transitive())
      assertMissingProperty(configs[1].visible())

      assertThat("worstConfig", equalTo(configs[2].name()))
      assertMissingProperty(configs[2].transitive())
      verifyPropertyModel(configs[2].visible(), BOOLEAN_TYPE, false, BOOLEAN, REGULAR, 0)

      configModel.removeConfiguration("badConfig")
      configModel.removeConfiguration("worseConfig")
      configModel.removeConfiguration(configs[2].name())
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, "")

    assertSize(0, buildModel.configurations().all())
  }

  @Test
  fun testRenameConfig() {
    writeToBuildFile(TestFile.RENAME_CONFIG)

    val buildModel = gradleBuildModel
    val configs = buildModel.configurations().all()
    configs.first { it.name() == "foo" }.rename("newFoo")
    configs.first { it.name() == "bar" }.rename("newBar")
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.RENAME_CONFIG_EXPECTED)
    assertThat(buildModel.configurations().all().map { it.name() }.toSet(), equalTo(setOf("newFoo", "newBar")))
  }

  private fun checkParseNonEmptyConfiguration(testFileName: TestFileName, vararg expectedConfigurations: String) {
    writeToBuildFile(testFileName)
    val buildModel = gradleBuildModel

    run {
      val configModel = buildModel.configurations()
      val configs = configModel.all()
      assertNotEmpty(configs)
      configs.forEach { config ->
        assertNotEmpty(config.declaredProperties)
      }
      assertThat(configs.map { it.name() }.toSet(), equalTo(expectedConfigurations.toSet()))
    }
  }

  @Test
  fun testParseGradleManualExample315() {
    assumeTrue("by configurations.creating not supported in KotlinScript parser", !isKotlinScript) // TODO(b/155075732)
    checkParseNonEmptyConfiguration(TestFile.MANUAL_EXAMPLE315, "smokeTest")
  }

  @Test
  fun testParseGradleManualExample319() {
    assumeTrue("implicit getByName on string configurations not supported in KotlinScript parser", !isKotlinScript) // TODO(b/143761795)
    checkParseNonEmptyConfiguration(TestFile.MANUAL_EXAMPLE319, "implementation")
  }

  @Test
  fun testParseGradleManualExample321() {
    checkParseNonEmptyConfiguration(TestFile.MANUAL_EXAMPLE321, "compileClasspath")
  }

  @Test
  fun testParseGradleManualExample345() {
    checkParseNonEmptyConfiguration(TestFile.MANUAL_EXAMPLE345, "compileClasspath")
  }

  @Test
  fun testParseGradleManualExample364() {
    checkParseNonEmptyConfiguration(TestFile.MANUAL_EXAMPLE364, "rejectConfig")
  }

  @Test
  fun testParseGradleManualExample369() {
    checkParseNonEmptyConfiguration(TestFile.MANUAL_EXAMPLE369, "pluginTool")
  }

  @Test
  fun testParseGradleManualExample377() {
    checkParseNonEmptyConfiguration(TestFile.MANUAL_EXAMPLE377, "compileClasspath", "runtimeClasspath")
  }

  @Test
  fun testParseGradleManualExample378() {
    checkParseNonEmptyConfiguration(TestFile.MANUAL_EXAMPLE378, "exposedApi", "exposedRuntime")
  }

  @Test
  fun testParseGradleFailOnVersionConflict() {
    checkParseNonEmptyConfiguration(TestFile.FAIL_ON_VERSION_CONFLICT, "implementation")
  }

  enum class TestFile(val path: @SystemDependent String) : TestFileName {
    PARSE_CONFIGS("parseConfigs"),
    PARSE_QUALIFIED_CONFIGS("parseQualifiedConfigs"),
    ADD_NEW_CONFIG_FROM_EMPTY("addNewConfigFromEmpty"),
    ADD_NEW_CONFIG_FROM_EMPTY_EXPECTED("addNewConfigFromEmptyExpected"),
    ADD_NEW_CONFIG("addNewConfig"),
    ADD_NEW_CONFIG_EXPECTED("addNewConfigExpected"),
    REMOVE_CONFIG("removeConfig"),
    RENAME_CONFIG("renameConfig"),
    RENAME_CONFIG_EXPECTED("renameConfigExpected"),
    MANUAL_EXAMPLE315("gradleManual541-ex315"),
    MANUAL_EXAMPLE319("gradleManual541-ex319"),
    MANUAL_EXAMPLE321("gradleManual541-ex321"),
    MANUAL_EXAMPLE345("gradleManual541-ex345"),
    MANUAL_EXAMPLE364("gradleManual541-ex364"),
    MANUAL_EXAMPLE369("gradleManual541-ex369"),
    MANUAL_EXAMPLE377("gradleManual541-ex377"),
    MANUAL_EXAMPLE378("gradleManual541-ex378"),
    FAIL_ON_VERSION_CONFLICT("failOnVersionConflict"),
    ;
    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/configurations/$path", extension)
    }

  }
}