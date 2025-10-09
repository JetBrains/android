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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.IdeAndroidArtifactCore
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeArtifactName.Companion.toWellKnownSourceSet
import com.android.tools.idea.gradle.model.IdeDependenciesCore
import com.android.tools.idea.gradle.model.IdeJavaArtifactCore
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.model.IdeVariantCore
import com.android.tools.idea.gradle.model.impl.IdeDependenciesCoreDirect
import com.android.tools.idea.gradle.model.impl.IdeLibraryModelResolverImpl
import com.android.tools.idea.gradle.model.impl.IdeModuleSourceSetImpl
import com.android.tools.idea.gradle.model.impl.IdeModuleWellKnownSourceSet
import com.android.tools.idea.gradle.model.impl.IdeTestSuiteVariantTargetImpl
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedLibraryTable
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedLibraryTableImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantImpl
import com.android.tools.idea.gradle.project.entities.attachDependenciesToModuleEntity
import com.android.tools.idea.gradle.project.sync.BuildId
import com.android.tools.idea.gradle.project.sync.patchForKapt
import com.android.tools.idea.projectsystem.gradle.GradleSourceSetProjectPath
import com.intellij.openapi.diagnostic.currentClassLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.JavadocOrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.LibraryEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.PathUtil
import java.io.File
import org.jetbrains.plugins.gradle.model.GradleSourceSetModel
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.virtualFileUrl

private val LOG = currentClassLogger()

/** Represents a source module. Used for setting up module to module dependencies. */
private data class SourceSetModuleId(
  val buildId: BuildId,
  val projectPath: String,
  val sourceSetName: String
)


/**
 * Each project needs a certain amount of input and mutable state when resolving dependencies.
 *
 * This class encapsulates that state for a single project. See [SyncContributorAndroidProjectContext] as well.
 */
private class SyncContributorAndroidProjectDependenciesContext(
  val androidProjectContext: SyncContributorAndroidProjectContext,
  val updatedEntities: MutableEntityStorage,
  val ideLibraryModelResolver: IdeLibraryModelResolverImpl,
  val sourceSetModuleIdToEntityMap: Map<SourceSetModuleId, ModuleEntity>,
  val moduleNameToEntityMap: Map<String, ModuleEntity>,
  val moduleNameToInstanceMap: Map<String, Module>,
  // Library id map is mutable to track newly created entities
  val libraryIdToEntityMap: MutableMap<LibraryId, LibraryEntity>,
  val libraryRootPathCache: MutableMap<File, VirtualFileUrl>,
) {
  val knownEntitySources = mutableSetOf<EntitySource>(androidProjectContext.holderModuleEntity.entitySource)
  val knownModuleNames = mutableSetOf<String>()

  fun IdeDependenciesCore.populateDependenciesForModule(scope: DependencyScope, name: IdeArtifactName) {
    val wellKnownSourceSetName = name.toWellKnownSourceSet().sourceSetName
    populateDependenciesForModule(scope, wellKnownSourceSetName)
  }

  /** Populates the dependencies of the module corresponding to the given artifact.  */
  fun IdeDependenciesCore.populateDependenciesForModule(scope: DependencyScope, sourceSetName: String) {
    val moduleName = "${androidProjectContext.resolveHolderModuleName()}.$sourceSetName"
    val entitySource = AndroidGradleSourceSetEntitySource(androidProjectContext.projectEntitySource, sourceSetName)
    val moduleEntity = moduleNameToEntityMap[moduleName]
    if (moduleEntity == null) {
      LOG.error("Expected module not found: $moduleName")
      return
    }

    val existingDependencies = moduleEntity.dependencies.toSet()

    knownEntitySources += entitySource
    knownModuleNames += moduleName

    dependencies.flatMap { ideLibraryModelResolver.resolve(it) }.mapNotNull {
      when (it) {
        is IdeAndroidLibrary ->
          LibraryDependency(it.getOrCreateLibraryEntity(entitySource, moduleName).symbolicId, false, scope)

        is IdeJavaLibrary ->
          LibraryDependency(it.getOrCreateLibraryEntity(entitySource, moduleName).symbolicId, false, scope)

        is IdeModuleLibrary ->
          sourceSetModuleIdToEntityMap[it.id()]?.let { entity ->
            ModuleDependency(
              entity.symbolicId,
              false,
              scope,
              // Dependencies to test fixtures modules are marked as "production on test"
              productionOnTest = it.sourceSet.sourceSetName == IdeModuleWellKnownSourceSet.TEST_FIXTURES.sourceSetName
            )
          }
        else -> null
      }.takeIf { it !in existingDependencies }
    }.distinct().let { dependenciesToAdd: List<ModuleDependencyItem> ->
      if(LOG.isTraceEnabled) {
        LOG.trace("Adding dependencies for $moduleName: $dependenciesToAdd")
      }
      if (dependenciesToAdd.isNotEmpty()) {
        updatedEntities.modifyModuleEntity(moduleEntity) {
          dependencies.addAll(dependenciesToAdd)
        }
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
  fun File.toLibraryRootPath() = libraryRootPathCache.computeIfAbsent(this) {
    androidProjectContext.context.virtualFileUrl(this)
  }

  /* Creates a library entity or find an existing one from storage, also counting any newly created ones. */
  fun getOrCreateLibraryEntity(moduleName: String, name: String, libraryEntityProvider: () -> LibraryEntityBuilder): LibraryEntity {
    fun lookup(tableId: LibraryTableId) = libraryIdToEntityMap[LibraryId(name, tableId)]
    // Look up existing modules, reducing specificity of the table each time
    val existingProjectLibrary = lookup(LibraryTableId.ModuleLibraryTableId(ModuleId(moduleName)))
                                 ?: lookup(LibraryTableId.ProjectLibraryTableId)

    if (existingProjectLibrary != null) {
      return existingProjectLibrary
    }

    return libraryIdToEntityMap.computeIfAbsent(LibraryId(name, LibraryTableId.ProjectLibraryTableId)) {
      LOG.trace("Creating new library entity in project table: $name")
      updatedEntities addEntity libraryEntityProvider()
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
    getOrCreateLibraryEntity(moduleName, processName()) {
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
    getOrCreateLibraryEntity(moduleName, processName()) {
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

  val libraryTable = context.getRootModel(IdeUnresolvedLibraryTableImpl::class.java) ?: run {
    LOG.info("No library table found, returning early with no updates")
    return SourceSetUpdateResult(updatedStorage = storage, knownEntitySources = emptySet())
  }
  val updatedEntities = MutableEntityStorage.from(storage)
  val ideLibraryModelResolver = buildIdeLibraryModelResolver(context, libraryTable)
  val sourceSetModuleIdToModuleEntityMap = buildSourceSetModuleIdToModuleEntityMap(storage, context, project, phase, allAndroidContexts)

  // Make the storage state into a mutable one to be able track newly created entities.
  val libraryIdToEntityMap: MutableMap<LibraryId, LibraryEntity> =
    updatedEntities.entities(LibraryEntity::class.java).associateBy { it.symbolicId }.toMutableMap()
  val moduleNameToEntityMap: Map<String, ModuleEntity> =
    storage.entities(ModuleEntity::class.java).associateBy { it.name }
  val moduleNameToInstanceMap: Map<String, Module> =
    project.modules.associateBy { it.name }
  val libraryRootPathCache = mutableMapOf<File, VirtualFileUrl>()

  val allKnownEntitySources = allAndroidContexts.flatMap {
    SyncContributorAndroidProjectDependenciesContext(
      it,
      updatedEntities,
      ideLibraryModelResolver,
      sourceSetModuleIdToModuleEntityMap,
      moduleNameToEntityMap,
      moduleNameToInstanceMap,
      libraryIdToEntityMap,
      libraryRootPathCache
    ).populateDependenciesForAndroidProject()
  }

  return SourceSetUpdateResult (
    updatedStorage = updatedEntities,
    knownEntitySources = allKnownEntitySources.toSet()
  )
}


private fun SyncContributorAndroidProjectDependenciesContext.populateDependenciesForAndroidProject() : Set<EntitySource> {
  val ideVariant = with(androidProjectContext) {
    val variant = context.getProjectModel(androidProjectContext.projectModel, IdeVariantCore::class.java) as? IdeVariantCoreImpl
    androidProjectContext.kaptGradleModel?.let { variant?.patchForKapt(it) } ?: variant ?: return emptySet()
  }

  val classpathsToProcess = listOfNotNull(
    ideVariant.mainArtifact.asCompileDependency(),
    ideVariant.testFixturesArtifact?.asCompileDependency(),
    ideVariant.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST }?.asTestDependency(),
  ) + ideVariant.hostTestArtifacts.map {
    it.asTestDependency()
  } + if (StudioFlags.AGP_TEST_SUITES_ENABLED.get()) {
    // TODO(445381129): Pass the dependencies through once they have been added to the test suite artifact
    ideVariant.testSuiteArtifacts.map { it.asEmptyTestDependency() }
  } else {
    emptyList()
  }

  classpathsToProcess.forEach { (name, classpath, scope) ->
    classpath.populateDependenciesForModule(scope, name)
  }

  val allKnownModuleEntities = listOfNotNull(moduleNameToEntityMap[androidProjectContext.resolveHolderModuleName()]) +
                               knownModuleNames.mapNotNull { moduleNameToEntityMap[it] }

  allKnownModuleEntities.forEach { entity ->
    attachDependenciesToModuleEntity(updatedEntities, entity, IdeVariantImpl(ideVariant, ideLibraryModelResolver))
  }

  return knownEntitySources
}

// Helpers, maps, etc.

/* Used to refer to the library table from dependency models. */
private fun buildIdeLibraryModelResolver(
  context: ProjectResolverContext,
  libraryTable: IdeUnresolvedLibraryTable
): IdeLibraryModelResolverImpl {
  val artifactToSourceSetMap = buildJarArtifactToSourceSetMapFromPlatformModels(context)
  val resolvedTable = ResolvedLibraryTableBuilder(
    getGradlePathBy = { null },
    getModuleDataNode = { null },
    resolveArtifact = { artifactToSourceSetMap[it] },
    resolveKmpAndroidMainSourceSet = { null },
    ignoreKmpFailures=true
  ).buildResolvedLibraryTable(libraryTable)

  return IdeLibraryModelResolverImpl.fromLibraryTables(
    globalLibraryTable = resolvedTable,
    kmpLibraryTable = null
  )
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
    buildJavaSourceSetModuleIdsMap(context, project, phase)).toMap()

  // And associate them with existing entities
  return storage.entities(ModuleEntity::class.java).mapNotNull { entity ->
    val exModuleOptions = entity.exModuleOptions ?: return@mapNotNull null.also {
      LOG.debug("External module options not found for module ${entity.name}")
    }
    val sourceSetModuleId = allSourceSetModuleIdsMap[exModuleOptions.linkedProjectId] ?: return@mapNotNull null.also {
      LOG.debug("Source set mapping not found for ${exModuleOptions.linkedProjectId}")
    }
    sourceSetModuleId to entity
  }.toMap()
}

/** Returns the mapping from [SourceSetModuleId] to module entities for Java projects. */
private fun buildJavaSourceSetModuleIdsMap(context: ProjectResolverContext, project: Project, phase: GradleSyncPhase): Map<String, SourceSetModuleId> = context.allBuilds.flatMap { buildModel ->
  buildModel.projects.flatMap { projectModel ->
    with(SyncContributorProjectContext(context, project, phase, buildModel, projectModel)) {
      val sourceSetModel = context.getProjectModel(projectModel, GradleSourceSetModel::class.java) ?: (return@flatMap emptyList()).also {
        LOG.debug("No GradleSourceSet model found for ${projectModel.path}")
      }
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


private fun IdeAndroidArtifactCore.asCompileDependency() = Triple(name.toWellKnownSourceSet().sourceSetName, compileClasspathCore, DependencyScope.COMPILE)
private fun IdeAndroidArtifactCore.asTestDependency() = Triple(name.toWellKnownSourceSet().sourceSetName, compileClasspathCore, DependencyScope.TEST)
private fun IdeJavaArtifactCore.asTestDependency() = Triple(name.toWellKnownSourceSet().sourceSetName, compileClasspathCore, DependencyScope.TEST)
private fun IdeTestSuiteVariantTargetImpl.asEmptyTestDependency() = Triple(suiteName, IdeDependenciesCoreDirect(emptyList()), DependencyScope.TEST)