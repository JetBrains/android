/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import java.io.File
import java.lang.IllegalArgumentException

class KotlinOptionsModelTest : GradleFileModelTestCase() {
  @Test
  fun parse() {
    writeToBuildFile(TestFile.BLOCK)

    val android = gradleBuildModel.android()
    val kotlinOptions = android.kotlinOptions()
    assertEquals("jvmTarget", LanguageLevel.JDK_1_6, kotlinOptions.jvmTarget().toLanguageLevel())
    assertEquals("useIR", false, kotlinOptions.useIR().toBoolean())
    verifyListProperty("freeCompilerArgs", kotlinOptions.freeCompilerArgs(), listOf("-XXLanguage:+InlineClasses"))
  }

  @Test
  fun `add valid JVM target`() {
    writeToBuildFile(TestFile.ADD)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    var kotlinOptions = android.kotlinOptions()
    assertMissingProperty(kotlinOptions.jvmTarget())
    kotlinOptions.jvmTarget().setLanguageLevel(LanguageLevel.JDK_1_8)
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_EXPECTED)
    android = buildModel.android()
    kotlinOptions = android.kotlinOptions()
    assertEquals("jvmTarget", LanguageLevel.JDK_1_8, kotlinOptions.jvmTarget().toLanguageLevel())
  }

  @Test
  fun `add unknown JVM target`() {
    writeToBuildFile(TestFile.ADD_UNKNOWN_TARGET)

    val buildModel = gradleBuildModel
    val android = buildModel.android()
    val kotlinOptions = android.kotlinOptions()
    assertMissingProperty(kotlinOptions.jvmTarget())
    kotlinOptions.jvmTarget().setLanguageLevel(LanguageLevel.JDK_1_7) // not supported
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_UNKNOWN_TARGET)
  }

  @Test
  fun `add freeCompilerArgs`() {
    writeToBuildFile(TestFile.ADD)

    val buildModel = gradleBuildModel
    val kotlinOptions = buildModel.android().kotlinOptions()
    kotlinOptions.freeCompilerArgs().addListValue()!!.setValue("-XX:1")
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_FREE_COMPILER_ARGS_EXPECTED)
  }


  @Test
  fun remove() {
    writeToBuildFile(TestFile.REMOVE)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    var kotlinOptions = android.kotlinOptions()
    kotlinOptions.jvmTarget().delete()
    kotlinOptions.useIR().delete()
    kotlinOptions.freeCompilerArgs().delete()
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.REMOVE_EXPECTED)
    android = buildModel.android()
    kotlinOptions = android.kotlinOptions()
    checkForInvalidPsiElement(kotlinOptions, KotlinOptionsModelImpl::class.java)
    assertMissingProperty(kotlinOptions.jvmTarget())
    assertMissingProperty(kotlinOptions.useIR())
    assertMissingProperty(kotlinOptions.freeCompilerArgs())
  }

  @Test
  fun modify() {
    writeToBuildFile(TestFile.MODIFY)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    var kotlinOptions = android.kotlinOptions()
    assertEquals("jvmTarget", LanguageLevel.JDK_1_6, kotlinOptions.jvmTarget().toLanguageLevel())
    verifyListProperty("freeCompilerArgs", kotlinOptions.freeCompilerArgs(), listOf("-XX:1"))
    kotlinOptions.jvmTarget().setLanguageLevel(LanguageLevel.JDK_1_9)
    kotlinOptions.useIR().setValue(true)
    kotlinOptions.freeCompilerArgs().addListValue()!!.setValue("-XX:2")
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.MODIFY_EXPECTED)
    android = buildModel.android()
    kotlinOptions = android.kotlinOptions()
    assertEquals("jvmTarget", LanguageLevel.JDK_1_9, kotlinOptions.jvmTarget().toLanguageLevel())
    assertEquals("useIR", true, kotlinOptions.useIR().toBoolean())
    verifyListProperty("freeCompilerArgs", kotlinOptions.freeCompilerArgs(), listOf("-XX:1", "-XX:2"))
  }


  enum class TestFile(val path: @SystemDependent String): TestFileName {
    ADD("add"),
    ADD_EXPECTED("addExpected"),
    ADD_FREE_COMPILER_ARGS_EXPECTED("addFreeCompilerArgsExpected"),
    BLOCK("block"),
    ADD_UNKNOWN_TARGET("addUnknownTarget"),
    MODIFY("modify"),
    MODIFY_EXPECTED("modifyExpected"),
    REMOVE("remove"),
    REMOVE_EXPECTED("removeExpected"),
    ;
    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/kotlinOptionsModel/$path", extension)
    }   
  }
}