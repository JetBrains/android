/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.lint

import com.android.SdkConstants
import com.android.ide.common.gradle.Module as ExternalModule
import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.ide.common.repository.GoogleMavenArtifactId.Companion.androidxIdOf
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.support.AndroidxNameUtils
import com.android.tools.idea.gradle.project.model.GradleAndroidDependencyModel
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.lint.common.LintIdeClient
import com.android.tools.idea.lint.common.LintIdeProject
import com.android.tools.idea.lint.model.LintModelFactory
import com.android.tools.idea.lint.model.LintModelFactory.Companion.getModuleType
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.getMainModule
import com.android.tools.idea.projectsystem.gradle.isHolderModule
import com.android.tools.idea.res.AndroidDependenciesCache
import com.android.tools.idea.util.findAndroidModule
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.ApiConstraint.Companion.get
import com.android.tools.lint.detector.api.ExtensionSdk
import com.android.tools.lint.detector.api.LintModelModuleProject
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.model.LintModelAndroidLibrary
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.LintModelModuleType
import com.android.tools.lint.model.LintModelVariant
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import java.io.File
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetProperties
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.android.facet.ResourceFolderManager
import org.jetbrains.android.sdk.getInstance
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.kotlin.config.KotlinModuleKind
import org.jetbrains.kotlin.idea.facet.KotlinFacet

/**
 * An [LintIdeProject] represents a lint project, which typically corresponds to a [Module], but can
 * also correspond to a library "project" such as an [LintModelAndroidLibrary].
 */
class AndroidLintIdeProject
internal constructor(client: LintClient, dir: File, referenceDir: File) :
  LintIdeProject(client, dir, referenceDir) {
  companion object {
    /** Creates a set of projects for the given IntelliJ modules */
    fun create(
      client: LintIdeClient,
      files: List<VirtualFile>?,
      vararg modules: Module,
    ): List<Project> {
      val projects = ArrayList<Project>()
      val projectMap = Maps.newHashMap<Project, Module>()
      val moduleMap = Maps.newHashMap<Module, Project>()
      val libraryMap = Maps.newHashMap<LintModelAndroidLibrary, Project>()
      val distinctModules = modules.map { it.getMainModule() }.distinct()
      if (!files.isNullOrEmpty()) {
        for (module in distinctModules) {
          addProjects(client, module, files, moduleMap, libraryMap, projectMap, projects, false)
        }
      } else {
        for (module in distinctModules) {
          addProjects(client, module, null, moduleMap, libraryMap, projectMap, projects, false)
        }
      }

      client.setModuleMap(projectMap)

      if (projects.size > 1) {
        // Partition the projects up such that we only return projects that aren't
        // included by other projects (e.g. because they are library projects)
        val roots = HashSet<Project>(projects)
        for (project in projects) {
          roots.removeAll(project.getAllLibraries().toSet())
        }
        return roots.toList()
      } else {
        return projects
      }
    }

    /**
     * Creates a project for a single file. Also, optionally creates a main project for the file, if
     * applicable.
     *
     * @param client the lint client
     * @param file the file to create a project for
     * @param module the module to create a project for
     * @return a project for the file, as well as a project (or null) for the main Android module
     */
    fun createForSingleFile(
      client: LintIdeClient,
      file: VirtualFile?,
      module: Module,
    ): Pair<Project, Project> {

      // TODO: Can make this method even more lightweight: we don't need to
      //    initialize anything in the project (source paths etc) other than the
      //    metadata necessary for this file's type
      val project: Project? = createModuleProject(client, module, true)
      var main: Project? = null
      val projectMap = Maps.newHashMap<Project, Module>()
      if (project != null) {
        project.directLibraries = listOf<Project>()
        if (file != null) {
          project.addFile(VfsUtilCore.virtualToIoFile(file))
        }
        projectMap[project] = module

        // Supply a main project too, such that when you for example edit a file in a Java library,
        // and lint asks for getMainProject().getMinSdk(), we return the min SDK of an application
        // using the library, not "1" (the default for a module without a manifest)
        if (!project.isAndroidProject) {
          val androidModule = findAndroidModule(module)
          if (androidModule != null) {
            main = createModuleProject(client, androidModule, true)
            if (main != null) {
              val path = module.getGradleProjectPath()?.path
              if (path == ":") {
                // Don't make main depend on root since parent hierarchy
                // goes in the opposite direction
                main.directLibraries = emptyList()
                client.setModuleMap(mapOf(main to module))
                val firstFile = project.subset?.firstOrNull()
                if (firstFile != null) {
                  main.addFile(firstFile)
                }
                main.isGradleRootHolder = true
                return Pair.create(main, null)
              } else {
                projectMap[main] = androidModule
                main.directLibraries = listOf(project)
              }
            }
          }
        }
        project.isGradleRootHolder = true
      }
      client.setModuleMap(projectMap)

      return Pair.create(project, main)
    }

    /**
     * Find an Android module that depends on this module; prefer app modules over library modules
     */
    private fun findAndroidModule(module: Module): Module? {
      if (module.isDisposed) {
        return null
      }

      // Search for dependencies of this module
      val graph =
        ApplicationManager.getApplication()
          .runReadAction(
            Computable {
              val project = module.project
              ModuleManager.getInstance(project).moduleGraph()
            }
          ) ?: return null

      var androidModule: Module? = null
      val seen = Sets.newHashSet<Module>()

      /**
       * As a side effect also stores the first Android module it comes across in `androidModule`
       * (if it's not an app module)
       */
      fun findAppModule(module: Module): Module? {
        if (!seen.add(module)) {
          return null
        }
        val facet = AndroidFacet.getInstance(module)
        if (facet != null) {
          if (facet.configuration.isAppProject()) {
            return facet.module.getMainModule()
          } else if (androidModule == null) {
            androidModule = facet.module.getMainModule()
          }
        }

        val iterator = graph.getOut(module)
        while (iterator.hasNext()) {
          val dep = iterator.next().getMainModule()
          val appModule = findAppModule(dep)
          if (appModule != null) {
            return appModule
          }
        }

        return null
      }

      return findAppModule(module)
        ?: androidModule
        // It might be a root project (e.g. when editing gradle/libs.versions.toml)
        // which doesn't have any reverse dependencies on it; if so, find first module
        ?: run {
          // Is this the mostly-empty root module with build scripts? (This is usually
          // the case when editing gradle/libs.versions.toml etc.)
          val path = module.getGradleProjectPath()?.path
          if (path == "" || path == ":") {
            graph.nodes.firstOrNull { AndroidFacet.getInstance(it) != null }?.getMainModule()
          } else {
            null
          }
        }
    }

    /**
     * Recursively add lint projects for the given module, and any other module or library it
     * depends on, and also populate the reverse maps so we can quickly map from a lint project to a
     * corresponding module/library (used by the lint client
     */
    private fun addProjects(
      client: LintClient,
      module: Module,
      files: List<VirtualFile>?,
      moduleMap: MutableMap<Module, Project>,
      libraryMap: MutableMap<LintModelAndroidLibrary, Project>,
      projectMap: MutableMap<Project, Module>,
      projects: MutableList<Project>,
      shallowModel: Boolean,
    ) {
      if (moduleMap.containsKey(module)) {
        return
      }

      val project = createModuleProject(client, module, shallowModel)

      if (project == null) {
        // It's possible for the module to *depend* on Android code, e.g. in a Gradle
        // project there will be a top-level non-Android module
        val dependentModules =
          AndroidDependenciesCache.getAllAndroidDependencies(module, false)
            .map { it.module.getMainModule() }
            .distinct()
        for (dependentModule in dependentModules) {
          addProjects(
            client,
            dependentModule,
            files,
            moduleMap,
            libraryMap,
            projectMap,
            projects,
            true,
          )
        }
        return
      }

      project.ideaProject = module.project

      projects.add(project)
      moduleMap[module] = project
      projectMap[project] = module

      if (processFileFilter(module, files, project)) {
        // No need to process dependencies when doing single file analysis
        return
      }

      val dependencies = ArrayList<Project>()
      // No, this shouldn't use getAllAndroidDependencies; we may have
      // non-Android dependencies that this won't include (e.g. Java-only
      // modules)
      val dependentModules =
        AndroidDependenciesCache.getAllAndroidDependencies(module, true)
          .map { it.module.getMainModule() }
          .distinct()
      for (dependentModule in dependentModules) {
        val p = moduleMap[dependentModule]
        if (p != null) {
          dependencies.add(p)
        } else {
          addProjects(
            client,
            dependentModule,
            files,
            moduleMap,
            libraryMap,
            projectMap,
            dependencies,
            true,
          )
        }
      }

      project.directLibraries = dependencies
    }

    /** Creates a new module project */
    private fun createModuleProject(
      client: LintClient,
      module: Module,
      shallowModel: Boolean,
    ): Project? {
      val androidModule = module.findAndroidModule()
      val facet = AndroidFacet.getInstance(androidModule ?: module)
      val dir = getLintProjectDirectory(module, facet) ?: return null
      val project: Project?
      if (facet == null) {
        val kotlinFacet = KotlinFacet.Companion.get(module)
        if (
          kotlinFacet != null &&
            kotlinFacet.configuration.settings.mppVersion != null &&
            kotlinFacet.configuration.settings.kind !=
              KotlinModuleKind.COMPILATION_AND_SOURCE_SET_HOLDER
        ) {
          return null
        }

        project = LintModuleProject(client, dir, dir, module)
        val f = findAndroidFacetInProject(module.project)
        if (f != null) {
          project.gradleProject = AndroidModel.isRequired(f)
        }

        if (module.isHolderModule()) {
          project.isGradleRootHolder = module.getGradleProjectPath()?.path == ":"
        }
      } else if (AndroidModel.isRequired(facet)) {
        val androidModel = AndroidModel.get(facet)
        if (androidModel is GradleAndroidDependencyModel) {
          val variantName = androidModel.selectedVariantName

          val lintModel = getLintModuleModel(facet, shallowModel)
          var variant = lintModel.findVariant(variantName)
          if (variant == null) {
            variant = lintModel.variants[0]
          }
          project = LintGradleProject(client, dir, dir, variant, facet, androidModel)
        } else if (androidModel != null) {
          project = LintAndroidModelProject(client, dir, dir, facet, androidModel)
        } else {
          project = LintAndroidProject(client, dir, dir, facet)
        }
      } else {
        project = LintAndroidProject(client, dir, dir, facet)
      }
      project.ideaProject = module.project
      client.registerProject(dir, project)
      return project
    }

    private fun getLintModuleModel(facet: AndroidFacet, shallowModel: Boolean): LintModelModule {
      val project = facet.module.project
      val cacheValueManager = CachedValuesManager.getManager(project)
      // This if statement may seem redundant, but is needed to ensure the cached
      // value providers (the lambdas) are distinct classes that do not hold
      // any state other than facet (i.e. they do not depend on shallowModel).
      return if (shallowModel) {
        cacheValueManager.getCachedValue(facet) { buildModuleModel(facet, true) }
      } else {
        cacheValueManager.getCachedValue(facet) { buildModuleModel(facet, false) }
      }
    }

    private fun buildModuleModel(
      facet: AndroidFacet,
      shallowModel: Boolean,
    ): Result<LintModelModule> {
      val model = GradleAndroidDependencyModel.get(facet)
      checkNotNull(model) { "GradleAndroidModel not available for $facet" }
      val builderModelProject = model.androidProject
      val multiVariantData = builderModelProject.multiVariantData
      checkNotNull(multiVariantData) {
        "GradleAndroidModel is expected to support multi variant plugins."
      }
      val externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(facet.module)
      checkNotNull(externalProjectPath) { "No external project path for " + facet.module }
      val dir = File(externalProjectPath)
      val module =
        LintModelFactory()
          .create(
            builderModelProject,
            model.variantsWithDependencies,
            multiVariantData,
            dir,
            !shallowModel,
          )
      return Result.create(module, ProjectSyncModificationTracker.getInstance(facet.module.project))
    }

    /** Returns the directory lint would use for a project wrapping the given module */
    private fun getLintProjectDirectory(module: Module, facet: AndroidFacet?): File? {
      if (
        ExternalSystemApiUtil.isExternalSystemAwareModule(
          GradleProjectSystemUtil.GRADLE_SYSTEM_ID,
          module,
        )
      ) {
        val externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module)
        if (!externalProjectPath.isNullOrBlank()) {
          return File(externalProjectPath)
        }
      }
      val dir: File?
      if (facet != null) {
        val mainContentRoot = AndroidRootUtil.getMainContentRoot(facet) ?: return null

        dir = VfsUtilCore.virtualToIoFile(mainContentRoot)
      } else {
        // For Java modules we just use the first content root that is we can find
        val roots = ModuleRootManager.getInstance(module).contentRoots
        if (roots.isEmpty()) {
          return null
        }
        dir = VfsUtilCore.virtualToIoFile(roots[0])
      }
      return dir
    }

    private fun findAndroidFacetInProject(
      project: com.intellij.openapi.project.Project
    ): AndroidFacet? {
      val androidFacetsInRandomOrder =
        ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID)
      return if (androidFacetsInRandomOrder.isEmpty()) null else androidFacetsInRandomOrder[0]
    }
  }

  override fun initialize() {
    // NOT calling super: super performs ADT/ant initialization. Here we want to use
    // the gradle data instead
  }

  /** Wraps an Android module */
  private open class LintAndroidProject(
    client: LintClient,
    dir: File,
    referenceDir: File,
    protected val facet: AndroidFacet,
  ) : LintModuleProject(client, dir, referenceDir, facet.module.getMainModule()) {
    init {
      gradleProject = false
      library = facet.configuration.isLibraryProject

      val platform = getInstance(facet.module.getMainModule())
      if (platform != null) {
        buildSdk = platform.apiLevel
        buildSdkLevel = platform.apiVersion.androidApiLevel
      }
    }

    override fun isAndroidProject(): Boolean {
      return true
    }

    override fun getName(): String {
      return facet.module.getModuleSystem().getDisplayNameForModuleGroup()
    }

    override fun getType(): LintModelModuleType {
      return getModuleType(facet.configuration.projectType)
    }

    override fun getManifestFiles(): List<File> {
      if (manifestFiles == null) {
        val manifestFile = AndroidRootUtil.getPrimaryManifestFile(facet)
        manifestFiles =
          if (manifestFile != null) {
            listOf(VfsUtilCore.virtualToIoFile(manifestFile))
          } else {
            emptyList()
          }
      }

      return manifestFiles
    }

    override fun getProguardFiles(): List<File> {
      if (proguardFiles == null) {
        val properties = facet.properties

        if (properties.RUN_PROGUARD) {
          val urls = properties.myProGuardCfgFiles

          if (urls.isNotEmpty()) {
            proguardFiles = ArrayList<File>()

            for (osPath in AndroidUtils.urlsToOsPaths(urls, null)) {
              if (!osPath.contains(AndroidFacetProperties.SDK_HOME_MACRO)) {
                proguardFiles.add(File(osPath))
              }
            }
          }
        }

        if (proguardFiles == null) {
          proguardFiles = emptyList()
        }
      }

      return proguardFiles
    }

    override fun getResourceFolders(): List<File> {
      if (resourceFolders == null) {
        val folders = ResourceFolderManager.getInstance(facet).folders
        val dirs = Lists.newArrayListWithExpectedSize<File>(folders.size)
        for (folder in folders) {
          dirs.add(VfsUtilCore.virtualToIoFile(folder))
        }
        resourceFolders = dirs
      }

      return resourceFolders
    }

    private fun dependsOn(id: GoogleMavenArtifactId, moduleSystem: AndroidModuleSystem): Boolean? {
      if (moduleSystem.hasResolvedDependency(id)) return true
      androidxIdOf(id)
        .takeIf { it != id }
        ?.let { if (moduleSystem.hasResolvedDependency(it)) return true }
      return null
    }

    private fun dependsOnForGradleProject(
      artifact: String,
      moduleSystem: GradleModuleSystem,
    ): Boolean? {
      ExternalModule.tryParse(artifact)?.let {
        if (moduleSystem.getResolvedDependency(it, DependencyScopeType.MAIN) != null) return true
      } ?: return null
      if (artifact.startsWith(SdkConstants.SUPPORT_LIB_GROUP_ID)) {
        val newArtifact = AndroidxNameUtils.getCoordinateMapping(artifact)
        if (newArtifact == artifact) return null
        ExternalModule.tryParse(newArtifact)?.let {
          if (moduleSystem.getResolvedDependency(it, DependencyScopeType.MAIN) != null) return true
        }
      }
      return null
    }

    override fun dependsOn(artifact: String): Boolean? {
      val id = GoogleMavenArtifactId.find(artifact)
      val moduleSystem = facet.module.getModuleSystem()
      return when {
        id != null -> dependsOn(id, moduleSystem)
        moduleSystem is GradleModuleSystem -> dependsOnForGradleProject(artifact, moduleSystem)
        else -> null
      } ?: super.dependsOn(artifact)
    }
  }

  private class LintAndroidModelProject(
    client: LintClient,
    dir: File,
    referenceDir: File,
    facet: AndroidFacet,
    private val androidModel: AndroidModel,
  ) : LintAndroidProject(client, dir, referenceDir, facet) {
    override fun getPackage(): String {
      val variant = buildVariant
      if (variant != null) {
        val pkg = variant.`package`
        if (pkg != null) {
          return pkg
        }
      }

      val manifestPackage = super.getPackage()
      if (manifestPackage != null) {
        return manifestPackage
      }

      return androidModel.getApplicationId()
    }

    override fun getMinSdkVersion(): AndroidVersion {
      return androidModel.getMinSdkVersion()
    }

    override fun getMinSdkVersions(): ApiConstraint {
      val version = androidModel.getMinSdkVersion()
      // TODO: Handle codenames better?
      return get(version.featureLevel, ExtensionSdk.ANDROID_SDK_ID)
    }

    override fun getTargetSdkVersion(): AndroidVersion {
      val version = androidModel.getTargetSdkVersion()
      if (version != null) {
        return version
      }
      return super.getTargetSdkVersion()
    }
  }

  private class LintGradleProject(
    client: LintClient,
    dir: File,
    referenceDir: File,
    variant: LintModelVariant,
    androidFacet: AndroidFacet,
    gradleAndroidModel: GradleAndroidDependencyModel,
  ) : LintModelModuleProject(client, dir, referenceDir, variant, null) {
    private val gradleAndroidModel: GradleAndroidDependencyModel
    private val facet: AndroidFacet

    /** Creates a new Project. Use one of the factory methods to create. */
    init {
      gradleProject = true
      mergeManifests = true
      this.facet = androidFacet
      this.gradleAndroidModel = gradleAndroidModel
    }

    override fun getJavaClassFolders(): List<File> {
      return if (LintIdeClient.SUPPORT_CLASS_FILES) {
        super.getJavaClassFolders()
      } else {
        emptyList()
      }
    }

    override fun getJavaLibraries(includeProvided: Boolean): List<File> {
      return if (LintIdeClient.SUPPORT_CLASS_FILES) {
        super.getJavaLibraries(includeProvided)
      } else {
        emptyList()
      }
    }

    override fun getBuildSdk(): Int {
      val compileTarget = gradleAndroidModel.androidProject.compileTarget
      val version = AndroidTargetHash.getPlatformVersion(compileTarget)
      if (version != null) {
        return version.featureLevel
      }

      val platform = getInstance(facet.module)
      if (platform != null) {
        return platform.apiVersion.featureLevel
      }

      return super.getBuildSdk()
    }

    override fun getBuildTargetHash(): String {
      return gradleAndroidModel.androidProject.compileTarget
    }
  }
}
