package com.google.idea.blaze.qsync.project

import com.google.idea.blaze.common.Label
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.thisLogger
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.Serializable
import java.nio.file.Path
import java.time.Instant

class ProjectProto {
  data class Project(
    val modules: List<Module>,
    val libraries: Map<Label, Library>,
    val artifactDirectories: ArtifactDirectories,
    val ccWorkspace: CcWorkspace,
    val activeLanguages: Set<QuerySyncLanguage>,
  ): ProjectProtoModel {
    class Builder(
      val modules: MutableList<Module.Builder> = mutableListOf(),
      val libraries: MutableMap<Label, Library> = mutableMapOf(),
      var artifactDirectories: ArtifactDirectories = ArtifactDirectories.getDefaultInstance(),
      var ccWorkspace: CcWorkspace = CcWorkspace.getDefaultInstance(),
      val activeLanguages: MutableSet<QuerySyncLanguage> = mutableSetOf(),
    ) {
      fun build(): Project = Project(
        modules = modules.map { it.build() },
        libraries = libraries.toMap(),
        artifactDirectories = artifactDirectories,
        ccWorkspace = ccWorkspace,
        activeLanguages = activeLanguages
      )
    }

    fun toBuilder(): Builder = Builder(
      modules = modules.map { it.toBuilder() }.toMutableList(),
      libraries = libraries.toMutableMap(),
      artifactDirectories = artifactDirectories,
      ccWorkspace = ccWorkspace,
      activeLanguages = activeLanguages.toMutableSet()
    )

    companion object {
      @JvmStatic
      fun getDefaultInstance(): Project = Project.Builder().build()
    }
  }

  data class ContentEntry(
    val root: ProjectPath,
    val sourceFolders: List<SourceFolder>,
    val excludes: List<ProjectPath>,
  ): ProjectProtoModel

  data class SourceFolder(val projectPath: ProjectPath, val isGenerated: Boolean, val isTest: Boolean, val packagePrefix: String): ProjectProtoModel

  data class Module(
    val name: String,
    val isAndroidModule: Boolean,
    val contentEntries: Map<ProjectPath, ContentEntry>,
    val androidResourceDirectories: List<ProjectPath>,
    val androidSourcePackages: List<String>,
    val androidCustomPackages: List<String>,
    val androidExternalLibraries: List<ExternalAndroidLibrary>,
    val kotlinCompilerFlags: List<String>,
  ): ProjectProtoModel {
    class Builder(
      var name: String,
      var isAndroidModule: Boolean = false,
      val contentEntries: MutableMap<ProjectPath, ContentEntry> = mutableMapOf(),
      val androidResourceDirectories: MutableList<ProjectPath> = mutableListOf(),
      val androidSourcePackages: MutableList<String> = mutableListOf(),
      val androidCustomPackages: MutableList<String> = mutableListOf(),
      val androidExternalLibraries: MutableList<ExternalAndroidLibrary> = mutableListOf(),
      val kotlinCompilerFlags: MutableList<String> = mutableListOf(),
    ) {
      fun build(): Module = Module(
        name = name,
        isAndroidModule = isAndroidModule,
        contentEntries = contentEntries.toMap(),
        androidResourceDirectories = androidResourceDirectories.toList(),
        androidSourcePackages = androidSourcePackages.toList(),
        androidCustomPackages = androidCustomPackages.toList(),
        androidExternalLibraries = androidExternalLibraries.toList(),
        kotlinCompilerFlags = kotlinCompilerFlags.toList(),
      )
    }

    fun toBuilder(): Builder = Builder(
      name = name,
      isAndroidModule = isAndroidModule,
      contentEntries = contentEntries.toMutableMap(),
      androidResourceDirectories = androidResourceDirectories.toMutableList(),
      androidSourcePackages = androidSourcePackages.toMutableList(),
      androidCustomPackages = androidCustomPackages.toMutableList(),
      androidExternalLibraries = androidExternalLibraries.toMutableList(),
      kotlinCompilerFlags = kotlinCompilerFlags.toMutableList(),
    )
  }

  data class Library(
    val name: Label,
    val classesJarList: List<ProjectPath>,
    val sourcesList: List<ProjectPath>,
  ): ProjectProtoModel

  data class ArtifactDirectories(
    val directoriesMap: Map<ProjectPath.ProjectRelativeProjectPath, ArtifactDirectoryContents>
  ): ProjectProtoModel {

    companion object {
      @JvmStatic
      fun getDefaultInstance(): ArtifactDirectories = ArtifactDirectories(emptyMap())
    }
  }

  data class ArtifactDirectoryContents(val contents: Map<String, ArtifactSource>): ProjectProtoModel {

    fun writeTo(output: OutputStream) {
      ObjectOutputStream(output).writeObject(this)
    }

    companion object {
      @Suppress("UnstableApiUsage")
      @JvmStatic
      fun readFrom(input: InputStream): ArtifactDirectoryContents {
        return runCatching { ObjectInputStream(input).readObject() as ArtifactDirectoryContents }
          .getOrLogException(thisLogger())
               ?: ArtifactDirectoryContents.getDefaultInstance()
      }

      @JvmStatic
      fun getDefaultInstance(): ArtifactDirectoryContents = ArtifactDirectoryContents(mapOf())
    }
  }

  sealed interface ArtifactSource: ProjectProtoModel {
    val fromBuild: Instant
  }
  data class ProjectArtifact(
    val target: Label,
    val buildArtifact: BuildArtifact,
    override val fromBuild: Instant,
    val transform: ArtifactTransform,
  ): ArtifactSource {

    enum class ArtifactTransform {
      COPY,
      UNZIP
    }
  }

  /**
   * Represents a Bazel external repository with a path relative to (bazel info output_base).
   */
  data class ExternalRepository(val name: String, val bazelRepositoryAbsolutePath: Path, override val fromBuild: Instant): ArtifactSource

  data class ExternalAndroidLibrary(
    val name: String,
    val location: ProjectPath,
    val manifestFile: ProjectPath,
    val resFolder: ProjectPath,
    val symbolFile: ProjectPath,
    val packageName: String,
  ): ProjectProtoModel

  data class BuildArtifact(val digest: String): ProjectProtoModel

  data class CcWorkspace(
    val targets: Map<Label, CcTarget>,
    val flagSets: Map<String, CcCompilerFlagSet>,
  ): ProjectProtoModel {
    val isEmpty: Boolean get() = targets.values.all { it.isEssentiallyEmpty() } && flagSets.isEmpty()

    init {
      targets.forEach { target ->
        target.value.contexts.values.forEach { context ->
          context.languageToCompilerSettings.values.forEach { settings ->
            val flagSetId = settings.flagSetId
            if (flagSetId.isNotEmpty()) {
              if (flagSets[flagSetId] == null) {
                error("Invalid CcWorkspace(Target: ${target.value.target}): Flagset $flagSetId not found")
              }
            }
          }
        }
      }
    }

    companion object {
      @JvmStatic
      fun getDefaultInstance(): CcWorkspace = CcWorkspace(mapOf(), mapOf())
    }
  }

  data class CcTarget(
    val target: Label,
    val sources: Map<ProjectPath.SourceCodeRepositoryRelativeProjectPath, CcSourceFile>,
    val contexts: Map<String, CcCompilationContext>,
  ): ProjectProtoModel {
    /**
     * Whether there is anything analyzable in this target.
     */
    fun isEssentiallyEmpty(): Boolean = contexts.isEmpty()
  }

  data class CcCompilationContext(
    val id: String,
    val humanReadableName: String,
    val languageToCompilerSettings: Map<CcLanguage, CcCompilerSettings>,
  ): ProjectProtoModel

  sealed interface CcCompilerFlag: ProjectProtoModel {
    val flag: String
  }

  data class CcCompilerStringFlag(override val flag: String, val value: String) : CcCompilerFlag

  data class CcCompilerPathFlag(override val flag: String, val path: ProjectPath) : CcCompilerFlag

  data class CcCompilerFlagSet(val flags: List<CcCompilerFlag>): ProjectProtoModel

  data class CcCompilerSettings(
    val compilerExecutablePath: ProjectPath,
    val flagSetId: String,
  ): ProjectProtoModel

  data class CcSourceFile(
    val workspacePath: ProjectPath.SourceCodeRepositoryRelativeProjectPath,
    val language: CcLanguage,
  ): ProjectProtoModel

  enum class CcLanguage { C, CPP, OBJ_C, OBJ_CPP }
}

interface ProjectProtoModel: Serializable, FormattableModel
