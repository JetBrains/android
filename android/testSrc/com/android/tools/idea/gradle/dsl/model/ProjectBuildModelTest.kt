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
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.BOOLEAN
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR
import org.gradle.internal.impldep.org.hamcrest.CoreMatchers.hasItems
import org.gradle.internal.impldep.org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File

class ProjectBuildModelTest : GradleFileModelTestCase() {
  @Test
  fun testAppliedFilesShared() {
    val parentText = """
                     apply from: "b.gradle"

                     ext {
                       property = "${'$'}greeting"
                     }""".trimIndent()
    val childText = """
                    apply from: "../b.gradle"

                    ext {
                      childProperty = greeting
                    }""".trimIndent()
    val text = """
               ext {
                 greeting = "hello"
               }""".trimIndent()
    writeToNewProjectFile("b.gradle", text)
    writeToBuildFile(parentText)
    writeToSubModuleBuildFile(childText)
    writeToSettingsFile("include ':${SUB_MODULE_NAME}'")

    val projectModel = ProjectBuildModel.get(myProject)!!
    val parentBuildModel = projectModel.projectBuildModel
    val childBuildModel = projectModel.getModuleBuildModel(mySubModule)!!

    run {
      val parentProperty = parentBuildModel.ext().findProperty("property")
      verifyPropertyModel(parentProperty.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 1, "property")
      val childProperty = childBuildModel.ext().findProperty("childProperty")
      verifyPropertyModel(childProperty.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 1, "childProperty")
      val appliedProperty = childProperty.dependencies[0]
      verifyPropertyModel(appliedProperty.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 0, "greeting")

      // Alter the value of the applied file variable
      appliedProperty.setValue("goodbye")
      childProperty.rename("dodgy")

      verifyPropertyModel(appliedProperty.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 0, "greeting")
      verifyPropertyModel(parentProperty.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "property")
      verifyPropertyModel(childProperty.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "dodgy")
    }

    assertFalse(parentBuildModel.isModified)
    assertTrue(childBuildModel.isModified)
    applyChangesAndReparse(projectModel)
    assertFalse(parentBuildModel.isModified)
    assertFalse(childBuildModel.isModified)

    run {
      val parentProperty = parentBuildModel.ext().findProperty("property")
      val childProperty = childBuildModel.ext().findProperty("dodgy")
      val appliedProperty = childProperty.dependencies[0]
      verifyPropertyModel(appliedProperty.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 0, "greeting")
      verifyPropertyModel(parentProperty.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "property")
      verifyPropertyModel(childProperty.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "dodgy")
    }
  }

  @Test
  fun testMultipleModelsPersistChanges() {
    val text = """
               def var = true

               ext {
                 prop = "Hello i am ${'$'}{var}!"
               }
               """.trimIndent()
    val childText = """
                    ext {
                      prop1 = "boo"
                    }""".trimIndent()
    writeToBuildFile(text)
    writeToSubModuleBuildFile(childText)

    val projectModel = ProjectBuildModel.get(myProject)!!
    val childModelOne = projectModel.getModuleBuildModel(mySubModule)!!
    val childModelTwo = projectModel.getModuleBuildModel(mySubModule)!!
    val parentModelOne = projectModel.projectBuildModel
    val parentModelTwo = projectModel.projectBuildModel

    // Edit the properties in one of the models.
    run  {
      val parentPropertyModel = parentModelTwo.ext().findProperty("prop")
      verifyPropertyModel(parentPropertyModel.resolve(), STRING_TYPE, "Hello i am true!", STRING, REGULAR, 1)
      val childPropertyModel = childModelOne.ext().findProperty("prop1")
      verifyPropertyModel(childPropertyModel.resolve(), STRING_TYPE, "boo", STRING, REGULAR, 0)

      // Change values on each file.
      parentPropertyModel.dependencies[0].setValue(false)
      childPropertyModel.setValue("ood")

      // Check that the properties have been updated in the original models
      verifyPropertyModel(parentPropertyModel.resolve(), STRING_TYPE, "Hello i am false!", STRING, REGULAR, 1)
      verifyPropertyModel(childPropertyModel.resolve(), STRING_TYPE, "ood", STRING, REGULAR, 0)
      // Check that the properties have been updated in the other models
      val otherParentPropertyModel = parentModelOne.ext().findProperty("prop")
      val otherChildPropertyModel = childModelTwo.ext().findProperty("prop1")
      verifyPropertyModel(otherParentPropertyModel.resolve(), STRING_TYPE, "Hello i am false!", STRING, REGULAR, 1)
      verifyPropertyModel(otherChildPropertyModel.resolve(), STRING_TYPE, "ood", STRING, REGULAR, 0)
    }

    applyChangesAndReparse(projectModel)

    run {
      val parentPropertyModel = parentModelTwo.ext().findProperty("prop")
      val childPropertyModel = childModelOne.ext().findProperty("prop1")
      // Check that the properties have been updated in the original models
      verifyPropertyModel(parentPropertyModel.resolve(), STRING_TYPE, "Hello i am false!", STRING, REGULAR, 1)
      verifyPropertyModel(childPropertyModel.resolve(), STRING_TYPE, "ood", STRING, REGULAR, 0)
      // Check that the properties have been updated in the other models
      val otherParentPropertyModel = parentModelOne.ext().findProperty("prop")
      val otherChildPropertyModel = childModelTwo.ext().findProperty("prop1")
      verifyPropertyModel(otherParentPropertyModel.resolve(), STRING_TYPE, "Hello i am false!", STRING, REGULAR, 1)
      verifyPropertyModel(otherChildPropertyModel.resolve(), STRING_TYPE, "ood", STRING, REGULAR, 0)
    }
  }

  @Test
  fun testSettingsFileUpdatesCorrectly() {
    val moduleText = """
                     ext {
                       moduleProp = "one"
                     }""".trimIndent()
    val otherModuleText = """
                          ext {
                            otherModuleProp = "two"
                          }""".trimIndent()
    val parentText = """
                     ext {
                       parentProp = "zero"
                     }""".trimIndent()
    writeToBuildFile(parentText)
    writeToSubModuleBuildFile(moduleText)
    writeToSettingsFile("include ':${SUB_MODULE_NAME}'")
    val newModule = writeToNewSubModule("lib", otherModuleText, "")

    val projectModel = ProjectBuildModel.get(myProject)!!
    val parentBuildModel = projectModel.projectBuildModel
    val childBuildModel = projectModel.getModuleBuildModel(File(mySubModule.moduleFilePath).parentFile)!!
    val otherChildBuildModel = projectModel.getModuleBuildModel(newModule)!!
    val settingsModel = projectModel.projectSettingsModel!!

    run {
      // Check the child build models are correct.
      val childPropertyModel = childBuildModel.ext().findProperty("moduleProp")
      verifyPropertyModel(childPropertyModel, STRING_TYPE, "one", STRING, REGULAR, 0, "moduleProp")
      val otherChildPropertyModel = otherChildBuildModel.ext().findProperty("otherModuleProp")
      verifyPropertyModel(otherChildPropertyModel, STRING_TYPE, "two", STRING, REGULAR, 0, "otherModuleProp")
      // Change the module paths are correct.
      val paths = settingsModel.modulePaths()
      assertThat(paths, hasItems(":", ":${SUB_MODULE_NAME}"))
      val parentBuildModelTwo = settingsModel.moduleModel(":")!!
      // Check that this model has the same view as one we obtained from the project model.
      val propertyModel = parentBuildModelTwo.ext().findProperty("parentProp")
      verifyPropertyModel(propertyModel.resolve(), STRING_TYPE, "zero", STRING, REGULAR, 0, "parentProp")
      val oldPropertyModel = parentBuildModel.ext().findProperty("parentProp")
      verifyPropertyModel(oldPropertyModel.resolve(), STRING_TYPE, "zero", STRING, REGULAR, 0, "parentProp")
      propertyModel.setValue(true)
      verifyPropertyModel(propertyModel.resolve(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, "parentProp")
      verifyPropertyModel(oldPropertyModel.resolve(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, "parentProp")

      // Add the new path to the settings model.
      settingsModel.addModulePath(":lib")
      val newPaths = settingsModel.modulePaths()
      assertThat(newPaths, hasItems(":", ":${SUB_MODULE_NAME}", ":lib"))
    }

    applyChangesAndReparse(projectModel)

    run {
      val paths = settingsModel.modulePaths()
      assertThat(paths, hasItems(":", ":${SUB_MODULE_NAME}", ":lib"))
    }
  }
}