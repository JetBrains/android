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
package com.android.tools.idea.gradle.project.sync.idea

import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.FN_ANNOTATIONS_ZIP
import com.android.builder.model.v2.models.VariantDependenciesAdjacencyList
import com.android.ide.gradle.model.composites.BuildMap
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeArtifactName.Companion.toWellKnownSourceSet
import com.android.tools.idea.gradle.model.IdeDependenciesCore
import com.android.tools.idea.gradle.model.IdeDependencyCore
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.gradle.model.IdeVariantCore
import com.android.tools.idea.gradle.model.impl.IdeModuleSourceSetImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.project.model.GradleAndroidDependencyModel
import com.android.tools.idea.gradle.project.sync.AndroidProjectPathResolver
import com.android.tools.idea.gradle.project.sync.AndroidVariantResolver
import com.android.tools.idea.gradle.project.sync.BuildId
import com.android.tools.idea.gradle.project.sync.IdeVariantWithPostProcessor
import com.android.tools.idea.gradle.project.sync.InternedModels
import com.android.tools.idea.gradle.project.sync.ModelResult
import com.android.tools.idea.gradle.project.sync.ModelResult.Companion.ignoreExceptionsAndGet
import com.android.tools.idea.gradle.project.sync.ResolvedAndroidProjectPath
import com.android.tools.idea.gradle.project.sync.SyncTestMode
import com.android.tools.idea.gradle.project.sync.VariantDependenciesCompat
import com.android.tools.idea.gradle.project.sync.buildVariantNameResolver
import com.android.tools.idea.gradle.project.sync.modelCacheV2Impl
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.gradle.GradleSourceSetProjectPath
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.JavadocOrderRootType
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.util.PathUtil
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.gradle.model.GradleSourceSetModel
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.virtualFileUrl
import java.io.File

private val LOG = fileLogger()

/** Represents a source module. Used for setting up module to module dependencies. */
private data class SourceSetModuleId(
  val buildId: BuildId,
  val projectPath: String,
  val sourceSetName: String
)

/** Used to refer to Android projects and resolve variant names for them. */
data class ResolvedAndroidProjectPathImpl(
  override val gradleProject: BasicGradleProject,
  override val androidVariantResolver: AndroidVariantResolver,
  // Lint jar is not really relevant here, but required by the IdeLibrary model when resolving
  override val lintJar: File?
): ResolvedAndroidProjectPath

/**
 * Each project needs a certain amount of input and mutable state when resolving dependencies.
 *
 * This class encapsulates that state for a single project. See [SyncContributorAndroidProjectContext] as well.
 */
private class SyncContributorAndroidProjectDependenciesContext(
  val androidProjectContext: SyncContributorAndroidProjectContext,
  val updatedEntities: MutableEntityStorage,
  val ideLibraryModelResolver: IdeLibraryModelResolver,
  val sourceSetModuleIdToEntityMap: Map<SourceSetModuleId, ModuleEntity>,
  val moduleNameToEntityMap: Map<String, ModuleEntity>,
  val moduleNameToInstanceMap: Map<String, Module>,
  // Library id map is mutable to track newly created entities
  val libraryIdToEntityMap: MutableMap<LibraryId, LibraryEntity>,
) {
  val knownEntitySources = mutableSetOf<EntitySource>()
  val knownModuleNames = mutableSetOf<String>()


  /** Populates the dependencies of the module corresponding to the given artifact.  */
  fun IdeDependenciesCore.populateDependenciesForModule(scope: DependencyScope, name: IdeArtifactName) {
    val wellKnownSourceSetName = name.toWellKnownSourceSet().sourceSetName
    val moduleName = "${androidProjectContext.resolveHolderModuleName()}.$wellKnownSourceSetName"
    val entitySource = AndroidGradleSourceSetEntitySource(androidProjectContext.projectEntitySource, wellKnownSourceSetName)

    knownEntitySources += entitySource
    knownModuleNames += moduleName

    dependencies.flatMap { ideLibraryModelResolver.resolve(it) }.mapNotNull {
      when (it) {
        is IdeAndroidLibrary ->
          LibraryDependency(it.getOrCreateLibraryEntity(entitySource, moduleName).symbolicId, false, scope)

        is IdeJavaLibrary ->
          LibraryDependency(it.getOrCreateLibraryEntity(entitySource, moduleName).symbolicId, false, scope)

        is IdeModuleLibrary ->
          ModuleDependency(
            sourceSetModuleIdToEntityMap[it.id()]!!.symbolicId,
            false,
            scope,
            // Dependencies to test fixtures modules are marked as "production on test"
            productionOnTest = it.sourceSet.sourceSetName == IdeModuleWellKnownSourceSet.TEST_FIXTURES.sourceSetName
          )
        else -> null
      }
    }.distinct().let { newDependencies: List<ModuleDependencyItem> ->
      val moduleEntity = moduleNameToEntityMap[moduleName]!!
      val existingDependencies = moduleEntity.dependencies
      updatedEntities.modifyModuleEntity(moduleEntity) {
        dependencies.addAll(
          newDependencies.filter { it !in existingDependencies }
        )
      }
    }
  }

  /** Convert the IDE model to an id for the map we use. */
  private fun IdeModuleLibrary.id() = SourceSetModuleId(
    BuildId(File(buildId)),
    projectPath,
    sourceSet.sourceSetName
  )

  fun IdeArtifactLibrary.processName() = "Gradle: $name"

  /** Converts a file to the exact format required by the platform .*/
  fun File.toLibraryRootPath() = androidProjectContext.context.virtualFileUrl(this)

  /* Creates a library entity or find an existing one from storage, also counting any newly created ones. */
  fun getOrCreateLibraryEntity(moduleName: String, libraryEntityProvider: () -> LibraryEntity.Builder): LibraryEntity {
    val libraryEntityToBeAdded = libraryEntityProvider()
    fun lookup(tableId: LibraryTableId) = libraryIdToEntityMap[LibraryId(libraryEntityToBeAdded.name, tableId)]
    // Look up existing modules, reducing specificity of the table each time
    val existingProjectLibrary = lookup(LibraryTableId.ModuleLibraryTableId(ModuleId(moduleName)))
                                 ?: lookup(LibraryTableId.ProjectLibraryTableId)

    if (existingProjectLibrary != null) {
      return existingProjectLibrary
    }

    return libraryIdToEntityMap.computeIfAbsent(LibraryId(libraryEntityToBeAdded.name, LibraryTableId.ProjectLibraryTableId)) {
      LOG.trace("Creating new library entity in project table: ${libraryEntityToBeAdded.name}")
      updatedEntities addEntity libraryEntityToBeAdded
    }
  }

  /** Tries to resolve any existing annotation files on disk next to an artifact jar. */
  fun File.annotationRoots() = listOf(
    parentFile.resolve(FN_ANNOTATIONS_ZIP),
    parentFile.resolve(name.removeSuffix(DOT_JAR) + "-" + FN_ANNOTATIONS_ZIP)
  ).filter { it.isFile }.map {
    LibraryRoot(it.toLibraryRootPath(), LibraryRootTypeId(AnnotationOrderRootType.getInstance().name()))
  }

  fun IdeArtifactLibrary.sourcesAndJavaDocRoots() =
    listOfNotNull(docJar?.let { LibraryRoot(it.toLibraryRootPath(), LibraryRootTypeId(JavadocOrderRootType.getInstance().name())) }) +
    srcJars.map { LibraryRoot(it.toLibraryRootPath(), LibraryRootTypeId.SOURCES) }


  fun IdeJavaLibrary.getOrCreateLibraryEntity(entitySource: AndroidGradleSourceSetEntitySource, moduleName: String) =
    getOrCreateLibraryEntity(moduleName) {
      LibraryEntity(
        processName(),
        LibraryTableId.ProjectLibraryTableId,
        roots = listOf(
          LibraryRoot(artifact.toLibraryRootPath(), LibraryRootTypeId.COMPILED))
                + sourcesAndJavaDocRoots()
                + artifact.annotationRoots()
        ,
        entitySource = entitySource
      )
    }


  fun IdeAndroidLibrary.getOrCreateLibraryEntity(entitySource: AndroidGradleSourceSetEntitySource, moduleName: String) =
    getOrCreateLibraryEntity(moduleName) {
      LibraryEntity(
        processName(),
        LibraryTableId.ProjectLibraryTableId,
        roots = (compileJarFiles + listOf(resFolder, manifest))
          .filter { it.exists() }
          .flatMap { listOf(LibraryRoot(it.toLibraryRootPath(), LibraryRootTypeId.COMPILED)) + it.annotationRoots()} +
                sourcesAndJavaDocRoots(),
        entitySource = entitySource
      )
  }
}


internal fun setupAndroidDependenciesForAllProjects(
  context: ProjectResolverContext,
  phase: GradleSyncPhase,
  allAndroidContexts: List<SyncContributorAndroidProjectContext>,
  storage: ImmutableEntityStorage
) : SourceSetUpdateResult {
  val project = context.project

  val buildPathMap = buildBuildPathMap(context)
  val androidProjectPathResolver = buildAndroidProjectPathResolver(context, allAndroidContexts)
  val internedModels = InternedModels(context.allBuilds.first().buildIdentifier.rootDir)
  val updatedEntities = MutableEntityStorage.from(storage)

  val allContextWithDependencies = allAndroidContexts.mapNotNull { androidContext ->
    with(androidContext) {
      val selectedVariant = ideAndroidProject.coreVariants.single {it.name == variantName }
      fetchAndProcessAndroidDependenciesModel(
        selectedVariant,
        internedModels,
        androidProjectPathResolver,
        buildPathMap
      )?.let { androidContext to it }
    }
  }
  val ideLibraryModelResolver: IdeLibraryModelResolver = buildIdeLibraryModelResolver(internedModels, context)
  val sourceSetModuleIdToModuleEntityMap = buildSourceSetModuleIdToModuleEntityMap(storage, context, project, phase, allAndroidContexts)

  // Make the storage state into a mutable one to be able track newly created entities.
  val libraryIdToEntityMap: MutableMap<LibraryId, LibraryEntity> =
    updatedEntities.entities(LibraryEntity::class.java).associateBy { it.symbolicId }.toMutableMap()
  val moduleNameToEntityMap: Map<String, ModuleEntity> =
    updatedEntities.entities(ModuleEntity::class.java).associateBy { it.name }
  val moduleNameToInstanceMap: Map<String, Module> =
    project.modules.associateBy { it.name }

  val allKnownEntitySources = allContextWithDependencies.flatMap { (context, variantWithDependencies) ->
    SyncContributorAndroidProjectDependenciesContext(
      context,
      updatedEntities,
      ideLibraryModelResolver,
      sourceSetModuleIdToModuleEntityMap,
      moduleNameToEntityMap,
      moduleNameToInstanceMap,
      libraryIdToEntityMap,
    ).populateDependenciesForAndroidProject(variantWithDependencies)
  }

  return SourceSetUpdateResult (
    allModuleActions = emptyMap(), // unused
    allAndroidProjectContexts =  emptyList(), // unused
    updatedStorage = updatedEntities,
    knownEntitySources = allKnownEntitySources.toSet()
  )
}

private fun SyncContributorAndroidProjectContext.fetchAndProcessAndroidDependenciesModel(
  ideVariant: IdeVariantCore,
  internedModels: InternedModels,
  androidProjectPathResolver: AndroidProjectPathResolver,
  buildPathMap: Map<String, BuildId>,
): ModelResult<IdeVariantWithPostProcessor>? {
  val variantDependencies = context.getProjectModel(projectModel, VariantDependenciesAdjacencyList::class.java) ?: return null

  // We need to be lenient when modules are being resolved as some might not be set up yet if not supported by phased sync
  // - for instance, the KMP modules are not supported and not yet populated in this case
  val modelCacheV2Impl = modelCacheV2Impl(internedModels, versions, syncTestMode = SyncTestMode.PRODUCTION, lenientModuleResolution = true)

  return modelCacheV2Impl.variantFrom(
    BuildId(buildModel.buildIdentifier.rootDir),
    projectModel.projectIdentifier.projectPath,
    ideVariant as IdeVariantCoreImpl,
    VariantDependenciesCompat.AdjacencyList(variantDependencies, versions),
    ideAndroidProject.bootClasspath,
    androidProjectPathResolver,
    buildPathMap,
  )
}

private fun SyncContributorAndroidProjectDependenciesContext.populateDependenciesForAndroidProject(
  ideVariantWithPostProcessor: ModelResult<IdeVariantWithPostProcessor>
) : Set<EntitySource> {
  ideVariantWithPostProcessor.exceptions.firstOrNull()?.let { throw it }
  val ideVariant = ideVariantWithPostProcessor.ignoreExceptionsAndGet()?.postProcess()  ?: return emptySet()

  ideVariant.mainArtifact.let { it.compileClasspathCore.populateDependenciesForModule(DependencyScope.COMPILE, it.name) }
  ideVariant.testFixturesArtifact?.let { it.compileClasspathCore.populateDependenciesForModule(DependencyScope.COMPILE, it.name) }
  ideVariant.hostTestArtifacts.forEach { it.compileClasspathCore.populateDependenciesForModule(DependencyScope.TEST, it.name)}
  ideVariant.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST }?.let {
    it.compileClasspathCore.populateDependenciesForModule(DependencyScope.TEST, it.name)
  }

  val allKnownModuleInstances = listOfNotNull(moduleNameToInstanceMap[androidProjectContext.resolveHolderModuleName()]) + knownModuleNames.mapNotNull { moduleNameToInstanceMap[it] }
  val dependencyModelFactory = GradleAndroidDependencyModel.createFactory(androidProjectContext.project, libraryResolver = ideLibraryModelResolver)
  allKnownModuleInstances.forEach { module ->
    val facet = AndroidFacet.getInstance(module) ?: return@forEach
    AndroidModel.set(facet, dependencyModelFactory(androidProjectContext.gradleAndroidModelFactory(module.name).copy(
      variants = listOf(ideVariant) // Just pass the resolved variant and discard the rest.
    )))
  }

  return knownEntitySources
}

// Helpers, maps, etc.

/* Used to refer to the library table from dependency models. */
private fun buildIdeLibraryModelResolver(
  internedModels: InternedModels,
  context: ProjectResolverContext
): IdeLibraryModelResolver {
  val artifactToSourceSetMap = buildJarArtifactToSourceSetMapFromPlatformModels(context)
  val libraryTable = internedModels.apply { prepare() }.createLibraryTable()
  val resolvedTable = ResolvedLibraryTableBuilder(
    getGradlePathBy = { null },
    getModuleDataNode = { null },
    resolveArtifact = { artifactToSourceSetMap[it] },
    resolveKmpAndroidMainSourceSet = { null }
  ).buildResolvedLibraryTable(libraryTable)

  val resolver: IdeLibraryModelResolver = object : IdeLibraryModelResolver {
    override fun resolve(unresolved: IdeDependencyCore) = resolvedTable.libraries[unresolved.target.libraryIndex].asSequence()
  }
  return resolver
}

/**
 * Artifacts jar paths are  used to resolve to Java projects to their source sets. This builds a mapping from jars to their Gradle paths
 * representing the source set modules.
 *
 * A jar might map to multiple source sets (as it's not specific enough), so each file might correspond to multiple source sets.
 */
private fun buildJarArtifactToSourceSetMapFromPlatformModels(context: ProjectResolverContext): Map<File?, List<GradleSourceSetProjectPath>> = context.allBuilds.flatMap { buildModel ->
  buildModel.projects.flatMap { projectModel ->
    context.getProjectModel(projectModel, GradleSourceSetModel::class.java)
      ?.sourceSets.orEmpty()
      .values.flatMap { sourceSet ->
        sourceSet.artifacts.map {
          it to GradleSourceSetProjectPath(
            PathUtil.toSystemIndependentName(buildModel.rootProject.projectDirectory.path),
            projectModel.path,
            IdeModuleSourceSetImpl.wellKnownOrCreate(sourceSet.name)
          )
        }
      }
      .groupBy( { (artifact, sourceSets) -> artifact } ) {
        (artifact, sourceSets) -> sourceSets
      }.entries.filter { (artifact, sourceSets) ->
        sourceSets.isNotEmpty()
      }
  }
}.associate { (artifact, sourceSets) ->
  artifact to sourceSets
}


/** Builds an [AndroidProjectPathResolver] instance, used to later refer to Android projects and resolve variant names for them. */
private fun buildAndroidProjectPathResolver(
  context: ProjectResolverContext,
  allAndroidContexts: List<SyncContributorAndroidProjectContext>
): AndroidProjectPathResolver {
  val projectIdentifierToResolvedProjectPathMap = allAndroidContexts.mapNotNull {
    with(it) {
      val basicGradleProject = context.getProjectModel(projectModel, BasicGradleProject::class.java) ?: return@mapNotNull null

      BuildId(projectModel.projectIdentifier.buildIdentifier.rootDir) to projectModel.path to
        ResolvedAndroidProjectPathImpl(
          basicGradleProject,
          buildVariantNameResolver(ideAndroidProject, ideAndroidProject.coreVariants),
          ideAndroidProject.lintJar
        )
    }
  }.toMap()
  return AndroidProjectPathResolver { buildId, projectPath -> projectIdentifierToResolvedProjectPathMap[buildId to projectPath] }
}

/** Map from the Gradle path (composite aware, supports nested builds) to the Gradle build root directory. */
private fun buildBuildPathMap(context: ProjectResolverContext): Map<String, BuildId> = context.allBuilds.flatMap { buildModel ->
  val buildMapModel = context.getProjectModel(buildModel.rootProject, BuildMap::class.java) ?: return@flatMap emptyList()
  buildMapModel.buildIdMap.entries
}.associate { it.key to BuildId(it.value) }


/** Returns the mapping from [SourceSetModuleId] to module entities for all projects. */
private fun buildSourceSetModuleIdToModuleEntityMap(
  storage: ImmutableEntityStorage,
  context: ProjectResolverContext,
  project: Project,
  phase: GradleSyncPhase,
  allAndroidContexts: List<SyncContributorAndroidProjectContext>
): Map<SourceSetModuleId, ModuleEntity> {
  // First build a map of all known source sets
  val allSourceSetModuleIdsMap: Map<String, SourceSetModuleId> = (
    buildAndroidSourceSetModuleIdsMap(allAndroidContexts, context) +
    buildJavaSourceSetModuleIdsMap(context, project, phase)
                                                                 ).toMap()

  // And associate them with existing entities
  return storage.entities(ModuleEntity::class.java).mapNotNull { entity ->
    val exModuleOptions = entity.exModuleOptions ?: return@mapNotNull null
    val sourceSetModuleId = allSourceSetModuleIdsMap[exModuleOptions.linkedProjectId] ?: return@mapNotNull null
    sourceSetModuleId to entity
  }.toMap()
}

/** Returns the mapping from [SourceSetModuleId] to module entities for Java projects. */
private fun buildJavaSourceSetModuleIdsMap(context: ProjectResolverContext, project: Project, phase: GradleSyncPhase): Map<String, SourceSetModuleId> = context.allBuilds.flatMap { buildModel ->
  buildModel.projects.flatMap { projectModel ->
    with(SyncContributorProjectContext(context, project, phase, buildModel, projectModel)) {
      val sourceSetModel = context.getProjectModel(projectModel, GradleSourceSetModel::class.java) ?: return@flatMap emptyList()
      sourceSetModel.sourceSets.values.map {
        val linkedProjectId = GradleProjectResolverUtil.getModuleId(context, externalProject, it)
        linkedProjectId to SourceSetModuleId(
          buildId = BuildId(buildModel.buildIdentifier.rootDir),
          projectPath = projectModel.path,
          sourceSetName = it.name,
        )
      }
    }
  }
}.toMap()

/** Returns the mapping from [SourceSetModuleId] to module entities for Android projects. */
private fun buildAndroidSourceSetModuleIdsMap(
  allAndroidContexts: List<SyncContributorAndroidProjectContext>,
  context: ProjectResolverContext
): Map<String, SourceSetModuleId> = allAndroidContexts.flatMap {
  with(it) {
    // Well known source sets can be a target dependency, so it's what we populate the map with
    IdeModuleWellKnownSourceSet.entries.map {
      val linkedProjectId = "${GradleProjectResolverUtil.getModuleId(context, externalProject)}:${it.sourceSetName}"
      linkedProjectId to SourceSetModuleId(
        buildId = BuildId(buildModel.buildIdentifier.rootDir),
        projectPath = projectModel.path,
        sourceSetName = it.sourceSetName
      )
    }
  }
}.toMap()