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
package org.jetbrains.android.uipreview.classloading

import com.android.SdkConstants
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.idea.res.TestResourceIdManager
import com.android.tools.idea.res.addAarDependency
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.runInEdtAndWait
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.fail
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LibraryResourceClassLoaderTest {
  @get:Rule
  val androidProjectRule = AndroidProjectRule.onDisk()

  private fun assertThrowsClassNotFoundException(code: () -> Unit) {
    try {
      code()
      fail("ClassNotFoundException expected")
    }
    catch (_: ClassNotFoundException) {
    }
  }

  private lateinit var resourceIdManger: TestResourceIdManager

  private lateinit var projectSystem: TestProjectSystem

  private val nullDelegatingClassLoader: DelegatingClassLoader.Loader = object : DelegatingClassLoader.Loader {
    override fun loadClass(fqcn: String): ByteArray? = null
  }

  @Before
  fun setup() {
    resourceIdManger = TestResourceIdManager.getManager(androidProjectRule.module)
    resourceIdManger.setFinalIdsUsed(false)

    addAarDependency(androidProjectRule.module, "aarLib", "com.example.mylibrary") { resDir ->
      resDir.parentFile.resolve(SdkConstants.FN_RESOURCE_TEXT).writeText(
        """
          int string my_aar_string 0x7f010001
          int string another_aar_string 0x7f010002
          int attr attrOne 0x7f040001
          int attr attrTwo 0x7f040002
          int[] styleable LibStyleable { 0x7f040001, 0x7f040002, 0x7f040003 }
          int styleable LibStyleable_attrOne 0
          int styleable LibStyleable_attrTwo 1
          int styleable LibStyleable_android_maxWidth 2
          """.trimIndent()
      )
      resDir.parentFile.resolve("res/values/strings.xml").writeText(
        // language=xml
        """
        <resources>
          <string name='my_aar_string'>app test resource</string>
          <string name='another_aar_string'>another app test resource</string>
        </resources>
      """.trimIndent()
      )
    }
    addAarDependency(androidProjectRule.module, "emptyAarLib", "com.example.mylibrary.empty") { resDir ->
      resDir.parentFile.resolve(SdkConstants.FN_RESOURCE_TEXT).writeText("")
    }

    projectSystem = TestProjectSystem(
      project = androidProjectRule.project,
      androidLibraryDependencies = androidProjectRule.module.getModuleSystem().getAndroidLibraryDependencies(DependencyScopeType.MAIN)
    )
    runInEdtAndWait { projectSystem.useInTests() }
  }

  @After
  fun tearDown() {
    resourceIdManger.resetFinalIdsUsed()
  }

  @Test
  fun `not existing class throws`() {
    val classLoader = LibraryResourceClassLoader(null, androidProjectRule.module, nullDelegatingClassLoader)
    assertThrowsClassNotFoundException { classLoader.loadClass("test.R") }
  }

  @Test
  fun `empty R class in library`() {
    val classLoader = LibraryResourceClassLoader(null, androidProjectRule.module, nullDelegatingClassLoader)
    assertNotNull(classLoader.loadClass("com.example.mylibrary.empty.R"))
    assertNotNull(classLoader.loadClass("com.example.mylibrary.empty.R${'$'}layout"))
  }

  @Test
  fun `library R class is correctly found`() {
    val classLoader = LibraryResourceClassLoader(null, androidProjectRule.module, nullDelegatingClassLoader)
    assertNotNull(classLoader.loadClass("com.example.mylibrary.R"))
    assertNotNull(classLoader.loadClass("com.example.mylibrary.R${'$'}layout"))
    val stringRclass = classLoader.loadClass("com.example.mylibrary.R${'$'}string")

    assertEquals("another_aar_string, my_aar_string", stringRclass.declaredFields
      .map { it.name }
      .sorted()
      .joinToString())
  }

  // Regression test for b/233862429
  @Test
  fun `library R class is not found if final IDs are used and module class loader finds class`() {
    resourceIdManger.setFinalIdsUsed(true)
    val delegatingClassLoader = object : DelegatingClassLoader.Loader {
      override fun loadClass(fqcn: String): ByteArray? = ByteArray(1) // stub for ModuleClassLoaderImpl being able to load the class
    }

    val classLoader = LibraryResourceClassLoader(null, androidProjectRule.module, delegatingClassLoader)
    // When final IDs are used, we should load the R classes from the compiled R classes, not the light classes created by this class loader
    assertThrowsClassNotFoundException { classLoader.loadClass("com.example.mylibrary.R") }
    assertThrowsClassNotFoundException { classLoader.loadClass("com.example.mylibrary.R${'$'}layout") }
  }

  // Regression test for b/236854148
  @Test
  fun `library R class is found if final IDs are used and module class loader does not find class`() {
    resourceIdManger.setFinalIdsUsed(true)
    val delegatingClassLoader = object : DelegatingClassLoader.Loader {
      override fun loadClass(fqcn: String): ByteArray? = null // stub for ModuleClassLoaderImpl being unable to load the class
    }

    val classLoader = LibraryResourceClassLoader(null, androidProjectRule.module, delegatingClassLoader)
    // When final IDs are used, we should only load the R classes from the compiled R classes if a build has happened. If not, we should
    // load them from light classes created by this class loader
    classLoader.loadClass("com.example.mylibrary.R")
    classLoader.loadClass("com.example.mylibrary.R${'$'}layout")
  }
}