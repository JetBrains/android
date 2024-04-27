// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.google.common.collect.HashBiMap
import com.google.common.collect.ImmutableMap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import java.io.File

class GradleBlockModelTest : GradleFileModelTestCase() {

  override fun setUp() {
    super.setUp()
    ApplicationManager.getApplication().getService(GradleBlockModelMap::class.java).resetCache()
    GradleBlockModelMap.BlockModelProvider.EP.point.registerExtension(MyTestModelProviderExtension(), testRootDisposable)
    GradleBlockModelMap.BlockModelProvider.EP.point.registerExtension(MyNestedModelProviderExtension(), testRootDisposable)
  }

  @Test
  fun testPluggableBlockCanBeRead() {
    writeToBuildFile(TestFile.PLUGGABLE_BLOCK)

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
    verifyFileContents(myBuildFile, TestFile.PLUGGABLE_BLOCK_WRITE_EXPECTED)
    assertEquals(1, myTestModel.getDigit())
  }

  @Test
  fun testPluggableBlockResolved() {
    writeToBuildFile(TestFile.PLUGGABLE_BLOCK_RESOLVED)

    val buildModel = gradleBuildModel
    val myTestModel = buildModel.getModel(MyTestDslModel::class.java)
    assertEquals(3, myTestModel.getDigit())
  }

  @Test
  fun testPluggableNestedBlock() {
    writeToBuildFile(TestFile.PLUGGABLE_BLOCK_NESTED)
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
    verifyFileContents(myBuildFile, TestFile.PLUGGABLE_BLOCK_NESTED)
  }


  @Test
  fun testBlockModelsRegisteredForBuildFile() {
    val modelMap = ApplicationManager.getApplication().getService(GradleBlockModelMap::class.java)
    val rootModels = modelMap.childrenOf(GradleBuildModel::class.java)
    assertContainsElements(rootModels, MyTestDslModel::class.java, AndroidModel::class.java)

    val nestedModels = modelMap.childrenOf(MyTestDslModel::class.java)
    assertSameElements(nestedModels, MyNestedDslModel::class.java)
    assertEmpty(modelMap.childrenOf(MyNestedDslModel::class.java))
  }


  fun testThrowsExceptionForUnregisteredModel() {
    UsefulTestCase.assertThrows(IllegalArgumentException::class.java,
                                "Block model for MyNestedTestDslModel.class is not registered in GradleBuildModel.class") {
      writeToBuildFile("")
      val buildModel = gradleBuildModel
      buildModel.getModel(MyNestedDslModel::class.java)
    }
  }

  internal enum class TestFile(private val path: @SystemDependent String) : TestFileName {
    PLUGGABLE_BLOCK("pluggableBlock/pluggableBlock"),
    PLUGGABLE_BLOCK_RESOLVED("pluggableBlock/pluggableBlockResolved"),
    PLUGGABLE_BLOCK_WRITE_EXPECTED("pluggableBlock/pluggableBlockWriteExpected"),
    PLUGGABLE_BLOCK_NESTED("pluggableBlock/pluggableBlockNested");

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/$path", extension)
    }
  }
}


//========== below classes required to be implemented to create new DSL block for root

class MyTestModelProviderExtension : GradleBlockModelMap.BlockModelProvider<GradleBuildModel, GradleDslFile> {

  override fun getParentClass() = GradleBuildModel::class.java

  override fun availableModels(): List<GradleBlockModelMap.BlockModelBuilder<*, GradleDslFile>> {
    return ROOT_MODELS
  }

  override fun elementsMap(): Map<String, PropertiesElementDescription<*>> {
    return ROOT_ELEMENTS_MAP
  }

  companion object {
    private val ROOT_MODELS = listOf(
      object : GradleBlockModelMap.BlockModelBuilder<MyTestDslModel, GradleDslFile> {
        override fun modelClass() = MyTestDslModel::class.java
        override fun create(file: GradleDslFile) = MyTestDslModelImpl(file.ensurePropertyElement(MyTestDslElement.MYTESTDSL))
      }
    )

    private val ROOT_ELEMENTS_MAP = mapOf(
      "myTestDslElement" to MyTestDslElement.MYTESTDSL
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
    val MYTESTDSL = PropertiesElementDescription("myTestDslElement", MyTestDslElement::class.java, ::MyTestDslElement)
    val elementName = "stringVal"
  }

  override fun getChildPropertiesElementsDescriptionMap(): ImmutableMap<String, PropertiesElementDescription<GradlePropertiesDslElement>> {
    return GradleBlockModelMap.getInstance().getOrCreateElementMap(MyTestDslElement::class.java)
  }
}

class MyTestDslModelImpl(val dslElement: MyTestDslElement) : MyTestDslModel, GradleDslBlockModel(dslElement) {
  override fun getDigit(): Int {
    if (PropertyUtil.isPropertiesElementOrMap(myDslElement)) {
      val value = GradlePropertyModelBuilder.create(myDslElement, MyTestDslElement.elementName).buildResolved().getValue(
        GradlePropertyModel.STRING_TYPE)
      return map.inverse()[value] ?: throw IllegalStateException(value)
    }
    return -1;
  }

  override fun setDigit(v: Int) {
    val value = map.get(v) ?: throw IllegalArgumentException()
    GradlePropertyModelBuilder.create(myDslElement, MyTestDslElement.elementName)
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

class MyNestedModelProviderExtension : GradleBlockModelMap.BlockModelProvider<MyTestDslModel, MyTestDslElement> {
  override fun getParentClass() = MyTestDslModel::class.java
  override fun availableModels(): List<GradleBlockModelMap.BlockModelBuilder<MyNestedDslModel, MyTestDslElement>> = listOf(
    object : GradleBlockModelMap.BlockModelBuilder<MyNestedDslModel, MyTestDslElement> {
      override fun modelClass() = MyNestedDslModel::class.java
      override fun create(dslElement: MyTestDslElement) = MyNestedDslModelImpl(
        dslElement.ensurePropertyElement(MyNestedDslElement.MYNESTEDDSL))
    }
  )

  override fun elementsMap() = mapOf(
    "nested" to MyNestedDslElement.MYNESTEDDSL
  )
}