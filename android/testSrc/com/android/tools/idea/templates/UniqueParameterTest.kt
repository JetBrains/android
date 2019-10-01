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
import com.android.tools.idea.templates.Parameter.Constraint.CLASS
import com.android.tools.idea.templates.Parameter.Constraint.DRAWABLE
import com.android.tools.idea.templates.Parameter.Constraint.LAYOUT
import com.android.tools.idea.templates.Parameter.Constraint.MODULE
import com.android.tools.idea.templates.Parameter.Constraint.NAVIGATION
import com.android.tools.idea.templates.Parameter.Constraint.PACKAGE
import com.android.tools.idea.templates.Parameter.Constraint.UNIQUE
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
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.sdk.AndroidPlatform
import org.mockito.Mockito
import javax.imageio.metadata.IIOMetadataNode

/**
 * Test for uniqueness and existence for Parameter validation. In fact, these are the same exact tests,
 * since UNIQUE and exists are inverse constraints.
 */
class UniqueParameterTest : AndroidGradleTestCase() {
  private var myAppModule: Module? = null
  private var myAppFacet: AndroidFacet? = null
  private lateinit var myPaidSourceProvider: SourceProvider
  private lateinit var myMainSourceProvider: SourceProvider
  private lateinit var myParameter: Parameter

  override fun setUp() {
    super.setUp()

    loadProject(PROJECT_WITH_APPAND_LIB)
    assertNotNull(myAndroidFacet)
    val androidModel = AndroidModuleModel.get(myAndroidFacet)
    assertNotNull(androidModel)

    // Set up modules
    myAppModule = ModuleManager.getInstance(project).modules.firstOrNull { it.name == "app" }

    assertNotNull(myAppModule)

    myAppFacet = AndroidFacet.getInstance(myAppModule!!)

    assertNotNull(myAppFacet)

    Sdks.addLatestAndroidSdk(myAppFacet!!, myAppModule!!)

    assertNotNull(AndroidPlatform.getInstance(myAppModule!!))

    assertNotNull(myAppFacet!!.configuration.model)
    // TODO: b/23032990
    val appAndroidModuleModel = AndroidModuleModel.get(myAppFacet!!)
    val paidFlavor = appAndroidModuleModel!!.findProductFlavor("paid")
    assertNotNull(paidFlavor)
    myPaidSourceProvider = paidFlavor!!.sourceProvider
    assertNotNull(myPaidSourceProvider)

    myMainSourceProvider = appAndroidModuleModel!!.defaultSourceProvider
    assertNotNull(myMainSourceProvider)

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

    myParameter = Parameter(mockMetadata, elem)
    myParameter.constraints.add(UNIQUE)
  }

  private fun assertViolates(value: String?,
                             c: Parameter.Constraint,
                             packageName: String? = null,
                             provider: SourceProvider? = null,
                             relatedValues: Set<Any> = setOf()) {
    assertThat(myParameter.validateStringType(project, myAppModule, provider, packageName, value, relatedValues)).contains(c)
  }

  private fun assertPasses(value: String?,
                           c: Parameter.Constraint,
                           packageName: String? = null,
                           provider: SourceProvider? = null,
                           relatedValues: Set<Any> = setOf()) {
    assertThat(myParameter.validateStringType(project, myAppModule, provider, packageName, value, relatedValues)).doesNotContain(c)
  }

  fun testUniqueLayout() {
    myParameter.constraints.add(LAYOUT)

    assertViolates("activity_main", UNIQUE, null, myMainSourceProvider)
    assertViolates("fragment_main", UNIQUE, null, myMainSourceProvider)

    assertPasses("activity_main", UNIQUE, null, myPaidSourceProvider)
    assertPasses("fragment_main", UNIQUE, null, myPaidSourceProvider)

    assertPasses("blahblahblah", UNIQUE, null, myMainSourceProvider)
  }

  fun testUniqueDrawable() {
    myParameter.constraints.add(DRAWABLE)

    assertViolates("drawer_shadow", UNIQUE, null, myMainSourceProvider)
    assertViolates("ic_launcher", UNIQUE, null, myMainSourceProvider)

    assertPasses("drawer_shadow", UNIQUE, null, myPaidSourceProvider)
    assertPasses("ic_launcher", UNIQUE, null, myPaidSourceProvider)

    assertPasses("blahblahblah", UNIQUE, null, myMainSourceProvider)
  }

  fun testUniqueNavigation() {
    myParameter.constraints.add(NAVIGATION)

    assertViolates("nav_graph", UNIQUE, null, myMainSourceProvider)

    assertPasses("nav_graph", UNIQUE, null, myPaidSourceProvider)

    assertPasses("blahblahblah", UNIQUE, null, myMainSourceProvider)
  }

  fun testUniqueModule() {
    myParameter.constraints.add(MODULE)

    assertViolates("app", UNIQUE, null, null)
    assertViolates("lib", UNIQUE, null, null)

    assertPasses("foo", UNIQUE, null, null)
  }

  // Existence check is the same for PACKAGE and APP_PACKAGE
  fun testUniquePackage() {
    myParameter.constraints.add(PACKAGE)

    assertViolates("com.example.projectwithappandlib", UNIQUE)
    assertViolates("com.example.projectwithappandlib.app", UNIQUE)

    // Ensure distinction between source sets
    assertViolates("com.example.projectwithappandlib.app.paid", UNIQUE, null, myPaidSourceProvider)
    assertPasses("com.example.projectwithappandlib.app.paid", UNIQUE, null, myMainSourceProvider)

    assertPasses("com.example.foo", UNIQUE)
    assertPasses("org.android.blah", UNIQUE)
  }

  fun testUniqueClass() {
    myParameter.constraints.add(CLASS)

    assertViolates("MainActivity", UNIQUE, "com.example.projectwithappandlib.app", myMainSourceProvider)
    assertViolates("NavigationDrawerFragment", UNIQUE, "com.example.projectwithappandlib.app", myMainSourceProvider)

    assertViolates("BlankFragment", UNIQUE, "com.example.projectwithappandlib.app.paid", myPaidSourceProvider)

    assertPasses("MainActivity", UNIQUE, "com.example.foo", myMainSourceProvider)

    assertPasses("MainActivity2", UNIQUE, "com.example.projectwithappandlib.app", myMainSourceProvider)
    assertPasses("MainActivity", UNIQUE, "com.example.projectwithappandlib.app", myPaidSourceProvider)

    assertViolates("dummy", UNIQUE, "com.example.projectwithappandlib.app", myMainSourceProvider)
    assertPasses("dummy2", UNIQUE, "com.example.projectwithappandlib.app", myMainSourceProvider)
  }

  fun testUniqueLayoutWithLayoutAlias() {
    myParameter.constraints.add(LAYOUT)

    assertViolates("fragment_foo", UNIQUE, null, myMainSourceProvider)
    assertPasses("fragment_foo", UNIQUE, null, myPaidSourceProvider)
  }

  fun testRelatedValue() {
    assertViolates("fragment_foo", UNIQUE, null, myPaidSourceProvider, setOf("bar", "fragment_foo"))
    assertPasses("fragment_foo", UNIQUE, null, myPaidSourceProvider, setOf("bar", "fragment_bar"))

    myParameter.constraints.remove(UNIQUE)
    assertPasses("fragment_foo", UNIQUE, null, myPaidSourceProvider, setOf("bar", "fragment_foo"))
  }
}
