/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.google.common.collect.HashBiMap
import com.google.common.collect.ImmutableMap
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import java.io.File

class GradleBlockModelTest : GradleFileModelTestCase() {

  override fun setUp() {
    super.setUp()
    // clean android model map
    ApplicationManager.getApplication().getService(GradleBlockModelMap::class.java).resetCache()

    BlockModelProvider.EP.point.registerExtension(MyTestModelProviderExtension(), testRootDisposable)
    BlockModelProvider.EP.point.registerExtension(MyNestedModelProviderExtension(), testRootDisposable)
  }

  @Test
  fun testPluggableBlockCanBeRead() {
    writeToBuildFile(TestFile.PARSE)

    val buildModel = gradleBuildModel
    val myTestModel = buildModel.getModel(MyTestDslModel::class.java)
    assertEquals(2, myTestModel.getDigit())
  }

  @Test
  fun testWriteToPluggableBlock() {
    writeToBuildFile("")

    val buildModel = gradleBuildModel
    val myTestModel = buildModel.getModel(MyTestDslModel::class.java)
    myTestModel.setDigit(1)
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.WRITE_EXPECTED)
    assertEquals(1, myTestModel.getDigit())
  }

  @Test
  fun testPluggableBlockResolved() {
    writeToBuildFile(TestFile.RESOLVE)

    val buildModel = gradleBuildModel
    val myTestModel = buildModel.getModel(MyTestDslModel::class.java)
    assertEquals(3, myTestModel.getDigit())
  }

  @Test
  fun testPluggableNestedBlock() {
    writeToBuildFile(TestFile.PARSE_NESTED)
    val buildModel = gradleBuildModel
    val myTestModel = buildModel.getModel(MyTestDslModel::class.java)
    val myNestedTestDslModel = myTestModel.getNestedModel(MyNestedDslModel::class.java)
    assertEquals("Qwerty", myNestedTestDslModel.getValue())

  }

  @Test
  fun testWriteToPluggableNestedBlock() {
    writeToBuildFile("")
    val buildModel = gradleBuildModel
    val myTestModel = buildModel.getModel(MyTestDslModel::class.java)
    myTestModel.setDigit(2)
    val myNestedTestDslModel = myTestModel.getNestedModel(MyNestedDslModel::class.java)
    myNestedTestDslModel.setValue("Qwerty")
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.PARSE_NESTED)
  }


  @Test
  fun testBlockModelsRegisteredForBuildFile() {
    writeToBuildFile("")
    val kind = (gradleBuildModel as GradleBuildModelImpl).dslFile.parser.kind
    val modelMap = ApplicationManager.getApplication().getService(GradleBlockModelMap::class.java)
    val rootModels = modelMap.childrenOf(GradleBuildModel::class.java, kind)
    assertContainsElements(rootModels, MyTestDslModel::class.java)

    val nestedModels = modelMap.childrenOf(MyTestDslModel::class.java, kind)
    assertSameElements(nestedModels, MyNestedDslModel::class.java)
    assertEmpty(modelMap.childrenOf(MyNestedDslModel::class.java, kind))
  }


  @Test
  fun testThrowsExceptionForUnregisteredModel() {
    writeToBuildFile("")
    val buildModel = gradleBuildModel
    assertThrows(
      java.lang.IllegalArgumentException::class.java,
      "Block model for interface com.android.tools.idea.gradle.dsl.model.MyNestedDslModel is" +
      " not registered in interface com.android.tools.idea.gradle.dsl.api.GradleBuildModel"
    ) { buildModel.getModel(MyNestedDslModel::class.java) }
  }

  enum class TestFile(val path: @SystemDependent String) : TestFileName {
    PARSE("parse"),
    PARSE_NESTED("parseNested"),
    RESOLVE("resolve"),
    WRITE_EXPECTED("writeExpected"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/pluggableBlock/$path", extension)
    }
  }
}

class MyTestModelProviderExtension : BlockModelProvider<GradleBuildModel, GradleDslFile> {

  override val parentClass = GradleBuildModel::class.java
  override val parentDslClass = GradleDslFile::class.java

  override fun availableModels(kind: GradleDslNameConverter.Kind): List<BlockModelBuilder<*, GradleDslFile>> {
    return ROOT_MODELS
  }

  override fun elementsMap(kind: GradleDslNameConverter.Kind): Map<String, PropertiesElementDescription<*>> {
    return ROOT_ELEMENTS_MAP
  }

  companion object {
    private val ROOT_MODELS = listOf(
      object : BlockModelBuilder<MyTestDslModel, GradleDslFile> {
        override fun modelClass() = MyTestDslModel::class.java
        override fun create(file: GradleDslFile) = MyTestDslModelImpl(file.ensurePropertyElement(MyTestDslElement.MY_TEST_DSL_ELEMENT_DESC))
      }
    )

    private val ROOT_ELEMENTS_MAP = mapOf(
      "myTestDslElement" to MyTestDslElement.MY_TEST_DSL_ELEMENT_DESC
    )
  }
}

interface MyTestDslModel : GradleDslModel {
  fun getDigit(): Int
  fun setDigit(v: Int)
  fun <T> getNestedModel(klass: Class<T>): T where T : GradleDslModel
}

class MyTestDslElement(parent: GradleDslElement, name: GradleNameElement) : GradleDslBlockElement(parent, name) {
  companion object {
    val MY_TEST_DSL_ELEMENT_DESC = PropertiesElementDescription("myTestDslElement", MyTestDslElement::class.java, ::MyTestDslElement)
    const val ELEMENT_NAME = "stringVal"
  }

  override fun getChildPropertiesElementsDescriptionMap(kind: GradleDslNameConverter.Kind): ImmutableMap<String, PropertiesElementDescription<*>> {
    return GradleBlockModelMap.instance.getOrCreateElementMap(MyTestDslElement::class.java, kind)
  }
}

class MyTestDslModelImpl(private val dslElement: MyTestDslElement) : MyTestDslModel, GradleDslBlockModel(dslElement) {
  override fun getDigit(): Int {
    if (PropertyUtil.isPropertiesElementOrMap(myDslElement)) {
      val value = GradlePropertyModelBuilder.create(myDslElement, MyTestDslElement.ELEMENT_NAME).buildResolved().getValue(
        GradlePropertyModel.STRING_TYPE)
      return map.inverse()[value] ?: throw IllegalStateException(value)
    }
    return -1
  }

  override fun setDigit(v: Int) {
    val value = map[v] ?: throw IllegalArgumentException()
    GradlePropertyModelBuilder.create(myDslElement, MyTestDslElement.ELEMENT_NAME)
      .build().setValue(value)
  }

  override fun <T : GradleDslModel> getNestedModel(klass: Class<T>): T {
    return GradleBlockModelMap.get(this.dslElement, MyTestDslModel::class.java, klass)
  }

  companion object {
    private val map = HashBiMap.create(
      mapOf(
        1 to "one",
        2 to "two",
        3 to "three"
      )
    )
  }

}

//=========== below classes are required for nested DSL element injection
interface MyNestedDslModel : GradleDslModel {
  fun getValue(): String
  fun setValue(v: String)
}

class MyNestedDslElement(parent: GradleDslElement, name: GradleNameElement) : GradleDslBlockElement(parent, name) {

  companion object {
    val MYNESTEDDSL = PropertiesElementDescription("nested", MyNestedDslElement::class.java, ::MyNestedDslElement)
  }
}

class MyNestedDslModelImpl(dslElement: MyNestedDslElement) : MyNestedDslModel, GradleDslBlockModel(dslElement) {
  override fun getValue(): String {
    return GradlePropertyModelBuilder.create(myDslElement, "nestedVal").buildResolved().getValue(GradlePropertyModel.STRING_TYPE) ?: "";
  }

  override fun setValue(v: String) {
    GradlePropertyModelBuilder.create(myDslElement, "nestedVal").build().setValue(v)
  }
}

class MyNestedModelProviderExtension : BlockModelProvider<MyTestDslModel, MyTestDslElement> {
  override val parentClass = MyTestDslModel::class.java
  override val parentDslClass = MyTestDslElement::class.java
  override fun availableModels(kind: GradleDslNameConverter.Kind): List<BlockModelBuilder<*, MyTestDslElement>> = listOf(
    object : BlockModelBuilder<MyNestedDslModel, MyTestDslElement> {
      override fun modelClass() = MyNestedDslModel::class.java
      override fun create(dslElement: MyTestDslElement) = MyNestedDslModelImpl(
        dslElement.ensurePropertyElement(MyNestedDslElement.MYNESTEDDSL))
    }
  )

  override fun elementsMap(kind: GradleDslNameConverter.Kind): Map<String, PropertiesElementDescription<*>> = mapOf(
    "nested" to MyNestedDslElement.MYNESTEDDSL
  )
}
