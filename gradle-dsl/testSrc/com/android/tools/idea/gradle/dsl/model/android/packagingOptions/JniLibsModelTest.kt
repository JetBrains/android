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

class JniLibsModelTest : GradleFileModelTestCase() {

  @Test
  fun testParse() {
    writeToBuildFile(TestFile.PARSE)
    val buildModel = gradleBuildModel
    val jniLibsModel = buildModel.android().packagingOptions().jniLibs()
    checkForValidPsiElement(jniLibsModel, JniLibsModelImpl::class.java)
    assertEquals("useLegacyPackaging", true, jniLibsModel.useLegacyPackaging())
    verifyListProperty("excludes", jniLibsModel.excludes(), listOf("foo"))
    verifyListProperty("pickFirsts", jniLibsModel.pickFirsts(), listOf("bar", "baz"))
    verifyListProperty("keepDebugSymbols", jniLibsModel.keepDebugSymbols(), listOf("a", "b", "c"))
  }

  @Test
  fun testAddAndApply() {
    writeToBuildFile(TestFile.ADD_AND_APPLY)
    val buildModel = gradleBuildModel
    val jniLibsModel = buildModel.android().packagingOptions().jniLibs()
    checkForInvalidPsiElement(jniLibsModel, JniLibsModelImpl::class.java)
    jniLibsModel.useLegacyPackaging().setValue(true)
    jniLibsModel.excludes().addListValue().setValue("foo")
    jniLibsModel.pickFirsts().addListValue().setValue("bar")
    jniLibsModel.pickFirsts().addListValue().setValue("baz")
    jniLibsModel.keepDebugSymbols().addListValue().setValue("a")
    jniLibsModel.keepDebugSymbols().addListValue().setValue("b")
    jniLibsModel.keepDebugSymbols().addListValue().setValue("c")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_EXPECTED)

    assertEquals("useLegacyPackaging", true, jniLibsModel.useLegacyPackaging())
    verifyListProperty("excludes", jniLibsModel.excludes(), listOf("foo"))
    verifyListProperty("pickFirsts", jniLibsModel.pickFirsts(), listOf("bar", "baz"))
    verifyListProperty("keepDebugSymbols", jniLibsModel.keepDebugSymbols(), listOf("a", "b", "c"))
  }

  enum class TestFile(val path: @SystemDependent String) : TestFileName {
    PARSE("parse"),
    ADD_AND_APPLY("addAndApply"),
    ADD_AND_APPLY_EXPECTED("addAndApplyExpected"),
    ;

    override fun toFile(basePath: String, extension: String): File = super.toFile("$basePath/jniLibsModel/$path", extension)
  }
}