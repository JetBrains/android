/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.configure

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING
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
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiFile
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.ConfigureKotlinStatus
import org.jetbrains.kotlin.idea.configuration.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector
import org.jetbrains.kotlin.idea.configuration.createConfigureKotlinNotificationCollector
import org.jetbrains.kotlin.idea.configuration.getBuildSystemType
import org.jetbrains.kotlin.idea.extensions.gradle.getBuildScriptPsiFile
import org.jetbrains.kotlin.idea.extensions.gradle.getTopLevelBuildScriptPsiFile
import org.jetbrains.kotlin.idea.framework.ui.ConfigureDialogWithModulesAndVersion
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.gradleJava.KotlinGradleFacadeImpl.findManipulator
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinWithGradleConfigurator
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.util.projectStructure.version
import org.jetbrains.kotlin.idea.versions.MAVEN_STDLIB_ID_JDK7
import org.jetbrains.kotlin.idea.versions.hasJreSpecificRuntime
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

class KotlinAndroidGradleModuleConfigurator : KotlinWithGradleConfigurator() {

    override val name: String = NAME

    override val targetPlatform: TargetPlatform = JvmPlatforms.defaultJvmPlatform

    @Suppress("DEPRECATION_ERROR")
    override fun getTargetPlatform(): org.jetbrains.kotlin.resolve.TargetPlatform = JvmPlatforms.CompatJvmPlatform

    override val presentableText: String = "Android with Gradle"

    /**
     * Copied from super-class as a way to workaround b/255827313
     * Differences from original copy have been commented out and explained.
     */
    override fun getStatus(moduleSourceRootGroup: ModuleSourceRootGroup): ConfigureKotlinStatus {
        val module = moduleSourceRootGroup.baseModule
        if (!isApplicable(module)) {
            return ConfigureKotlinStatus.NON_APPLICABLE
        }

        // The Android new project template already has a dependency on the Kotlin stdlib due to its dependencies.
        // As a result ::hasAnyKotlinRuntimeInScope returns true for these modules even though the Kotlin plugin
        // is not configured in this project.

        //if (moduleSourceRootGroup.sourceRootModules.all(::hasAnyKotlinRuntimeInScope)) {
        //    return ConfigureKotlinStatus.CONFIGURED
        //}

        val buildFiles = runReadAction {
            listOf(
              module.getBuildScriptPsiFile(),
              module.project.getTopLevelBuildScriptPsiFile()
            ).filterNotNull()
        }

        if (buildFiles.isEmpty()) {
            return ConfigureKotlinStatus.NON_APPLICABLE
        }

        // isConfiguredByAnyGradleConfigurator is private within the super-class.
        // Also KotlinNativeGradleConfigurator always reports itself as configured due to its Kotlin plugin name being
        // the empty string, this causes us to always report the status as BROKEN.

        //if (buildFiles.none { it.isConfiguredByAnyGradleConfigurator() }) {
        //  return ConfigureKotlinStatus.CAN_BE_CONFIGURED
        //}

        //return ConfigureKotlinStatus.BROKEN

        // We add the following condition to check if the Android Kotlin plugin has been applied but not use any other configurator.
        if (buildFiles.none { isFileConfigured(it) }) {
            return ConfigureKotlinStatus.CAN_BE_CONFIGURED
        }


        return ConfigureKotlinStatus.BROKEN
    }

    // Copied from super-class as it is private there.
    private fun isFileConfigured(buildScript: PsiFile): Boolean {
        val manipulator = findManipulator(buildScript) ?: return false
        return with(manipulator) {
            isConfiguredWithOldSyntax(kotlinPluginName) || isConfigured(getKotlinPluginExpression(buildScript.isKtDsl()))
        }
    }

    public override fun isApplicable(module: Module): Boolean = module.getBuildSystemType() == BuildSystemType.AndroidGradle

    override val kotlinPluginName: String = KOTLIN_ANDROID

    override fun getKotlinPluginExpression(forKotlinDsl: Boolean): String =
      if (forKotlinDsl) "kotlin(\"android\")" else "id 'org.jetbrains.kotlin.android' "

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

    override fun addElementsToFile(file: PsiFile, isTopLevelProjectFile: Boolean, version: String): Boolean {
        val module = ModuleUtil.findModuleForPsiElement(file) ?: return false
        val project = module.project
        val projectBuildModel = ProjectBuildModel.get(project)
        val moduleBuildModel = projectBuildModel.getModuleBuildModel(module) ?: error("Build model for module $module not found")
        val sdk = ModuleRootManager.getInstance(module).sdk
        val jvmTarget = getJvmTarget(sdk, version)

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
            val kotlinPluginAdded = addToBuildscriptDependencies(moduleBuildModel, version) ||
                                    addToPluginsBlock(projectBuildModel, moduleBuildModel, version) ||
                                    addToPluginsManagementBlock(projectBuildModel, moduleBuildModel, version)

            // handle an allprojects block if present.
            if (kotlinPluginAdded) {
                moduleBuildModel.repositories().takeIf { it.psiElement != null }?.addRepositoryFor(version)
                projectBuildModel.applyChanges()
            }
            return kotlinPluginAdded
        }
        else {
            if (file.project.isAndroidx()) {
                addDependency(moduleBuildModel, ANDROIDX_CORE_GROUP, CORE_KTX, "+")
                addKtxDependenciesFromMap(module, moduleBuildModel, androidxKtxLibraryMap)
            }
            addKtxDependenciesFromMap(module, moduleBuildModel, nonAndroidxKtxLibraryMap)
            /*
            We need to
            - apply the plugin (it is known to be missing at this point, otherwise we would not be configuring Kotlin in the first place)
            - add a dependency on the kotlin stdlib, for kotlin versions less than 1.4 (it is added automatically for versions after that)
            - add an android.kotlinOptions jvmTarget property, if jvmTarget is not null.

            Also, if we failed to find repositories in the top-level project, we should add repositories to this build file.
             */
            moduleBuildModel.applyPlugin("org.jetbrains.kotlin.android")
            if (version == "default_version" /* for tests */ ||
                GradleVersion.tryParse(version)?.compareTo("1.4")?.let { it < 0 } != false) {
                val stdLibArtifactName = getStdlibArtifactName(sdk, version)
                val buildModel = projectBuildModel.projectBuildModel
                val versionString = when (buildModel?.buildscript()?.ext()?.findProperty("kotlin_version")?.valueType) {
                      STRING -> if (file.isKtDsl()) "\${extra[\"kotlin_version\"]}" else "\$kotlin_version"
                      null -> version
                      else -> version
                  }
                val spec = ArtifactDependencySpec.create(stdLibArtifactName, "org.jetbrains.kotlin", versionString)
                moduleBuildModel.dependencies().addArtifact("implementation", spec)
            }
            if (jvmTarget != null) {
                moduleBuildModel.android().kotlinOptions().jvmTarget().setValue(jvmTarget)
            }
            moduleBuildModel.repositories().takeIf { it.psiElement != null }?.addRepositoryFor(version)
            projectBuildModel.projectSettingsModel?.dependencyResolutionManagement()?.repositories()
              ?.takeIf { it.psiElement != null }?.addRepositoryFor(version)

            projectBuildModel.applyChanges()
            return true
        }
    }

    override fun getStdlibArtifactName(sdk: Sdk?, version: String): String {
        if (sdk != null && hasJreSpecificRuntime(version)) {
            val sdkVersion = sdk.version
            if (sdkVersion != null && sdkVersion.isAtLeast(JavaSdkVersion.JDK_1_8)) {
                // Android dex can't convert our kotlin-stdlib-jre8 artifact, so use jre7 instead (KT-16530)
                return MAVEN_STDLIB_ID_JDK7
            }
        }

        return super.getStdlibArtifactName(sdk, version)
    }

    @JvmSuppressWildcards
    override fun configure(project: Project, excludeModules: Collection<Module>) {
        // We override this, inlining the superclass method, in order to be able to trigger sync on undo and redo
        // across this operation.
        val dialog = ConfigureDialogWithModulesAndVersion(project, this, excludeModules, getMinimumSupportedVersion())

        dialog.show()
        if (!dialog.isOK) return

        val collector = doConfigure(project, dialog.modulesToConfigure, dialog.kotlinVersion)
        collector.showNotification()
    }

    fun doConfigure(project: Project, modules: List<Module>, version: String): NotificationMessageCollector {
        return project.executeCommand(KotlinIdeaGradleBundle.message("command.name.configure.kotlin")) {
            val collector = createConfigureKotlinNotificationCollector(project)
            val changedFiles = configureWithVersion(project, modules, version, collector)

            for (file in changedFiles) {
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
                freeCompilerArgs.getListValue(enabledString) ?: freeCompilerArgs.addListValue().setValue(enabledString)
            }
            LanguageFeature.State.DISABLED -> {
                freeCompilerArgs.getListValue(enabledString)?.delete()
                freeCompilerArgs.getListValue(disabledString) ?: freeCompilerArgs.addListValue().setValue(disabledString)
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
