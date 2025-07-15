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
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.gradle.dcl.lang.flags.DeclarativeIdeSupport
import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.api.SoftwareTypesModel
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.android.tools.idea.gradle.dsl.parser.settings.DefaultsDslElement
import com.android.tools.idea.gradle.feature.flags.DeclarativeStudioSupport
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.SystemDependent
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class GradleBlockModelDefaultsTest : GradleFileModelTestCase() {

  @Before
  override fun before() {
    isIrrelevantForKotlinScript("Defaults is only for declarative")
    isIrrelevantForGroovy("Defaults is only for declarative")
    DeclarativeIdeSupport.override(true)
    DeclarativeStudioSupport.override(true)
    super.before()

    // clean android model map
    ApplicationManager.getApplication().getService(GradleBlockModelMap::class.java).resetCache()
    BlockModelProvider.EP.point.registerExtension(MyTestModelDefaultsProviderExtension(), testRootDisposable)
    BlockModelProvider.EP.point.registerExtension(MyNestedModelProviderExtension(), testRootDisposable)
  }

  @After
  fun onAfter() {
    DeclarativeIdeSupport.clearOverride()
    DeclarativeStudioSupport.clearOverride()
  }

  @Test
  fun testPluggableBlockCanBeRead() {
    writeToSettingsFile(TestFile.PARSE)

    val settingsModel = gradleDeclarativeSettingsModel
    val defaults = settingsModel.defaults()
    val myTestModel = defaults.getModel(MyTestDslModel::class.java)
    assertEquals(2, myTestModel.getDigit())
  }

  @Test
  fun testWriteToPluggableBlock() {
    writeToSettingsFile("")

    val settingsModel = gradleDeclarativeSettingsModel
    val defaults = settingsModel.defaults()
    val myTestModel = defaults.getModel(MyTestDslModel::class.java)
    myTestModel.setDigit(1)
    applyChanges(settingsModel)
    settingsModel.reparse()
    verifyFileContents(mySettingsFile, TestFile.WRITE_EXPECTED)
    assertEquals(1, myTestModel.getDigit())
  }

  @Test
  fun testPluggableNestedBlock() {
    writeToSettingsFile(TestFile.PARSE_NESTED)
    val settingsMode = gradleDeclarativeSettingsModel
    val defaults = settingsMode.defaults()
    val myTestModel = defaults.getModel(MyTestDslModel::class.java)
    val myNestedTestDslModel = myTestModel.getNestedModel(MyNestedDslModel::class.java)
    assertEquals("Qwerty", myNestedTestDslModel.getValue())

  }

  @Test
  fun testWriteToPluggableNestedBlock() {
    writeToSettingsFile("")
    val settingsMode = gradleDeclarativeSettingsModel
    val defaults = settingsMode.defaults()
    val myTestModel = defaults.getModel(MyTestDslModel::class.java)
    myTestModel.setDigit(2)
    val myNestedTestDslModel = myTestModel.getNestedModel(MyNestedDslModel::class.java)
    myNestedTestDslModel.setValue("Qwerty")
    applyChanges(settingsMode)
    settingsMode.reparse()
    verifyFileContents(mySettingsFile, TestFile.PARSE_NESTED)
  }


  @Test
  fun testBlockModelsRegisteredForDefaultsBlock() {
    writeToSettingsFile("")
    val kind = (gradleBuildModel as GradleBuildModelImpl).dslFile.parser.kind
    val modelMap = ApplicationManager.getApplication().getService(GradleBlockModelMap::class.java)
    val softwareTypeModels = modelMap.childrenOf(SoftwareTypesModel::class.java, kind)
    assertContainsElements(softwareTypeModels, MyTestDslModel::class.java)

    val nestedModels = modelMap.childrenOf(MyTestDslModel::class.java, kind)
    assertSameElements(nestedModels, MyNestedDslModel::class.java)
    assertEmpty(modelMap.childrenOf(MyNestedDslModel::class.java, kind))
  }

  @Test
  fun testThrowsExceptionForUnregisteredModel() {
    writeToSettingsFile("")
    val settingsModel = gradleDeclarativeSettingsModel
    val defaults = settingsModel.defaults()
    assertThrows(
      java.lang.IllegalArgumentException::class.java,
      "Block model for interface com.android.tools.idea.gradle.dsl.model.MyNestedDslModel is" +
      " not registered in class com.android.tools.idea.gradle.dsl.model.SoftwareTypesModelImpl"
    ) { defaults.getModel(MyNestedDslModel::class.java) }
  }

  enum class TestFile(val path: @SystemDependent String) : TestFileName {
    PARSE("parseDefaults"),
    PARSE_NESTED("parseNestedDefaults"),
    WRITE_EXPECTED("writeExpectedDefaults"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/pluggableBlock/$path", extension)
    }
  }
}

class MyTestModelDefaultsProviderExtension : BlockModelProvider<SoftwareTypesModel, DefaultsDslElement> {

  override val parentClass = SoftwareTypesModel::class.java
  override val parentDslClass = DefaultsDslElement::class.java

  override fun availableModels(kind: GradleDslNameConverter.Kind): List<BlockModelBuilder<*, DefaultsDslElement>> {
    return ROOT_MODELS
  }

  override fun elementsMap(kind: GradleDslNameConverter.Kind): Map<String, PropertiesElementDescription<*>> {
    return ROOT_ELEMENTS_MAP
  }

  companion object {
    private val ROOT_MODELS = listOf(
      object : BlockModelBuilder<MyTestDslModel, DefaultsDslElement> {
        override fun modelClass() = MyTestDslModel::class.java
        override fun create(parent: DefaultsDslElement) = MyTestDslModelImpl(
          parent.ensurePropertyElement(MyTestDslElement.MY_TEST_DSL_ELEMENT_DESC))
      }
    )

    private val ROOT_ELEMENTS_MAP = mapOf(
      "myTestDslElement" to MyTestDslElement.MY_TEST_DSL_ELEMENT_DESC
    )

  }
}