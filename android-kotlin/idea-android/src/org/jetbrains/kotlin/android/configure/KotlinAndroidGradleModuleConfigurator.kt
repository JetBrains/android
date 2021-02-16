/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.configure

import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.projectsystem.DependencyManagementException
import com.android.tools.idea.projectsystem.getModuleSystem
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_LANGUAGE_KOTLIN_CONFIGURED
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiFile
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.configuration.AndroidGradle
import org.jetbrains.kotlin.idea.configuration.GradleBuildScriptManipulator
import org.jetbrains.kotlin.idea.configuration.KotlinWithGradleConfigurator
import org.jetbrains.kotlin.idea.configuration.getBuildSystemType
import org.jetbrains.kotlin.idea.util.projectStructure.version
import org.jetbrains.kotlin.idea.versions.MAVEN_STDLIB_ID_JDK7
import org.jetbrains.kotlin.idea.versions.hasJreSpecificRuntime
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.TargetPlatform

class KotlinAndroidGradleModuleConfigurator : KotlinWithGradleConfigurator() {

    override val name: String = NAME

    override val targetPlatform: TargetPlatform = JvmPlatforms.defaultJvmPlatform

    @Suppress("DEPRECATION_ERROR")
    override fun getTargetPlatform(): org.jetbrains.kotlin.resolve.TargetPlatform = JvmPlatforms.CompatJvmPlatform

    override val presentableText: String = "Android with Gradle"

    public override fun isApplicable(module: Module): Boolean = module.getBuildSystemType() == AndroidGradle

    override val kotlinPluginName: String = KOTLIN_ANDROID

    override fun getKotlinPluginExpression(forKotlinDsl: Boolean): String =
        if (forKotlinDsl) "kotlin(\"android\")" else "id 'org.jetbrains.kotlin.android' "

    override fun addElementsToFile(file: PsiFile, isTopLevelProjectFile: Boolean, version: String): Boolean {
        val manipulator = getManipulator(file, false)
        val module = ModuleUtil.findModuleForPsiElement(file)?: return false
        val sdk = ModuleRootManager.getInstance(module).sdk
        val jvmTarget = getJvmTarget(sdk, version)

        return if (isTopLevelProjectFile) {
            manipulator.configureProjectBuildScript(kotlinPluginName, version)
        }
        else {
            if (file.project.isAndroidx()) {
                addDependency(manipulator, ANDROIDX_CORE_GROUP, CORE_KTX, "+")
                addKtxDependenciesFromMap(module, manipulator, androidxKtxLibraryMap)
            }
            addKtxDependenciesFromMap(module, manipulator, nonAndroidxKtxLibraryMap)
            manipulator.configureModuleBuildScript(
                    kotlinPluginName,
                    getKotlinPluginExpression(file.isKtDsl()),
                    getStdlibArtifactName(sdk, version),
                    version,
                    jvmTarget
            )
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
        super.configure(project, excludeModules)
        // Sync after changing build scripts
        GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncInvoker.Request(TRIGGER_LANGUAGE_KOTLIN_CONFIGURED))
    }

    private fun addDependency(manipulator: GradleBuildScriptManipulator<*>, groupId: String, artifactId: String, version: String) {
        manipulator.addKotlinLibraryToModuleBuildScript(
          DependencyScope.COMPILE,
          ExternalLibraryDescriptor(groupId, artifactId, version, version))
    }

    // Return version string of the specified dependency if module depends on it, and null otherwise.
    private fun getDependencyVersion(module: Module, groupId: String, artifactId: String): String? {
        try {
            val coordinate = GradleCoordinate(groupId, artifactId, "+")
            return module.getModuleSystem().getResolvedDependency(coordinate)?.revision
        } catch (e: DependencyManagementException) {
            return null
        }
    }

    private fun addKtxDependenciesFromMap(module: Module, manipulator: GradleBuildScriptManipulator<*>, librayMap: Map<String, String>) {
        for ((library, ktxLibrary) in librayMap) {
            val ids = library.split(":")
            val ktxIds = ktxLibrary.split(":")
            getDependencyVersion(module, ids[0], ids[1])?.let {addDependency(manipulator, ktxIds[0], ktxIds[1], it)}
        }
    }

  override fun changeGeneralFeatureConfiguration(
    module: Module,
    feature: LanguageFeature,
    state: LanguageFeature.State,
    forTests: Boolean
  ) {
    if (feature == LanguageFeature.InlineClasses) {
      val project = module.project
      val projectBuildModel = ProjectBuildModel.get(project)
      val moduleBuildModel = projectBuildModel.getModuleBuildModel(module) ?: error("Build model for module $module not found")
      when (state) {
        LanguageFeature.State.ENABLED ->
          moduleBuildModel.android().kotlinOptions().freeCompilerArgs().addListValue().setValue("-Xinline-classes")
        LanguageFeature.State.DISABLED ->
          moduleBuildModel.android().kotlinOptions().freeCompilerArgs().getListValue("-Xinline-classes")?.delete()
        LanguageFeature.State.ENABLED_WITH_ERROR, LanguageFeature.State.ENABLED_WITH_WARNING -> Unit
      }
      projectBuildModel.applyChanges()
      moduleBuildModel.reparse()
      moduleBuildModel.android().kotlinOptions().freeCompilerArgs().psiElement?.let {
        OpenFileDescriptor(project, it.containingFile.virtualFile, it.textRange.startOffset).navigate(true)
      }
    }
    else {
      super.changeGeneralFeatureConfiguration(module, feature, state, forTests)
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
