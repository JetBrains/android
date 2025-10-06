package com.google.idea.blaze.qsync.project

import com.google.idea.blaze.common.Label
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.thisLogger
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.Serializable
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
    val androidResourceDirectories: List<ProjectPath.WorkspaceRelativeProjectPath>,
    val androidSourcePackages: List<String>,
    val androidCustomPackages: List<String>,
    val androidExternalLibraries: List<ExternalAndroidLibrary>,
  ): ProjectProtoModel {
    class Builder(
      var name: String,
      var isAndroidModule: Boolean = false,
      val contentEntries: MutableMap<ProjectPath, ContentEntry> = mutableMapOf(),
      val androidResourceDirectories: MutableList<ProjectPath.WorkspaceRelativeProjectPath> = mutableListOf(),
      val androidSourcePackages: MutableList<String> = mutableListOf(),
      val androidCustomPackages: MutableList<String> = mutableListOf(),
      val androidExternalLibraries: MutableList<ExternalAndroidLibrary> = mutableListOf(),
    ) {
      fun build(): Module = Module(
        name = name,
        isAndroidModule = isAndroidModule,
        contentEntries = contentEntries.toMap(),
        androidResourceDirectories = androidResourceDirectories.toList(),
        androidSourcePackages = androidSourcePackages.toList(),
        androidCustomPackages = androidCustomPackages.toList(),
        androidExternalLibraries = androidExternalLibraries.toList(),
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

  data class ArtifactDirectoryContents(val contents: Map<String, ProjectArtifact>): ProjectProtoModel {

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

  data class ProjectArtifact(
    val target: Label,
    val buildArtifact: BuildArtifact,
    val fromBuild: Instant,
    val transform: ArtifactTransform,
  ): ProjectProtoModel {

    enum class ArtifactTransform {
      COPY,
      UNZIP
    }
  }

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
    val contexts: List<CcCompilationContext>,
    val flagSets: Map<String, CcCompilerFlagSet>,
  ): ProjectProtoModel {
    class Builder(
      val contexts: MutableList<CcCompilationContext> = mutableListOf(),
      val flagSets: MutableMap<String, CcCompilerFlagSet> = mutableMapOf(),
    ) {
      fun putFlagSets(id: String, flagSet: CcCompilerFlagSet) {
        flagSets[id] = flagSet
      }

      fun build(): CcWorkspace = CcWorkspace(contexts.toList(), flagSets.toMap())
    }

    val isEmpty: Boolean get() = contexts.isEmpty() && flagSets.isEmpty()
    fun toBuilder(): Builder = Builder(contexts.toMutableList(), flagSets.toMutableMap())

    companion object {
      @JvmStatic
      fun getDefaultInstance(): CcWorkspace = CcWorkspace(listOf(), mapOf())
    }
  }

  data class CcCompilationContext(
    val id: String,
    val humanReadableName: String,
    val sources: List<CcSourceFile>,
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
    val workspacePath: ProjectPath.WorkspaceRelativeProjectPath,
    val language: CcLanguage,
    val compilerSettings: CcCompilerSettings,
  ): ProjectProtoModel

  enum class CcLanguage { C, CPP, OBJ_C, OBJ_CPP }
}

interface ProjectProtoModel: Serializable, FormattableModel
