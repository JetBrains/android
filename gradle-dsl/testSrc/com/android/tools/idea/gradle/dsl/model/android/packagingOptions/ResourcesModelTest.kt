/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android.packagingOptions

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import java.io.File

class ResourcesModelTest : GradleFileModelTestCase() {

  @Test
  fun testParse() {
    writeToBuildFile(TestFile.PARSE)
    val buildModel = gradleBuildModel
    val resourcesModel = buildModel.android().packagingOptions().resources()
    checkForValidPsiElement(resourcesModel, ResourcesModelImpl::class.java)
    verifyListProperty("excludes", resourcesModel.excludes(), listOf("foo"))
    verifyListProperty("pickFirsts", resourcesModel.pickFirsts(), listOf("bar", "baz"))
    verifyListProperty("merges", resourcesModel.merges(), listOf("a", "b", "c"))
  }

  @Test
  fun testAddAndApply() {
    writeToBuildFile(TestFile.ADD_AND_APPLY)
    val buildModel = gradleBuildModel
    val resourcesModel = buildModel.android().packagingOptions().resources()
    checkForInvalidPsiElement(resourcesModel, ResourcesModelImpl::class.java)
    resourcesModel.excludes().addListValue()!!.setValue("foo")
    resourcesModel.pickFirsts().addListValue()!!.setValue("bar")
    resourcesModel.pickFirsts().addListValue()!!.setValue("baz")
    resourcesModel.merges().addListValue()!!.setValue("a")
    resourcesModel.merges().addListValue()!!.setValue("b")
    resourcesModel.merges().addListValue()!!.setValue("c")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_EXPECTED)

    verifyListProperty("excludes", resourcesModel.excludes(), listOf("foo"))
    verifyListProperty("pickFirsts", resourcesModel.pickFirsts(), listOf("bar", "baz"))
    verifyListProperty("merges", resourcesModel.merges(), listOf("a", "b", "c"))
  }

  @Test
  fun testAddElementsAndApply() {
    writeToBuildFile(TestFile.ADD_ELEMENTS_AND_APPLY)
    val buildModel = gradleBuildModel
    val resourcesModel = buildModel.android().packagingOptions().resources()

    resourcesModel.excludes().addListValue()!!.setValue("excludes2")
    resourcesModel.pickFirsts().addListValue()!!.setValue("pickFirsts2")
    resourcesModel.merges().addListValue()!!.setValue("merges1")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_ELEMENTS_AND_APPLY_EXPECTED)
  }

  @Test
  fun testRemoveElementsAndApply() {
    writeToBuildFile(TestFile.REMOVE_ELEMENTS_AND_APPLY)
    val buildModel = gradleBuildModel
    val resourcesModel = buildModel.android().packagingOptions().resources()

    resourcesModel.excludes().getListValue("excludes1")?.delete()
    resourcesModel.pickFirsts().getListValue("pickFirsts1")?.delete()
    resourcesModel.merges().getListValue("merges1")?.delete()

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.REMOVE_ELEMENTS_AND_APPLY_EXPECTED)
  }

  @Test
  fun testEditElementsAndApply() {
    writeToBuildFile(TestFile.EDIT_ELEMENTS_AND_APPLY)
    val buildModel = gradleBuildModel
    val resourcesModel = buildModel.android().packagingOptions().resources()

    resourcesModel.excludes().getListValue("excludes1")?.setValue("excludesX")
    resourcesModel.pickFirsts().getListValue("pickFirsts1")?.setValue("pickFirstsX")
    resourcesModel.merges().getListValue("merges1")?.setValue("mergesX")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.EDIT_ELEMENTS_AND_APPLY_EXPECTED)
  }


  enum class TestFile(val path: @SystemDependent String) : TestFileName {
    PARSE("parse"),
    ADD_AND_APPLY("addAndApply"),
    ADD_AND_APPLY_EXPECTED("addAndApplyExpected"),
    ADD_ELEMENTS_AND_APPLY("addElementsAndApply"),
    ADD_ELEMENTS_AND_APPLY_EXPECTED("addElementsAndApplyExpected"),
    REMOVE_ELEMENTS_AND_APPLY("removeElementsAndApply"),
    REMOVE_ELEMENTS_AND_APPLY_EXPECTED("removeElementsAndApplyExpected"),
    EDIT_ELEMENTS_AND_APPLY("editElementsAndApply"),
    EDIT_ELEMENTS_AND_APPLY_EXPECTED("editElementsAndApplyExpected"),
    ;

    override fun toFile(basePath: String, extension: String): File = super.toFile("$basePath/resourcesModel/$path", extension)
  }
}