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

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.BOOLEAN
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class ConfigurationsTest : GradleFileModelTestCase() {
  @Test
  fun testParseConfigs() {
    val text = """
               configurations {
                 goodConfig

                 compile.transitive = true
                 newConfig.visible = false
               }""".trimIndent()
    writeToBuildFile(text)

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
    /**
     * Note: Gradle will only allow the use of superBadConfig from here. But we detect them all anyway.
     */
    val text = """
               configurations.badConfig
               configurations.otherBadConfig.visible = false
               configurations {
                 superBadConfig.visible = false
               }
               configurations.evenWorseConfig.transitive = true
               """.trimIndent()
    writeToBuildFile(text)

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
    val text = """
               android {
                 buildToolsVersion = "23.0.0"
                 compileSdkVersion = "android-23"
                 defaultPublishConfig = "debug"
                 generatePureSplits = true
               }

               dependencies {
                 runtime group: 'org.gradle.test.classifiers', name: 'service', version: '1.0', classifier: 'jdk14', ext: 'jar'
               }
               """.trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    val configsModel = buildModel.configurations()
    configsModel.addConfiguration("newConfig")
    configsModel.addConfiguration("otherNewConfig").transitive().setValue(true)

    applyChangesAndReparse(buildModel)

    val expected = """
                   android {
                     buildToolsVersion = "23.0.0"
                     compileSdkVersion = "android-23"
                     defaultPublishConfig = "debug"
                     generatePureSplits = true
                   }

                   configurations {
                     newConfig {
                     }
                     otherNewConfig {
                       transitive = true
                     }
                  }

                  dependencies {
                    runtime group: 'org.gradle.test.classifiers', name: 'service', version: '1.0', classifier: 'jdk14', ext: 'jar'
                  }""".trimIndent()
    verifyFileContents(myBuildFile, expected)

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
    val text = """
               ext {
                 var1 = true
                 var2 = false
               }
               configurations {
                 newConfig
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val configModel = buildModel.configurations()
      val newConfig =configModel.addConfiguration("otherNewConfig")
      newConfig.visible().setValue(ReferenceTo("var1"))
    }

    applyChangesAndReparse(buildModel)

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

    val expected = """
                   ext {
                     var1 = true
                     var2 = false
                   }
                   configurations {
                     newConfig
                     otherNewConfig {
                       visible = var1
                     }
                   }
                   """.trimIndent()
    verifyFileContents(myBuildFile, expected)
  }

  @Test
  fun testRemoveConfig() {
    val text = """
               configurations {
                 badConfig {
                   transitive = true
                 }
                 worseConfig
                 worstConfig.visible = false
               }
               """.trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val configModel = buildModel.configurations()
      val configs = configModel.all()
      assertSize(3, configs)

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

    assertSize(0, buildModel.configurations().all())
    val expected = ""
    verifyFileContents(myBuildFile, expected)
  }
}