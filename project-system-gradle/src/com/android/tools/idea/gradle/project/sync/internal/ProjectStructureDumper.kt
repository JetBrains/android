/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.internal

import com.android.tools.idea.gradle.project.ProjectStructure
import com.android.tools.idea.gradle.project.build.invoker.GradleTaskFinder
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacetConfiguration
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacetConfiguration
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.profiler.CpuProfilerConfig
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.android.tools.idea.util.CommonAndroidUtil.LINKED_ANDROID_MODULE_GROUP
import com.android.tools.idea.util.LinkedAndroidModuleGroup
import com.android.tools.idea.util.androidFacet
import com.android.utils.FileUtils
import com.intellij.execution.RunManagerEx
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.service.project.manage.SourceFolderManager
import com.intellij.openapi.externalSystem.service.project.manage.SourceFolderManagerImpl
import com.intellij.openapi.externalSystem.service.project.manage.SourceFolderModelState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.DependencyScope.COMPILE
import com.intellij.openapi.roots.ExcludeFolder
import com.intellij.openapi.roots.ExportableOrderEntry
import com.intellij.openapi.roots.InheritedJdkOrderEntry
import com.intellij.openapi.roots.JavadocOrderRootType
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.roots.TestModuleProperties
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.text.nullize
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetConfiguration
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.idea.facet.KotlinFacetConfiguration
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.pathString

fun ProjectDumper.dumpProject(project: Project) {
  println("<PROJECT>     <== ${File(project.basePath!!)}")
  head("PROJECT") { project.name }
  nest(File(project.basePath!!), "PROJECT") {
    head("PROJECT_JDK") { ProjectRootManager.getInstance(project).projectSdk?.name?.replaceJdkName() }
    nest {
      prop("Version") { ProjectRootManager.getInstance(project).projectSdk?.versionString?.replaceJdkVersion() }
    }
    ModuleManager.getInstance(project).modules.sortedBy { it.name }.forEach { dump(it) }
    RunManagerEx.getInstanceEx(project).allConfigurationsList.sortedBy { it.name }.forEach { dump(it) }
    val libraries = LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries
    if (libraries.isNotEmpty()) {
      head("LIBRARY_TABLE")
      nest {
        libraries.sortedBy { it.name }.forEach { dump(it) }
      }
    }
    @Suppress("UnstableApiUsage")
    dumpTasks { buildMode ->
      fun List<Module>.sortedModules(): Array<Module> = sortedBy { it.moduleFilePath.replaceKnownPaths() }.toTypedArray()

      val allModules = ModuleManager.getInstance(project).modules.toList().sortedModules()
      val appModules = ProjectStructure.getInstance(project).appHolderModules.sortedModules()
      val leafModules = ProjectStructure.getInstance(project).leafHolderModules.sortedModules()

      when (buildMode) {
        BuildMode.REBUILD -> allModules
        BuildMode.COMPILE_JAVA -> allModules
        BuildMode.ASSEMBLE -> leafModules
        BuildMode.CLEAN -> allModules
        BuildMode.APK_FROM_BUNDLE -> appModules
        BuildMode.BUNDLE -> appModules
        BuildMode.SOURCE_GEN -> allModules
      }
    }
  }
}

fun ProjectDumper.dump(module: Module) {
  val moduleFile = module.moduleFilePath.toPrintablePath()
  head("MODULE") { module.name }
  nest {
    val groups = ModuleManager.getInstance(module.project).getModuleGroupPath(module)
    groups?.forEach { group ->
      prop("- ModuleGroupPath") { group }
    }
    module.getUserData(LINKED_ANDROID_MODULE_GROUP)?.let { dump(it) }
    val externalPropertyManager = ExternalSystemModulePropertyManager.getInstance(module)
    prop("ExternalModuleGroup") { externalPropertyManager.getExternalModuleGroup() }
    prop("ExternalModuleType") { externalPropertyManager.getExternalModuleType() }
    prop("ExternalModuleVersion") { externalPropertyManager.getExternalModuleVersion() }
    prop("ExternalSystemId") { externalPropertyManager.getExternalSystemId()?.takeUnless { it == "GRADLE" } }
    prop("LinkedProjectId") { externalPropertyManager.getLinkedProjectId() }
    prop("LinkedProjectPath") { externalPropertyManager.getLinkedProjectPath()?.toSystemIndependent()?.toPrintablePath() }
    prop("RootProjectPath") { externalPropertyManager.getRootProjectPath()?.toPrintablePath() }
    prop("IsMavenized") { externalPropertyManager.isMavenized().takeIf { it }?.toString() }
    val compilerModuleExtension = CompilerModuleExtension.getInstance(module)
    if (compilerModuleExtension != null) {
      dump(compilerModuleExtension)
    }
    dump(TestModuleProperties.getInstance(module))

    prop("ModuleFile") { moduleFile }
    prop("ModuleTypeName") { module.moduleTypeName }
    FacetManager.getInstance(module).allFacets.sortedBy { it.name }.forEach { dump(it) }
    val moduleRootManager = ModuleRootManager.getInstance(module)
    prop("ExternalSource.DisplayName") { moduleRootManager.externalSource?.displayName?.takeUnless { it == "Gradle" } }
    prop("ExternalSource.Id") { moduleRootManager.externalSource?.id?.takeUnless { it == "GRADLE" } }
    val moduleRootModel = moduleRootManager as ModuleRootModel
    moduleRootModel.contentEntries.sortedBy { it.url.toPrintablePath() }.forEach { dump(it) }
    val sourceFolderManager = SourceFolderManager.getInstance(module.project) as SourceFolderManagerImpl
    val sourceFolders = sourceFolderManager.state?.sourceFolders?.filter { it.moduleName == module.name }.orEmpty()
    sourceFolders.sortedBy { it.url.toPrintablePath() }.forEach {
      dump(it)
    }

    // TODO(b/124658218): Remove sorting if the order can be made stable.
    moduleRootModel.orderEntries.sortedWith(
      compareBy({ it.presentableName.removeAndroidVersionsFromDependencyNames().replaceKnownPaths() },
                { (it as? LibraryOrderEntry)?.scope})).forEach {
      dump(it)
    }
    val classes = moduleRootModel.orderEntries().withoutDepModules().withoutLibraries().withoutSdk().classes().urls
    if (classes.isNotEmpty()) {
      head("Classes")
      nest {
        classes.forEach {
          prop("-") { it.replaceKnownPaths() }
        }
      }
    }

    dumpTasks{ arrayOf(module) }
  }
}

fun ProjectDumper.dumpTasks(modulesProvider: (buildMode: BuildMode) -> Array<Module>) {
  val taskFinder = GradleTaskFinder.getInstance()
  head("BUILD_TASKS")
  nest {
    TestCompileType.values().forEach { testCompileMode ->
      head("TEST_COMPILE_MODE") { testCompileMode.displayName }
      nest {
        BuildMode.values().forEach { buildMode ->
          val modules = modulesProvider(buildMode).takeUnless { it.isEmpty()  } ?: return@forEach
          val expectedRoot = modules.mapNotNull {it.getGradleProjectPath()?.buildRoot?.let(::File)?.toPath()}.singleOrNull()

          fun Map<Path, MutableCollection<String>>.asFirstEntry(): Set<String> {
            if (expectedRoot != null && keys.size > 1) {
              prop("ERROR") {
                "Multiple project roots for a single module: " +
                  keys.sortedBy { it.pathString.replaceKnownPaths() }.joinToString(",") { it.absolutePathString().replaceKnownPaths() }
              }
            }
            return entries
              .sortedBy { it.key.pathString.replaceKnownPaths() }
              .flatMap { (path, tasks) ->
                if (path == expectedRoot) tasks
                else tasks.map { "${path.pathString.replaceKnownPaths()}:$it" }
              }
              .toSet()
          }

          fun getTasks(): Set<String> = taskFinder.findTasksToExecute(modules, buildMode, testCompileMode).asMap().asFirstEntry()

          fun Set<String>.dumpAs(name: String) {
            prop(name) { this.takeUnless { it.isEmpty() }?.sorted()?.joinToString(", ") }
          }

          getTasks().dumpAs(buildMode.toString())
        }
      }
    }
  }
}

private fun ProjectDumper.dump(runConfiguration: RunConfiguration) {
  head("RUN_CONFIGURATION") { runConfiguration.name }
  nest {
    prop("*class*") { runConfiguration.javaClass.simpleName }
    when (runConfiguration) {
      is AndroidRunConfigurationBase -> dump(runConfiguration)
      else -> prop("**UNSUPPORTED**") { "*" }
    }
  }
}

private fun ProjectDumper.dump(runConfiguration: AndroidRunConfigurationBase) {
  prop("ModuleName") { runConfiguration.configurationModule.moduleName }
  prop("Module") { runConfiguration.configurationModule.module?.name }
  prop("ClearLogcat") { runConfiguration.CLEAR_LOGCAT.takeUnless { it == false }?.toString() }
  prop("ShowLogcatAutomatically") { runConfiguration.SHOW_LOGCAT_AUTOMATICALLY.takeUnless { it == false }?.toString() }
  prop("EnableInspectionWithoutActivityRestart") { runConfiguration.INSPECTION_WITHOUT_ACTIVITY_RESTART.takeUnless { !it }?.toString() }
  if (runConfiguration is AndroidTestRunConfiguration) {
    prop("TestingType") { runConfiguration.TESTING_TYPE.takeUnless { it == AndroidTestRunConfiguration.TEST_ALL_IN_MODULE }?.toString() }
    prop("MethodName") { runConfiguration.METHOD_NAME }
    prop("ClassName") { runConfiguration.CLASS_NAME }
    prop("PackageName") { runConfiguration.PACKAGE_NAME }
    prop("InstrumentationRunnerClass") { runConfiguration.INSTRUMENTATION_RUNNER_CLASS }
    prop("ExtraOptions") { runConfiguration.EXTRA_OPTIONS }
    prop("TargetSelectionMode") { runConfiguration.deployTargetContext.TARGET_SELECTION_MODE }
    prop("DebuggerType") { runConfiguration.androidDebuggerContext.DEBUGGER_TYPE }
  }
  prop("AdvancedProfilingEnabled") { runConfiguration.profilerState.ADVANCED_PROFILING_ENABLED.takeUnless { it == false }?.toString() }
  prop(
    "StartupCpuProfilingEnabled") { runConfiguration.profilerState.STARTUP_CPU_PROFILING_ENABLED.takeUnless { it == false }?.toString() }
  prop(
    "StartupCpuProfilingConfigurationName") {
    runConfiguration.profilerState.STARTUP_CPU_PROFILING_CONFIGURATION_NAME.takeUnless { it == CpuProfilerConfig.Technology.SAMPLED_JAVA.getName() }
  }
}

private fun ProjectDumper.dump(orderEntry: OrderEntry) {
  when (orderEntry) {
    is JdkOrderEntry -> dumpJdk(orderEntry)
    is LibraryOrderEntry -> dumpLibrary(orderEntry)
    else -> {
      head("ORDER_ENTRY") { orderEntry.presentableName.removeAndroidVersionsFromDependencyNames() }
      val exportable = orderEntry as? ExportableOrderEntry
      if (exportable != null) {
        nest {
          prop("Scope") { exportable.scope.takeIf { it != COMPILE }?.toString() }
          prop("IsExported") { exportable.isExported.takeIf { it }?.toString() }
        }
      }
    }
  }
}

private fun ProjectDumper.dumpJdk(jdkOrderEntry: JdkOrderEntry) {
  jdkOrderEntry.jdk?.let { jdk ->
    head("JDK") { "<NAME_CUT> ${jdk.sdkType.name}" }
    prop("*isInherited") { (jdkOrderEntry is InheritedJdkOrderEntry).toString() }
    if (jdkOrderEntry !is InheritedJdkOrderEntry) {
      nest {
        prop("SdkType") { jdk.sdkType.name }
        prop("HomePath") { jdk.homePath?.toPrintablePath() }
        prop("VersionString") { jdk.versionString?.replaceJdkVersion() }
      }
    }
  }
}

private fun ProjectDumper.dumpLibrary(library: LibraryOrderEntry) {
  head("LIBRARY") { library.libraryName?.markMatching(library?.library?.name.orEmpty())?.removeAndroidVersionsFromDependencyNames()?.replaceKnownPaths() }
  nest {
    prop("LibraryLevel") { library.libraryLevel.takeUnless { it == "project" } }
    prop("IsModuleLevel") { library.isModuleLevel.takeIf { it }?.toString() }
    prop("Scope") { library.scope.takeIf {it != COMPILE }?.toString () }
    prop("IsExported") { library.isExported.takeIf{ it}?.toString() }
    if (library.libraryLevel != "project" ) {
      library.library?.let { dump(it) }
    }
  }
}

private fun ProjectDumper.dump(library: Library) {
  val androidVersion = library.name?.getAndroidVersionFromDependencyName()
  head("LIBRARY") { library.name?.removeAndroidVersionsFromDependencyNames()?.replaceKnownPaths() }
  nest {
    val orderRootTypes = OrderRootType.getAllPersistentTypes().toList() + OrderRootType.DOCUMENTATION
    orderRootTypes.forEach { type ->
      library
        .getUrls(type)
        .filterNot { file ->
          // Do not allow sources and java docs coming from cache sources as their content may change.
          (file.toPrintablePath().contains("<M2>") || file.toPrintablePath().contains("<GRADLE>")) &&
          (type == OrderRootType.DOCUMENTATION ||
           type == OrderRootType.SOURCES ||
           type == JavadocOrderRootType.getInstance())
        }
        .filter { file ->
          !file.toPrintablePath().contains("<USER_M2>") || type != AnnotationOrderRootType.getInstance()
        }
        .map { file ->
          file.toPrintablePath().replaceMatchingVersion(androidVersion).also {
            if (type == OrderRootType.SOURCES || type == OrderRootType.DOCUMENTATION || type == JavadocOrderRootType.getInstance()) {
              println("$file -> $it")
            }
          }
        }
        .sorted()
        .forEach { printerPath ->
          // TODO(b/124659827): Include source and JavaDocs artifacts when available.
          prop("*" + type.name()) { printerPath }
        }
    }
  }
}

private fun ProjectDumper.dump(contentEntry: ContentEntry) {
  head("CONENT_ENTRY") { contentEntry.url.toPrintablePath() }
  nest {
    contentEntry.sourceFolders.sortedBy { it.url.toPrintablePath() }.forEach { dump(it) }
    contentEntry.excludeFolders.sortedBy { it.url.toPrintablePath() }.forEach { dump(it) }
  }
}

private fun ProjectDumper.dump(model: SourceFolderModelState) {
  head("WATCHED_${model.type}_FOLDER") { model.url.toPrintablePath() }
  nest {
    prop("PackagePrefix") { model.packagePrefix }
    prop("Generated") { model.generated.takeIf { it == true }?.toString() }
  }
}

private fun ProjectDumper.dump(excludeFolder: ExcludeFolder) {
  head("EXCLUDE_FOLDER") { excludeFolder.url.toPrintablePath() }
}

private fun ProjectDumper.dump(sourceFolder: SourceFolder) {
  prop(
    sourceFolder.rootType.javaClass.simpleName.removeSuffix("RootType") +
    if (sourceFolder.isTestSource) " (test)" else ""
  ) { sourceFolder.url.toPrintablePath() }
  nest {
    prop("PackagePrefix") { sourceFolder.packagePrefix.nullize() }
  }
}

private fun ProjectDumper.dump(facet: Facet<*>) {
  head("FACET") { facet.name }
  nest {
    prop("TypeId") { facet.typeId.toString() }
    prop("ExternalSource") { facet.externalSource?.id }
    val configuration = facet.configuration
    when (configuration) {
      is GradleFacetConfiguration -> dump(configuration)
      is AndroidFacetConfiguration -> dump(configuration)
      is NdkFacetConfiguration -> dump(configuration)
      is KotlinFacetConfiguration -> dump(configuration)
      else -> prop("Configuration") { configuration.toString() }
    }
  }
}

private fun ProjectDumper.dump(gradleFacetConfiguration: GradleFacetConfiguration) {
  prop("GradlePath") { gradleFacetConfiguration.GRADLE_PROJECT_PATH }
}

private fun ProjectDumper.dump(androidFacetConfiguration: AndroidFacetConfiguration) {
  with(androidFacetConfiguration.state ?: return) {
    prop("SelectedBuildVariant") { SELECTED_BUILD_VARIANT.nullize() }
    prop("AllowUserConfiguration") {
      @Suppress("DEPRECATION")
      ALLOW_USER_CONFIGURATION.toString()
    }
    prop("GenFolderRelativePathApt") { GEN_FOLDER_RELATIVE_PATH_APT.nullize() }
    prop("GenFolderRelativePathAidl") { GEN_FOLDER_RELATIVE_PATH_AIDL.nullize() }
    prop("ManifestFileRelativePath") { MANIFEST_FILE_RELATIVE_PATH.nullize() }
    prop("ResFolderRelativePath") { RES_FOLDER_RELATIVE_PATH.nullize() }
    RES_FOLDERS_RELATIVE_PATH?.toPrintablePaths()?.forEach { prop("- ResFoldersRelativePath") { it } }
    TEST_RES_FOLDERS_RELATIVE_PATH?.toPrintablePaths()?.forEach { prop("- TestResFoldersRelativePath") { it } }
    prop("AssetsFolderRelativePath") { ASSETS_FOLDER_RELATIVE_PATH.nullize() }
    prop("LibsFolderRelativePath") { LIBS_FOLDER_RELATIVE_PATH.nullize() }
    prop("ApkPath") { APK_PATH.nullize() }
    prop("ProjectType") { PROJECT_TYPE.toString() }
    prop("CustomDebugKeystorePath") { CUSTOM_DEBUG_KEYSTORE_PATH.nullize() }
    prop("PackTestCode") { PACK_TEST_CODE.toString() }
    prop("RunProguard") { RUN_PROGUARD.toString() }
    prop("ProguardLogsFolderRelativePath") { PROGUARD_LOGS_FOLDER_RELATIVE_PATH.nullize() }
    prop("UseCustomManifestPackage") { USE_CUSTOM_MANIFEST_PACKAGE.toString() }
    prop("CustomManifestPackage") { CUSTOM_MANIFEST_PACKAGE.nullize() }
    myProGuardCfgFiles.forEach { prop("- ProGuardCfgFiles") { it } }
  }
}

private fun ProjectDumper.dump(ndkFacetConfiguration: NdkFacetConfiguration) {
  prop("SelectedVariantAbi") { ndkFacetConfiguration.selectedVariantAbi.toString().nullize() }
}

private fun ProjectDumper.dump(kotlinFacetConfiguration: KotlinFacetConfiguration) {
  with(kotlinFacetConfiguration.settings) {
    prop("ApiLevel") { apiLevel?.toString() }
    compilerArguments?.let { compilerArguments ->
      head("CompilerArguments") { null }
      dump(compilerArguments)
    }
    compilerSettings?.let { compilerSettings ->
      head("CompilerSettings") { null }
      dump(compilerSettings)
    }
    prop("ExternalProjectId") { externalProjectId.nullize() }
    implementedModuleNames.forEach { prop("- ImplementedModuleName") { it } }
    prop("IsTestModule") { isTestModule.toString() }
    prop("Kind") { kind.toString() }
    prop("LanguageLevel") { languageLevel?.toString() }

    // The Kotlin plugin invokes this workaround in several places including where it is read by JPS build.
    // It doesn't look like we need to do it when opening a project, but we need to refresh them before
    // dumping them since they are not automatically restored when the project is re-opened.
    updateMergedArguments()
    mergedCompilerArguments?.let { mergedCompilerArguments ->
      head("MergedCompilerArguments") { null }
      dump(mergedCompilerArguments)
    }
    prop("Platform") { targetPlatform?.toString() }
    prop("ProductionOutputPath") { productionOutputPath?.replaceKnownPaths() }
    sourceSetNames.forEach { prop("- SourceSetName") { it } }
    prop("TestOutputPath") { testOutputPath?.replaceKnownPaths() }
    prop("UseProjectSettings") { useProjectSettings.toString() }
    prop("Version") { version.toString() }
  }
}

private fun ProjectDumper.dump(compilerArguments: CommonCompilerArguments) {
  nest {
    prop("allowKotlinPackage") { compilerArguments.allowKotlinPackage.takeIf { it }?.toString() }
    prop("allowResultReturnType") { compilerArguments.allowResultReturnType.takeIf { it }?.toString() }
    prop("apiVersion") { compilerArguments.apiVersion }
    prop("autoAdvanceApiVersion") { compilerArguments.autoAdvanceApiVersion.takeIf { it }?.toString() }
    prop("autoAdvanceLanguageVersion") { compilerArguments.autoAdvanceLanguageVersion.takeIf { it }?.toString() }
    compilerArguments.commonSources?.forEach { prop("- commonSources") { it } }
    compilerArguments.disablePhases?.forEach { prop("- disablePhases") { it } }
    prop("dumpPerf") { compilerArguments.dumpPerf }
    prop("effectSystem") { compilerArguments.effectSystem.takeIf { it }?.toString() }
    prop("intellijPluginRoot") { compilerArguments.intellijPluginRoot }
    prop("kotlinHome") { compilerArguments.kotlinHome }
    prop("languageVersion") { compilerArguments.languageVersion }
    prop("legacySmartCastAfterTry") { compilerArguments.legacySmartCastAfterTry.takeIf { it }?.toString() }
    prop("listPhases") { compilerArguments.listPhases.takeIf { it }?.toString() }
    prop("metadataVersion") { compilerArguments.metadataVersion }
    prop("multiPlatform") { compilerArguments.multiPlatform.takeIf { it }?.toString() }
    prop("noCheckActual") { compilerArguments.noCheckActual.takeIf { it }?.toString() }
    prop("noInline") { compilerArguments.noInline.takeIf { it }?.toString() }
    compilerArguments.phasesToDump?.forEach { prop("- phasesToDump") { it } }
    compilerArguments.phasesToDumpAfter?.forEach { prop("- phasesToDumpAfter") { it } }
    compilerArguments.phasesToDumpBefore?.forEach { prop("- phasesToDumpBefore") { it } }
    // TODO(b/136991404): Review whether the following sorted() is safe.
    compilerArguments.pluginClasspaths?.map { it.toPrintablePath() }?.sorted()?.forEach { prop("- pluginClasspaths") { it } }
    compilerArguments.pluginOptions?.forEach { prop("- pluginOptions") {
      it.nullizePrefixedWith("plugin:org.jetbrains.kotlin.android:configuration=") }
    }
    prop("profilePhases") { compilerArguments.profilePhases.takeIf { it }?.toString() }
    prop("progressiveMode") { compilerArguments.progressiveMode.takeIf { it }?.toString() }
    prop("properIeee754Comparisons") { compilerArguments.properIeee754Comparisons.takeIf { it }?.toString() }
    prop("readDeserializedContracts") { compilerArguments.readDeserializedContracts.takeIf { it }?.toString() }
    prop("reportOutputFiles") { compilerArguments.reportOutputFiles.takeIf { it }?.toString() }
    prop("reportPerf") { compilerArguments.reportPerf.takeIf { it }?.toString() }
    prop("skipMetadataVersionCheck") { compilerArguments.skipMetadataVersionCheck.takeIf { it }?.toString() }
    compilerArguments.optIn?.forEach { prop("- optIn") { it } }
    compilerArguments.verbosePhases?.forEach { prop("- verbosePhases") { it } }
  }
}

private fun String.nullizePrefixedWith(vararg prefixes: String): String? = if (prefixes.any { this.startsWith(it) }) null else this

private fun ProjectDumper.dump(compilerSettings: CompilerSettings) {
  nest {
    prop("additionalArguments") { compilerSettings.additionalArguments }
    prop("copyJsLibraryFiles") { compilerSettings.copyJsLibraryFiles.takeIf { it }?.toString() }
    prop("outputDirectoryForJsLibraryFiles") { compilerSettings.outputDirectoryForJsLibraryFiles }
    prop("scriptTemplates") { compilerSettings.scriptTemplates.nullize() }
    prop("scriptTemplatesClasspath") { compilerSettings.scriptTemplatesClasspath.nullize() }
  }
}

private fun ProjectDumper.dump(compilerModuleExtension: CompilerModuleExtension) {
  head("COMPILER_MODULE_EXTENSION") { null }
  nest {
    prop("compilerSourceOutputPath") { compilerModuleExtension.compilerOutputUrl?.toPrintablePath() }
    prop("compilerTestOutputPath") { compilerModuleExtension.compilerOutputUrlForTests?.toPrintablePath() }
    prop("isCompilerPathInherited") { compilerModuleExtension.isCompilerOutputPathInherited.toString() }
    prop("isExcludeOutput") { compilerModuleExtension.isExcludeOutput.toString() }
  }
}

private fun ProjectDumper.dump(testModuleProperties: TestModuleProperties?) {
  if (testModuleProperties?.productionModuleName == null) return
  head("TEST_MODULE_PROPERTIES") { null }
  nest {
    prop("productionModuleName") { testModuleProperties.productionModuleName }
  }
}

private fun ProjectDumper.dump(linkedAndroidModuleGroup: LinkedAndroidModuleGroup) {
  head("LINKED_ANDROID_MODULE_GROUP") { null }
  nest {
    prop("holder") { linkedAndroidModuleGroup.holder.name }
    prop("main") { linkedAndroidModuleGroup.main.name }
    prop("unitTest") { linkedAndroidModuleGroup.unitTest?.name }
    prop("androidTest") { linkedAndroidModuleGroup.androidTest?.name }
    prop("testFixtures") { linkedAndroidModuleGroup.testFixtures?.name }
  }
}

private fun String.toSystemIndependent() = FileUtils.toSystemIndependentPath(this)

class DumpProjectAction : DumbAwareAction("Dump Project Structure") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val dumper = ProjectDumper(projectJdk = ProjectRootManager.getInstance(project).projectSdk)
    dumper.dumpProject(project)
    val dump = dumper.toString().trimIndent()
    val outputFile = File(File(project.basePath), sanitizeFileName(project.name) + ".project_dump")
    outputFile.writeText(dump)
    FileEditorManager.getInstance(project).openEditor(OpenFileDescriptor(project, VfsUtil.findFileByIoFile(outputFile, true)!!), true)
    VfsUtil.markDirtyAndRefresh(true, false, false, outputFile)
    println("Dumped to: file://$outputFile")
  }
}
