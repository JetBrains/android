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
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.facet.AndroidFacet
import org.junit.runner.Description

/**
 * Rule that provides access to a [Project] containing one module configured with the Android facet.
 */
class AndroidProjectRule() : NamedExternalResource() {
  lateinit var fixture: CodeInsightTestFixture
  lateinit var module: Module
  /**
   * true iff the default module should be a valid Android module (if it should have an Android manifest and the Android
   * facet attached)
   */
  var initAndroid: Boolean = true
  val project: Project get() = fixture.project

  private lateinit var mocks: IdeComponents
  private val facets = ArrayList<Facet<*>>()

  fun initAndroid(shouldInit: Boolean): AndroidProjectRule {
    initAndroid = shouldInit
    return this
  }

  fun <T> replaceProjectService(serviceType: Class<T>, newServiceInstance: T) =
      mocks.replaceProjectService(serviceType, newServiceInstance)

  fun <T> replaceService(serviceType: Class<T>, newServiceInstance: T) =
    mocks.replaceService(serviceType, newServiceInstance)

  fun <T> mockService(serviceType: Class<T>): T = mocks.mockService(serviceType)

  fun <T> mockProjectService(serviceType: Class<T>): T = mocks.mockProjectService(serviceType)

  override fun before(description: Description) {
    // This is a very abstract way to initialize a new Project and a single Module.
    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val projectBuilder = factory.createLightFixtureBuilder(LightCodeInsightFixtureTestCase.JAVA_8)
    fixture = factory.createCodeInsightFixture(projectBuilder.fixture, LightTempDirTestFixtureImpl(true))

    fixture.setUp()
    module = fixture.module
    // Initialize an Android manifest
    if (initAndroid) {
      addFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME)
    }
    mocks = IdeComponents(fixture.project)
  }

  fun <T : Facet<C>, C: FacetConfiguration> addFacet(type: FacetType<T, C>, facetName: String): T {
    val facetManager = FacetManager.getInstance(module)
    val facet = facetManager.createFacet<T, C>(type, facetName, null)
    runInEdtAndWait {
      val facetModel = facetManager.createModifiableModel()
      facetModel.addFacet(facet)
      ApplicationManager.getApplication().runWriteAction { facetModel.commit() }
      facets.add(facet)
    }
    return facet
  }

  override fun after(description: Description) {
    mocks.restore()
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