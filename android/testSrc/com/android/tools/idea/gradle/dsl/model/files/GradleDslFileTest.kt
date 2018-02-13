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
package com.android.tools.idea.gradle.dsl.model.files

import com.android.tools.idea.gradle.dsl.api.GradleFileModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.*
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.*
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.*
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.utils.FileUtils.toSystemIndependentPath
import java.io.File

class GradleDslFileTest : GradleFileModelTestCase() {
  fun testInvolvedFiles() {
    val parentText = """
                      def parentVar1 = true

                      ext {
                        parentProperty1 = "hello"
                      }

                      def parentVar2 = parentVar1
                      """.trimIndent()
    val childText = """
                           ext {
                             def childVar1 = 23
                             childProp1 = childVar1
                             childProp3 = [parentProperty1, childProp1]
                           }

                           def childVar2 = "value"
                           """.trimIndent()
    val childProperties = "childPropProp1 = somevalue"
    val parentProperties = "parentPropProp1 = othervalue"
    writeToBuildFile(parentText)
    writeToSubModuleBuildFile(childText)
    writeToSettingsFile("include ':${SUB_MODULE_NAME}'")
    writeToPropertiesFile(parentProperties)
    writeToSubModulePropertiesFile(childProperties)

    val buildModel = subModuleGradleBuildModel

    run {
      val files = buildModel.involvedFiles
      assertSize(4, files)
      val expected = listOf(myBuildFile.absolutePath, mySubModuleBuildFile.absolutePath,
          myPropertiesFile.absolutePath, mySubModulePropertiesFile.absolutePath)
      assertContainsElements(files.map { it.virtualFile.path }, expected.map { toSystemIndependentPath(it) })
    }
  }

  fun testPropertiesList() {
    val parentText = """
                      def parentVar1 = true

                      ext {
                        parentProperty1 = "hello"
                      }

                      def parentVar2 = parentVar1
                      """.trimIndent()
    val childText = """
                           ext {
                             def childVar1 = 23
                             childProp1 = childVar1
                             childProp3 = [parentProperty1, childProp1]
                           }

                           def childVar2 = "value"
                           """.trimIndent()
    val childProperties = "childPropProp1 = somevalue"
    val parentProperties = "parentPropProp1 = othervalue"
    writeToBuildFile(parentText)
    writeToSubModuleBuildFile(childText)
    writeToSettingsFile("include ':${SUB_MODULE_NAME}'")
    writeToPropertiesFile(parentProperties)
    writeToSubModulePropertiesFile(childProperties)

    val buildModel = subModuleGradleBuildModel
    val files = buildModel.involvedFiles

    run {
      val properties = getFile(mySubModuleBuildFile, files).declaredProperties
      assertSize(4, properties)
      verifyPropertyModel(properties[0], STRING_TYPE, "value", STRING, VARIABLE, 0, "childVar2")
      verifyPropertyModel(properties[1], INTEGER_TYPE, 23, INTEGER, VARIABLE, 0, "childVar1")
      verifyPropertyModel(properties[2], STRING_TYPE, "childVar1", REFERENCE, REGULAR, 1, "childProp1")
      verifyListProperty(properties[3], listOf("hello", 23), REGULAR, 2, "childProp3")
    }

    run {
      // Parent file properties
      val properties = getFile(myBuildFile, files).declaredProperties
      assertSize(3, properties)
      verifyPropertyModel(properties[0], BOOLEAN_TYPE, true, BOOLEAN, VARIABLE, 0, "parentVar1")
      verifyPropertyModel(properties[1], STRING_TYPE, "parentVar1", REFERENCE, VARIABLE, 1, "parentVar2")
      verifyPropertyModel(properties[2], STRING_TYPE, "hello", STRING, REGULAR, 0, "parentProperty1")
    }

    run {
      // Parent properties file
      val properties = getFile(myPropertiesFile, files).declaredProperties
      assertSize(1, properties)
      verifyPropertyModel(properties[0], STRING_TYPE, "othervalue", STRING, PROPERTIES_FILE, 0, "parentPropProp1")
    }

    run {
      // Child properties file
      val properties = getFile(mySubModulePropertiesFile, files).declaredProperties
      assertSize(1, properties)
      verifyPropertyModel(properties[0], STRING_TYPE, "somevalue", STRING, PROPERTIES_FILE, 0, "childPropProp1")
    }
  }

  fun testInvolvedAppliedFiles() {
    val firstApplyFileText = """
                           def var1 = "1"

                           ext {
                             prop1 = [var1, var2, var3]
                             prop2 = true
                           }""".trimIndent()
    val secondApplyFileText = """
                            ext {
                              def hello = "hello"
                              prop2 = false
                            }

                            apply from: "b.gradle"
                            """.trimIndent()
    val text = """
             apply from: "a.gradle"

             def goodbye = "goodbye"

             ext {
               prop5 = 5
             }""".trimIndent()
    writeToNewProjectFile("b.gradle", firstApplyFileText)
    writeToNewProjectFile("a.gradle", secondApplyFileText)
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val files = buildModel.involvedFiles
      assertSize(3, files)
      val fileParent = myBuildFile.parentFile
      val expected = listOf(myBuildFile.absolutePath,
          File(fileParent, "a.gradle").absolutePath, File(fileParent, "b.gradle").absolutePath)
      assertContainsElements(files.map { it.virtualFile.path }, expected.map { toSystemIndependentPath(it) })
    }
  }

  fun testListPropertiesFromAppliedFiles() {
    val firstApplyFileText = """
                           def var1 = "1"
                           def var2 = 2
                           def var3 = "3"

                           ext {
                             prop1 = [var1, var2, var3]
                             prop2 = true
                           }""".trimIndent()
    val secondApplyFileText = """
                            ext {
                              def hello = "hello"
                              prop2 = false
                            }

                            apply from: "b.gradle"
                            """.trimIndent()
    val text = """
             apply from: "a.gradle"

             def goodbye = "goodbye"

             ext {
               prop5 = 5
             }""".trimIndent()
    writeToNewProjectFile("b.gradle", firstApplyFileText)
    writeToNewProjectFile("a.gradle", secondApplyFileText)
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val files = buildModel.involvedFiles
    val fileParent = myBuildFile.parentFile

    run {
      val properties = getFile(myBuildFile, files).declaredProperties
      assertSize(2, properties)
      verifyPropertyModel(properties[0], STRING_TYPE, "goodbye", STRING, VARIABLE, 0, "goodbye")
      verifyPropertyModel(properties[1], INTEGER_TYPE, 5, INTEGER, REGULAR, 0, "prop5")
    }

    run {
      val properties = getFile(File(fileParent, "a.gradle"), files).declaredProperties
      assertSize(2, properties)
      verifyPropertyModel(properties[0], STRING_TYPE, "hello", STRING, VARIABLE, 0, "hello")
      verifyPropertyModel(properties[1], BOOLEAN_TYPE, false, BOOLEAN, REGULAR, 0, "prop2")
    }

    run {
      val properties = getFile(File(fileParent, "b.gradle"), files).declaredProperties
      assertSize(5, properties)
      verifyPropertyModel(properties[0], STRING_TYPE, "1", STRING, VARIABLE, 0, "var1")
      verifyPropertyModel(properties[1], INTEGER_TYPE, 2, INTEGER, VARIABLE, 0, "var2")
      verifyPropertyModel(properties[2], STRING_TYPE, "3", STRING, VARIABLE, 0, "var3")
      verifyListProperty(properties[3], listOf("1", 2, "3"), REGULAR, 3, "prop1")
      verifyPropertyModel(properties[4], BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, "prop2")
    }
  }

  fun getFile(file : File, files : Set<GradleFileModel>) = files.first { toSystemIndependentPath(file.absolutePath) == it.virtualFile.path }
}