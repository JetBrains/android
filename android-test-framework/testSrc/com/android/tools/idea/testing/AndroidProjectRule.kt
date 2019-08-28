/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.testing

import com.intellij.facet.Facet
import com.intellij.facet.FacetConfiguration
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.*
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.AndroidTestCase.initializeModuleFixtureBuilderWithSrcAndGen
import org.jetbrains.android.facet.AndroidFacet
import org.junit.runner.Description

/**
 * Rule that provides access to a [Project] containing one module configured
 * with the Android facet.
 *
 * The defaults settings are using a [LightTempDirTestFixtureImpl] which means
 * that it does not create any file on disk,
 * but instead rely on  a [com.intellij.openapi.vfs.ex.temp.TempFileSystem]].
 *
 * For tests that rely on file on disk, use the [AndroidProjectRule.Factory.onDisk()]
 * factory method to use a full on disk fixture with a single module, otherwise use
 * the [AndroidProjectRule.Factory.inMemory()] method.
 */
class AndroidProjectRule private constructor(
    /**
     * true iff the default module should be a valid Android module
     * (if it should have an Android manifest and the Android facet attached).
     */
    private var initAndroid: Boolean = true,

    /**
     * True if this rule should use a [LightTempDirTestFixtureImpl] and create
     * file in memory.
     */
    private var lightFixture: Boolean = true,

    /**
     * True if this rule should include an Android SDK.
     */
    private var withAndroidSdk: Boolean = false,

    /**
     * Name of the fixture used to create the project directory when not
     * using a light fixture.
     *
     * Default is the test class' short name.
     */
    private var fixtureName: String? = null)
  : NamedExternalResource() {

  lateinit var fixture: CodeInsightTestFixture
  lateinit var module: Module

  val project: Project get() = fixture.project

  private val facets = ArrayList<Facet<*>>()

  /**
   * Factories method to build an [AndroidProjectRule]
   */
  companion object {
    /**
     * Returns an [AndroidProjectRule] that uses a fixture which create the
     * project in an in memory TempFileSystem
     *
     * @see IdeaTestFixtureFactory.createLightFixtureBuilder()
     */
    @JvmStatic
    fun inMemory() = AndroidProjectRule()

    /**
     * Returns an [AndroidProjectRule] that uses a fixture on disk
     * using a [JavaTestFixtureFactory]
     */
    @JvmStatic
    fun onDisk(fixtureName: String? = null) = AndroidProjectRule(
        lightFixture = false,
        fixtureName = fixtureName)

    /**
     * Returns an [AndroidProjectRule] that uses a fixture on disk
     * using a [JavaTestFixtureFactory] with an Android SDK.
     */
    @JvmStatic
    fun withSdk() = AndroidProjectRule(
      lightFixture = false,
      withAndroidSdk = true)
  }

  fun initAndroid(shouldInit: Boolean): AndroidProjectRule {
    initAndroid = shouldInit
    return this
  }

  fun <T : Any> replaceService(serviceType: Class<T>, newServiceInstance: T) {
    ApplicationManager.getApplication().replaceService(serviceType, newServiceInstance, fixture.projectDisposable)
  }

  fun <T> mockProjectService(serviceType: Class<T>): T {
    return IdeComponents.mockProjectService(fixture.project, serviceType, fixture.projectDisposable)
  }

  override fun before(description: Description) {
    fixture = if (lightFixture) {
      createLightFixture()
    }
    else {
      createJavaCodeInsightTestFixture(description)
    }
    fixture.setUp()
    module = fixture.module
    // Initialize an Android manifest
    if (initAndroid) {
      addFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME)
    }
  }

  private fun createLightFixture(): CodeInsightTestFixture {
    // This is a very abstract way to initialize a new Project and a single Module.
    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val projectBuilder = factory.createLightFixtureBuilder(LightJavaCodeInsightFixtureTestCase.JAVA_8)
    return factory.createCodeInsightFixture(projectBuilder.fixture, LightTempDirTestFixtureImpl(true))
  }

  /**
   * Create a project using [JavaCodeInsightTestFixture] with an Android module.
   * The project is created on disk under the /tmp folder
   */
  private fun createJavaCodeInsightTestFixture(description: Description): JavaCodeInsightTestFixture {
    IdeaTestFixtureFactory.getFixtureFactory().registerFixtureBuilder(
        AndroidTestCase.AndroidModuleFixtureBuilder::class.java,
        AndroidTestCase.AndroidModuleFixtureBuilderImpl::class.java)

    val projectBuilder = IdeaTestFixtureFactory
        .getFixtureFactory()
        .createFixtureBuilder(fixtureName ?: description.testClass.simpleName)

    val javaCodeInsightTestFixture = JavaTestFixtureFactory
        .getFixtureFactory()
        .createCodeInsightFixture(projectBuilder.fixture)

    val moduleFixtureBuilder = projectBuilder.addModule(AndroidTestCase.AndroidModuleFixtureBuilder::class.java)
    initializeModuleFixtureBuilderWithSrcAndGen(moduleFixtureBuilder, javaCodeInsightTestFixture.tempDirPath)

    return javaCodeInsightTestFixture
  }

  fun <T : Facet<C>, C : FacetConfiguration> addFacet(type: FacetType<T, C>, facetName: String): T {
    val facetManager = FacetManager.getInstance(module)
    val facet = facetManager.createFacet<T, C>(type, facetName, null)
    runInEdtAndWait {
      if (withAndroidSdk) {
        Sdks.addLatestAndroidSdk(fixture.testRootDisposable, module)
      }
      val facetModel = facetManager.createModifiableModel()
      facetModel.addFacet(facet)
      ApplicationManager.getApplication().runWriteAction { facetModel.commit() }
      facets.add(facet)
    }
    return facet
  }

  override fun after(description: Description) {
    runInEdtAndWait {
      val facetManager = FacetManager.getInstance(module)
      val facetModel = facetManager.createModifiableModel()
      facets.forEach {
        facetModel.removeFacet(it)
      }
      ApplicationManager.getApplication().runWriteAction { facetModel.commit() }
      facets.clear()
    }
    fixture.tearDown()
  }
}
