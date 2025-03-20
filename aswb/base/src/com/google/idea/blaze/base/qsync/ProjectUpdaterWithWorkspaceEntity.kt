package com.google.idea.blaze.base.qsync

import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.qsync.ProjectUpdaterWithWorkspaceEntity.LibraryData
import com.google.idea.blaze.base.qsync.ProjectUpdaterWithWorkspaceEntity.ModuleData
import com.google.idea.blaze.base.qsync.ProjectUpdaterWithWorkspaceEntity.ProjectData
import com.google.idea.blaze.base.qsync.entity.BazelEntitySource
import com.google.idea.blaze.base.sync.projectview.LanguageSupport
import com.google.idea.blaze.base.util.UrlUtil
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.qsync.QuerySyncProjectListener
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot
import com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.WORKSPACE_MODULE_NAME
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.common.util.Transactions
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
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.storage.MutableEntityStorage
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService

/** An object that monitors the build graph and applies the changes to the project structure by using WorkspaceEntity. */
class ProjectUpdaterWithWorkspaceEntity(
  private val project: Project,
  private val projectViewSet: ProjectViewSet,
  private val workspaceRoot: WorkspaceRoot,
  private val projectPathResolver: ProjectPath.Resolver,
) : QuerySyncProjectListener {
  override fun onNewProjectSnapshot(
    context: Context<*>,
    graph: QuerySyncProjectSnapshot,
  ) {
    EntityWorker(project, graph.project(), context).updateProjectModel()
    updateProjectModel(graph.project(), context)
  }

  data class ModuleData(val name: String) {
    companion object
  }

  data class LibraryData(
    val name: String,
    val jarUrls: List<String>,
    val sourceUrls: List<String>,
  ) {
    companion object
  }

  data class ProjectData(
    val modules: Map<String, ModuleData>,
    val libraries: Map<String, LibraryData>,
  ) {
    companion object
  }

  inner class EntityWorker(val project: Project, val spec: ProjectProto.Project, val context: Context<*>) {
    private val virtualFileManager = VirtualFileManager.getInstance()
    private val projectBase = Paths.get(project.getBasePath())

    fun ProjectProto.JarDirectory.toIdeaUrl(): String {
      return UrlUtil.pathToIdeaUrl(projectBase.resolve(this.path))
        .also { virtualFileManager.findFileByUrl(it) }  // Register roots in a background thread.
    }

    fun ProjectProto.LibrarySource.toIdeaUrl(): String {
      val projectPath = ProjectPath.create(srcjar)
      return UrlUtil.pathToUrl(projectPathResolver.resolve(projectPath).toString(), projectPath.innerJarPath())
        .also { virtualFileManager.findFileByUrl(it) }  // Register roots in a background thread.
    }

    fun ModuleData.Companion.from(module: ProjectProto.Module): ModuleData {
      return ModuleData(
        name = module.name,
      )
    }

    fun LibraryData.Companion.from(library: ProjectProto.Library): LibraryData {
      return LibraryData(
        name = library.name,
        jarUrls = library.classesJarList.map { it.toIdeaUrl() },
        sourceUrls = library.sourcesList.filter { it.hasSrcjar() }.map { it.toIdeaUrl() },
      )
    }


    fun ProjectData.Companion.from(project: ProjectProto.Project): ProjectData {
      return ProjectData(
        modules = project.modulesList.map { ModuleData.from(it) }.associateBy { it.name },
        libraries = project.libraryList.map { LibraryData.from(it) }.associateBy { it.name },
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
          val lib = it.value
          addEntity(LibraryEntity(
            name = lib.name,
            tableId = LibraryTableId.ProjectLibraryTableId,
            roots = lib.jarUrls.map {
              LibraryRoot(
                url = virtualFileUrlManager.getOrCreateFromUrl(it), type = LibraryRootTypeId.COMPILED)
            } + lib.sourceUrls.map {
              LibraryRoot(
                url = virtualFileUrlManager.getOrCreateFromUrl(it), type = LibraryRootTypeId.SOURCES)
            } ,
            entitySource = BazelEntitySource
          ))
        }
        addEntity(ModuleEntity(
          name = WORKSPACE_MODULE_NAME,
          dependencies =
            listOf(ModuleSourceDependency, InheritedSdkDependency) +
            libraries.map {
              LibraryDependency(library = it.symbolicId, exported = false, scope = DependencyScope.COMPILE)
            },
          entitySource = BazelEntitySource
        ))
      }
    )
  }

  private fun
    updateProjectModel(spec: ProjectProto.Project, context: Context<*>) {
    Transactions.submitWriteActionTransactionAndWait {
      val models =
        ProjectDataManager.getInstance().createModifiableModelsProvider(project)
      for (syncPlugin in BlazeQuerySyncPlugin.EP_NAME.extensions) {
        syncPlugin.updateProjectSettingsForQuerySync(project, context, projectViewSet)
      }
      for (moduleSpec in spec.getModulesList()) {
        val module =
          models.findIdeModule(moduleSpec.getName())!!

        val roots = models.getModifiableRootModel(module)
        // TODO: should this be encapsulated in ProjectProto.Module?
        roots.inheritSdk()

        // TODO instead of removing all content entries and re-adding, we should calculate the
        //  diff.
        for (entry in roots.getContentEntries()) {
          roots.removeContentEntry(entry)
        }
        for (ceSpec in moduleSpec.getContentEntriesList()) {
          val projectPath = ProjectPath.create(ceSpec.getRoot())

          val contentEntry =
            roots.addContentEntry(
              UrlUtil.pathToUrl(projectPathResolver.resolve(projectPath).toString())
            )
          for (sfSpec in ceSpec.getSourcesList()) {
            val sourceFolderProjectPath = ProjectPath.create(sfSpec.getProjectPath())

            val properties =
              JpsJavaExtensionService.getInstance()
                .createSourceRootProperties(
                  sfSpec.getPackagePrefix(), sfSpec.getIsGenerated()
                )
            val rootType =
              if (sfSpec.getIsTest()) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE
            val url =
              UrlUtil.pathToUrl(
                projectPathResolver.resolve(sourceFolderProjectPath).toString(),
                sourceFolderProjectPath.innerJarPath()
              )
            val unused =
              contentEntry.addSourceFolder<JavaSourceRootProperties?>(url, rootType, properties)
          }
          for (exclude in ceSpec.getExcludesList()) {
            contentEntry.addExcludeFolder(
              UrlUtil.pathToIdeaDirectoryUrl(workspaceRoot.absolutePathFor(exclude))
            )
          }
        }

        val workspaceLanguageSettings =
          LanguageSupport.createWorkspaceLanguageSettings(projectViewSet)

        for (syncPlugin in BlazeQuerySyncPlugin.EP_NAME.extensions) {
          // TODO update ProjectProto.Module and updateProjectStructure() to allow a more
          // suitable
          //   data type to be passed in here instead of androidResourceDirectories and
          //   androidSourcePackages
          syncPlugin.updateProjectStructureForQuerySync(
            project,
            context,
            models,
            workspaceRoot,
            module,
            ImmutableSet.copyOf<String?>(moduleSpec.getAndroidResourceDirectoriesList()),
            ImmutableSet.builder<String?>()
              .addAll(moduleSpec.getAndroidSourcePackagesList())
              .addAll(moduleSpec.getAndroidCustomPackagesList())
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