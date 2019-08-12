/*
 * Copyright (C) 2019 The Android Open Source Project
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

@file:JvmName("TemplateTestUtils")

package com.android.tools.idea.templates

import com.android.SdkConstants
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.SdkVersionInfo
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.idea.gradle.project.common.GradleInitScripts
import com.android.tools.idea.lint.LintIdeClient
import com.android.tools.idea.lint.LintIdeIssueRegistry
import com.android.tools.idea.lint.LintIdeRequest
import com.android.tools.idea.npw.FormFactor.Companion.get
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.templates.Parameter.Type
import com.android.tools.idea.templates.Template.CATEGORY_APPLICATION
import com.android.tools.idea.templates.Template.CATEGORY_PROJECTS
import com.android.tools.idea.templates.TemplateMetadata.ATTR_ANDROIDX_SUPPORT
import com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_TOOLS_VERSION
import com.android.tools.idea.templates.TemplateMetadata.ATTR_KOTLIN_VERSION
import com.android.tools.idea.templates.TemplateMetadata.ATTR_LANGUAGE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_MODULE_NAME
import com.android.tools.idea.templates.TemplateMetadata.ATTR_PACKAGE_NAME
import com.android.tools.idea.templates.TemplateTestBase.ATTR_CREATE_ACTIVITY
import com.android.tools.idea.templates.TemplateTestBase.CHECK_LINT
import com.android.tools.idea.templates.TemplateTestBase.TEST_FEWER_API_VERSIONS
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.templates.recipe.RenderingContext.Builder
import com.android.tools.idea.wizard.WizardConstants.MODULE_TEMPLATE_NAME
import com.android.tools.lint.checks.ManifestDetector
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplateRenderer
import com.google.wireless.android.sdk.stats.KotlinSupport
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.util.WaitFor
import junit.framework.TestCase
import junit.framework.TestCase.assertTrue
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.inspections.lint.ProblemData
import org.jetbrains.android.sdk.AndroidSdkData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlin.streams.toList

/**
 * Is the given api level interesting for testing purposes? This is used to skip gaps,
 * such that we for example only check say api 14, 16, 21, 23, etc -- versions where the **templates** are doing conditional changes.
 *
 * Note: To be EXTRA comprehensive, occasionally try returning true unconditionally here to test absolutely everything.
 */
internal fun isInterestingApiLevel(api: Int, manualApi: Int, apiSensitiveTemplate: Boolean): Boolean = when {
  // If a manual api version was specified then accept only that version
  manualApi > 0 -> api == manualApi
  // For templates that aren't API sensitive, only test with latest API
  !apiSensitiveTemplate -> api == SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
  // Always accept the highest known version
  api == SdkVersionInfo.HIGHEST_KNOWN_STABLE_API -> true
  // Relevant versions, used to prune down the set of targets we need to check on.
  // This is determined by looking at the minApi and minBuildApi versions found in the template.xml files.
  else -> when (api) {
    14, 16, 21, 23 -> true
    25, 28 -> !TEST_FEWER_API_VERSIONS
    else -> false
  }
}

internal fun createNewProjectState(createWithProject: Boolean,
                                   sdkData: AndroidSdkData,
                                   moduleTemplate: Template): TestNewProjectWizardState {
  val projectState = TestNewProjectWizardState(moduleTemplate)
  val moduleState = projectState.moduleTemplateState.apply {
    Template.convertApisToInt(parameters)
    put(ATTR_CREATE_ACTIVITY, createWithProject)
    put(ATTR_MODULE_NAME, "TestModule")
    put(ATTR_PACKAGE_NAME, "test.pkg")
  }
  TemplateValueInjector(moduleState.parameters).addGradleVersions(null)
  val buildTool = sdkData.getLatestBuildTool(false)
  if (buildTool != null) {
    moduleState.put(ATTR_BUILD_TOOLS_VERSION, buildTool.revision.toString())
  }
  return projectState
}

internal fun getModuleTemplateForFormFactor(templateFile: File): Template {
  val activityTemplate = Template.createFromPath(templateFile)
  val moduleTemplate = defaultModuleTemplate
  val activityMetadata = activityTemplate.metadata!!
  val activityFormFactorName = activityMetadata.formFactor ?: return moduleTemplate
  val activityFormFactor = get(activityFormFactorName)
  val manager = TemplateManager.getInstance()
  val applicationTemplates = manager!!.getTemplatesInCategory(CATEGORY_APPLICATION)
  for (formFactorTemplateFile in applicationTemplates) {
    val metadata = manager.getTemplateMetadata(formFactorTemplateFile!!)
    if (metadata?.formFactor != null && get(metadata.formFactor!!) === activityFormFactor) {
      return Template.createFromPath(formFactorTemplateFile)
    }
  }
  return moduleTemplate
}

val defaultModuleTemplate: Template get() = Template.createFromName(CATEGORY_PROJECTS, MODULE_TEMPLATE_NAME)

@JvmOverloads
internal fun createRenderingContext(
  projectTemplate: Template, project: Project, projectRoot: File, moduleRoot: File, parameters: Map<String, Any> = mapOf()
): RenderingContext = Builder.newContext(projectTemplate, project)
  .withOutputRoot(projectRoot)
  .withModuleRoot(moduleRoot)
  .withParams(parameters)
  .build()

/**
 * Runs lint and returns a message with information about the first issue with severity at least X or null if there are no such issues.
 */
internal fun getLintIssueMessage(project: Project, maxSeverity: Severity, ignored: Set<Issue>): String? {
  val registry = LintIdeIssueRegistry()
  val map = mutableMapOf<Issue, Map<File, List<ProblemData>>>()
  val client = LintIdeClient.forBatch(project, map, AnalysisScope(project), registry.issues.toSet())
  val modules = ModuleManager.getInstance(project).modules.toList()
  val request = LintIdeRequest(client, project, null, modules, false)
  val scope = EnumSet.allOf(Scope::class.java).apply {
    remove(Scope.CLASS_FILE)
    remove(Scope.ALL_CLASS_FILES)
    remove(Scope.JAVA_LIBRARIES)
  }
  request.setScope(scope)
  LintDriver(registry, client, request).analyze()
  map.values.forEach { fileListMap ->
    fileListMap.forEach { (file, problems) ->
      val problem = problems.filterNot { it.issue in ignored }.firstOrNull { it.issue.defaultSeverity < maxSeverity }
      if (problem != null) {
        return "Found lint issue ${problem.issue.id} with severity ${problem.issue.defaultSeverity} in $file at ${problem.textRange}: ${problem.message}"
      }
    }
  }
  return null
}

internal fun setAndroidSupport(setAndroidx: Boolean, moduleState: TestTemplateWizardState, activityState: TestTemplateWizardState?) {
  moduleState.put(ATTR_ANDROIDX_SUPPORT, setAndroidx)
  activityState?.put(ATTR_ANDROIDX_SUPPORT, setAndroidx)
}

/**
 * Validates this template to make sure it's supported
 *
 * @param currentMinSdk the minimum SDK in the project, or -1 or 0 if unknown (e.g. codename)
 * @param buildApi      the build API, or -1 or 0 if unknown (e.g. codename)
 * @return an error message, or null if there is no problem
 */
internal fun validateTemplate(metadata: TemplateMetadata, currentMinSdk: Int, buildApi: Int): String? {
  val templateMinSdk = metadata.minSdk
  val templateMinBuildApi = metadata.minBuildApi
  return when {
    !metadata.isSupported -> "This template requires a more recent version of Android Studio. Please update."
    currentMinSdk in 1 until templateMinSdk ->
      "This template requires a minimum SDK version of at least $templateMinSdk, and the current min version is $currentMinSdk"
    buildApi in 1 until templateMinBuildApi ->
      "This template requires a build target API version of at least $templateMinBuildApi, and the current version is $buildApi"
    else -> null
  }
}

private const val specialChars = "!@#$^&()_+=-.`~"
private const val nonAsciiChars = "你所有的基地都属于我们"
internal fun getModifiedProjectName(projectName: String, activityState: TestTemplateWizardState?): String = when {
  SystemInfo.isWindows -> "app"
  // Bug 137161906
  projectName.startsWith("BasicActivity") && activityState != null &&
  Language.KOTLIN.toString() == activityState.getString(ATTR_LANGUAGE) -> projectName
  else -> "$projectName$specialChars,$nonAsciiChars"
}

/**
 * Checks that the most recent log in usageTracker is a [EventKind.TEMPLATE_RENDER] event with expected info.
 *
 * @param templateRenderer the expected value of usage.getStudioEvent().getTemplateRenderer(), where usage is the most recent logged usage
 * @param paramMap         the paramMap, containing kotlin support info for template render event
 */
internal fun verifyLastLoggedUsage(usageTracker: TestUsageTracker, templateRenderer: TemplateRenderer, paramMap: Map<String, Any>) {
  val usages = usageTracker.usages
  assertFalse(usages.isEmpty())
  // get last logged usage
  val usage = usages.last()
  assertEquals(EventKind.TEMPLATE_RENDER, usage!!.studioEvent.kind)
  assertEquals(templateRenderer, usage.studioEvent.templateRenderer)
  assertTrue(paramMap.getOrDefault(ATTR_KOTLIN_VERSION, "unknown") is String)
  assertEquals(KotlinSupport.newBuilder()
                 .setIncludeKotlinSupport(paramMap.getOrDefault(ATTR_LANGUAGE, "Java") == Language.KOTLIN.toString())
                 .setKotlinSupportVersion(paramMap.getOrDefault(ATTR_KOTLIN_VERSION, "unknown") as String).build(),
               usage.studioEvent.kotlinSupport)
}

fun Parameter.getDefaultValue(templateState: TestTemplateWizardState): Any? {
  requireNotNull(id)
  return templateState.get(id!!) ?: if (type === Type.BOOLEAN)
    initial!!.toBoolean()
  else
    initial
}

// TODO(qumeric) should it be removed in favor of AndroidGradleTestCase.setUpFixture?
internal fun setUpFixtureForProject(projectName: String): JavaCodeInsightTestFixture {
  val factory = IdeaTestFixtureFactory.getFixtureFactory()
  val projectBuilder = factory.createFixtureBuilder(projectName)
  return JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.fixture).apply {
    setUp()
  }
}

@UiThread
internal fun addIconsIfNecessary(activityState: TestTemplateWizardState) {
  if (activityState.templateMetadata == null || activityState.templateMetadata!!.iconName == null) {
    return
  }
  val drawableFolder = File(FileUtil.join(activityState.getString(TemplateMetadata.ATTR_RES_OUT)), FileUtil.join("drawable"))
  drawableFolder.mkdirs()
  val fileName = StringEvaluator().evaluate(activityState.templateMetadata!!.iconName!!, activityState.parameters)
  val sourceFile = File(AndroidTestBase.getTestDataPath(), FileUtil.join("drawables", "progress_horizontal.xml"))
  val iconFile = File(drawableFolder, fileName + SdkConstants.DOT_XML)
  FileUtil.copy(sourceFile, iconFile)
}

internal fun verifyLanguageFiles(projectDir: File, language: Language) {
  // Note: Files.walk() stream needs to be closed (or consumed completely), otherwise it will leave locked directories on Windows
  val allPaths = Files.walk(projectDir.toPath()).toList()
  if (language === Language.KOTLIN) {
    assertFalse(allPaths.any { it.toString().endsWith(".java") })
    assertTrue(allPaths.any { it.toString().endsWith(".kt") })
  }
  else {
    assertFalse(allPaths.any { it.toString().endsWith(".kt") })
    assertTrue(allPaths.any { it.toString().endsWith(".java") })
  }
}

internal fun invokeGradleForProjectDir(projectRoot: File) {
  val connection = (GradleConnector.newConnector() as DefaultGradleConnector).apply {
    forProjectDirectory(projectRoot)
    daemonMaxIdleTime(10000, TimeUnit.MILLISECONDS)
  }.connect()
  val buildLauncher = connection.newBuild().forTasks("assembleDebug")
  val commandLineArguments = mutableListOf<String>()
  GradleInitScripts.getInstance().addLocalMavenRepoInitScriptCommandLineArg(commandLineArguments)
  buildLauncher.withArguments(commandLineArguments)
  val baos = ByteArrayOutputStream()
  try {
    buildLauncher.setStandardOutput(baos).setStandardError(baos).run()
  }
  //// Use the following commented out code to debug the generated project in case of a failure.
  catch (e: Exception) {
    //  File tmpDir = new File("/tmp", "Test-Dir-" + projectName);
    //  FileUtil.copyDir(new File(projectDir, ".."), tmpDir);
    //  System.out.println("Failed project copied to: " + tmpDir.getAbsolutePath());
    throw RuntimeException(baos.toString("UTF-8"), e)
  }
  finally {
    shutDownGradleConnection(connection, projectRoot)
  }
}

internal fun shutDownGradleConnection(connection: ProjectConnection, projectRoot: File) {
  connection.close()
  // Windows work-around: After closing the gradle connection, it's possible that some files (eg local.properties) are locked
  // for a bit of time. It is also possible that there are Virtual Files that are still synchronizing to the File System, this will
  // break tear-down, when it tries to delete the project.
  if (SystemInfo.isWindows) {
    println("Windows: Attempting to delete project Root - $projectRoot")
    object : WaitFor(60000) {
      override fun condition(): Boolean {
        if (!FileUtil.delete(projectRoot)) {
          println("Windows: delete project Root failed - time = " + System.currentTimeMillis())
        }
        return projectRoot.mkdir()
      }
    }
  }
}

internal fun lintIfNeeded(project: Project) {
  if (CHECK_LINT) {
    val lintMessage = getLintIssueMessage(project, Severity.INFORMATIONAL, setOf(ManifestDetector.TARGET_NEWER))
    if (lintMessage != null) {
      TestCase.fail(lintMessage)
    }
    // TODO: Check for other warnings / inspections, such as unused imports?
  }
}

internal fun cleanupProjectFiles(projectDir: File) {
  if (projectDir.exists()) {
    FileUtil.delete(projectDir)
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectDir)
  }
}

data class Option(@JvmField val id: String, @JvmField val minSdk: Int, @JvmField val minBuild: Int)

internal fun getOption(option: Element): Option {
  fun readSdkAttribute(sdkAttr: String): Int {
    val sdkString: String = option.getAttribute(sdkAttr).orEmpty()
    return if (sdkString.isNotEmpty())
      sdkString.toInt() // Templates aren't allowed to contain codenames, should  always be an integer
    else
      1
  }

  val optionId: String = option.getAttribute(SdkConstants.ATTR_ID)
  val optionMinSdk = readSdkAttribute(TemplateMetadata.ATTR_MIN_API)
  val optionMinBuildApi = readSdkAttribute(TemplateMetadata.ATTR_MIN_BUILD_API)

  return Option(optionId, optionMinSdk, optionMinBuildApi)
}

internal fun getCheckKey(category: String, name: String, createWithProject: Boolean) = "$category:$name:$createWithProject"
