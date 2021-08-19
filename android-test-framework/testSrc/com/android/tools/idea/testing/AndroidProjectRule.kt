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

import com.android.testutils.MockitoThreadLocalsCleaner
import com.android.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidProjectRule.Companion.withAndroidModels
import com.android.tools.idea.testing.flags.override
import com.android.utils.FileUtils
import com.intellij.application.options.CodeStyle
import com.intellij.facet.Facet
import com.intellij.facet.FacetConfiguration
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.registerExtension
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.PlatformUtils
import org.jetbrains.android.AndroidTempDirTestFixture
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.AndroidTestCase.applyAndroidCodeStyleSettings
import org.jetbrains.android.AndroidTestCase.initializeModuleFixtureBuilderWithSrcAndGen
import org.jetbrains.android.LightJavaCodeInsightFixtureAdtTestCase
import org.jetbrains.android.facet.AndroidFacet
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import java.io.File
import java.time.Clock
import java.util.concurrent.TimeoutException

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
   * See also: [withAndroidModels].
   */
  private val projectModuleBuilders: List<ModuleModelBuilder>? = null,

  /**
   * When not null and the project is initialized from an instance of [AndroidModel] is called to prepare source code.
   */
  private val prepareProjectSourcesWith: ((File) -> Unit)? = null,

  /**
   * Name of the fixture used to create the project directory when not
   * using a light fixture.
   *
   * Default is the test class' short name.
   */
  private var fixtureName: String? = null
) : NamedExternalResource(), IntegrationTestEnvironment {

  private var userHome: String? = null
  lateinit var fixture: CodeInsightTestFixture
  val mockitoCleaner = MockitoThreadLocalsCleaner()

  val module: Module get() = fixture.module

  val project: Project get() = fixture.project

  val testRootDisposable: Disposable get() = fixture.testRootDisposable

  private lateinit var mocks: IdeComponents
  private val facets = ArrayList<Facet<*>>()

  /**
   * Factories method to build an [AndroidProjectRule]
   */
  companion object {
    /**
     * Returns an [AndroidProjectRule] that uses a fixture which create the
     * project in an in memory TempFileSystem
     *
     * @see IdeaTestFixtureFactory.createLightFixtureBuilder
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
    @JvmStatic
    fun withAndroidModel(
      androidProjectBuilder: AndroidProjectBuilder = createAndroidProjectBuilderForDefaultTestProjectStructure()
    ): AndroidProjectRule {
      return withAndroidModels(
        AndroidModuleModelBuilder(
          gradlePath = ":",
          selectedBuildVariant = "debug",
          projectBuilder = androidProjectBuilder
        )
      )
    }

    /**
     * Returns an [AndroidProjectRule] that initializes the project from an instances of [AndroidProject] obtained from
     * [androidProjectBuilder].
     */
    @JvmStatic
    fun withAndroidModels(
      prepareProjectSources: ((dir: File) -> Unit)? = null,
      vararg projectModuleBuilders: ModuleModelBuilder
    ): AndroidProjectRule = AndroidProjectRule(
      initAndroid = false,
      lightFixture = false,
      withAndroidSdk = false,
      prepareProjectSourcesWith = prepareProjectSources,
      projectModuleBuilders = projectModuleBuilders.toList()
    )

    /**
     * Returns an [AndroidProjectRule] that initializes the project from an instances of [AndroidProject] obtained from
     * [androidProjectBuilder].
     */
    @JvmStatic
    fun withAndroidModels(
      vararg projectModuleBuilders: ModuleModelBuilder
    ): AndroidProjectRule = AndroidProjectRule(
      initAndroid = false,
      lightFixture = false,
      withAndroidSdk = false,
      projectModuleBuilders = projectModuleBuilders.toList()
    )
  }

  fun initAndroid(shouldInit: Boolean): AndroidProjectRule {
    initAndroid = shouldInit
    return this
  }

  fun named(projectName: String?): AndroidProjectRule {
    fixtureName = projectName
    return this
  }

  fun <T : Any> replaceProjectService(serviceType: Class<T>, newServiceInstance: T) =
      mocks.replaceProjectService(serviceType, newServiceInstance)

  fun <T : Any> replaceService(serviceType: Class<T>, newServiceInstance: T) =
      mocks.replaceApplicationService(serviceType, newServiceInstance)

  fun <T> mockService(serviceType: Class<T>): T = mocks.mockApplicationService(serviceType)

  fun <T> mockProjectService(serviceType: Class<T>): T = mocks.mockProjectService(serviceType)

  fun <T : Any> registerExtension(epName: ExtensionPointName<T>, extension: T) =
    project.registerExtension(epName, extension, fixture.projectDisposable)

  inline fun <reified T: CodeInsightTestFixture> getTypedFixture(): T? {
    return fixture as? T
  }

  override fun before(description: Description) {
    try {
      doBeforeActions(description)
    } catch (t: Throwable){
      // cleanup if init failed
      mockitoCleaner.cleanupAndTearDown()
      throw t
    }
  }

  private fun doBeforeActions(description: Description) {
    mockitoCleaner.setup()
    fixture = if (lightFixture) {
      createLightFixture(description.displayName)
    }
    else {
      createJavaCodeInsightTestFixture(description)
    }

    userHome = System.getProperty("user.home")
    if ("AndroidStudio" == PlatformUtils.getPlatformPrefix()) {
      // Overriding "user.home" leads to some bad situations:
      // 1. When running in IDEA from sources: some files in kotlin plugin in Test IDE are obtained from the local M2 repository (see
      //    org.jetbrains.kotlin.idea.artifacts.UtilKt.findLibrary: it parses `.idea/libraries` and finds some M2 files there,
      //    e.g. kotlin-dist-for-ide-1.5.10-release-941.jar). Using different "user.home" in Host and Test IDEs
      //    makes these files inaccessible in Test IDE.
      // 2. IDEA downloads and setups new wrapper in each test because .gradle is new directory in different tests. This works really SLOW.
      // 3. `user.home` sometimes not restored if AndroidProjectRule fails during initialization.

      val testSpecificName = UsefulTestCase.TEMP_DIR_MARKER + description.testClass.simpleName.substringAfterLast('$')
      // Reset user home directory.
      System.setProperty("user.home", FileUtils.join(FileUtil.getTempDirectory(), testSpecificName, "nonexistent_user_home"))
    }

    // Disable antivirus checks on Windows.
    StudioFlags.ANTIVIRUS_METRICS_ENABLED.override(false, testRootDisposable)
    StudioFlags.ANTIVIRUS_NOTIFICATION_ENABLED.override(false, testRootDisposable)

    fixture.setUp()
    // Initialize an Android manifest
    if (initAndroid) {
      addFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME)
    }
    if (projectModuleBuilders != null) {
      if (IdeSdks.getInstance().androidSdkPath != TestUtils.getSdk()) {
        println("Tests: Replacing Android SDK from ${IdeSdks.getInstance().androidSdkPath} to ${TestUtils.getSdk()}")
        AndroidGradleTests.setUpSdks(fixture, TestUtils.getSdk().toFile())
      }
      ApplicationManager.getApplication().invokeAndWait {
        // Similarly to AndroidGradleTestCase, sync (fake sync here) requires SDKs to be set up and cleaned after the test to behave
        // properly.
        val basePath = File(fixture.tempDirPath)
        prepareProjectSourcesWith?.let { it(basePath) }
        if (projectModuleBuilders.isNotEmpty()) {
          setupTestProjectFromAndroidModel(project, basePath, *projectModuleBuilders.toTypedArray())
        }
      }
    }
    mocks = IdeComponents(fixture)

    // Apply Android Studio code style settings (tests running as the Android plugin in IDEA should behave the same)
    val settings = CodeStyle.getSettings(project).clone()
    applyAndroidCodeStyleSettings(settings)
    CodeStyleSettingsManager.getInstance(project).setTemporarySettings(settings)
  }

  private fun createLightFixture(projectName: String): CodeInsightTestFixture {
    // This is a very abstract way to initialize a new Project and a single Module.
    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val projectBuilder = factory.createLightFixtureBuilder(LightJavaCodeInsightFixtureAdtTestCase.getAdtProjectDescriptor(), projectName)
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

    // Below "p" is just a short directory name for a project directory. We have some tests that need more than one project in a test,
    // and they rely on ability to do "../another". This temporary directory test fixture will delete all of them at tear down.
    val name = fixtureName ?: "p"
    val tempDirFixture = object: AndroidTempDirTestFixture(name) {
      private val tempRoot: String =
        FileUtil.createTempDirectory("${UsefulTestCase.TEMP_DIR_MARKER}${Clock.systemUTC().millis()}", null, false).path
      override fun getRootTempDirectory(): String = tempRoot
      override fun tearDown() {
        super.tearDown()  // Deletes the project directory.
        try {
          // Delete the temp directory where the project directory was created.
          runWriteAction { VfsUtil.createDirectories(tempRoot).delete(this) }
        }
        catch (e: Throwable) {
          addSuppressedException(e)
        }
      }
    }
    val projectBuilder = IdeaTestFixtureFactory
        .getFixtureFactory()
        .createFixtureBuilder(name, tempDirFixture.projectDir.parentFile.toPath(), true)

    val javaCodeInsightTestFixture = JavaTestFixtureFactory
      .getFixtureFactory()
      .createCodeInsightFixture(projectBuilder.fixture, tempDirFixture)

    if (projectModuleBuilders == null) {
      val moduleFixtureBuilder = projectBuilder.addModule(AndroidTestCase.AndroidModuleFixtureBuilder::class.java)
      initializeModuleFixtureBuilderWithSrcAndGen(moduleFixtureBuilder, javaCodeInsightTestFixture.tempDirPath)
    }
    else {
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

  fun setupProjectFrom(vararg moduleBuilders: ModuleModelBuilder) {
    val basePath = File(fixture.tempDirPath)
    setupTestProjectFromAndroidModel(project, basePath, *moduleBuilders)
  }

  override fun after(description: Description) {
    runInEdtAndWait {
      if (facets.isNotEmpty()) {
        val facetManager = FacetManager.getInstance(module)
        val facetModel = facetManager.createModifiableModel()
        facets.forEach {
          facetModel.removeFacet(it)
        }
        ApplicationManager.getApplication().runWriteAction { facetModel.commit() }
        facets.clear()
      }
      CodeStyleSettingsManager.getInstance(project).dropTemporarySettings()
      if (withAndroidSdk) {
        val sdks = AndroidSdks.getInstance().allAndroidSdks
        for (sdk in sdks) {
          WriteAction.runAndWait<RuntimeException> {
            ProjectJdkTable.getInstance().removeJdk(sdk!!)
          }
        }
      }
    }
    fixture.tearDown()
    userHome?.let { System.setProperty("user.home", it) } ?: System.clearProperty("user.home")
    mockitoCleaner.cleanupAndTearDown()
    AndroidTestBase.checkUndisposedAndroidRelatedObjects()
  }

  /** Waits 2 seconds for the app resource repository to finish currently pending updates. */
  @Throws(InterruptedException::class, TimeoutException::class)
  fun waitForResourceRepositoryUpdates() {
    waitForResourceRepositoryUpdates(module)
  }

  override fun getBaseTestPath(): String {
    return fixture.tempDirPath
  }
}

class EdtAndroidProjectRule(val projectRule: AndroidProjectRule) :
  IntegrationTestEnvironment by projectRule,
  TestRule by RuleChain.outerRule(projectRule).around(EdtRule())!! {
  val project: Project get() = projectRule.project
  val fixture: CodeInsightTestFixture get() = projectRule.fixture
  val testRootDisposable: Disposable get() = projectRule.testRootDisposable
  fun setupProjectFrom(vararg moduleBuilders: ModuleModelBuilder) = projectRule.setupProjectFrom(*moduleBuilders)
}

fun AndroidProjectRule.onEdt(): EdtAndroidProjectRule = EdtAndroidProjectRule(this)
