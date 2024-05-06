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
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
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

    private fun RepositoriesModel.addRepositoryFor(version: String) {
        if (version.contains("SNAPSHOT")) {
            addMavenRepositoryByUrl("https://oss.sonatype.org/content/repositories/snapshots", "Sonatype OSS Snapshot Repository")
        }
        if (!containsMethodCall("jcenter")) {
            // Despite the name this doesn't add it if it's already there.
            addRepositoryByMethodName("mavenCentral")
        }
    }

    private fun addToBuildscriptDependencies(moduleBuildModel: GradleBuildModel, version: String): Boolean {
        moduleBuildModel.buildscript().dependencies().takeIf { it.psiElement != null }
          ?.let { dependencies -> // known to exist on file
              val existing = dependencies
                .artifacts("classpath")
                .firstOrNull { it.group().forceString() == "org.jetbrains.kotlin" && it.name().forceString() == "kotlin-gradle-plugin" }
              if (existing == null) {
                  moduleBuildModel.buildscript().ext().findProperty("kotlin_version").setValue(version)
                  dependencies.addArtifact("classpath", "org.jetbrains.kotlin:kotlin-gradle-plugin:\$kotlin_version")
                  moduleBuildModel.buildscript().repositories().addRepositoryFor(version)
                  return true
              }
              else {
                  val currentVersion = existing.version().resolve().getValue(STRING_TYPE)
                  if (currentVersion != version) {
                      existing.version().resolve().setValue(version)
                      return true
                  }
              }
          }
        return false
    }

    private fun addToPluginsBlock(projectBuildModel: ProjectBuildModel, moduleBuildModel: GradleBuildModel, version: String): Boolean {
        moduleBuildModel.plugins().takeIf { moduleBuildModel.pluginsPsiElement != null }
          ?.let { plugins -> // known to exist on file
              val existing = plugins
                .firstOrNull { it.name().forceString() == "org.jetbrains.kotlin.android" }
              if (existing == null) {
                  // TODO(xof): kotlin("android") for kotlin [cosmetic]
                  moduleBuildModel.applyPlugin("org.jetbrains.kotlin.android", version, false)
                  // TODO(xof): is this the right place to add this dependency?
                  projectBuildModel.projectSettingsModel?.pluginManagement()?.repositories()?.addRepositoryFor(version)
                  return true
              }
              else {
                  val currentVersion = existing.version().resolve().getValue(STRING_TYPE)
                  if (currentVersion != version) {
                      existing.version().setValue(version)
                      return true
                  }
              }
          }
        return false
    }

    private fun addToPluginsManagementBlock(
      projectBuildModel: ProjectBuildModel, moduleBuildModel: GradleBuildModel, version: String
    ): Boolean {
        projectBuildModel.projectSettingsModel?.pluginManagement()?.plugins()?.takeIf { it.psiElement != null }
          ?.let { plugins -> // known to exist on file
              val existing = plugins.plugins()
                .firstOrNull { it.name().forceString() == "org.jetbrains.kotlin.android" }
              if (existing == null) {
                  // TODO(xof): kotlin("android") for kotlin [cosmetic]
                  moduleBuildModel.applyPlugin("org.jetbrains.kotlin.android", version)
                  projectBuildModel.projectSettingsModel?.pluginManagement()?.repositories()?.addRepositoryFor(version)
                  return true
              }
              else {
                  val currentVersion = existing.version().resolve().getValue(STRING_TYPE)
                  if (currentVersion != version) {
                      existing.version().setValue(version)
                      return true
                  }
              }
          }
        return false
    }

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
            changedFiles.storeOriginalFileContent(file)
            val kotlinPluginAdded = addToBuildscriptDependencies(moduleBuildModel, version) ||
                                    addToPluginsBlock(projectBuildModel, moduleBuildModel, version) ||
                                    addToPluginsManagementBlock(projectBuildModel, moduleBuildModel, version)

            // handle an allprojects block if present.
            if (kotlinPluginAdded) {
                moduleBuildModel.repositories().takeIf { it.psiElement != null }?.addRepositoryFor(version)
                projectBuildModel.applyChanges()
            }
        }
        else {
            changedFiles.storeOriginalFileContent(file)
            if (file.project.isAndroidx()) {
                addDependency(moduleBuildModel, ANDROIDX_CORE_GROUP, CORE_KTX, "+")
                addKtxDependenciesFromMap(module, moduleBuildModel, androidxKtxLibraryMap)
            }
            addKtxDependenciesFromMap(module, moduleBuildModel, nonAndroidxKtxLibraryMap)
            /*
            We need to
            - apply the plugin (it is known to be missing at this point, otherwise we would not be configuring Kotlin in the first place)
            - add an android.kotlinOptions jvmTarget property, if jvmTarget is not null.

            Also, if we failed to find repositories in the top-level project, we should add repositories to this build file.
             */
            moduleBuildModel.applyPlugin("org.jetbrains.kotlin.android")
            if (jvmTarget != null) {
                moduleBuildModel.android().kotlinOptions().jvmTarget().setValue(jvmTarget)
            }
            moduleBuildModel.repositories().takeIf { it.psiElement != null }?.addRepositoryFor(version)
            projectBuildModel.projectSettingsModel?.dependencyResolutionManagement()?.repositories()
              ?.takeIf { it.psiElement != null }?.addRepositoryFor(version)

            projectBuildModel.applyChanges()
        }
    }

    @JvmSuppressWildcards
    override fun configure(project: Project, excludeModules: Collection<Module>) {
        configureAndGetConfiguredModules(project, excludeModules)
    }

    override fun configureAndGetConfiguredModules(project: Project, excludeModules: Collection<Module>): Set<Module> {
        // We override this, inlining the superclass method, in order to be able to trigger sync on undo and redo
        // across this operation.
        val dialog = ConfigureDialogWithModulesAndVersion(project, this, excludeModules, getMinimumSupportedVersion())

        dialog.show()
        if (!dialog.isOK) return emptySet()
        val kotlinVersion = dialog.kotlinVersion ?: return emptySet()

        val (collector, configuredModules) = doConfigure(project, dialog.modulesToConfigure, IdeKotlinVersion.get(kotlinVersion))
        collector.showNotification()
        return configuredModules
    }

    fun doConfigure(
      project: Project,
      modules: List<Module>,
      version: IdeKotlinVersion
    ): Pair<NotificationMessageCollector, Set<Module>> {
        return project.executeCommand(KotlinIdeaGradleBundle.message("command.name.configure.kotlin")) {
            val collector = NotificationMessageCollector.create(project)
            val (configuredModules, changedFiles) = configureWithVersion(project, modules, version, collector, kotlinVersionsAndModules = emptyMap())

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
            Pair(collector, configuredModules)
        }
    }

    private fun addDependency(moduleBuildModel: GradleBuildModel, groupId: String, artifactId: String, version: String) {
        moduleBuildModel.dependencies().addArtifact("implementation", ArtifactDependencySpec.create(artifactId, groupId, version))
    }

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

    private fun addKtxDependenciesFromMap(module: Module, moduleBuildModel: GradleBuildModel, librayMap: Map<String, String>) {
        for ((library, ktxLibrary) in librayMap) {
            val ids = library.split(":")
            val ktxIds = ktxLibrary.split(":")
            getDependencyVersion(module, ids[0], ids[1])?.let { addDependency(moduleBuildModel, ktxIds[0], ktxIds[1], it) }
        }
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
