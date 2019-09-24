/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.templates

import com.android.builder.model.SourceProvider
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.templates.Template.ATTR_CONSTRAINTS
import com.android.tools.idea.templates.Template.ATTR_DEFAULT
import com.android.tools.idea.templates.Template.ATTR_HELP
import com.android.tools.idea.templates.Template.ATTR_ID
import com.android.tools.idea.templates.Template.ATTR_NAME
import com.android.tools.idea.templates.Template.ATTR_SUGGEST
import com.android.tools.idea.templates.Template.ATTR_TYPE
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.Sdks
import com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB
import com.android.tools.idea.wizard.template.Constraint
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.DRAWABLE
import com.android.tools.idea.wizard.template.Constraint.LAYOUT
import com.android.tools.idea.wizard.template.Constraint.MODULE
import com.android.tools.idea.wizard.template.Constraint.NAVIGATION
import com.android.tools.idea.wizard.template.Constraint.PACKAGE
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.SourceProviderManager
import org.jetbrains.android.sdk.AndroidPlatform
import org.mockito.Mockito
import javax.imageio.metadata.IIOMetadataNode

/**
 * Test for uniqueness and existence for Parameter validation. In fact, these are the same exact tests,
 * since UNIQUE and exists are inverse constraints.
 */
class UniqueParameterTest : AndroidGradleTestCase() {
  private var appModule: Module? = null
  private var appFacet: AndroidFacet? = null
  private lateinit var paidSourceProvider: SourceProvider
  private lateinit var mainSourceProvider: SourceProvider
  private lateinit var parameter: Parameter

  override fun setUp() {
    super.setUp()

    loadProject(PROJECT_WITH_APPAND_LIB)
    assertNotNull(AndroidModuleModel.get(myAndroidFacet!!)!!)

    // Set up modules
    appModule = ModuleManager.getInstance(project).modules.firstOrNull { it.name == "app" }!!
    appFacet = AndroidFacet.getInstance(appModule!!)!!

    Sdks.addLatestAndroidSdk(appFacet!!, appModule!!)

    assertNotNull(AndroidPlatform.getInstance(appModule!!))
    assertNotNull(appFacet!!.configuration.model)
    // TODO: b/23032990
    val appModuleModel = AndroidModuleModel.get(appFacet!!)!!
    val paidFlavor = appModuleModel.findProductFlavor("paid")!!
    paidSourceProvider = paidFlavor.sourceProvider
    mainSourceProvider = appModuleModel.defaultSourceProvider

    val mockMetadata = Mockito.mock(TemplateMetadata::class.java)

    val elem = IIOMetadataNode().apply {
      setAttribute(ATTR_TYPE, Parameter.Type.STRING.toString())
      setAttribute(ATTR_ID, "testParam")
      setAttribute(ATTR_DEFAULT, "")
      setAttribute(ATTR_SUGGEST, null)
      setAttribute(ATTR_NAME, "Test Param")
      setAttribute(ATTR_HELP, "This is a test parameter")
      setAttribute(ATTR_CONSTRAINTS, "")
    }

    parameter = Parameter(mockMetadata, elem).apply {
      constraints.add(UNIQUE)
    }
  }

  private fun assertViolates(
    value: String?, c: Constraint, packageName: String? = null, provider: SourceProvider? = null, relatedValues: Set<Any> = setOf()
  ) = assertThat(parameter.validateStringType(project, appModule, provider, packageName, value, relatedValues)).contains(c)

  private fun assertPasses(
    value: String?, c: Constraint, packageName: String? = null, provider: SourceProvider? = null, relatedValues: Set<Any> = setOf()
  ) = assertThat(parameter.validateStringType(project, appModule, provider, packageName, value, relatedValues)).doesNotContain(c)

  fun testUniqueLayout() {
    parameter.constraints.add(LAYOUT)

    assertViolates("activity_main", UNIQUE, null, mainSourceProvider)
    assertViolates("fragment_main", UNIQUE, null, mainSourceProvider)

    assertPasses("activity_main", UNIQUE, null, paidSourceProvider)
    assertPasses("fragment_main", UNIQUE, null, paidSourceProvider)

    assertPasses("blahblahblah", UNIQUE, null, mainSourceProvider)
  }

  fun testUniqueDrawable() {
    parameter.constraints.add(DRAWABLE)

    assertViolates("drawer_shadow", UNIQUE, null, mainSourceProvider)
    assertViolates("ic_launcher", UNIQUE, null, mainSourceProvider)

    assertPasses("drawer_shadow", UNIQUE, null, paidSourceProvider)
    assertPasses("ic_launcher", UNIQUE, null, paidSourceProvider)

    assertPasses("blahblahblah", UNIQUE, null, mainSourceProvider)
  }

  fun testUniqueNavigation() {
    parameter.constraints.add(NAVIGATION)

    assertViolates("nav_graph", UNIQUE, null, mainSourceProvider)

    assertPasses("nav_graph", UNIQUE, null, paidSourceProvider)

    assertPasses("blahblahblah", UNIQUE, null, mainSourceProvider)
  }

  fun testUniqueModule() {
    parameter.constraints.add(MODULE)

    assertViolates("app", UNIQUE, null, null)
    assertViolates("lib", UNIQUE, null, null)

    assertPasses("foo", UNIQUE, null, null)
  }

  // Existence check is the same for PACKAGE and APP_PACKAGE
  fun testUniquePackage() {
    parameter.constraints.add(PACKAGE)

    assertViolates("com.example.projectwithappandlib", UNIQUE)
    assertViolates("com.example.projectwithappandlib.app", UNIQUE)

    // Ensure distinction between source sets
    assertViolates("com.example.projectwithappandlib.app.paid", UNIQUE, null, paidSourceProvider)
    assertPasses("com.example.projectwithappandlib.app.paid", UNIQUE, null, mainSourceProvider)

    assertPasses("com.example.foo", UNIQUE)
    assertPasses("org.android.blah", UNIQUE)
  }

  fun testUniqueClass() {
    parameter.constraints.add(CLASS)

    assertViolates("MainActivity", UNIQUE, "com.example.projectwithappandlib.app", mainSourceProvider)
    assertViolates("NavigationDrawerFragment", UNIQUE, "com.example.projectwithappandlib.app", mainSourceProvider)

    assertViolates("BlankFragment", UNIQUE, "com.example.projectwithappandlib.app.paid", paidSourceProvider)

    assertPasses("MainActivity", UNIQUE, "com.example.foo", mainSourceProvider)

    assertPasses("MainActivity2", UNIQUE, "com.example.projectwithappandlib.app", mainSourceProvider)
    assertPasses("MainActivity", UNIQUE, "com.example.projectwithappandlib.app", paidSourceProvider)

    assertViolates("dummy", UNIQUE, "com.example.projectwithappandlib.app", mainSourceProvider)
    assertPasses("dummy2", UNIQUE, "com.example.projectwithappandlib.app", mainSourceProvider)
  }

  fun testUniqueLayoutWithLayoutAlias() {
    parameter.constraints.add(LAYOUT)

    assertViolates("fragment_foo", UNIQUE, null, mainSourceProvider)
    assertPasses("fragment_foo", UNIQUE, null, paidSourceProvider)
  }

  fun testRelatedValue() {
    assertViolates("fragment_foo", UNIQUE, null, paidSourceProvider, setOf("bar", "fragment_foo"))
    assertPasses("fragment_foo", UNIQUE, null, paidSourceProvider, setOf("bar", "fragment_bar"))

    parameter.constraints.remove(UNIQUE)
    assertPasses("fragment_foo", UNIQUE, null, paidSourceProvider, setOf("bar", "fragment_foo"))
  }
}
