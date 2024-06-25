/*
 * Copyright (C) 2023 The Android Open Source Project
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

package org.jetbrains.kotlin.android.configure

import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.gradle.dependencies.AddDependencyPolicy
import com.android.tools.idea.gradle.dependencies.AddDependencyPolicy.Companion.calculateAddDependencyPolicy
import com.android.tools.idea.gradle.dependencies.DependenciesHelper
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.DependencyManagementException
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.toReason
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_LANGUAGE_KOTLIN_CONFIGURED
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_MODIFIER_ACTION_REDONE
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_MODIFIER_ACTION_UNDONE
import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiFile
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.ChangedConfiguratorFiles
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector
import org.jetbrains.kotlin.idea.configuration.buildSystemType
import org.jetbrains.kotlin.idea.framework.ui.ConfigureDialogWithModulesAndVersion
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.KotlinWithGradleConfigurator
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.utils.addIfNotNull

class KotlinAndroidGradleModuleConfigurator : KotlinWithGradleConfigurator() {

    override val name: String = NAME

    override val targetPlatform: TargetPlatform = JvmPlatforms.defaultJvmPlatform

    override val presentableText: String = "Android with Gradle"

    public override fun isApplicable(module: Module): Boolean = module.buildSystemType == BuildSystemType.AndroidGradle

    override val kotlinPluginName: String = KOTLIN_ANDROID

    override fun getKotlinPluginExpression(forKotlinDsl: Boolean): String =
      if (forKotlinDsl) "kotlin(\"android\")" else "id 'org.jetbrains.kotlin.android' "

    /**
     * The KGP versions displayed on the dropdown come from [ConfigureDialogWithModulesAndVersion.VERSIONS_LIST_URL]
     * response where the latest 20 rows are used. However, this class doesn't handle the case when the stdlib isn't
     * added by default for versions < 1.4.0 since those will never end up being displayed
     * https://kotlinlang.org/docs/whatsnew14.html#dependency-on-the-standard-library-added-by-default
     */
    override fun getMinimumSupportedVersion() = "1.4.0"

    private fun RepositoriesModel.addRepositoryFor(version: String): PsiFile? {
        var updated = false
        if (version.contains("SNAPSHOT")) {
            addMavenRepositoryByUrl("https://oss.sonatype.org/content/repositories/snapshots", "Sonatype OSS Snapshot Repository")
            updated = true
        }
        if (!containsMethodCall("jcenter")) {
            // Despite the name this doesn't add it if it's already there.
            updated = addRepositoryByMethodName("mavenCentral") || updated
        }
        return this.psiElement?.containingFile?.takeIf { updated }
    }

    private fun hasVersionCatalog(projectModel: ProjectBuildModel) =
        calculateAddDependencyPolicy(projectModel) == AddDependencyPolicy.VERSION_CATALOG

    private fun tryAddToBuildscriptDependencies(
      projectBuildModel: ProjectBuildModel,
      moduleBuildModel: GradleBuildModel,
      version: String
    ): TryAddResult {
        // do not add classpath if project is catalog oriented
        // we'll define plugin version there
        if (hasVersionCatalog(projectBuildModel)) return TryAddResult.failed()

        moduleBuildModel.buildscript().dependencies().takeIf { it.psiElement != null }
          ?.let { dependencies -> // known to exist on file
              val existing = dependencies
                .artifacts("classpath")
                .firstOrNull { it.group().forceString() == "org.jetbrains.kotlin" && it.name().forceString() == "kotlin-gradle-plugin" }
              if (existing == null) {
                  val updatedFiles = mutableSetOf<PsiFile>()
                  updatedFiles.addAll(
                    DependenciesHelper.withModel(projectBuildModel).addClasspathDependencyWithVersionVariable(
                      "org.jetbrains.kotlin:kotlin-gradle-plugin:$version",
                      "kotlin_version"
                    )
                  )
                  updatedFiles.addIfNotNull(
                    moduleBuildModel.buildscript().repositories().addRepositoryFor(version)
                  )
                  return TryAddResult(updatedFiles, true)
              }
              else {
                  val currentVersion = existing.version().resolve().getValue(STRING_TYPE)
                  if (currentVersion != version) {
                      existing.version().resolve().setValue(version)
                      return TryAddResult(moduleBuildModel.psiFile?.let { setOf(it) } ?: setOf(), true)
                  }
              }
              // returns here means no changes are done as classpath is in requried state
              return TryAddResult.succeedNoChanges()
          }
        return TryAddResult.failed()
    }

    private fun ChangedConfiguratorFiles.addAll(files:Set<PsiFile>) =
        files.forEach { storeOriginalFileContent(it) }

    private fun tryAddToPluginsBlock(
      projectBuildModel: ProjectBuildModel,
      moduleBuildModel: GradleBuildModel,
      version: String
    ): TryAddResult {
        moduleBuildModel.plugins().takeIf { moduleBuildModel.pluginsPsiElement != null }
          ?.let { plugins -> // known to exist on file
              val existing = plugins.firstOrNull { it.name().forceString() == "org.jetbrains.kotlin.android" }
              if (existing == null) {
                  // TODO(xof): kotlin("android") for kotlin [cosmetic]
                  val updatedFiles = mutableSetOf<PsiFile>()
                  updatedFiles.addAll(
                    DependenciesHelper.withModel(projectBuildModel).addPlugin(
                      "org.jetbrains.kotlin.android",
                      version,
                      apply = false,
                      moduleBuildModel,
                      moduleBuildModel)
                  )
                  // TODO(xof): is this the right place to add this dependency?
                  updatedFiles.addIfNotNull(
                    projectBuildModel.projectSettingsModel?.pluginManagement()?.repositories()?.addRepositoryFor(version)
                  )
                  return TryAddResult(updatedFiles, true)
              }
              else {
                  val currentVersion = existing.version().resolve().getValue(STRING_TYPE)
                  if (currentVersion != version) {
                      existing.version().setValue(version)
                      return TryAddResult(moduleBuildModel.psiFile?.let { setOf(it) } ?: setOf(), true)
                  }
              }
              return TryAddResult.succeedNoChanges()
          }
        return  TryAddResult.failed()
    }

    private fun tryAddToPluginsManagementBlock(
      projectBuildModel: ProjectBuildModel,
      moduleBuildModel: GradleBuildModel,
      version: String
    ): TryAddResult {
        projectBuildModel.projectSettingsModel?.pluginManagement()?.plugins()?.takeIf { it.psiElement != null }
          ?.let { plugins -> // known to exist on file
              val existing = plugins.plugins().firstOrNull { it.name().forceString() == "org.jetbrains.kotlin.android" }
              if (existing == null) {
                  // TODO(xof): kotlin("android") for kotlin [cosmetic]
                  val updatedFiles = mutableSetOf<PsiFile>()
                  updatedFiles.addAll(
                    DependenciesHelper.withModel(projectBuildModel).addPlugin(
                      "org.jetbrains.kotlin.android",
                      version,
                      apply = null,
                      plugins,
                      moduleBuildModel)
                  )
                  updatedFiles.addIfNotNull(
                    projectBuildModel.projectSettingsModel?.pluginManagement()?.repositories()?.addRepositoryFor(version)
                  )
                  return TryAddResult(updatedFiles, true)
              }
              else {
                  val currentVersion = existing.version().resolve().getValue(STRING_TYPE)
                  if (currentVersion != version) {
                      existing.version().setValue(version)
                      return TryAddResult(moduleBuildModel.psiFile?.let { setOf(it) } ?: setOf(), false)
                  }
              }
              return TryAddResult.succeedNoChanges()
          }
        return TryAddResult.failed()
    }

    // Files may be already in proper state, so we need additional flag `succeed` to make sure
    // all changes already there
    data class TryAddResult(val changedFiles: Set<PsiFile>, val succeed: Boolean){
        companion object{
            fun failed() =  TryAddResult(setOf(), false)
            fun succeedNoChanges() =  TryAddResult(setOf(), true)
        }
    }

    // for all cases method is called for top level build.gradle and then for selected/all modules build.gradle
    override fun addElementsToFiles(file: PsiFile, isTopLevelProjectFile: Boolean, originalVersion: IdeKotlinVersion,
                                    jvmTarget: String?, addVersion: Boolean, changedFiles: ChangedConfiguratorFiles) {
        val version = originalVersion.rawVersion // TODO(b/244338901): Migrate to IdeKotlinVersion.
        val module = ModuleUtil.findModuleForPsiElement(file) ?: return
        val project = module.project
        val projectBuildModel = ProjectBuildModel.get(project)
        val moduleBuildModel = projectBuildModel.getModuleBuildModel(module) ?: error("Build model for module $module not found")

        if (isTopLevelProjectFile) {
            // We need to handle the following cases:

            // 1. The top-level project configures plugins through classpath dependencies, with a version possibly indirected
            //    through a variable:
            //        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"

            // 2. The top-level project configures its plugins through a plugins block in the top-level project file:
            //        plugins {
            //            id 'org.jetbrains.kotlin.android' version '1.4.31' apply false // Groovy
            //            kotlin("android") version "1.4.31" apply false // KotlinScript
            //        }

            // 3. The top-level project configures plugins through a plugins block in pluginsManagement in settings:
            //        pluginsManagement {
            //            plugins {
            //                id 'org.jetbrains.kotlin.android' version '1.4.31'
            //                kotlin("android") version "1.4.31"
            //            }
            //        }
            // Each case needs to take account of version catalog. If it's there - we need to insert
            // reference instead of literal. This logic is hidden in DependenciesHelper

            // run through lazy sequence - first method that returns `TryAddResult.succeed = true` value will stop iteration
            val kotlinPluginAddedFiles = sequenceOf(
              lazy { tryAddToBuildscriptDependencies(projectBuildModel, moduleBuildModel, version) },
              lazy { tryAddToPluginsBlock(projectBuildModel, moduleBuildModel, version) },
              lazy { tryAddToPluginsManagementBlock(projectBuildModel, moduleBuildModel, version) }
            ).firstOrNull { it.value.succeed }?.value?.changedFiles ?: setOf()

            // handle an allprojects block if present.
            if (kotlinPluginAddedFiles.isNotEmpty()) {
                changedFiles.addAll(kotlinPluginAddedFiles)

                moduleBuildModel.repositories().takeIf { it.psiElement != null }?.addRepositoryFor(version)?.let {
                    changedFiles.storeOriginalFileContent(it)
                }
                projectBuildModel.applyChanges()
            }
        }
        else {
            if (file.project.isAndroidx()) {
                val ktxCoreVersion = IdeGoogleMavenRepository.findVersion(ANDROIDX_CORE_GROUP, CORE_KTX)?.toString() ?: "+"
                (addDependency(projectBuildModel, moduleBuildModel, ANDROIDX_CORE_GROUP, CORE_KTX, ktxCoreVersion) +
                 addKtxDependenciesFromMap(projectBuildModel, module, moduleBuildModel, androidxKtxLibraryMap))
                  .let {
                      changedFiles.addAll(it)
                  }
            }
            addKtxDependenciesFromMap(projectBuildModel, module, moduleBuildModel, nonAndroidxKtxLibraryMap).let {
                changedFiles.addAll(it)
            }
            /*
            We need to
            - apply the plugin (it is known to be missing at this point, otherwise we would not be configuring Kotlin in the first place)
            - add an android.kotlinOptions jvmTarget property, if jvmTarget is not null.

            Also, if we failed to find repositories in the top-level project, we should add repositories to this build file.
             */
            DependenciesHelper.withModel(projectBuildModel).addPluginToModule("org.jetbrains.kotlin.android", version, moduleBuildModel).let {
                changedFiles.addAll(it)
            }

            LanguageLevel.parse(jvmTarget)?.let { languageLevel ->
                moduleBuildModel.android().kotlinOptions().jvmTarget().setLanguageLevel(languageLevel)
                moduleBuildModel.psiFile?.let { changedFiles.storeOriginalFileContent(it) }
            }
            moduleBuildModel.repositories().takeIf { it.psiElement != null }?.addRepositoryFor(version)?.let {
                changedFiles.storeOriginalFileContent(it)
            }
            projectBuildModel.projectSettingsModel?.dependencyResolutionManagement()?.repositories()
              ?.takeIf { it.psiElement != null }?.addRepositoryFor(version)?.let {
                changedFiles.storeOriginalFileContent(it)
            }
            projectBuildModel.applyChanges()
        }
    }

    @JvmSuppressWildcards
    override fun configure(project: Project, excludeModules: Collection<Module>) {
        // We override this, inlining the superclass method, in order to be able to trigger sync on undo and redo
        // across this operation.
        val dialog = ConfigureDialogWithModulesAndVersion(project, this, excludeModules, getMinimumSupportedVersion())

        dialog.show()
        if (!dialog.isOK) return
        val kotlinVersion = dialog.kotlinVersion ?: return

        val collector = doConfigure(project, dialog.modulesToConfigure, IdeKotlinVersion.get(kotlinVersion))
        collector.showNotification()
    }

    fun doConfigure(project: Project, modules: List<Module>, version: IdeKotlinVersion): NotificationMessageCollector {
        return project.executeCommand(KotlinIdeaGradleBundle.message("command.name.configure.kotlin")) {
            val collector = NotificationMessageCollector.create(project)
            val modulesAndJvmTargets = modules
              .mapNotNull { module -> GradleAndroidModel.get(module)?.getTargetLanguageLevel()?.let {
                  languageLevel -> module.name to languageLevel.toJavaVersion().toString() }
              }
              .toMap()
            val changedFiles = configureWithVersion(project, modules, version, collector, emptyMap(), modulesAndJvmTargets)

            for (file in changedFiles.getChangedFiles()) {
                OpenFileAction.openFile(file.virtualFile, project)
            }
            // Sync after changing build scripts
            project.getProjectSystem().getSyncManager().syncProject(TRIGGER_LANGUAGE_KOTLIN_CONFIGURED.toReason())
            UndoManager.getInstance(project).undoableActionPerformed(object : BasicUndoableAction() {
                override fun undo() {
                    project.getProjectSystem().getSyncManager().syncProject(TRIGGER_MODIFIER_ACTION_UNDONE.toReason())
                }

                override fun redo() {
                    project.getProjectSystem().getSyncManager().syncProject(TRIGGER_MODIFIER_ACTION_REDONE.toReason())
                }
            })
            collector
        }
    }

    private fun addDependency(
      projectBuildModel: ProjectBuildModel,
      moduleBuildModel: GradleBuildModel,
      groupId: String,
      artifactId: String,
      version: String
    ): Set<PsiFile> =
      DependenciesHelper.withModel(projectBuildModel).addDependency("implementation",
                                                                    ArtifactDependencySpec.create(artifactId, groupId, version).compactNotation(),
                                                                    moduleBuildModel)

    // Return version string of the specified dependency if module depends on it, and null otherwise.
    private fun getDependencyVersion(module: Module, groupId: String, artifactId: String): String? {
        try {
            val coordinate = GradleCoordinate(groupId, artifactId, "+")
            return module.getModuleSystem().getResolvedDependency(coordinate)?.revision
        }
        catch (e: DependencyManagementException) {
            return null
        }
    }

    private fun addKtxDependenciesFromMap(
      projectBuildModel: ProjectBuildModel,
      module: Module,
      moduleBuildModel: GradleBuildModel,
      libraryMap: Map<String, String>
    ): Set<PsiFile> {
        val updatedFiles = mutableSetOf<PsiFile>()
        for ((library, ktxLibrary) in libraryMap) {
            val ids = library.split(":")
            val ktxIds = ktxLibrary.split(":")
            getDependencyVersion(module, ids[0], ids[1])?.let {
                updatedFiles.addAll(
                  addDependency(projectBuildModel, moduleBuildModel, ktxIds[0], ktxIds[1], it)
                )
            }
        }
        return updatedFiles
    }

    override fun changeGeneralFeatureConfiguration(
      module: Module,
      feature: LanguageFeature,
      state: LanguageFeature.State,
      forTests: Boolean
    ) {
        val (enabledString, disabledString) = when (feature) {
            LanguageFeature.InlineClasses -> "-Xinline-classes" to "-XXLanguage:-InlineClasses"
            else -> "-XXLanguage:+${feature.name}" to "-XXLanguage:-${feature.name}"
        }
        val project = module.project
        val projectBuildModel = ProjectBuildModel.get(project)
        val moduleBuildModel = projectBuildModel.getModuleBuildModel(module) ?: error("Build model for module $module not found")
        val freeCompilerArgs = moduleBuildModel.android().kotlinOptions().freeCompilerArgs()
        when (state) {
            LanguageFeature.State.ENABLED -> {
                freeCompilerArgs.getListValue(disabledString)?.delete()
                freeCompilerArgs.getListValue(enabledString) ?: freeCompilerArgs.addListValue()?.setValue(enabledString)
            }
            LanguageFeature.State.DISABLED -> {
                freeCompilerArgs.getListValue(enabledString)?.delete()
                freeCompilerArgs.getListValue(disabledString) ?: freeCompilerArgs.addListValue()?.setValue(disabledString)
            }
            else -> {
                throw UnsupportedOperationException("Setting a Kotlin language feature to state $state is unsupported in android-kotlin")
            }
        }
        projectBuildModel.applyChanges()
        moduleBuildModel.reparse()
        moduleBuildModel.android().kotlinOptions().freeCompilerArgs().psiElement?.let {
            OpenFileDescriptor(project, it.containingFile.virtualFile, it.textRange.startOffset).navigate(true)
        }
    }

    override fun configureSettingsFile(file: PsiFile, version: IdeKotlinVersion, changedFiles: ChangedConfiguratorFiles): Boolean {
        // This is just a stub, for Android its own implementation is done in the fun addToPluginsManagementBlock
        return false
    }

    companion object {
        private const val NAME = "android-gradle"

        private const val KOTLIN_ANDROID = "kotlin-android"

        private const val ANDROIDX_CORE_GROUP = "androidx.core"
        private const val CORE_KTX = "core-ktx"

        private val nonAndroidxKtxLibraryMap = mapOf(
          "android.arch.navigation:navigation-ui" to "android.arch.navigation:navigation-ui-ktx",
          "android.arch.navigation:navigation-fragment" to "android.arch.navigation:navigation-fragment-ktx"
        )

        private val androidxKtxLibraryMap = mapOf(
          "androidx.lifecycle:lifecycle-extensions" to "androidx.lifecycle:lifecycle-viewmodel-ktx"
        )
    }
}
