package com.google.idea.blaze.base.qsync

import com.android.AndroidProjectTypes
import com.google.idea.blaze.base.qsync.ProjectUpdater.IdeaUrl.Companion.findFileByUrl
import com.google.idea.blaze.base.qsync.ProjectUpdater.IdeaUrl.Companion.getOrCreateFromUrl
import com.google.idea.blaze.base.qsync.entity.BazelEntitySource
import com.google.idea.blaze.base.sync.projectview.LanguageSupport
import com.google.idea.blaze.base.util.UrlUtil
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot
import com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.WORKSPACE_MODULE_NAME
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.common.experiments.BoolExperiment
import com.google.idea.common.experiments.EnumExperiment
import com.google.idea.common.experiments.IntExperiment
import com.google.idea.common.util.Transactions
import com.intellij.facet.impl.FacetUtil
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.FacetEntityTypeId
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetType
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinModuleKind
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider.Companion.isK2Mode
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins
import org.jetbrains.kotlin.idea.serialization.KotlinFacetSettingsWorkspaceModel
import org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity

/** An object that monitors the build graph and applies the changes to the project structure by using WorkspaceEntity. */
class ProjectUpdater(private val project: Project) : QuerySyncProjectListener {

  enum class ProjectStructure {
    SHARDED_LIBRARY,
    LIBRARY_PER_TARGET
  }

  companion object {
    val projectStructureExperiment = EnumExperiment("query.sync.project.structure", ProjectStructure.SHARDED_LIBRARY)
    val libraryShardsExperiment = IntExperiment("query.sync.library.shards", 10)
    val coexistWithJpsSourceEntitiesExperiment = BoolExperiment("query.sync.coexist.with.jps.source", true)
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

  @JvmInline
  value class LibraryName private constructor (private val name: String) {
    fun asString(): String = name

    companion object {
      fun from(name: String): LibraryName = LibraryName(name)
    }
  }

  data class ModuleData(
    val name: String,
    val dependencies: List<LibraryName>,
    val contentRoots: List<ContentRootData>,
    val isAndroidModule: Boolean,
  ) {
    companion object
  }

  @JvmInline
  value class IdeaUrl private constructor (private val url: String) {
    companion object {
      fun fromPath(path: Path, innerJarPath: Path): IdeaUrl {
        return IdeaUrl(UrlUtil.pathToUrl(path.toString(), innerJarPath))
      }

      fun fromJarPath(path: Path, innerJarPath: Path): IdeaUrl {
        return IdeaUrl(UrlUtil.pathToUrl(path.toString(), innerJarPath))
      }

      fun VirtualFileManager.findFileByUrl(url: IdeaUrl): VirtualFile? {
        return this.findFileByUrl(url.url)
      }

      fun VirtualFileUrlManager.getOrCreateFromUrl(url: IdeaUrl): VirtualFileUrl {
        return this.getOrCreateFromUrl(url.url)
      }
    }
  }

  data class LibraryData(
    val name: LibraryName,
    val jarUrls: List<IdeaUrl>,
    val sourceUrls: List<IdeaUrl>,
  ) {
    companion object
  }

  data class ContentRootData(
    val url: IdeaUrl,
    val sourceRoots: List<SourceRootData>,
    val excludedRoots: List<IdeaUrl>,
  ) {
    companion object
  }

  data class SourceRootData(
    val url: IdeaUrl,
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

    fun ProjectPath.toIdeaUrl(): IdeaUrl {
      return IdeaUrl.fromPath(projectPathResolver.resolve(this), innerPath)
        .also { virtualFileManager.findFileByUrl(it) }  // Register roots in a background thread.
    }

    fun ModuleData.Companion.from(
      module: ProjectProto.Module,
      dependencies: List<LibraryName>,
      contentRoots: List<ContentRootData>,
    ): ModuleData {
      return ModuleData(name = module.name, dependencies = dependencies, contentRoots = contentRoots, isAndroidModule = module.isAndroidModule)
    }

    fun LibraryData.Companion.from(library: ProjectProto.Library): LibraryData {
      return LibraryData(
        name = LibraryName.from(library.name.toString()),
        jarUrls = library.classesJarList.map { it.toIdeaUrl() },
        sourceUrls = library.sourcesList.map { it.toIdeaUrl() },
      )
    }

    fun LibraryData.Companion.from(name: String, libraries: List<ProjectProto.Library>): LibraryData {
      return LibraryData(
        name = LibraryName.from(name),
        jarUrls = libraries.flatMap { it.classesJarList }.map { it.toIdeaUrl() },
        sourceUrls = libraries.flatMap { it.sourcesList }.map { it.toIdeaUrl() },
      )
    }

    fun SourceRootData.Companion.from(
      projectPath: ProjectPath,
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
      root: ProjectPath,
      sources: List<ProjectProto.SourceFolder>,
      excludedRoots: List<ProjectPath>,
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
        excludedRoots = excludedRoots.map { it.toIdeaUrl() },
      )
    }

    fun ProjectData.Companion.from(project: ProjectProto.Project): ProjectData {
      val libraries = when (projectStructureExperiment.value) {
        ProjectStructure.LIBRARY_PER_TARGET ->
          project.libraries.values.map { LibraryData.from(it) }

        ProjectStructure.SHARDED_LIBRARY -> let {
          val shards = libraryShardsExperiment.value.toULong()
          project.libraries.values
            .groupBy { it.name.hashCode().toULong() % shards }
            .map {
              LibraryData.from("Lib ${it.key}", it.value)
            }
        }
      }
      return ProjectData(
        modules = project.modules.map {
          ModuleData.from(it, libraries.map { it.name }, it.contentEntries.values.map {
            ContentRootData.from(it.root, sources = it.sourceFolders, excludedRoots = it.excludes)
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
    projectData: ProjectData
  ) {
    val virtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    val replaceJpsSourceEntities =
      !coexistWithJpsSourceEntitiesExperiment.value ||
      storage.entitiesBySource {it is BazelEntitySource}.none() // Conversion of an older project.
    storage.replaceBySource(
      { it is BazelEntitySource || replaceJpsSourceEntities && it is JpsProjectFileEntitySource },
      MutableEntityStorage.create().apply {
        val libraries = projectData.libraries.associate {
          it.name to addEntity(LibraryEntity(
            name = it.name.asString(),
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
        }

        for (moduleData in projectData.modules) {
          val dependencies = listOf(ModuleSourceDependency, InheritedSdkDependency) +
                             moduleData.dependencies.map {
                               LibraryDependency(libraries[it]?.symbolicId ?: error("Unresolved library dependency: $it"),
                                                 exported = false,
                                                 scope = DependencyScope.COMPILE)
                             }
          val moduleEntity = addEntity(
            ModuleEntity(name = WORKSPACE_MODULE_NAME, dependencies = dependencies, entitySource = BazelEntitySource) {
              this.contentRoots = moduleData.contentRoots.map {
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
              if (moduleData.isAndroidModule) {
                addEntity(FacetEntity(
                  moduleId = ModuleId(this@ModuleEntity.name),
                  name = AndroidFacet.NAME,
                  typeId = FacetEntityTypeId(AndroidFacetType.TYPE_ID),
                  entitySource = BazelEntitySource
                ) {
                  module = this@ModuleEntity // Needed despite moduleId is required above.
                  val facetConfiguration = AndroidFacet.getFacetType().createDefaultConfiguration().apply {
                    state.apply {
                      ALLOW_USER_CONFIGURATION = false
                      PROJECT_TYPE = AndroidProjectTypes.PROJECT_TYPE_LIBRARY
                      MANIFEST_FILE_RELATIVE_PATH = ""
                      RES_FOLDER_RELATIVE_PATH = ""
                      ASSETS_FOLDER_RELATIVE_PATH = ""
                    }
                  }
                  configurationXmlTag = JDOMUtil.write(FacetUtil.saveFacetConfiguration(facetConfiguration) ?: error("Failed to save facet configuration"))
                })
              }
              let { // Setup Kotlin.
                addEntity(
                  KotlinSettingsEntity(
                    moduleId = ModuleId(this@ModuleEntity.name),
                    name = KotlinFacetType.NAME,
                    sourceRoots = emptyList(),
                    configFileItems = emptyList(),
                    useProjectSettings = false,
                    implementedModuleNames = emptyList(),
                    dependsOnModuleNames = emptyList(),
                    additionalVisibleModuleNames = emptySet(),
                    sourceSetNames = emptyList(),
                    isTestModule = false,
                    externalProjectId = "",
                    isHmppEnabled = false,
                    pureKotlinSourceFolders = emptyList(),
                    kind = KotlinModuleKind.COMPILATION_AND_SOURCE_SET_HOLDER,
                    externalSystemRunTasks = emptyList(),
                    version = KotlinFacetSettings.CURRENT_VERSION,
                    flushNeeded = false,
                    entitySource = BazelEntitySource
                    ) {
                    module = this@ModuleEntity
                    updatePluginOptions(KotlinFacetSettingsWorkspaceModel(this), listOf())
                  }
                )
              }
            }
          )
        }
      }
    )
  }

  private fun updateProjectModel(querySyncProject: ReadonlyQuerySyncProject, spec: ProjectProto.Project, context: Context<*>) {
    Transactions.submitWriteActionTransactionAndWait {
      for (syncPlugin in BlazeQuerySyncPlugin.EP_NAME.extensions) {
        syncPlugin.updateProjectSettingsForQuerySync(project, context, querySyncProject.projectViewSet)
      }
      for (moduleSpec in spec.modules) {
        val module = ModuleManager.getInstance(project).findModuleByName(moduleSpec.name)!!
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
            querySyncProject.workspaceRoot,
            module,
            moduleSpec.androidResourceDirectories.map { it.relativePath.toString() }.toSet(),
            moduleSpec.androidSourcePackages.toSet() + moduleSpec.androidCustomPackages.toSet(),
            workspaceLanguageSettings
          )
        }
        ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(
          EmptyRunnable.getInstance(), RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED
        )
      }
    }
  }

  /** Entry point for instantiating [ProjectUpdater].  */
  class Provider : QuerySyncProjectListenerProvider {
    override fun createListener(querySyncManager: QuerySyncManager): QuerySyncProjectListener {
      return ProjectUpdater(querySyncManager.ideProject)
    }
  }
}
private val qsyncDisableCompose = BoolExperiment("qsync.disable.compose", false)

private fun updatePluginOptions(
  facetSettings: IKotlinFacetSettings,
  newPluginOptions: List<String>
) {
  var commonArguments = facetSettings.compilerArguments
  if (commonArguments == null) {
    commonArguments = K2JVMCompilerArguments()
  }

  if (isK2Mode() && !qsyncDisableCompose.value) {
    // Register the bundled directly, as KtCompilerPluginsProviderIdeImpl consistently replaces
    // user's plugin class path with it.
    // Note: This implementation may need updating if the Kotlin plugin alters its provider
    // replacement logic.
    commonArguments.pluginClasspaths = arrayOf(
      KotlinK2BundledCompilerPlugins.COMPOSE_COMPILER_PLUGIN.bundledJarLocation
        .toString(),
    )
  }
  commonArguments.pluginOptions = newPluginOptions.toTypedArray<String>()
  facetSettings.compilerArguments = commonArguments
}

