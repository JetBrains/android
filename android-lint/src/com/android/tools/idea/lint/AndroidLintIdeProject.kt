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
import com.android.ide.common.repository.GradleCoordinate
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.support.AndroidxNameUtils
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModel.Companion.get
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.lint.common.LintIdeClient
import com.android.tools.idea.lint.common.LintIdeProject
import com.android.tools.idea.lint.model.LintModelFactory
import com.android.tools.idea.lint.model.LintModelFactory.Companion.getModuleType
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.getMainModule
import com.android.tools.idea.res.AndroidDependenciesCache
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.ApiConstraint.Companion.get
import com.android.tools.lint.detector.api.ExtensionSdk
import com.android.tools.lint.detector.api.LintModelModuleAndroidLibraryProject
import com.android.tools.lint.detector.api.LintModelModuleProject
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.model.LintModelAndroidLibrary
import com.android.tools.lint.model.LintModelDependency
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
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.graph.Graph
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
      if (files != null && !files.isEmpty()) {
        // Wrap list with a mutable list since we'll be removing the files as we see them
        val files = files.toMutableList()
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
          roots.removeAll(project.getAllLibraries())
        }
        return roots.toList()
      } else {
        return projects
      }
    }

    /**
     * Creates a project for a single file. Also optionally creates a main project for the file, if
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
        project.setDirectLibraries(listOf<Project>())
        if (file != null) {
          project.addFile(VfsUtilCore.virtualToIoFile(file))
        }
        projectMap.put(project, module)

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
                main.setDirectLibraries(emptyList())
                client.setModuleMap(mapOf(main to module))
                return Pair.create<Project, Project>(main, null)
              } else {
                projectMap.put(main, androidModule)
                main.setDirectLibraries(listOf<Project>(project))
              }
            }
          }
        }
      }
      client.setModuleMap(projectMap)

      return Pair.create<Project, Project>(project, main)
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
          .runReadAction<Graph<Module>>(
            Computable<Graph<Module>> {
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
      files: MutableList<VirtualFile>?,
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
      moduleMap.put(module, project)
      projectMap.put(project, module)

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

      val facet = AndroidFacet.getInstance(module)
      if (facet != null) {
        val variant = project.buildVariant
        if (variant != null) {
          val roots = variant.artifact.dependencies.compileDependencies.roots
          addGradleLibraryProjects(
            client,
            files,
            libraryMap,
            projects,
            facet,
            project,
            projectMap,
            dependencies,
            roots,
          )
        }
      }

      project.setDirectLibraries(dependencies)
    }

    /** Creates a new module project */
    private fun createModuleProject(
      client: LintClient,
      module: Module,
      shallowModel: Boolean,
    ): Project? {
      val facet = AndroidFacet.getInstance(module)
      val dir: File? = getLintProjectDirectory(module, facet)
      if (dir == null) return null
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
      } else if (AndroidModel.isRequired(facet)) {
        val androidModel = AndroidModel.get(facet)
        if (
          androidModel is GradleAndroidModel &&
            androidModel.androidProject.projectType !=
              IdeAndroidProjectType.PROJECT_TYPE_KOTLIN_MULTIPLATFORM
        ) {
          val model = androidModel
          val variantName = model.selectedVariantName

          val lintModel = getLintModuleModel(facet, shallowModel)
          var variant = lintModel.findVariant(variantName)
          if (variant == null) {
            variant = lintModel.variants[0]
          }
          project = LintGradleProject(client, dir, dir, variant, facet, model)
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
        cacheValueManager.getCachedValue<LintModelModule>(
          facet,
          CachedValueProvider { buildModuleModel(facet, true) },
        )
      } else {
        cacheValueManager.getCachedValue<LintModelModule>(
          facet,
          CachedValueProvider { buildModuleModel(facet, false) },
        )
      }
    }

    private fun buildModuleModel(
      facet: AndroidFacet,
      shallowModel: Boolean,
    ): Result<LintModelModule> {
      val model = get(facet)
      checkNotNull(model) { "GradleAndroidMode l not available for $facet" }
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
          .create(builderModelProject, model.variants, multiVariantData, dir, !shallowModel)
      return Result.create<LintModelModule>(
        module,
        ProjectSyncModificationTracker.getInstance(facet.module.project),
      )
    }

    /** Returns the directory lint would use for a project wrapping the given module */
    fun getLintProjectDirectory(module: Module, facet: AndroidFacet?): File? {
      if (
        ExternalSystemApiUtil.isExternalSystemAwareModule(
          GradleProjectSystemUtil.GRADLE_SYSTEM_ID,
          module,
        )
      ) {
        val externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module)
        if (externalProjectPath != null && externalProjectPath.isNotBlank()) {
          return File(externalProjectPath)
        }
      }
      val dir: File?
      if (facet != null) {
        val mainContentRoot = AndroidRootUtil.getMainContentRoot(facet)

        if (mainContentRoot == null) {
          return null
        }
        dir = VfsUtilCore.virtualToIoFile(mainContentRoot)
      } else {
        // For Java modules we just use the first content root that is we can find
        val roots = ModuleRootManager.getInstance(module).contentRoots
        if (roots.size == 0) {
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
        ProjectFacetManager.getInstance(project).getFacets<AndroidFacet>(AndroidFacet.ID)
      return if (androidFacetsInRandomOrder.isEmpty()) null else androidFacetsInRandomOrder[0]
    }

    /** Adds any gradle library projects to the dependency list */
    private fun addGradleLibraryProjects(
      client: LintClient,
      files: MutableList<VirtualFile>?,
      libraryMap: MutableMap<LintModelAndroidLibrary, Project>,
      projects: MutableList<Project>,
      facet: AndroidFacet,
      project: Project,
      projectMap: MutableMap<Project, Module>,
      dependencies: MutableList<Project>,
      graphItems: List<LintModelDependency>,
    ) {
      var files = files
      val ideaProject = facet.module.project
      for (dependency in graphItems) {
        val l = dependency.findLibrary()
        if (l !is LintModelAndroidLibrary) {
          continue
        }
        val library = l
        var p = libraryMap[library]
        if (p == null) {
          val dir = library.folder
          p = LintGradleLibraryProject(client, dir, dir, dependency, library)
          p.ideaProject = ideaProject
          libraryMap.put(library, p)
          projectMap.put(p, facet.module.getMainModule())
          projects.add(p)

          if (files != null) {
            val libraryDir = LocalFileSystem.getInstance().findFileByIoFile(dir)
            if (libraryDir != null) {
              val iterator = files.listIterator()
              while (iterator.hasNext()) {
                val file = iterator.next()
                if (VfsUtilCore.isAncestor(libraryDir, file, false)) {
                  project.addFile(VfsUtilCore.virtualToIoFile(file))
                  iterator.remove()
                }
              }
            }
            if (files.isEmpty()) {
              files = null // No more work in other modules
            }
          }
        }
        dependencies.add(p)
      }
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
        if (manifestFile != null) {
          manifestFiles = listOf<File>(VfsUtilCore.virtualToIoFile(manifestFile))
        } else {
          manifestFiles = emptyList()
        }
      }

      return manifestFiles
    }

    override fun getProguardFiles(): List<File> {
      if (proguardFiles == null) {
        val properties = facet.properties

        if (properties.RUN_PROGUARD) {
          val urls = properties.myProGuardCfgFiles

          if (!urls.isEmpty()) {
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

    override fun dependsOn(artifact: String): Boolean? {
      var queryCoordinate = GradleCoordinate.parseCoordinateString("$artifact:+")
      if (queryCoordinate != null) {
        var foundDependency = facet.module.getModuleSystem().getResolvedDependency(queryCoordinate)
        if (foundDependency != null) {
          return java.lang.Boolean.TRUE
        }

        // Check new AndroidX namespace too
        if (artifact.startsWith(SdkConstants.SUPPORT_LIB_GROUP_ID)) {
          val newArtifact = AndroidxNameUtils.getCoordinateMapping(artifact)
          if (newArtifact != artifact) {
            queryCoordinate = GradleCoordinate.parseCoordinateString("$newArtifact:+")
            if (queryCoordinate != null) {
              foundDependency =
                facet.module.getModuleSystem().getResolvedDependency(queryCoordinate)
              if (foundDependency != null) {
                return java.lang.Boolean.TRUE
              }
            }
          }
        }
      }

      return super.dependsOn(artifact)
    }
  }

  private class LintAndroidModelProject(
    client: LintClient,
    dir: File,
    referenceDir: File,
    facet: AndroidFacet,
    private val androidModel: AndroidModel,
  ) : LintAndroidProject(client, dir, referenceDir, facet) {
    override fun getPackage(): String? {
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
    gradleAndroidModel: GradleAndroidModel,
  ) : LintModelModuleProject(client, dir, referenceDir, variant, null) {
    private val gradleAndroidModel: GradleAndroidModel
    private val facet: AndroidFacet

    /** Creates a new Project. Use one of the factory methods to create. */
    init {
      gradleProject = true
      mergeManifests = true
      this.facet = androidFacet
      this.gradleAndroidModel = gradleAndroidModel
    }

    override fun getJavaClassFolders(): List<File> {
      if (LintIdeClient.SUPPORT_CLASS_FILES) {
        return super.getJavaClassFolders()
      } else {
        return emptyList()
      }
    }

    override fun getJavaLibraries(includeProvided: Boolean): List<File> {
      if (LintIdeClient.SUPPORT_CLASS_FILES) {
        return super.getJavaLibraries(includeProvided)
      } else {
        return emptyList()
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

    override fun getBuildTargetHash(): String? {
      return gradleAndroidModel.androidProject.compileTarget
    }
  }

  private class LintGradleLibraryProject(
    client: LintClient,
    dir: File,
    referenceDir: File,
    dependency: LintModelDependency,
    library: LintModelAndroidLibrary,
  ) : LintModelModuleAndroidLibraryProject(client, dir, referenceDir, dependency, library) {
    override fun getJavaClassFolders(): List<File> {
      if (LintIdeClient.SUPPORT_CLASS_FILES) {
        return super.getJavaClassFolders()
      } else {
        return emptyList()
      }
    }

    override fun getJavaLibraries(includeProvided: Boolean): List<File> {
      if (LintIdeClient.SUPPORT_CLASS_FILES) {
        return super.getJavaLibraries(includeProvided)
      } else {
        return emptyList()
      }
    }
  }
}
