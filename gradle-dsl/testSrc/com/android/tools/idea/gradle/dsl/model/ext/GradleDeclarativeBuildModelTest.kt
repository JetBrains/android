/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.ext

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.model.GradleBuildModelImpl
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtilCore
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.collections.set

/**
 * Tests introduced to make sure Declarative plugin syntax works as it is.
 * Most probably in future all blow test cases will be merged in to AndroidModelTest and other similar tests.
 */
class GradleDeclarativeBuildModelTest : GradleFileModelTestCase() {

  @Before
  override fun before() {
    assumeTrue(isDeclarative)
    Registry.get("android.gradle.declarative.plugin.studio.support").setValue(true)
    super.before()
  }

  @After
  fun clearFlags() {
    Registry.get("android.gradle.declarative.plugin.studio.support").resetToDefault()
  }

  @Test
  fun flagTest(){
    Registry.get("android.gradle.declarative.plugin.studio.support").resetToDefault()
    writeToSettingsFile(subModuleSettingsText)
    writeToSubModuleBuildFile("""
      [[plugins]]
      id = "com.android.application"
      [android]
      compileSdk = 33
      """.trimIndent())

    val projectBuildModel = projectBuildModel
    val buildModel = projectBuildModel.getModuleBuildModel(mySubModule)
    assertNull(buildModel)
  }

  @Test
  fun testParseDeclarativeFile() {
    writeToSettingsFile(subModuleSettingsText)
    writeToSubModuleBuildFile("""
      [[plugins]]
      id = "com.android.application"
      [android]
      compileSdk = 33
      namespace = "com.example.app"
      [android.defaultConfig]
      minSdk = 21
    """.trimIndent())

    val buildModel = subModuleGradleBuildModel

    assertEquals(1, buildModel.plugins().size)
    assertEquals("plugins","com.android.application", buildModel.plugins().first().name())
    assertEquals("compileSdkVersion", 33, buildModel.android().compileSdkVersion())
    assertEquals("namespace", "com.example.app", buildModel.android().namespace())

    assertEquals("minSdkVersion", 21, buildModel.android().defaultConfig().minSdkVersion())

    val expected = mapOf("plugins" to listOf(mapOf("id" to "com.android.application")),
                         "android" to mapOf("mCompileSdkVersion" to 33,
                                            "mNamespace" to "com.example.app",
                                            "defaultConfig" to mapOf("mMinSdkVersion" to 21)))
    assertEquals(expected, propertiesToMap(buildModel))
  }

  @Test
  fun testParseDeclarativeFile2() {
    writeToSettingsFile(subModuleSettingsText)
    writeToSubModuleBuildFile("""
      [android]
      flavorDimensions = [ "version" ]
      [android.productFlavors.demo]
      dimension="version"
      applicationIdSuffix=".demo"
      versionNameSuffix="-demo"
      [android.productFlavors.full]
      dimension="version"
      applicationIdSuffix=".full"
      versionNameSuffix="-full"
    """.trimIndent())

    val buildModel = subModuleGradleBuildModel

    assertEquals(listOf("demo", "full"), buildModel.android().productFlavors().map { it.name() } )
    assertEquals(listOf("version"), buildModel.android().flavorDimensions().toList()!!.map { it.toString() })
    assertEquals("demo.applicationIdSuffix", ".demo", buildModel.android().productFlavors().first().applicationIdSuffix())
    val expected = mapOf("android" to mapOf("mFlavorDimensions" to listOf("version"),
                                            "productFlavors" to
                                              mapOf("demo" to mapOf("mDimension" to "version",
                                                                    "mApplicationIdSuffix" to ".demo",
                                                                    "mVersionNameSuffix" to "-demo"),
                                                    "full" to mapOf("mDimension" to "version",
                                                                    "mApplicationIdSuffix" to ".full",
                                                                    "mVersionNameSuffix" to "-full"))))
    assertEquals(expected, propertiesToMap(buildModel))
  }

  @Test
  fun testUpdateDeclarativeProperty() {
    writeToSettingsFile(subModuleSettingsText)
    writeToSubModuleBuildFile("""
      [[plugins]]
      id = "com.android.application"
      [android]
      compileSdk = 33
      namespace = "com.example.app"
      [android.defaultConfig]
      minSdk = 21
    """.trimIndent())
    val projectBuildModel = projectBuildModel
    var maybeBuildModel = projectBuildModel.getModuleBuildModel(mySubModule)
    assertNotNull(maybeBuildModel)
    var buildModel = maybeBuildModel!!

    buildModel.android().namespace().setValue("com.example.app2")
    buildModel.android().compileSdkVersion().setValue(34)

    assertEquals("namespace", "com.example.app2", buildModel.android().namespace())
    assertEquals("compileSdkVersion", 34, buildModel.android().compileSdkVersion())

    applyChangesAndReparse(projectBuildModel)
    maybeBuildModel = projectBuildModel.getModuleBuildModel(mySubModule)
    assertNotNull(maybeBuildModel)
    buildModel = maybeBuildModel!!

    assertEquals("compileSdkVersion", 34, buildModel.android().compileSdkVersion())
    assertEquals("namespace", "com.example.app2", buildModel.android().namespace())

    val expected2 = mapOf("plugins" to listOf(mapOf("id" to "com.android.application")),
                          "android" to mapOf("mCompileSdkVersion" to 34,
                                             "mNamespace" to "com.example.app2",
                                             "defaultConfig" to mapOf("mMinSdkVersion" to 21)))
    assertEquals(expected2, propertiesToMap(buildModel))
  }

  @Test
  fun testWritePropertyToHaveSegmentedHeader() {
    val expected = """
      [android.defaultConfig]
      minSdk = 21
    """.trimIndent()
    // "\n" here is the fix for Windows as most probably TOML plugin is trying to guess delimiter symbol
    // and using windows "\r\n"  if we have empty file at the beginning
    doTest("\n", expected) {
      android().defaultConfig().minSdkVersion().setValue(21)
    }
  }

  @Test
  fun testAddAndroidSectionWithNewAttribute() {
    val given = """
        [[plugins]]
        id = "com.android.application"
        """.trimIndent()
    val expected = """
        [[plugins]]
        id = "com.android.application"
        [android]
        compileSdk = 34
      """.trimIndent()
    doTest(given, expected) {
      android().compileSdkVersion().setValue(34)
    }
  }

  @Test
  fun testReadDeclarativePropertyThatIgnoreEmptyBlockContainer() {
    val given = """
        [[plugins]]
        id = "com.android.application"
        """.trimIndent()
    doTest(given, given) {
      assertSize(2, android().buildTypes())
    }
  }

  @Test
  fun testWritePropertyToCreateUnsegmentedHeader() {
    val given = """
     [android.defaultConfig]
     minSdk = 21
      """.trimIndent()

    val expected = """
      [android.defaultConfig]
      minSdk = 21
      [android]
      compileSdk = 33
      namespace = "namespace"
    """.trimIndent()

    doTest(given, expected) {
      android().compileSdkVersion().setValue(33)
      android().namespace().setValue("namespace")
    }
  }

  @Test
  fun testWritePropertyToCreateMultiSegmentHeader() {
    val given = """
     [android.defaultConfig]
     minSdk = 21
      """.trimIndent()

    val expected = """
      [android.defaultConfig]
      minSdk = 21
      [android.aaptOptions]
      namespaced = true
      additionalParameters = ["abcd"]
    """.trimIndent()

    doTest(given, expected) {
      android().aaptOptions().namespaced().setValue(true)
      android().aaptOptions().additionalParameters().addListValue()!!.setValue("abcd")
    }
  }

  @Test
  fun testWriteBlockElements() {
    val given = """
     [android]
     compileSdkVersion = 33
     """.trimIndent()
    val expected = """
      [android]
      compileSdkVersion = 33
      [android.defaultConfig]
      minSdk = 21
      """.trimIndent()

    doTest(given, expected) {
      android().defaultConfig().minSdkVersion().setValue(21)
    }
  }

  @Test
  fun testAddToMainBlockWhenSegmentedExists() {
    val given = """
      [android.defaultConfig]
      minSdk = 21
      [android]
      compileSdkVersion = 33
     """.trimIndent()
    val expected = """
      [android.defaultConfig]
      minSdk = 21
      [android]
      compileSdkVersion = 33
      namespace = "example"
      """.trimIndent()

    doTest(given, expected) {
      android().namespace().setValue("example")
    }
  }

  @Test
  fun testAddToMainBlockWhenSegmentedExists2() {
    val given = """
      [android]
      compileSdkVersion = 33
      [android.defaultConfig]
      minSdk = 21
     """.trimIndent()
    val expected = """
      [android]
      compileSdkVersion = 33
      namespace = "example"
      [android.defaultConfig]
      minSdk = 21
      """.trimIndent()

    doTest(given, expected) {
      android().namespace().setValue("example")
    }
  }

  @Test
  fun testRemove() {
    val given = """
     [android]
     compileSdkVersion = 33
     """.trimIndent()
    val expected = ""

    doTest(given, expected) {
      android().compileSdkVersion().delete()
    }
  }

  @Test
  fun testRemove2() {
    val given = """
      [android]
      compileSdkVersion = 33
      [android.defaultConfig]
      minSdk = 21
     """.trimIndent()
    val expected = """
      [android]
      compileSdkVersion = 33

      """.trimIndent()

    doTest(given, expected) {
      android().defaultConfig().minSdkVersion().delete()
    }
  }


  private fun doTest(content:String, expected:String, transformation: GradleBuildModel.() -> Unit){
    writeToSettingsFile(subModuleSettingsText)
    writeToSubModuleBuildFile(content)
    val maybeBuildModel = projectBuildModel.getModuleBuildModel(mySubModule)
    assertNotNull(maybeBuildModel)
    val buildModel = maybeBuildModel!!

    transformation.invoke(buildModel)
    applyChangesAndReparse(buildModel)

    val actualText = VfsUtilCore.loadText(mySubModuleBuildFile)
    assertEquals(expected, actualText)
  }

  private fun propertiesToMap(declarativeBuildModel: GradleBuildModel): Map<String, Any> {
    val dslFile = (declarativeBuildModel as GradleBuildModelImpl).dslFile
    fun populate(key: String, element: GradleDslElement?, setter: (String, Any) -> Unit) {
      val value = when (element) {
        is GradleDslLiteral -> element.value ?: "null literal"
        is GradleDslElementList, is GradleDslExpressionList -> {
          val newList = ArrayList<Any>()
          (element as GradlePropertiesDslElement).allElements.forEach { populate("", it) { _, v -> newList.add(v) } }
          newList
        }
        is GradlePropertiesDslElement -> { // some properties elements are lists so need to move handler down
          val newMap = LinkedHashMap<String, Any>()
          element.currentElements.forEach { populate(it.name, element.getElement(it.name)) { k, v -> newMap[k] = v } }
          newMap
        }
        else -> {
          "Unknown element: $element"
        }
      }
      setter(key, value)
    }

    val map = LinkedHashMap<String, Any>()
    dslFile.properties.forEach { populate(it, dslFile.getElement(it)) { key, value -> map[key] = value } }
    return map
  }
}
