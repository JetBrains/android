/*
 * Copyright (C) 2025 The Android Open Source Project
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
@file:Suppress("DEPRECATION")

package com.android.tools.idea.refactoring.catalog

import com.android.SdkConstants.FD_GRADLE
import com.android.SdkConstants.FN_VERSION_CATALOG
import com.android.ide.common.gradle.Dependency
import com.android.ide.common.gradle.RichVersion
import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.pickLibraryVariableName
import com.android.ide.common.repository.pickPluginVariableName
import com.android.ide.common.repository.pickVersionVariableName
import com.android.projectmodel.ExternalAndroidLibrary
import com.android.tools.idea.lint.common.LintBatchResult
import com.android.tools.idea.lint.common.LintIdeRequest
import com.android.tools.idea.lint.common.LintIdeSupport.Companion.get as lint
import com.android.tools.idea.lint.common.LintProblemData
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.util.CommonAndroidUtil
import com.android.tools.lint.checks.GradleDetector
import com.android.tools.lint.checks.GradleDetector.Companion.GRADLE_PLUGIN_ARTIFACT_SUFFIX
import com.android.tools.lint.checks.GradleDetector.Companion.KEY_COORDINATE
import com.android.tools.lint.checks.GradleDetector.Companion.getKtsDependency
import com.android.tools.lint.checks.GradleDetector.Companion.getMavenVersion
import com.android.tools.lint.checks.GradleDetector.Companion.getNamedDependency
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleContext.Companion.getStringLiteralValue
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeText
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.util.CommonRefactoringUtil.checkReadOnlyStatus
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usageView.UsageViewUtil
import com.intellij.util.IncorrectOperationException
import java.io.File
import java.io.File.separator
import org.gradle.internal.extensions.stdlib.invert
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class MigrateToCatalogProcessor(project: Project) : BaseRefactoringProcessor(project, null) {
  interface MigrateToCatalogProcessorUsageInfo {
    var include: Boolean
  }

  class DependencyUsageInfo(
    element: PsiElement,
    start: Int,
    end: Int,
    var dependency: Dependency,
    val fromVersion: Version,
    val toVersion: Version,
    var name: String = "",
    override var include: Boolean = false,
  ) : UsageInfo(element, start, end, true), MigrateToCatalogProcessorUsageInfo {
    fun isPlugin(): Boolean {
      return dependency.isPlugin()
    }

    fun hasKnownVersion(): Boolean {
      return dependency.hasKnownVersion()
    }
  }

  private class CreateTomlFile(element: PsiFile, val contents: String) :
    UsageInfo(element), MigrateToCatalogProcessorUsageInfo {
    override var include: Boolean = true
  }

  private var elements = PsiElement.EMPTY_ARRAY

  /**
   * Whether to pick a shared version variable name for artifacts in the same group provided they
   * already have the same version.
   */
  var groupVersionVariables = true

  /** Whether to unify to a single version of each artifact across all modules. */
  var unifyVersions = true

  /**
   * If true, update the version numbers to the latest available version rather than the current
   * version.
   */
  var updateVersions = false

  /** Whether to sync the project upon completion of the migration. */
  var syncProject = false

  /** Whether to open the version catalog file in the editor upon completion. */
  var openCatalogFile = false

  /** Whether to include a particular id (group:artifact or plugin id) */
  var includeFilter: ((String) -> Boolean)? = null

  private val repository = getGoogleMavenRepository()

  override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor =
    MigrateToCatalogUsageViewDescriptor(usages)

  public override fun findUsages(): Array<UsageInfo> {
    val allUsages = computeVersionReferences()
    val usages = UsageViewUtil.removeDuplicatedUsages(allUsages.toTypedArray())
    elements = usages.map { it.element }.toTypedArray()
    return usages
  }

  private fun computeVersionReferences(): List<UsageInfo> {
    val referenceMap = computeVersionReferenceMap()
    val usages: MutableList<UsageInfo> = mutableListOf()

    ApplicationManager.getApplication().assertReadAccessAllowed()

    val psiManager = PsiManager.getInstance(myProject)

    val localFileSystem = LocalFileSystem.getInstance()
    val files =
      referenceMap.values
        .flatMap { value -> value.keys }
        .distinct()
        .associateWith { javaFile ->
          localFileSystem
            .findFileByIoFile(javaFile)
            ?.takeUnless(VirtualFile::isDirectory)
            ?.let(psiManager::findFile)
        }
        .mapNotNull { (javaFile, psiFile) -> psiFile?.let { javaFile to it } }
        .toMap()

    val projectSystem = myProject.getProjectSystem()

    for ((_, fileListMap) in referenceMap) {
      if (fileListMap.isEmpty() || files.isEmpty()) continue

      for ((file, psiFile) in files) {
        if (!checkReadOnlyStatus(myProject, psiFile)) {
          continue
        }

        val problems = fileListMap[file] ?: continue

        val module = ModuleUtilCore.findModuleForFile(psiFile)
        val moduleSystem = if (module != null) projectSystem.getModuleSystem(module) else null

        for (problem in problems) {
          val incident = problem.incident
          val dependencyString = incident.clientProperties?.get(KEY_COORDINATE) ?: continue
          val dependencyType = incident.message
          val location = incident.location
          val startOffset = location.start?.offset ?: continue
          val endOffset = location.end?.offset ?: continue
          val element = incident.scope as? PsiElement ?: psiFile.findElementAt(startOffset)
          if (element != null) {
            val s: Int = startOffset - element.startOffset
            val e: Int = endOffset - element.startOffset

            val artifactIndex = dependencyString.indexOf(':')
            val versionIndex = dependencyString.indexOf(':', artifactIndex + 1)

            var group: String?
            var name: String
            var version: String

            if (artifactIndex == -1) {
              // Plugin declaration (id()) without version
              group = dependencyString
              name = "$dependencyString$GRADLE_PLUGIN_ARTIFACT_SUFFIX"
              version = "+"
            } else if (versionIndex != -1) {
              group = dependencyString.substring(0, artifactIndex)
              name = dependencyString.substring(artifactIndex + 1, versionIndex)
              version = dependencyString.substring(versionIndex + 1)
            } else {
              group = null
              name = dependencyString.substring(0, artifactIndex)
              version = dependencyString.substring(artifactIndex + 1)
            }
            val currentVersion =
              getCurrentVersion(moduleSystem, group, name, version, dependencyType)
            val newVersion = getNewVersion(group, name, currentVersion)
            val dependency = Dependency(group, name, newVersion)
            val usageInfo =
              DependencyUsageInfo(
                element,
                s,
                e,
                dependency,
                currentVersion.lowerBound,
                newVersion.lowerBound,
              )
            usages.add(usageInfo)
          }
        }
      }
    }

    val contents = getVersionCatalogContents(usages)
    val virtualFile = LightVirtualFile(FN_VERSION_CATALOG, contents)
    val createFileInfo =
      PsiManager.getInstance(myProject).findFile(virtualFile) ?: return emptyList()

    usages.add(CreateTomlFile(createFileInfo, contents))

    return usages
  }

  private fun getGoogleMavenRepository(): GoogleMavenRepository {
    val client = lint().createClient(myProject)
    val cacheDir =
      client.getCacheDir(GoogleMavenRepository.Companion.MAVEN_GOOGLE_CACHE_DIR_KEY, true)
    val repository =
      object : GoogleMavenRepository(cacheDir?.toPath()) {

        public override fun readUrlData(
          url: String,
          timeout: Int,
          lastModified: Long,
        ): ReadUrlDataResult =
          com.android.tools.lint.detector.api.readUrlData(client, url, timeout, lastModified)

        public override fun error(throwable: Throwable, message: String?) =
          client.log(throwable, message)
      }

    return repository
  }

  private fun getCurrentVersion(
    moduleSystem: AndroidModuleSystem?,
    group: String?,
    name: String,
    sourceVersion: String,
    dependencyType: String,
  ): RichVersion {
    var version = sourceVersion

    // Read current version from source, and if not found (e.g. via variable reference), from
    // model
    if ((version.contains("$") || version.contains("+")) && moduleSystem != null) {
      val coordinate =
        if (group == null) {
          GradleCoordinate.parseCoordinateString("$name:$name$GRADLE_PLUGIN_ARTIFACT_SUFFIX:+")
        } else {
          GradleCoordinate.parseCoordinateString("$group:$name:+")
        }

      if (coordinate != null) {
        val resolved = moduleSystem.getResolvedDependency(coordinate)
        if (resolved != null) {
          version = resolved.version.toString()
        } else {
          val key = "$group:$name:"
          val scope =
            if (dependencyType.startsWith("test")) DependencyScopeType.UNIT_TEST
            else if (dependencyType.startsWith("android")) DependencyScopeType.ANDROID_TEST
            else DependencyScopeType.MAIN

          fun find(scope: DependencyScopeType): ExternalAndroidLibrary? {
            return moduleSystem.getAndroidLibraryDependencies(scope).firstOrNull {
              it.address.startsWith(key)
            }
          }

          val dep =
            find(scope)
              ?: find(DependencyScopeType.MAIN)
              ?: find(DependencyScopeType.UNIT_TEST)
              ?: find(DependencyScopeType.ANDROID_TEST)
          if (dep != null) {
            version = Dependency.parse(dep.address).version.toString()
          }
        }
      }
    }
    val current = RichVersion.parse(version)
    return current
  }

  private fun getNewVersion(group: String?, name: String, current: RichVersion): RichVersion {
    if (updateVersions) {
      val client = lint().createClient(myProject)
      val (groupId, artifactId) =
        if (group == null) {
          name to "$name$GRADLE_PLUGIN_ARTIFACT_SUFFIX"
        } else {
          group to name
        }

      val currentVersion = if (!current.toString().contains("$")) current.lowerBound else null
      return getMavenVersion(
          client,
          groupId,
          artifactId,
          currentVersion,
          filter = null,
          allowCache = true,
          includeJitpack = true,
          repository,
        )
        ?.let { RichVersion.parse(it.toString()) } ?: current
    }

    return current
  }

  private fun getVersionCatalogContents(usages: List<UsageInfo>): String {
    val sb = StringBuilder()

    val includeFilter = includeFilter
    val usageToDependency: Map<DependencyUsageInfo, Dependency> =
      usages
        .mapNotNull {
          if (it is DependencyUsageInfo) {
            it to it.dependency
          } else {
            null
          }
        }
        .toMap()

    var dependencies = usageToDependency.values.toSet().toList().sortedBy { it.name }

    val unknownVersion = dependencies.filter { !it.hasKnownVersion() }.toSet()

    val replacementDependency = mutableMapOf<Dependency, Dependency>()
    if (unifyVersions) {
      val versions = dependencies.filter { !unknownVersion.contains(it) }.groupBy { it.id() }
      for ((_, list) in versions) {
        if (list.size > 1) {
          val maxDependency = list.sortedBy { it.version?.lowerBound }.last()
          for (dependency in list) {
            if (dependency != maxDependency) {
              replacementDependency[dependency] = maxDependency
            }
          }
        }
      }
      for (usage in usageToDependency.keys) {
        usage.dependency = replacementDependency[usage.dependency] ?: usage.dependency
      }
      dependencies = dependencies.filter { !replacementDependency.containsKey(it) }
    }

    val groupBy: Map<Boolean, List<Dependency>> = dependencies.groupBy { it.isPlugin() }
    val libraries = groupBy[false] ?: emptyList()
    val plugins = groupBy[true] ?: emptyList()

    val sharedVariables: Map<Pair<String, RichVersion?>, List<Dependency>> =
      dependencies.groupBy { (it.group ?: it.name) to it.version }
    val dependenciesToGroupVersions: Map<List<Dependency>, Pair<String, RichVersion?>> =
      sharedVariables.invert()

    val idToVersions = dependencies.groupBy { it.id() }
    fun includeVersionInKey(dependency: Dependency): Boolean {
      if (!unifyVersions) {
        val versions = idToVersions[dependency.id()]
        if (versions != null && versions.size > 1) {
          val max = versions.mapNotNull { it.version?.lowerBound }.max()
          if (dependency.version?.lowerBound != max) {
            return true
          }
        }
      }
      return false
    }

    val reserved = mutableSetOf<String>()
    val versionMap = mutableMapOf<Dependency, String>()

    if (groupVersionVariables) {
      // Assign version variables
      for ((dependenciesInGroup, groupAndVersion) in dependenciesToGroupVersions) {
        if (dependenciesInGroup.size > 1) {
          val group = groupAndVersion.first
          val primary = Dependency(group, group.substringAfterLast('.'), groupAndVersion.second)
          val name =
            getDefaultName(dependenciesInGroup.first().id(), reserved)
              ?: pickVersionVariableName(primary, reserved, includeVersionInKey(primary))
          reserved.add(name)
          for (dependency in dependenciesInGroup) {
            if (unknownVersion.contains(dependency)) {
              continue
            }
            versionMap[dependency] = name
          }
        } else {
          val dependency = dependenciesInGroup[0]
          if (unknownVersion.contains(dependency)) {
            continue
          }
          if (dependency.isPlugin()) {
            val name =
              getDefaultName(dependency.id(), reserved)
                ?: pickPluginVariableName(dependency.getPluginId(), reserved)
            reserved.add(name)
            versionMap[dependency] = name
          } else {
            val name =
              pickVersionVariableName(dependency, reserved, includeVersionInKey(dependency))
            reserved.add(name)
            versionMap[dependency] = name
          }
        }
      }
    } else {
      for (dependency in libraries) {
        if (unknownVersion.contains(dependency)) {
          continue
        }
        val name =
          getDefaultName(dependency.id(), reserved)
            ?: pickVersionVariableName(dependency, reserved, includeVersionInKey(dependency))
        reserved.add(name)
        versionMap[dependency] = name
      }
      // Sharing same reserved names between the two
      for (dependency in plugins) {
        if (unknownVersion.contains(dependency)) {
          continue
        }
        val name =
          getDefaultName(dependency.id(), reserved)
            ?: pickPluginVariableName(dependency.getPluginId(), reserved)
        reserved.add(name)
        versionMap[dependency] = name
      }
    }

    val libraryMap = mutableMapOf<Dependency, String>()
    reserved.clear()

    for (dependency in libraries) {
      val includeVersionInKey = includeVersionInKey(dependency)
      val name = pickLibraryVariableName(dependency, includeVersionInKey, reserved)
      reserved.add(name)
      libraryMap[dependency] = name
    }

    val pluginMap = mutableMapOf<Dependency, String>()
    reserved.clear()
    for (dependency in plugins) {
      val name = pickPluginVariableName(dependency.getPluginId(), reserved)
      reserved.add(name)
      pluginMap[dependency] = name
    }

    sb.append("[versions]\n")
    for ((name, dependency) in versionMap.invert().entries.sortedBy { it.key }) {
      if (includeFilter != null) {
        // See if any included dependencies have this version
        if (
          libraryMap.entries.none { !unknownVersion.contains(it.key) && includeFilter(it.key.id()) }
        ) {
          continue
        }
      }
      sb.append("$name = \"${dependency.version}\"\n")
    }
    sb.append("\n")

    sb.append("[libraries]\n")
    if (libraryMap.isNotEmpty()) {
      for ((dependency, name) in libraryMap.entries.sortedBy { it.value }) {
        if (
          unknownVersion.contains(dependency) ||
            includeFilter != null && !includeFilter(dependency.id())
        ) {
          continue
        }
        sb.append(
          "$name = { group = \"${dependency.group}\", name = \"${dependency.name}\", version.ref = \"${versionMap[dependency]}\" }\n"
        )
      }
    }

    if (plugins.isNotEmpty()) {
      sb.append("\n")
      sb.append("[plugins]\n")
      if (pluginMap.isNotEmpty()) {
        for ((dependency, name) in pluginMap.entries.sortedBy { it.value }) {
          if (
            unknownVersion.contains(dependency) ||
              includeFilter != null && !includeFilter(dependency.id())
          ) {
            continue
          }
          sb.append(
            "$name = { id = \"${dependency.getPluginId()}\", version.ref = \"${versionMap[dependency]}\" }\n"
          )
        }
      }
    }

    // Update library reference names
    for (usage in usages) {
      (usage as? DependencyUsageInfo)?.include = false
    }
    for (usage in usageToDependency.keys) {
      val map = if (usage.dependency.isPlugin()) pluginMap else libraryMap
      usage.name = map[usage.dependency]!!
      if (includeFilter != null && !includeFilter(usage.dependency.id())) {
        continue
      }
      usage.include = usage.dependency.hasKnownVersion()
    }

    return sb.toString()
  }

  private fun getDefaultName(id: String, reserved: Set<String>): String? {
    // Conventional variables used in default Studio projects etc
    val suggestion =
      when {
        id in GradleDetector.ALL_PLUGIN_IDS || id == "com.android.tools.build:gradle" -> {
          // Unconditionally assigned even if reserved (we want to unify all
          // plugins here under the same name
          return "agp"
        }
        id.startsWith("org.jetbrains.kotlin:kotlin") || id == "org.jetbrains.kotlin.android" ->
          "kotlin"
        id.startsWith("com.google.android.material:") -> "material"
        id.startsWith("androidx.test.espresso:") -> "espresso"
        id.startsWith("androidx.test.ext:") -> "junitVersion" // not a great name!
        id == "junit:junit" -> "junit"
        else -> null
      }
    return if (suggestion != null && !reserved.contains(suggestion)) {
      suggestion
    } else {
      null
    }
  }

  private fun computeVersionReferenceMap(): Map<Issue, Map<File, List<LintProblemData>>> {
    val map: MutableMap<Issue, Map<File, List<LintProblemData>>> = mutableMapOf()
    val scope = AnalysisScope(myProject)

    val lintResult =
      LintBatchResult(myProject, map, scope, setOf(CollectDependenciesDetector.issue), null)
    val registry = lint().getIssueRegistry(listOf(CollectDependenciesDetector.issue))
    val client = lint().createIsolatedClient(lintResult, registry)

    // Note: We pass in *all* modules in the project here, not just those in the scope of the
    // resource refactoring. If you for example are running the unused resource refactoring on a
    // library module, we want to only remove unused resources from the specific library
    // module, but we still have to have lint analyze all modules such that it doesn't consider
    // resources in the library as unused when they could be referenced from other modules.
    // So, we'll analyze all modules with lint, and then in the UnusedResourceProcessor
    // we'll filter the matches down to only those in the target modules when we're done.
    val modules = ModuleManager.getInstance(myProject).modules.toList()
    val request =
      LintIdeRequest(client, myProject, null, modules, false).apply { setScope(Scope.ALL) }

    // Make sure we don't remove resources that are still referenced from
    // tests (though these should probably be in a test resource source
    // set instead.)
    with(client.createDriver(request, registry)) {
      checkTestSources = true
      analyze()
    }

    return map
  }

  class CollectDependenciesDetector : Detector(), GradleScanner {
    override fun checkDslPropertyAssignment(
      context: GradleContext,
      property: String,
      value: String,
      parent: String,
      parentParent: String?,
      propertyCookie: Any,
      valueCookie: Any,
      statementCookie: Any,
    ) {
      if (parent == "dependencies" && !value.startsWith("files")) {
        var dependencyString = getStringLiteralValue(value, valueCookie)
        if (
          dependencyString == null &&
            (listOf("platform", "testFixtures", "enforcedPlatform").any {
              value.startsWith("$it(")
            } && value.endsWith(")"))
        ) {
          val argumentString = value.substring(value.indexOf('(') + 1, value.length - 1)
          dependencyString =
            if (valueCookie is UCallExpression && valueCookie.valueArguments.size == 1) {
              getStringLiteralValue(argumentString, valueCookie.valueArguments.first())
            } else {
              getStringLiteralValue(argumentString, valueCookie)
            }
        }
        if (dependencyString == null) {
          dependencyString = getNamedDependency(value)
        }
        // If the dependency is a GString (i.e. it uses Groovy variable substitution,
        // with a $variable_name syntax) then don't try to parse it.
        if (dependencyString != null) {
          var element: Any? = valueCookie
          if (element is UElement) { // KTS
            var curr = element.sourcePsi
            while (curr != null) {
              val elementParent = curr.parent
              if (elementParent is KtValueArgument) {
                element = elementParent
                break
              }
              curr = elementParent
            }
          }

          recordUsage(context, element ?: valueCookie, property, dependencyString)
        }
      }
    }

    override fun checkMethodCall(
      context: GradleContext,
      statement: String,
      parent: String?,
      parentParent: String?,
      namedArguments: Map<String, String>,
      unnamedArguments: List<String>,
      cookie: Any,
    ) {
      if (statement == "id") {
        if (cookie is PsiElement) {
          if (unnamedArguments.isNotEmpty()) {
            val plugin = getStringLiteralValue(unnamedArguments.first(), cookie) ?: return
            if (!plugin.contains(".")) {
              // Built-in plugin
              return
            }

            val elementParent = cookie.parent
            if (
              elementParent is GrReferenceExpression && elementParent.referenceName == "version"
            ) {
              val grandParent = elementParent.parent
              if (grandParent is GrApplicationStatement) {
                val list = grandParent.argumentList
                val arguments = list.expressionArguments
                if (arguments.size == 1) {
                  val version = getStringLiteralValue(arguments.first().text, arguments.first())
                  if (version != null) {
                    val dependencyString = "$plugin:$version"
                    val curr: PsiElement = grandParent
                    recordUsage(context, curr, "", dependencyString)
                  }
                }
              }
            } else {
              recordUsage(context, cookie, "", plugin)
            }
          }
        }
      } else if (parent == "dependencies") {
        if (statement != "files" && cookie is UCallExpression && cookie.valueArgumentCount >= 3) {
          val (dependency, versionElement) = getKtsDependency(cookie) ?: return
          val argList = versionElement.parentOfType<KtValueArgumentList>() ?: versionElement
          recordUsage(
            context,
            argList,
            statement,
            "${dependency.group}:${dependency.name}:${dependency.version}",
          )
        }
      }
    }

    private fun recordUsage(
      context: GradleContext,
      scope: Any,
      dependencyType: String,
      dependencyString: String,
    ) {
      context.report(
        Incident(issue, scope, context.getLocation(scope), dependencyType).apply {
          clientProperties = map().put(KEY_COORDINATE, dependencyString)
        }
      )
    }

    companion object {

      @Suppress("LintImplIdFormat")
      val issue =
        Issue.create(
          id = "_MigrateToCatalogIssue",
          briefDescription = "Internal",
          explanation = "Internal",
          category = Category.LINT,
          priority = 5,
          severity = Severity.WARNING,
          implementation =
            Implementation(CollectDependenciesDetector::class.java, Scope.GRADLE_SCOPE),
        )
    }
  }

  override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>) = true

  override fun refreshElements(elements: Array<PsiElement>) {
    System.arraycopy(elements, 0, this.elements, 0, elements.size)
  }

  override fun getBeforeData() = RefactoringEventData().apply { addElements(elements) }

  override fun getRefactoringId() = "refactoring.migrate.catalog"

  public override fun performRefactoring(usages: Array<UsageInfo>) {
    try {
      for (usage in usages) {
        if (usage is DependencyUsageInfo) {
          val element = usage.element ?: continue
          if (!element.isValid) {
            continue
          }
          if (!usage.include) {
            // Unresolved version number etc
            continue
          }
          val name = usage.name.replace("-", ".").replace("_", ".")
          val expression =
            if (usage.isPlugin()) {
              if (element.language == GroovyLanguage) "alias libs.plugins.$name"
              else "alias(libs.plugins.$name)"
            } else {
              "libs.$name"
            }
          val newElement =
            if (element.language == GroovyLanguage) {
              GroovyPsiElementFactory.getInstance(myProject).createExpressionFromText(expression)
            } else if (element.language == KotlinLanguage.INSTANCE) {
              KtPsiFactory(myProject).createExpression(expression)
            } else {
              continue
            }
          if (element is KtValueArgumentList) {
            // When replacing an argument list, we really mean to
            // replace the contents of the list, not the list itself
            for (argument in element.arguments) {
              element.removeArgument(argument)
            }
            val argument = KtPsiFactory(myProject).createArgument(newElement as KtExpression)
            element.addArgument(argument)
          } else {
            element.replace(newElement)
          }
        } else if (usage is CreateTomlFile) {
          val gradle = getGradleFolder()
          val file = gradle.createChildData(this, FN_VERSION_CATALOG)
          val contents =
            if (includeFilter != null) {
              // Recompute catalog contents in case the filter has changed
              getVersionCatalogContents(usages.toList())
            } else {
              usage.contents
            }
          file.writeText(contents)
        }
      }
    } catch (e: IncorrectOperationException) {
      RefactoringUIUtil.processIncorrectOperation(myProject, e)
    }

    if (syncProject) {
      myProject
        .getProjectSystem()
        .getSyncManager()
        .requestSyncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED)
    }

    if (openCatalogFile) {
      val gradle = getGradleFolder()
      val file = gradle.findChild(FN_VERSION_CATALOG)
      if (file != null) {
        OpenFileDescriptor(myProject, file).navigateInEditor(myProject, true)
      }
    }
  }

  private fun getGradleFolder(): VirtualFile {
    val gradle =
      myProject.baseDir.findChild(FD_GRADLE)
        ?: myProject.baseDir.createChildDirectory(this, FD_GRADLE)
    return gradle
  }

  override fun getCommandName() = "Migrating to Version Catalog"

  override fun skipNonCodeUsages() = true

  override fun isToBeChanged(usageInfo: UsageInfo): Boolean {
    if (
      ApplicationManager.getApplication().isUnitTestMode &&
        usageInfo.element?.text?.contains("AUTO-EXCLUDE") == true
    ) {
      // Automatically exclude/deselect elements that contain the string "AUTO-EXCLUDE".
      // This is our simple way to unit test the UI operation of users deselecting certain
      // elements in the refactoring UI.
      return false
    }

    return super.isToBeChanged(usageInfo)
  }

  companion object {
    fun applies(project: Project?): Boolean {
      if (project == null || !CommonAndroidUtil.getInstance().isAndroidProject(project)) {
        return false
      }

      return !File(project.basePath, "$FD_GRADLE$separator$FN_VERSION_CATALOG").isFile &&
        // Because we couldn't check GradleProjectSystem above
        File(project.basePath, FD_GRADLE).isDirectory
    }
  }
}

private fun Dependency.id(): String {
  return if (group != null) {
    "$group:$name"
  } else {
    name
  }
}

private fun Dependency.hasKnownVersion(): Boolean {
  return !version.toString().startsWith("$")
}

private fun Dependency.isPlugin(): Boolean {
  return group == null || name.endsWith(GRADLE_PLUGIN_ARTIFACT_SUFFIX)
}

private fun Dependency.getPluginId(): String {
  return name.removeSuffix(GRADLE_PLUGIN_ARTIFACT_SUFFIX)
}
