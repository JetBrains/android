/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.android.model

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.android.model.android.android
import com.android.tools.idea.gradle.dsl.android.api.android.BuildTypeModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.android.parser.android.BuildTypeDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import java.io.File
import org.jetbrains.annotations.SystemDependent
import org.junit.Test

class CustomBlockInAndroidModelTest : com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase() {

  init {
    myTestDataRelativePath = "tools/adt/idea/gradle-dsl-android/testData/parser"
  }

  override fun setUp() {
    super.setUp()
    // clean android model map
    ApplicationManager.getApplication().getService(com.android.tools.idea.gradle.dsl.model.GradleBlockModelMap::class.java).resetCache()

    com.android.tools.idea.gradle.dsl.model.BlockModelProvider.Companion.EP.point.registerExtension(MyBuildTypeModelProviderExtension(), testRootDisposable)
  }

  @Test
  fun testWriteToPluggableNestedBlockInBuildType() {
    writeToBuildFile("")
    val buildModel = gradleBuildModel
    val releaseBuildType =  buildModel.android().buildTypes().find { it.name() == "release"}
    assertThat(releaseBuildType).isNotNull()
    val myTestModel = releaseBuildType!!.getModel(MyBuildTypeNestedDslModel::class.java)
    myTestModel.setValue("some")
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.PARSE_BUILD_TYPE_NESTED)
  }

  enum class TestFile(val path: @SystemDependent String) : TestFileName {
    PARSE_BUILD_TYPE_NESTED("parseBuildTypeNested"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/pluggableBlock/$path", extension)
    }
  }

}

interface MyBuildTypeNestedDslModel : GradleDslModel {
  fun getValue(): String
  fun setValue(v: String)
}

class MyBuildTypeNestedDslElement(parent: GradleDslElement, name: GradleNameElement) : com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement(parent, name) {

  companion object {
    val MYNESTEDDSL = PropertiesElementDescription("buildTypeNested", MyBuildTypeNestedDslElement::class.java, ::MyBuildTypeNestedDslElement)
  }
}

class MyNestedTypeDslModelImpl(dslElement: MyBuildTypeNestedDslElement) : MyBuildTypeNestedDslModel, com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel(dslElement) {
  override fun getValue(): String {
    return GradlePropertyModelBuilder.create(myDslElement, "nestedVal").buildResolved().getValue(GradlePropertyModel.STRING_TYPE) ?: "";
  }

  override fun setValue(v: String) {
    GradlePropertyModelBuilder.create(myDslElement, "nestedVal").build().setValue(v)
  }
}

class MyBuildTypeModelProviderExtension : com.android.tools.idea.gradle.dsl.model.BlockModelProvider<BuildTypeModel, BuildTypeDslElement> {
  override val parentClass = BuildTypeModel::class.java
  override val parentDslClass = BuildTypeDslElement::class.java
  override fun availableModels(kind: GradleDslNameConverter.Kind): List<com.android.tools.idea.gradle.dsl.model.BlockModelBuilder<*, BuildTypeDslElement>> = listOf(
    object : com.android.tools.idea.gradle.dsl.model.BlockModelBuilder<MyBuildTypeNestedDslModel, BuildTypeDslElement> {
      override fun modelClass() = MyBuildTypeNestedDslModel::class.java
      override fun create(dslElement: BuildTypeDslElement) = MyNestedTypeDslModelImpl(
        dslElement.ensurePropertyElement(MyBuildTypeNestedDslElement.MYNESTEDDSL))
    }
  )

  override fun elementsMap(kind: GradleDslNameConverter.Kind): Map<String, PropertiesElementDescription<*>> = mapOf(
    "buildTypeNested" to MyBuildTypeNestedDslElement.MYNESTEDDSL
  )
}