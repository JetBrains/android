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

import com.android.sdklib.AndroidVersion
import com.android.sdklib.SdkVersionInfo
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelImpl
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultModuleTemplate
import com.android.tools.idea.lint.common.LintBatchResult
import com.android.tools.idea.lint.common.LintIdeRequest
import com.android.tools.idea.lint.common.LintIdeSupport
import com.android.tools.idea.lint.common.LintProblemData
import com.android.tools.idea.npw.model.titleToTemplateRenderer
import com.android.tools.idea.npw.model.titleToTemplateType
import com.android.tools.idea.npw.template.ModuleTemplateDataBuilder
import com.android.tools.idea.npw.template.ProjectTemplateDataBuilder
import com.android.tools.idea.templates.KeystoreUtils.getOrCreateDefaultDebugKeystore
import com.android.tools.idea.templates.KeystoreUtils.sha1
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.wizard.template.ApiTemplateData
import com.android.tools.idea.wizard.template.ApiVersion
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.ThemesData
import com.android.tools.idea.wizard.template.ViewBindingSupport
import com.android.tools.lint.checks.ManifestDetector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.TemplateType.CUSTOM_TEMPLATE
import com.google.wireless.android.sdk.stats.KotlinSupport
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import junit.framework.TestCase
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import java.io.File
import java.nio.file.Files
import java.util.EnumSet
import kotlin.streams.toList

/**
 * The following templates are known to be broken! We need to work through these and fix them such that tests on them can be re-enabled.
 */
internal fun isBroken(templateName: String): Boolean {
  if (SystemInfo.isWindows) {
    if ("AIDL File" == templateName) return true // b/37139315
    if ("Native C++" == templateName) return true // b/158067606
    if ("Game Activity (C++)" == templateName) return true // b/158067606
  }

  return false
}

/**
 * Runs lint and returns a message with information about the first issue with severity at least X or null if there are no such issues.
 */
internal fun getLintIssueMessage(project: Project, maxSeverity: Severity, ignored: Set<Issue>): String? {
  val registry = LintIdeSupport.get().getIssueRegistry()
  val map = mutableMapOf<Issue, Map<File, List<LintProblemData>>>()
  val result = LintBatchResult(project, map, AnalysisScope(project), registry.issues.toSet())
  val client = LintIdeSupport.get().createBatchClient(result)
  val modules = ModuleManager.getInstance(project).modules.toList()
  val request = LintIdeRequest(client, project, null, modules, false)
  val scope = EnumSet.allOf(Scope::class.java).apply {
    remove(Scope.CLASS_FILE)
    remove(Scope.ALL_CLASS_FILES)
    remove(Scope.JAVA_LIBRARIES)
  }
  request.setScope(scope)
  client.createDriver(request, registry).analyze()
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

private const val specialChars = "!@#$^&()_+=-.`~"
private const val nonAsciiChars = "你所有的基地都属于我们"
internal fun getModifiedModuleName(moduleName: String, avoidModifiedModuleName: Boolean): String {
  if (SystemInfo.isWindows){
    if (moduleName.startsWith("Native C++")) return moduleName // cmake can't handle especial path chars
  }
  if (avoidModifiedModuleName) {
    // Avoid special chars if view binding is used because kapt doesn't recognize special chars b/156452586
    return moduleName
  }
  return "$moduleName$specialChars,$nonAsciiChars"
}

/**
 * Checks that the most recent log in usageTracker is a [EventKind.TEMPLATE_RENDER] event with expected info.
 *
 * @param templateName Template name/title
 * @param formFactor Template Form Factor
 * @param moduleState  the module state, containing kotlin support info for template render event
 */
internal fun verifyLastLoggedUsage(usageTracker: TestUsageTracker, templateName: String, formFactor: FormFactor, moduleState: ModuleTemplateData) {
  val usage = usageTracker.usages.last { it.studioEvent.kind == EventKind.TEMPLATE_RENDER }!!
  assertEquals(EventKind.TEMPLATE_RENDER, usage.studioEvent.kind)

  val templateRenderer = titleToTemplateRenderer(templateName, formFactor)
  assertEquals(templateRenderer, usage.studioEvent.templateRenderer)

  val templateType = titleToTemplateType(templateName, formFactor)
  assertNotEquals("Template '$templateName' missing metrics", CUSTOM_TEMPLATE, templateType)
  assertEquals(KotlinSupport.newBuilder()
                 .setIncludeKotlinSupport(moduleState.projectTemplateData.language == Language.Kotlin)
                 .setKotlinSupportVersion(moduleState.projectTemplateData.kotlinVersion).build(),
               usage.studioEvent.kotlinSupport)
}

internal fun verifyLanguageFiles(projectDir: File, language: Language) {
  // Note: Files.walk() stream needs to be closed (or consumed completely), otherwise it will leave locked directories on Windows
  val allPaths = Files.walk(projectDir.toPath())
    .filter {!it.endsWith("LibrariesForLibs.java")} // Exclude the file generated by using Version Catalogs.
    .toList()
  val wrongLanguageExtension = if (language == Language.Kotlin) ".java" else ".kt"
  assertTrue(allPaths.none { it.toString().endsWith(wrongLanguageExtension) })
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

internal fun checkDslParser(project: Project) {
  val projectBuildModel = ProjectBuildModel.get(project)
  val allModels = projectBuildModel.allIncludedBuildModels
  val unresolvedDependencies = allModels.flatMap {
    when (it) {
      is GradleFileModelImpl ->
        it.dslFile.context.dependencyManager.myUnresolvedReferences.entries.flatMap { entry -> entry.component2() }
      else -> listOf()
    }
  }
  assertEmpty(unresolvedDependencies)
}

private const val defaultPackage = "template.test.pkg"

internal fun getDefaultModuleState(project: Project, template: Template): ModuleTemplateDataBuilder {
  // TODO(qumeric): is always new?
  val projectStateBuilder = ProjectTemplateDataBuilder(true).apply {
    androidXSupport = true
    setProjectDefaults(project)
    language = Language.Java
    topOut = project.guessProjectDir()!!.toIoFile()
    debugKeyStoreSha1 = sha1(getOrCreateDefaultDebugKeystore())
    applicationPackage = defaultPackage
    overridePathCheck = true // To disable android plugin checking for ascii in paths (windows tests)
  }

  val minSdk = template.minSdk.coerceAtLeast(AndroidVersion.VersionCodes.M)
  return ModuleTemplateDataBuilder(
    projectStateBuilder,
    isNewModule = true,
    viewBindingSupport = ViewBindingSupport.SUPPORTED_4_0_MORE).apply { name = "Template test module"
    packageName = defaultPackage
    val paths = createDefaultModuleTemplate(project, name!!).paths
    setModuleRoots(paths, projectTemplateDataBuilder.topOut!!.path, name!!, packageName!!)
    isLibrary = false
    formFactor = template.formFactor
    category = template.category
    themesData = ThemesData("App")
    apis = ApiTemplateData(
      buildApi = ApiVersion(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API, SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString()),
      targetApi = ApiVersion(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API, SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString()),
      minApi = ApiVersion(minSdk, minSdk.toString()),
      // The highest supported/recommended appCompact version is P(28)
      appCompatVersion = SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.coerceAtMost(AndroidVersion.VersionCodes.P)
    )
  }
}
