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

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.ide.common.repository.GoogleMavenArtifactId.ANDROIDX_CORE_KTX
import com.android.ide.common.repository.GoogleMavenArtifactId.ANDROIDX_LIFECYCLE_EXTENSIONS
import com.android.ide.common.repository.GoogleMavenArtifactId.ANDROIDX_LIFECYCLE_VIEWMODEL_KTX
import com.android.ide.common.repository.GoogleMavenArtifactId.NAVIGATION_FRAGMENT
import com.android.ide.common.repository.GoogleMavenArtifactId.NAVIGATION_FRAGMENT_KTX
import com.android.ide.common.repository.GoogleMavenArtifactId.NAVIGATION_UI
import com.android.ide.common.repository.GoogleMavenArtifactId.NAVIGATION_UI_KTX
import com.android.tools.idea.gradle.dependencies.DependenciesHelper
import com.android.tools.idea.gradle.dependencies.GroupNameDependencyMatcher
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig.MatchedStrategy
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig.PluginInsertionStep
import com.android.tools.idea.gradle.dependencies.PluginsHelper
import com.android.tools.idea.gradle.dependencies.PluginInsertionConfig.*
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
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
import org.gradle.api.plugins.JavaPlatformPlugin.CLASSPATH_CONFIGURATION_NAME
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

    private fun ChangedConfiguratorFiles.addAll(files:Set<PsiFile>) =
        files.forEach { storeOriginalFileContent(it) }

    private val insertionConfig: PluginInsertionConfig

    init {
        val steps = LinkedHashSet<PluginInsertionStep>()
        steps.addAll(listOf(
            BuildscriptClasspathWithVariableInsertionStep,
            PluginBlockInsertionStep,
            PluginManagementInsertionStep))
        insertionConfig = PluginInsertionConfig(
            steps,
            MatchedStrategy.UPDATE_VERSION,
            "kotlin_version",
            true
        )
    }

    // for all cases method is called for top level build.gradle and then for selected/all modules build.gradle
    override fun addElementsToFiles(file: PsiFile, isTopLevelProjectFile: Boolean, originalVersion: IdeKotlinVersion,
                                    jvmTarget: String?, addVersion: Boolean, changedFiles: ChangedConfiguratorFiles) {
        if (isTopLevelProjectFile) return
        val version = originalVersion.rawVersion // TODO(b/244338901): Migrate to IdeKotlinVersion.
        val module = ModuleUtil.findModuleForPsiElement(file) ?: return
        val project = module.project
        val projectBuildModel = ProjectBuildModel.get(project)
        val moduleBuildModel = projectBuildModel.getModuleBuildModel(module) ?: error("Build model for module $module not found")

        val pluginId = "org.jetbrains.kotlin.android"
        val classpathModule = "org.jetbrains.kotlin:kotlin-gradle-plugin"
        val helper = PluginsHelper.withModel(projectBuildModel)
        val kotlinPluginAddedFiles = helper.addPluginOrClasspath(
            pluginId,
            classpathModule,
            version,
            listOf(moduleBuildModel),
            classpathMatcher = GroupNameDependencyMatcher(CLASSPATH_CONFIGURATION_NAME, "$classpathModule:$version"),
            config = insertionConfig
        )
        if (kotlinPluginAddedFiles.isNotEmpty()) {
            changedFiles.addAll(kotlinPluginAddedFiles)

            // handle an allprojects block if present.
            projectBuildModel.projectBuildModel?.repositories()?.takeIf { it.psiElement != null }?.let {
                helper.addRepositoryFor(version, it)?.let { file -> changedFiles.storeOriginalFileContent(file) }
            }
        }
        if (file.project.isAndroidx()) {
            val ktxCoreVersion = IdeGoogleMavenRepository
                                     .findVersion(ANDROIDX_CORE_KTX.mavenGroupId, ANDROIDX_CORE_KTX.mavenArtifactId)
                                     ?.toString() ?: "+"
            (addDependency(projectBuildModel, moduleBuildModel, ANDROIDX_CORE_KTX, ktxCoreVersion) +
             addKtxDependenciesFromMap(projectBuildModel, module, moduleBuildModel, androidxKtxLibraryMap))
                .let {
                    changedFiles.addAll(it)
                }
        }
        addKtxDependenciesFromMap(projectBuildModel, module, moduleBuildModel, nonAndroidxKtxLibraryMap).let {
            changedFiles.addAll(it)
        }

        LanguageLevel.parse(jvmTarget)?.let { languageLevel ->
            moduleBuildModel.android().kotlinOptions().jvmTarget().setLanguageLevel(languageLevel)
            moduleBuildModel.psiFile?.let { changedFiles.storeOriginalFileContent(it) }
        }
        moduleBuildModel.repositories().takeIf { it.psiElement != null }?.let {
            helper.addRepositoryFor(version, it)?.let { file -> changedFiles.storeOriginalFileContent(file) }
        }
        projectBuildModel.projectSettingsModel?.dependencyResolutionManagement()?.repositories()
            ?.takeIf { it.psiElement != null }?.let {
                helper.addRepositoryFor(version, it)?.let { file ->
                    changedFiles.storeOriginalFileContent(file)
                }
            }
        projectBuildModel.applyChanges()
    }

    @JvmSuppressWildcards
    override fun configure(project: Project, excludeModules: Collection<Module>) {
        configureAndGetConfiguredModules(project, excludeModules)
    }

    @JvmSuppressWildcards
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

    private fun doConfigure(project: Project,
                            modules: List<Module>,
                            version: IdeKotlinVersion): Pair<NotificationMessageCollector, Set<Module>> {
        return project.executeCommand(KotlinIdeaGradleBundle.message("command.name.configure.kotlin")) {
            val collector = NotificationMessageCollector.create(project)
            val modulesAndJvmTargets = modules
              .mapNotNull { module -> GradleAndroidModel.get(module)?.getTargetLanguageLevel()?.let {
                  languageLevel -> module.name to languageLevel.toJavaVersion().toString() }
              }
              .toMap()
            val (configuredModules, changedFiles) = configureWithVersion(project, modules, version, collector,
                                                                         kotlinVersionsAndModules = emptyMap(),
                                                                         modulesAndJvmTargets = modulesAndJvmTargets)

            for (file in changedFiles.getChangedFiles()) {
                OpenFileAction.openFile(file.virtualFile, project)
            }
            // Sync after changing build scripts
            project.getProjectSystem().getSyncManager().requestSyncProject(TRIGGER_LANGUAGE_KOTLIN_CONFIGURED.toReason())
            UndoManager.getInstance(project).undoableActionPerformed(object : BasicUndoableAction() {
                override fun undo() {
                    project.getProjectSystem().getSyncManager().requestSyncProject(TRIGGER_MODIFIER_ACTION_UNDONE.toReason())
                }

                override fun redo() {
                    project.getProjectSystem().getSyncManager().requestSyncProject(TRIGGER_MODIFIER_ACTION_REDONE.toReason())
                }
            })
            Pair(collector, configuredModules)
        }
    }

    private fun addDependency(
      projectBuildModel: ProjectBuildModel,
      moduleBuildModel: GradleBuildModel,
      id: GoogleMavenArtifactId,
      version: String
    ): Set<PsiFile> =
      DependenciesHelper.withModel(projectBuildModel).addDependency("implementation",
                                                                    ArtifactDependencySpec.create(id.mavenArtifactId, id.mavenGroupId, version).compactNotation(),
                                                                    moduleBuildModel)

    // Return version string of the specified dependency if module depends on it, and null otherwise.
    private fun getDependencyVersion(module: Module, id: GoogleMavenArtifactId): String? {
        try {
            return module.getModuleSystem().getResolvedDependency(id)?.revision
        }
        catch (e: DependencyManagementException) {
            return null
        }
    }

    private fun addKtxDependenciesFromMap(
      projectBuildModel: ProjectBuildModel,
      module: Module,
      moduleBuildModel: GradleBuildModel,
      libraryMap: Map<GoogleMavenArtifactId, GoogleMavenArtifactId>
    ): Set<PsiFile> {
        val updatedFiles = mutableSetOf<PsiFile>()
        for ((library, ktxLibrary) in libraryMap) {
            getDependencyVersion(module, library)?.let {
                updatedFiles.addAll(
                  addDependency(projectBuildModel, moduleBuildModel, ktxLibrary, it)
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

    override fun queueSyncIfNeeded(project: Project) {
        // Do nothing; we queue syncs for Gradle and Maven projects for Kotlin stdlib to be loaded before Java to Kotlin conversion
    }


    companion object {
        private const val NAME = "android-gradle"

        private const val KOTLIN_ANDROID = "kotlin-android"

        private val nonAndroidxKtxLibraryMap = mapOf(
          NAVIGATION_UI to NAVIGATION_UI_KTX,
          NAVIGATION_FRAGMENT to NAVIGATION_FRAGMENT_KTX,
        )

        private val androidxKtxLibraryMap = mapOf(
          ANDROIDX_LIFECYCLE_EXTENSIONS to ANDROIDX_LIFECYCLE_VIEWMODEL_KTX,
        )
    }
}
