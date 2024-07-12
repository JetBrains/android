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

import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoThreadLocalsCleaner
import com.android.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.flags.override
import com.android.tools.tests.AdtTestProjectDescriptor
import com.android.tools.tests.AdtTestProjectDescriptors
import com.android.tools.tests.KotlinAdtTestProjectDescriptor
import com.android.utils.FileUtils
import com.intellij.application.options.CodeStyle
import com.intellij.facet.Facet
import com.intellij.facet.FacetConfiguration
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.registerExtension
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.AndroidTempDirTestFixture
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.AndroidTestCase.applyAndroidCodeStyleSettings
import org.jetbrains.android.AndroidTestCase.initializeModuleFixtureBuilderWithSrcAndGen
import org.jetbrains.android.UndisposedAndroidObjectsCheckerRule.Companion.checkUndisposedAndroidRelatedObjects
import org.jetbrains.android.facet.AndroidFacet
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.mockito.Mockito
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
interface AndroidProjectRule : TestRule {

  interface Typed<T : CodeInsightTestFixture, H> : AndroidProjectRule {
    override val fixture: T
    override fun initAndroid(shouldInit: Boolean): Typed<T, H>
    override fun named(projectName: String?): Typed<T, H>
    override fun withKotlin(descriptor: KotlinAdtTestProjectDescriptor?): Typed<T, H>
    val testHelpers: H
  }

  val fixture: CodeInsightTestFixture

  /**
   * true iff the default module should be a valid Android module
   * (if it should have an Android manifest and the Android facet attached).
   *
   * Note: this property applies to some [AndroidProjectRule] builders only.
   */
  fun initAndroid(shouldInit: Boolean): AndroidProjectRule

  /**
   * Gives a name to the project created by this rule.
   *
   * Note: this property applies to some [AndroidProjectRule] builders only.
   */
  fun named(projectName: String?): AndroidProjectRule

  /**
   * Enables Kotlin and the Kotlin standard library for this project, using the
   * given Kotlin project descriptor. Passing `null` disables Kotlin.
   */
  fun withKotlin(descriptor: KotlinAdtTestProjectDescriptor? = AdtTestProjectDescriptors.kotlin()): AndroidProjectRule

  val testRootDisposable: Disposable get() = fixture.testRootDisposable
  val project: Project get() = fixture.project
  val module: Module get() = fixture.module

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
    fun inMemory(): Typed<CodeInsightTestFixture, Nothing> {
      val testEnvironmentRule = TestEnvironmentRuleImpl(withAndroidSdk = false)
      val fixtureRule = FixtureRuleImpl(::createLightFixture, withAndroidSdk = false)
      val projectEnvironmentRule = ProjectEnvironmentRuleImpl { fixtureRule.project }
      return chain(
        testEnvironmentRule,
        fixtureRule,
        projectEnvironmentRule
      )
    }

    /**
     * Returns an [AndroidProjectRule] that uses a fixture on disk
     * using a [JavaTestFixtureFactory]
     */
    @JvmStatic
    @JvmOverloads
    fun onDisk(
      fixtureName: String? = null,
      extraModules: List<String> = listOf(),
    ): Typed<JavaCodeInsightTestFixture, Nothing> {
      val testEnvironmentRule = TestEnvironmentRuleImpl(withAndroidSdk = false)
      val fixtureRule =
        FixtureRuleImpl(
          createHeavyFixtureFactory(extraModules),
          withAndroidSdk = false,
          fixtureName = fixtureName,
        )
      val projectEnvironmentRule = ProjectEnvironmentRuleImpl { fixtureRule.project }
      return chain(
        testEnvironmentRule,
        fixtureRule,
        projectEnvironmentRule
      )
    }

    /**
     * Returns an [AndroidProjectRule] that uses a fixture on disk
     * using a [JavaTestFixtureFactory] with an Android SDK using the given Android platform version.
     */
    @JvmStatic
    @JvmOverloads
    fun withSdk(
      androidPlatformVersion: AndroidVersion,
      fixtureName: String? = null,
      extraModules: List<String> = listOf(),
    ): Typed<JavaCodeInsightTestFixture, Nothing> {
      val testEnvironmentRule = TestEnvironmentRuleImpl(withAndroidSdk = true)
      val fixtureRule = FixtureRuleImpl(
        createHeavyFixtureFactory(extraModules),
        withAndroidSdk = true,
        androidPlatformVersion = androidPlatformVersion,
        fixtureName = fixtureName,
      )
      val projectEnvironmentRule = ProjectEnvironmentRuleImpl { fixtureRule.project }
      return chain(
        testEnvironmentRule,
        fixtureRule,
        projectEnvironmentRule
      )
    }

    /**
     * Returns an [AndroidProjectRule] that uses a fixture on disk
     * using a [JavaTestFixtureFactory] with an Android SDK using the given Android platform version.
     */
    // Needs an explicit overload so that callers don't need to depend on AndroidVersion in sdklib
    // to pass the default argument.
    @JvmStatic
    @JvmOverloads
    fun withSdk(
      fixtureName: String? = null,
      extraModules: List<String> = listOf(),
    ) = withSdk(Sdks.getLatestAndroidPlatform(), fixtureName, extraModules)

    /**
     * Returns an [AndroidProjectRule] that initializes the project from an instance of
     * [com.android.tools.idea.gradle.model.IdeAndroidProject] obtained from [androidProjectBuilder]. Such a project will have a module
     * from which an instance of [com.android.tools.idea.model.AndroidModel] can be retrieved.
     */
    @JvmStatic
    fun withAndroidModel(
      androidProjectBuilder: AndroidProjectBuilder = createAndroidProjectBuilderForDefaultTestProjectStructure()
    ): Typed<JavaCodeInsightTestFixture, Nothing> {
      return withAndroidModels(
        AndroidModuleModelBuilder(
          gradlePath = ":",
          selectedBuildVariant = "debug",
          projectBuilder = androidProjectBuilder
        )
      )
    }

    /**
     * Returns an [AndroidProjectRule] that initializes the project from models obtained from [projectModuleBuilders] and populates its
     * source directories by invoking [prepareProjectSources].
     */
    @JvmStatic
    fun withAndroidModels(
      prepareProjectSources: ((dir: File) -> Unit)? = null,
      vararg projectModuleBuilders: ModuleModelBuilder
    ): Typed<JavaCodeInsightTestFixture, Nothing> {
      val fixtureFactory: FixtureFactory<JavaCodeInsightTestFixture> = { projectName, _ ->
        createJavaCodeInsightTestFixtureAndModels(
          projectName,
          projectModuleBuilders = projectModuleBuilders.toList(),
          prepareProjectSourcesWith = prepareProjectSources
        )
      }

      val testEnvironmentRule = TestEnvironmentRuleImpl(withAndroidSdk = false)
      val fixtureRule =
        FixtureRuleImpl(fixtureFactory, withAndroidSdk = false, initAndroid = false)
      val projectEnvironmentRule = ProjectEnvironmentRuleImpl { fixtureRule.project }
      return chain(
        testEnvironmentRule,
        fixtureRule,
        projectEnvironmentRule
      )
    }

    /**
     * Returns an [AndroidProjectRule] that initializes the project from models obtained from [projectModuleBuilders].
     */
    @JvmStatic
    fun withAndroidModels(
      vararg projectModuleBuilders: ModuleModelBuilder
    ): Typed<JavaCodeInsightTestFixture, Nothing> = withAndroidModels(prepareProjectSources = null, *projectModuleBuilders)

    @JvmStatic
    fun withIntegrationTestEnvironment(): IntegrationTestEnvironmentRule {
      val projectRule = withAndroidModels()
      val wrappedRules: TestRule = RuleChain.outerRule(EdtAndroidProjectRule(projectRule)).around(EdtRule())!!
      return object : IntegrationTestEnvironmentRule, TestRule by wrappedRules {
        override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
        override fun <T : Any> replaceService(serviceType: Class<T>, newServiceInstance: T) =
          projectRule.replaceService(serviceType, newServiceInstance)
        override val testRootDisposable: Disposable get() = projectRule.testRootDisposable
      }
    }

    @JvmStatic
    fun testProject(testProjectDefinition: TestProjectDefinition): Typed<JavaCodeInsightTestFixture, TestProjectTestHelpers> {
      val testEnvironmentRule = TestEnvironmentRuleImpl(withAndroidSdk = true)
      val fixtureRule = TestProjectFixtureRuleImpl(testProjectDefinition)
      val projectEnvironmentRule = ProjectEnvironmentRuleImpl { fixtureRule.fixture.project }
      return chain(
        testEnvironmentRule,
        fixtureRule,
        projectEnvironmentRule,
        edtRule = EdtRule(),
        tools = object : TestProjectTestHelpers {
          override val projectRoot: File get() = fixtureRule.projectRoot
          override fun selectModule(module: Module) {
            fixtureRule.selectModule(module)
          }
        }
      )
    }
  }

  fun <T : Any> replaceProjectService(serviceType: Class<T>, newServiceInstance: T) {
    project.replaceService(serviceType, newServiceInstance, testRootDisposable)
  }

  fun <T : Any> replaceService(serviceType: Class<T>, newServiceInstance: T) {
    ApplicationManager.getApplication().replaceService(serviceType, newServiceInstance, testRootDisposable)
  }

  fun <T : Any> mockService(serviceType: Class<T>): T {
    val mock = Mockito.mock(serviceType)
    ApplicationManager.getApplication().replaceService(serviceType, mock, testRootDisposable)
    return mock
  }

  fun <T : Any> mockProjectService(serviceType: Class<T>): T {
    val mock = Mockito.mock(serviceType)
    project.replaceService<T>(serviceType, mock, testRootDisposable)
    return mock
  }

  fun <T : Any> registerExtension(epName: ExtensionPointName<T>, extension: T) =
    project.registerExtension(epName, extension, fixture.projectDisposable)

  fun setupProjectFrom(vararg moduleBuilders: ModuleModelBuilder) {
    val basePath = File(fixture.tempDirPath)
    setupTestProjectFromAndroidModel(project, basePath, *moduleBuilders)
  }

  /** Waits 2 seconds for the app resource repository to finish currently pending updates. */
  @Throws(InterruptedException::class, TimeoutException::class)
  fun waitForResourceRepositoryUpdates() {
    waitForResourceRepositoryUpdates(module)
  }

  interface TestProjectTestHelpers {
    val projectRoot: File
    fun selectModule(module: Module)
  }
}

/**
 * The outer rule in the default implementation of the [AndroidProjectRule] chain of rules.
 *
 * [TestEnvironmentRule] is supposed to set up the Android Studio test environment which does not require IntelliJ's test application
 * being initialized.
 */
internal interface TestEnvironmentRule : TestRule

/**
 * The inner rule in the default implementation of the [AndroidProjectRule] chain of rules.
 *
 * [FixtureRule] is supposed to set up the project and code insight fixtures used by the test. .
 */
internal interface FixtureRule<T: CodeInsightTestFixture> : TestRule {
  var initAndroid: Boolean
  var fixtureName: String?
  var projectDescriptor: AdtTestProjectDescriptor

  val testRootDisposable: Disposable
  val fixture: T
}

/**
 * The innermost rule in the default implementation of the [AndroidProjectRule] chain of rules.
 *
 * [ProjectEnvironmentRule] is supposed to apply project specific test environment settings.
 */
internal interface ProjectEnvironmentRule : TestRule

/**
 * Combines implementations of the standard [AndroidProjectRule] chain components into a functioning rule.
 *
 * Note: This utility provides a common structure to [AndroidProjectRule] variations, however custom implementations that do not follow
 *       this pattern are possible.
 */
private fun <T : CodeInsightTestFixture, H> chain(
  testEnvironmentRule: TestEnvironmentRule,
  fixtureRule: FixtureRule<T>,
  projectEnvironmentRule: ProjectEnvironmentRule,
  edtRule: EdtRule? = null,
  tools: H? = null
): AndroidProjectRule.Typed<T, H> {
  val chain = RuleChain
    .outerRule(testEnvironmentRule)
    .maybeAround(edtRule)
    .around(fixtureRule)
    .around(projectEnvironmentRule)
  return object : AndroidProjectRule.Typed<T, H>, TestRule by chain {
    override val testRootDisposable: Disposable
      get() = fixtureRule.testRootDisposable

    override val fixture: T
      get() = fixtureRule.fixture

    override fun initAndroid(shouldInit: Boolean): AndroidProjectRule.Typed<T, H> {
      fixtureRule.initAndroid = shouldInit
      return this
    }

    override fun named(projectName: String?): AndroidProjectRule.Typed<T, H> {
      fixtureRule.fixtureName = projectName
      return this
    }

    override fun withKotlin(descriptor: KotlinAdtTestProjectDescriptor?): AndroidProjectRule.Typed<T, H> {
      fixtureRule.projectDescriptor = descriptor ?: AdtTestProjectDescriptors.java()
      return this
    }

    override val testHelpers: H
      get() = tools ?: error("Tools not available")
  }
}


class TestEnvironmentRuleImpl(
  val withAndroidSdk: Boolean
) : NamedExternalResource(), TestEnvironmentRule {
  private val flagsDisposable: Disposable = Disposer.newDisposable()
  val mockitoCleaner = MockitoThreadLocalsCleaner()
  private var userHome: String? = null

  override fun before(description: Description) {
    try {
      doBeforeActions(description)
    } catch (t: Throwable) {
      // cleanup if init failed
      mockitoCleaner.cleanupAndTearDown()
      throw t
    }
  }

  private fun doBeforeActions(description: Description) {
    mockitoCleaner.setup()

    userHome = System.getProperty("user.home")
    val testSpecificName = UsefulTestCase.TEMP_DIR_MARKER + description.testClass.simpleName.substringAfterLast('$')
    // Reset user home directory.
    System.setProperty("user.home", FileUtils.join(FileUtil.getTempDirectory(), testSpecificName, "nonexistent_user_home"))

    // Disable antivirus checks on Windows.
    StudioFlags.ANTIVIRUS_METRICS_ENABLED.override(false, flagsDisposable)
    StudioFlags.ANTIVIRUS_NOTIFICATION_ENABLED.override(false, flagsDisposable)
  }

  override fun after(description: Description) {
    runInEdtAndWait {
      if (withAndroidSdk) {
        removeAllAndroidSdks()
      }
    }
    userHome?.let { System.setProperty("user.home", it) } ?: System.clearProperty("user.home")
    mockitoCleaner.cleanupAndTearDown()
    runInEdtAndWait { Disposer.dispose(flagsDisposable) }
    checkUndisposedAndroidRelatedObjects()
  }
}

typealias FixtureFactory<T> =
  (projectName: String, projectDescriptor: AdtTestProjectDescriptor) -> T

class FixtureRuleImpl<T: CodeInsightTestFixture>(
  /**
   * A method to create [CodeInsightTestFixture] instance.
   */
  private val fixtureFactory: FixtureFactory<T>,

  /**
   * True if this rule should include an Android SDK.
   */
  private val withAndroidSdk: Boolean = false,

  /**
   * Version of the android platform to use e.g. `android-33`.
   * Only has effect when `withAndroidSdk = true`.
   */
  private val androidPlatformVersion: AndroidVersion = Sdks.getLatestAndroidPlatform(),

  /**
   * true iff the default module should be a valid Android module
   * (if it should have an Android manifest and the Android facet attached).
   */
  override var initAndroid: Boolean = true,

  /**
   * Name of the fixture used to create the project directory when not
   * using a light fixture.
   *
   * Default is the test class' short name.
   */
  override var fixtureName: String? = null,

  /**
   * The project descriptor to use to set up language features and standard libraries.
   */
  override var projectDescriptor: AdtTestProjectDescriptor = AdtTestProjectDescriptors.default(),
) : NamedExternalResource(), FixtureRule<T> {

  override val testRootDisposable: Disposable
    get() = fixture.testRootDisposable

  lateinit var _fixture: T
  override val fixture: T get() = _fixture

  val module: Module get() = fixture.module
  val project: Project get() = fixture.project

  val mockitoCleaner = MockitoThreadLocalsCleaner()
  private val facets = ArrayList<Facet<*>>()


  override fun before(description: Description) {
    try {
      doBeforeActions(description)
    } catch (t: Throwable) {
      // cleanup if init failed
      mockitoCleaner.cleanupAndTearDown()
      throw t
    }
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
    }
    fixture.tearDown()
  }

  private fun doBeforeActions(description: Description) {
    val projectName = fixtureName ?: description.shortDisplayName
    _fixture = fixtureFactory(projectName, projectDescriptor)

    fixture.setUp()
    // Initialize an Android manifest
    if (initAndroid) {
      addFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME)
    }

    // Wait for indexing to finish, especially if an SDK was added to the classpath.
    IndexingTestUtil.waitUntilIndexesAreReady(fixture.project)
  }

  private fun <T : Facet<C>, C : FacetConfiguration> addFacet(type: FacetType<T, C>, facetName: String): T {
    val facetManager = FacetManager.getInstance(module)
    val facet = facetManager.createFacet<T, C>(type, facetName, null)
    runInEdtAndWait {
      if (withAndroidSdk) {
        Sdks.addAndroidSdk(fixture.testRootDisposable, module, androidPlatformVersion)
      }
      val facetModel = facetManager.createModifiableModel()
      facetModel.addFacet(facet)
      ApplicationManager.getApplication().runWriteAction { facetModel.commit() }
      facets.add(facet)
    }
    return facet
  }

}

class ProjectEnvironmentRuleImpl(
  private val project: () -> Project
) : NamedExternalResource(), ProjectEnvironmentRule {

  override fun before(description: Description) {
    // Apply Android Studio code style settings (tests running as the Android plugin in IDEA should behave the same)
    val settings = CodeStyle.getSettings(project()).clone()
    applyAndroidCodeStyleSettings(settings)
    CodeStyleSettingsManager.getInstance(project()).setTemporarySettings(settings)

    AndroidTestCase.registerLongRunningThreads()
  }

  override fun after(description: Description) {
    CodeStyleSettingsManager.getInstance(project()).dropTemporarySettings()
  }
}


private fun createLightFixture(projectName: String, projectDescriptor: AdtTestProjectDescriptor): CodeInsightTestFixture {
  // This is a very abstract way to initialize a new Project and a single Module.
  val factory = IdeaTestFixtureFactory.getFixtureFactory()
  val projectBuilder = factory.createLightFixtureBuilder(projectDescriptor, projectName)
  return factory.createCodeInsightFixture(projectBuilder.fixture, LightTempDirTestFixtureImpl(true))
}

private fun createJavaCodeInsightTestFixture(
  projectName: String,
): Pair<TestFixtureBuilder<IdeaProjectTestFixture>, JavaCodeInsightTestFixture> {
  val name = projectName
  val tempDirFixture = AndroidProjectRuleTempDirectoryFixture(name)
  val projectBuilder = IdeaTestFixtureFactory
    .getFixtureFactory()
    .createFixtureBuilder(name, tempDirFixture.projectDir.parentFile.toPath(), true)

  val javaCodeInsightTestFixture = JavaTestFixtureFactory
    .getFixtureFactory()
    .createCodeInsightFixture(projectBuilder.fixture, tempDirFixture)

  return projectBuilder to javaCodeInsightTestFixture
}

/**
 * A [com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl] that creates a unique temp directory for each test and deletes it
 * at [tearDown].
 */
internal class AndroidProjectRuleTempDirectoryFixture(name: String) : AndroidTempDirTestFixture(name) {
  private val tempRoot: String =
    FileUtil.createTempDirectory("${UsefulTestCase.TEMP_DIR_MARKER}${Clock.systemUTC().millis()}", null, false).path

  override fun getRootTempDirectory(): String = tempRoot
  override fun tearDown() {
    super.tearDown()  // Deletes the project directory.
    try {
      // Delete the temp directory where the project directory was created.
      runWriteAction { VfsUtil.createDirectories(tempRoot).delete(this) }
    } catch (e: Throwable) {
      addSuppressedException(e);
    }
  }
}

private fun createHeavyFixtureFactory(
  extraModuleNames: List<String>,
): FixtureFactory<JavaCodeInsightTestFixture> {
  fun createHeavyFixture(
    projectName: String,
    projectDescriptor: AdtTestProjectDescriptor,
  ): JavaCodeInsightTestFixture {
    IdeaTestFixtureFactory.getFixtureFactory().registerFixtureBuilder(
      AndroidTestCase.AndroidModuleFixtureBuilder::class.java,
      AndroidTestCase.AndroidModuleFixtureBuilderImpl::class.java
    )

    val (projectBuilder, javaCodeInsightTestFixture) = createJavaCodeInsightTestFixture(projectName)

    val moduleFixtureBuilder =
      projectBuilder.addModule(AndroidTestCase.AndroidModuleFixtureBuilder::class.java)
    moduleFixtureBuilder.setProjectDescriptor(projectDescriptor)
    initializeModuleFixtureBuilderWithSrcAndGen(
      moduleFixtureBuilder,
      javaCodeInsightTestFixture.tempDirPath,
    )

    for (extraModuleName in extraModuleNames) {
      val moduleRootPath =
        "${javaCodeInsightTestFixture.tempDirPath}/${extraModuleName}"
      File(moduleRootPath).mkdirs()
      val extraModuleFixtureBuilder =
        projectBuilder.addModule(AndroidTestCase.AndroidModuleFixtureBuilder::class.java)
      extraModuleFixtureBuilder.setModuleName(extraModuleName)
      initializeModuleFixtureBuilderWithSrcAndGen(extraModuleFixtureBuilder, moduleRootPath)
    }

    return javaCodeInsightTestFixture
  }

  return ::createHeavyFixture
}

/**
 * Create a project using [JavaCodeInsightTestFixture] with an Android module.
 * The project is created on disk under the /tmp folder
 */
private fun createJavaCodeInsightTestFixtureAndModels(
  projectName: String,
  projectModuleBuilders: List<ModuleModelBuilder>,
  prepareProjectSourcesWith: ((File) -> Unit)? = null
): JavaCodeInsightTestFixture {
  val (projectBuilder, javaCodeInsightTestFixture) = createJavaCodeInsightTestFixture(projectName)

  return object : JavaCodeInsightTestFixture by javaCodeInsightTestFixture {
    override fun getTestRootDisposable(): Disposable = javaCodeInsightTestFixture.testRootDisposable  // KT-18324

    override fun setUp() {
      javaCodeInsightTestFixture.setUp()
      prepareSdksForTests(javaCodeInsightTestFixture)
      invokeAndWaitIfNeeded {
        // Similarly to AndroidGradleTestCase, sync (fake sync here) requires SDKs to be set up and cleaned after the test to behave
        // properly.
        val basePath = File(javaCodeInsightTestFixture.tempDirPath)
        prepareProjectSourcesWith?.let { it(basePath) }
        if (projectModuleBuilders.isNotEmpty()) {
          setupTestProjectFromAndroidModel(project, basePath, *projectModuleBuilders.toTypedArray())
        }
      }
    }
  }
}

interface IntegrationTestEnvironmentRule : IntegrationTestEnvironment, TestRule {
  val testRootDisposable: Disposable
  fun <T : Any> replaceService(serviceType: Class<T>, newServiceInstance: T)
}

class EdtAndroidProjectRule(val projectRule: AndroidProjectRule) :
  TestRule by RuleChain.outerRule(projectRule).around(EdtRule())!! {
  val project: Project get() = projectRule.project
  val fixture: CodeInsightTestFixture get() = projectRule.fixture
  val testRootDisposable: Disposable get() = projectRule.testRootDisposable
  fun setupProjectFrom(vararg moduleBuilders: ModuleModelBuilder) = projectRule.setupProjectFrom(*moduleBuilders)
}

fun AndroidProjectRule.onEdt(): EdtAndroidProjectRule = EdtAndroidProjectRule(this)

internal fun removeAllAndroidSdks() {
  val sdks = AndroidSdks.getInstance().allAndroidSdks
  for (sdk in sdks) {
    WriteAction.runAndWait<RuntimeException> {
      ProjectJdkTable.getInstance().removeJdk(sdk!!)
    }
  }
}

private fun prepareSdksForTests(javaCodeInsightTestFixture: JavaCodeInsightTestFixture) {
  if (IdeSdks.getInstance().androidSdkPath != TestUtils.getSdk()) {
    println("Tests: Replacing Android SDK from ${IdeSdks.getInstance().androidSdkPath} to ${TestUtils.getSdk()}")
    AndroidGradleTests.setUpSdks(javaCodeInsightTestFixture, TestUtils.getSdk().toFile())
  }
}

private fun RuleChain.maybeAround(rule: TestRule?): RuleChain {
  return if (rule != null) around(rule) else this
}