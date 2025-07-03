package com.google.idea.blaze.base.qsync

import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.base.qsync.entity.BazelEntitySource
import com.google.idea.blaze.base.sync.projectview.LanguageSupport
import com.google.idea.blaze.base.util.UrlUtil
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot
import com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.WORKSPACE_MODULE_NAME
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.common.experiments.EnumExperiment
import com.google.idea.common.experiments.IntExperiment
import com.google.idea.common.util.Transactions
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import java.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** An object that monitors the build graph and applies the changes to the project structure by using WorkspaceEntity. */
class ProjectUpdaterWithWorkspaceEntity(private val project: Project) : QuerySyncProjectListener {

  enum class ProjectStructure {
    SHARDED_LIBRARY,
    LIBRARY_PER_TARGET
  }

  companion object {
    val projectStructureExperiment = EnumExperiment("query.sync.project.structure", ProjectStructure.SHARDED_LIBRARY)
    val libraryShardsExperiment = IntExperiment("query.sync.library.shards", 10)
  }

  private var lastProjectProtoSnapshot: ProjectProto.Project = ProjectProto.Project.getDefaultInstance()

  override fun onNewProjectStructure(
    context: Context<*>,
    querySyncProject: ReadonlyQuerySyncProject,
    graph: QuerySyncProjectSnapshot,
  ) {
    val newProjectProtoSnapshot = graph.project()
    if (lastProjectProtoSnapshot == newProjectProtoSnapshot) {
      context.output(PrintOutput.output("IDE project structure up-to-date"))
      return
    }
    EntityWorker(project, querySyncProject, newProjectProtoSnapshot, context).updateProjectModel()
    updateProjectModel(querySyncProject, newProjectProtoSnapshot, context)
    lastProjectProtoSnapshot = newProjectProtoSnapshot
  }

  data class ProjectData(
    val modules: List<ModuleData>,
    val libraries: List<LibraryData>,
  ) {
    companion object
  }

  data class ModuleData(
    val name: String,
    val dependencies: List<String>,
    val contentRoots: List<ContentRootData>,
  ) {
    companion object
  }

  data class LibraryData(
    val name: String,
    val jarUrls: List<String>,
    val sourceUrls: List<String>,
  ) {
    companion object
  }

  data class ContentRootData(
    val url: String,
    val sourceRoots: List<SourceRootData>,
    val excludedRoots: List<String>,
  ) {
    companion object
  }

  data class SourceRootData(
    val url: String,
    val rootTypeId: SourceRootTypeId,
    val isGenerated: Boolean,
    val packagePrefix: String,
  ) {
    companion object
  }

  inner class EntityWorker(
    val project: Project,
    val querySyncProject: ReadonlyQuerySyncProject,
    val spec: ProjectProto.Project,
    val context: Context<*>,
  ) {
    private val virtualFileManager = VirtualFileManager.getInstance()
    private val projectBase = Paths.get(project.getBasePath())
    private val projectPathResolver = querySyncProject.projectPathResolver
    fun ProjectProto.JarDirectory.toIdeaUrl(): String {
      return UrlUtil.pathToIdeaUrl(projectBase.resolve(this.path))
        .also { virtualFileManager.findFileByUrl(it) }  // Register roots in a background thread.
    }

    fun ProjectProto.LibrarySource.toIdeaUrl(): String {
      val projectPath = ProjectPath.create(srcjar)
      return UrlUtil.pathToUrl(projectPathResolver.resolve(projectPath).toString(), projectPath.innerJarPath())
        .also { virtualFileManager.findFileByUrl(it) }  // Register roots in a background thread.
    }

    fun ProjectProto.ProjectPath.toIdeaUrl(): String {
      val sourceFolderProjectPath = ProjectPath.create(this)
      return sourceFolderProjectPath.toIdeaUrl()
    }

    fun ProjectPath.toIdeaUrl(): String {
      return UrlUtil.pathToIdeaUrl(projectPathResolver.resolve(this))
    }

    fun ModuleData.Companion.from(
      module: ProjectProto.Module,
      dependencies: List<String>,
      contentRoots: List<ContentRootData>,
    ): ModuleData {
      return ModuleData(name = module.name, dependencies = dependencies, contentRoots = contentRoots)
    }

    fun LibraryData.Companion.from(library: ProjectProto.Library): LibraryData {
      return LibraryData(
        name = library.name,
        jarUrls = library.classesJarList.map { it.toIdeaUrl() },
        sourceUrls = library.sourcesList.filter { it.hasSrcjar() }.map { it.toIdeaUrl() },
      )
    }

    fun LibraryData.Companion.from(name: String, libraries: List<ProjectProto.Library>): LibraryData {
      return LibraryData(
        name = name,
        jarUrls = libraries.flatMap { it.classesJarList }.map { it.toIdeaUrl() },
        sourceUrls = libraries.flatMap { it.sourcesList }.filter { it.hasSrcjar() }.map { it.toIdeaUrl() },
      )
    }

    fun SourceRootData.Companion.from(
      projectPath: ProjectProto.ProjectPath,
      isTest: Boolean,
      isGenerated: Boolean,
      packagePrefix: String,
    ): SourceRootData {
      return SourceRootData(
        url = projectPath.toIdeaUrl(),
        rootTypeId = if (isTest) JAVA_TEST_ROOT_ENTITY_TYPE_ID else JAVA_SOURCE_ROOT_ENTITY_TYPE_ID,
        isGenerated = isGenerated,
        packagePrefix = packagePrefix
      )
    }

    fun ContentRootData.Companion.from(
      root: ProjectProto.ProjectPath,
      sources: List<ProjectProto.SourceFolder>,
      excludedRoots: List<String>,
    ): ContentRootData {
      return ContentRootData(
        url = root.toIdeaUrl(),
        sourceRoots = sources.map {
          SourceRootData.from(
            it.projectPath,
            it.isTest,
            it.isGenerated,
            it.packagePrefix)
        },
        excludedRoots = excludedRoots.map { ProjectPath.workspaceRelative(it).toIdeaUrl() },
      )
    }

    fun ProjectData.Companion.from(project: ProjectProto.Project): ProjectData {
      val libraries = when (projectStructureExperiment.value) {
        ProjectStructure.LIBRARY_PER_TARGET ->
          project.libraryList.map { LibraryData.from(it) }

        ProjectStructure.SHARDED_LIBRARY -> let {
          val shards = libraryShardsExperiment.value.toULong()
          project.libraryList
            .groupBy { it.name.hashCode().toULong() % shards }
            .map {
              LibraryData.from("Lib ${it.key}", it.value)
            }
        }
      }
      return ProjectData(
        modules = project.modulesList.map {
          ModuleData.from(it, libraries.map { it.name }, it.contentEntriesList.map {
            ContentRootData.from(it.root, sources = it.sourcesList, excludedRoots = it.excludesList)
          })
        },
        libraries = libraries,
      )
    }
  }

  private fun EntityWorker.updateProjectModel() {
    runBlocking {
      context.output(PrintOutput.output("Begin updating project model"))
      val originalSnapshot = WorkspaceModel.getInstance(project).currentSnapshot
      val changes = MutableEntityStorage.from(originalSnapshot)
      buildChanges(changes, ProjectData.from(spec))
      withContext(Dispatchers.EDT) {
        edtWriteAction {
          WorkspaceModel.getInstance(project).updateProjectModel("Updating project model") { builder ->
            context.output(PrintOutput.output("Applying project model changes"))
            if (originalSnapshot !== WorkspaceModel.getInstance(project).currentSnapshot) {
              context.output(PrintOutput.error("FAILED: Project model has changed"))
              error("Concurrent changes to project model detected. TODO: Retry.")
            }
            builder.applyChangesFrom(changes)
            context.output(PrintOutput.output("Project model changes applied"))
          }
        }
      }
    }
  }

  private fun buildChanges(
    storage: MutableEntityStorage,
    projectData: ProjectData,
  ) {
    val virtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    storage.replaceBySource(
      { it is BazelEntitySource || it is JpsProjectFileEntitySource },
      MutableEntityStorage.create().apply {
        val libraries = projectData.libraries.map {
          addEntity(LibraryEntity(
            name = it.name,
            tableId = LibraryTableId.ProjectLibraryTableId,
            roots = it.jarUrls.map {
              LibraryRoot(
                url = virtualFileUrlManager.getOrCreateFromUrl(it), type = LibraryRootTypeId.COMPILED)
            } + it.sourceUrls.map {
              LibraryRoot(
                url = virtualFileUrlManager.getOrCreateFromUrl(it), type = LibraryRootTypeId.SOURCES)
            },
            entitySource = BazelEntitySource
          ))
        }.associateBy { it.name }

        for (module in projectData.modules) {
          val dependencies = listOf(ModuleSourceDependency, InheritedSdkDependency) +
                             module.dependencies.map {
                               LibraryDependency(libraries[it]?.symbolicId ?: error("Unresolved library dependency: $it"),
                                                 exported = false,
                                                 scope = DependencyScope.COMPILE)
                             }
          addEntity(
            ModuleEntity(name = WORKSPACE_MODULE_NAME, dependencies = dependencies, entitySource = BazelEntitySource) {
              this.contentRoots = module.contentRoots.map {
                ContentRootEntity(
                  url = virtualFileUrlManager.getOrCreateFromUrl(it.url),
                  excludedPatterns = listOf(),
                  entitySource = BazelEntitySource
                ) {
                  this.sourceRoots = it.sourceRoots.map {
                    SourceRootEntity(
                      url = virtualFileUrlManager.getOrCreateFromUrl(it.url),
                      rootTypeId = it.rootTypeId,
                      entitySource = BazelEntitySource
                    ) {
                      this.javaSourceRoots += JavaSourceRootPropertiesEntity(
                        generated = it.isGenerated,
                        packagePrefix = it.packagePrefix,
                        entitySource = BazelEntitySource
                      )
                    }
                  }
                  this.excludedUrls = it.excludedRoots.map {
                    ExcludeUrlEntity(
                      url = virtualFileUrlManager.getOrCreateFromUrl(it),
                      entitySource = BazelEntitySource
                    ) { }
                  }
                }
              }
            })
        }
      }
    )
  }

  private fun updateProjectModel(querySyncProject: ReadonlyQuerySyncProject, spec: ProjectProto.Project, context: Context<*>) {
    Transactions.submitWriteActionTransactionAndWait {
      val models =
        ProjectDataManager.getInstance().createModifiableModelsProvider(project)
      for (syncPlugin in BlazeQuerySyncPlugin.EP_NAME.extensions) {
        syncPlugin.updateProjectSettingsForQuerySync(project, context, querySyncProject.projectViewSet)
      }
      for (moduleSpec in spec.getModulesList()) {
        val module =
          models.findIdeModule(moduleSpec.getName())!!
        val workspaceLanguageSettings =
          LanguageSupport.createWorkspaceLanguageSettings(querySyncProject.projectViewSet)
        for (syncPlugin in BlazeQuerySyncPlugin.EP_NAME.extensions) {
          // TODO update ProjectProto.Module and updateProjectStructure() to allow a more
          // suitable
          //   data type to be passed in here instead of androidResourceDirectories and
          //   androidSourcePackages
          syncPlugin.updateProjectStructureForQuerySync(
            project,
            context,
            models,
            querySyncProject.workspaceRoot,
            module,
            ImmutableSet.copyOf<String?>(moduleSpec.androidResourceDirectoriesList),
            ImmutableSet.builder<String?>()
              .addAll(moduleSpec.androidSourcePackagesList)
              .addAll(moduleSpec.androidCustomPackagesList)
              .build(),
            workspaceLanguageSettings
          )
        }
        models.commit()
        ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(
          EmptyRunnable.getInstance(), RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED
        )
      }
    }
  }
}