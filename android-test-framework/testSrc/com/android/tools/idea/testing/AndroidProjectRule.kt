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

import com.android.testutils.TestUtils
import com.android.tools.idea.io.FilePaths.toSystemDependentPath
import com.intellij.application.options.CodeStyle
import com.intellij.facet.Facet
import com.intellij.facet.FacetConfiguration
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.*
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.AndroidTestCase.applyAndroidCodeStyleSettings
import org.jetbrains.android.AndroidTestCase.initializeModuleFixtureBuilderWithSrcAndGen
import org.jetbrains.android.facet.AndroidFacet
import org.junit.runner.Description
import java.io.File

/**
 * Rule that provides access to a [Project] containing one module configured
 * with the Android facet.
 *
 * The defaults settings are using a [LightTempDirTestFixtureImpl] which means
 * that it does not create any file on disk,
 * but instead relly on  a [com.intellij.openapi.vfs.ex.temp.TempFileSystem]].
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
     * Not null if the project should be initialized from an instance of [AndroidModel].
     *
     * See also: [withAndroidModel].
     */
    private val androidProjectBuilder: AndroidProjectBuilder? = null,

    /**
     * Name of the fixture used to create the project directory when not
     * using a light fixture.
     *
     * Default is the test class' short name.
     */
    private var fixtureName: String? = null)
  : NamedExternalResource() {

  lateinit var fixture: CodeInsightTestFixture
  val module: Module get() = fixture.module

  val project: Project get() = fixture.project

  private lateinit var mocks: IdeComponents
  private val facets = ArrayList<Facet<*>>()

  /**
   * Factories method to build an [AndroidProjectRule]
   */
  companion object {
    /**
     * Returns an [AndroidProjectRule] that uses a fixture which create the
     * project in an in memeroy TempFileSystem
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
    @JvmOverloads
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

    /**
     * Returns an [AndroidProjectRule] that initializes the project from an instances of [AndroidProject] obtained from
     * [androidProjectBuilder]. Such a project will have a module from which an instance of [AndroidModel] can be retrieved.
     */
    fun withAndroidModel(androidProjectBuilder: AndroidProjectBuilder = createAndroidProjectBuilder()) =
      AndroidProjectRule(initAndroid = false, lightFixture = false, withAndroidSdk = false, androidProjectBuilder = androidProjectBuilder)
  }

  fun initAndroid(shouldInit: Boolean): AndroidProjectRule {
    initAndroid = shouldInit
    return this
  }

  fun <T> replaceProjectService(serviceType: Class<T>, newServiceInstance: T) =
      mocks.replaceProjectService(serviceType, newServiceInstance)

  fun <T> replaceService(serviceType: Class<T>, newServiceInstance: T) =
      mocks.replaceApplicationService(serviceType, newServiceInstance)

  fun <T> mockService(serviceType: Class<T>): T = mocks.mockApplicationService(serviceType)

  fun <T> mockProjectService(serviceType: Class<T>): T = mocks.mockProjectService(serviceType)

  fun <T> registerExtension(epName: ExtensionPointName<T>, extension: T) =
    PlatformTestUtil.registerExtension<T>(Extensions.getArea(project), epName, extension, fixture.projectDisposable)

  fun <T: CodeInsightTestFixture> getFixture(type: Class<T>): T? {
    return if (type.isInstance(fixture)) fixture as T else null
  }

  override fun before(description: Description) {
    fixture = if (lightFixture) {
      createLightFixture()
    }
    else {
      createJavaCodeInsightTestFixture(description)
    }
    fixture.setUp()
    // Initialize an Android manifest
    if (initAndroid) {
      addFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME)
    }
    if (androidProjectBuilder != null) {
      invokeAndWaitIfNeeded {
        // Similarly to AndroidGradleTestCase, sync (fake sync here) requires SDKs to be set up and cleaned after the test to behave
        // properly.
        AndroidGradleTests.setUpSdks(fixture, TestUtils.getSdk())
        val basePath = File(fixture.tempDirPath)
        setupTestProjectFromAndroidModel(project, basePath) { projectName, _ ->
          androidProjectBuilder.invoke(projectName, basePath)
        }
      }
    }
    mocks = IdeComponents(fixture)

    // Apply Android Studio code style settings (tests running as the Android plugin in IDEA should behave the same)
    val settings = CodeStyle.getSettings(project).clone()
    applyAndroidCodeStyleSettings(settings)
    CodeStyleSettingsManager.getInstance(project).setTemporarySettings(settings)
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

    val tempDirFixture =
        if (androidProjectBuilder == null) {
            // Use a default temp dir fixture for simple tests which do not require an AndroidModel.
            TempDirTestFixtureImpl()
        }
        else {
            // Projects set up to match the provided AndroidModel require content files to be located under the project directory.
            // Otherwise adding a new directory may require re-(fake)syncing to make it visible to the project.
            object : TempDirTestFixtureImpl() {
                override fun getTempHome(): File = toSystemDependentPath(projectBuilder.fixture.project.basePath)!!
            }
        }

    val javaCodeInsightTestFixture = JavaTestFixtureFactory
        .getFixtureFactory()
        .createCodeInsightFixture(projectBuilder.fixture, tempDirFixture)

    if (androidProjectBuilder == null) {
        val moduleFixtureBuilder = projectBuilder.addModule(AndroidTestCase.AndroidModuleFixtureBuilder::class.java)
        initializeModuleFixtureBuilderWithSrcAndGen(moduleFixtureBuilder, javaCodeInsightTestFixture.tempDirPath)
    } else {
        // Do nothing. There is no need to setup a module manually. It will be created by sync from the AndroidProject model.
    }

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
      CodeStyleSettingsManager.getInstance(project).dropTemporarySettings()
    }
    fixture.tearDown()
  }
}
