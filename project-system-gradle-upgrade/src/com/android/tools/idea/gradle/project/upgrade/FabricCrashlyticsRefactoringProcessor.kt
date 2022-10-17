/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.PluginModel
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.repositories.MavenRepositoryModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.Companion.standardRegionNecessity
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

class FabricCrashlyticsRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override fun necessity() = standardRegionNecessity(current, new, COMPATIBLE_WITH, INCOMPATIBLE_VERSION)

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    projectBuildModel.allIncludedBuildModels.forEach model@{ model ->
      val modelPsiElement = model.psiElement ?: return@model

      // ref. https://firebase.google.com/docs/crashlytics/upgrade-sdk?platform=android Step 2.1:
      // - Replace Fabric's Maven repository with Google's Maven repository.
      // - Replace the Fabric Gradle plugin with the Firebase Crashlytics Gradle plugin.
      run {
        var hasGoogleServices = false
        var hasFirebaseCrashlytics = false
        var seenFabricCrashlytics = false
        val dependencies = model.buildscript().dependencies()
        val dependenciesOrHigherPsiElement = dependencies.psiElement ?: model.buildscript().psiElement ?: modelPsiElement
        dependencies.artifacts(CommonConfigurationNames.CLASSPATH).forEach dep@{ dep ->
          when {
            dep.spec.group == "com.google.gms" && dep.spec.name == "google-services" -> hasGoogleServices = true
            dep.spec.group == "com.google.firebase" && dep.spec.name == "firebase-crashlytics-gradle" -> hasFirebaseCrashlytics = true
            dep.spec.group == "io.fabric.tools" && dep.spec.name == "gradle" -> {
              // remove the dependency on the Fabric Gradle plugin
              val psiElement = dep.psiElement ?: dependenciesOrHigherPsiElement
              val wrappedPsiElement = WrappedPsiElement(psiElement, this, REMOVE_FABRIC_CLASSPATH_USAGE_TYPE)
              val usageInfo = RemoveFabricClasspathDependencyUsageInfo(wrappedPsiElement, dependencies, dep)
              usages.add(usageInfo)
              seenFabricCrashlytics = true
            }
          }
        }
        if (seenFabricCrashlytics) {
          // if we are a project that currently declares a dependency on io.fabric.tools:gradle (the Fabric Gradle plugin) ...
          if (!hasGoogleServices) {
            // ... if we don't have Google Services already, add it
            val wrappedPsiElement = WrappedPsiElement(dependenciesOrHigherPsiElement, this, ADD_GOOGLE_SERVICES_CLASSPATH_USAGE_TYPE)
            val usageInfo = AddGoogleServicesClasspathDependencyUsageInfo(wrappedPsiElement, dependencies)
            usages.add(usageInfo)
          }
          if (!hasFirebaseCrashlytics) {
            // ... if we don't have Firebase Crashlytics already
            val wrappedPsiElement = WrappedPsiElement(dependenciesOrHigherPsiElement, this, ADD_FIREBASE_CRASHLYTICS_CLASSPATH_USAGE_TYPE)
            val usageInfo = AddFirebaseCrashlyticsClasspathDependencyUsageInfo(wrappedPsiElement, dependencies)
            usages.add(usageInfo)
          }
        }

        var seenFabricMavenRepository = false
        val repositories = model.buildscript().repositories()
        val repositoriesOrHigherPsiElement = repositories.psiElement ?: model.buildscript().psiElement ?: modelPsiElement
        repositories.repositories().filterIsInstance(MavenRepositoryModel::class.java).forEach repo@{ repo ->
          if (repo.url().forceString().startsWith("https://maven.fabric.io/public")) {
            val psiElement = repo.psiElement ?: repositoriesOrHigherPsiElement
            val wrappedPsiElement = WrappedPsiElement(psiElement, this, REMOVE_FABRIC_REPOSITORY_USAGE_TYPE)
            val usageInfo = RemoveFabricMavenRepositoryUsageInfo(wrappedPsiElement, repositories, repo)
            usages.add(usageInfo)
            seenFabricMavenRepository = true
          }
        }
        if (seenFabricMavenRepository && !repositories.hasGoogleMavenRepository()) {
          val wrappedPsiElement = WrappedPsiElement(repositoriesOrHigherPsiElement, this, ADD_GMAVEN_REPOSITORY_USAGE_TYPE)
          val usageInfo = AddGoogleMavenRepositoryUsageInfo(wrappedPsiElement, repositories)
          usages.add(usageInfo)
        }
      }

      // ref. https://firebase.google.com/docs/crashlytics/upgrade-sdk?platform=android Step 2.2
      // - In your app-level build.gradle, replace the Fabric plugin with the Firebase Crashlytics plugin.
      run {
        val pluginsOrHigherPsiElement = model.pluginsPsiElement ?: modelPsiElement
        var seenFabricPlugin = false
        var seenGoogleServicesPlugin = false
        model.plugins().forEach { plugin ->
          when (plugin.name().forceString()) {
            "com.google.gms.google-services" -> seenGoogleServicesPlugin = true
            "io.fabric" -> {
              val psiElement = plugin.psiElement ?: pluginsOrHigherPsiElement
              val wrappedPsiElement = WrappedPsiElement(psiElement, this, REPLACE_FABRIC_PLUGIN_USAGE_TYPE)
              val usageInfo = ReplaceFabricPluginUsageInfo(wrappedPsiElement, plugin)
              usages.add(usageInfo)
              seenFabricPlugin = true
            }
          }
        }
        if (seenFabricPlugin && !seenGoogleServicesPlugin) {
          val wrappedPsiElement = WrappedPsiElement(pluginsOrHigherPsiElement, this, APPLY_GOOGLE_SERVICES_PLUGIN_USAGE_TYPE)
          val usageInfo = ApplyGoogleServicesPluginUsageInfo(wrappedPsiElement, model)
          usages.add(usageInfo)
        }
      }

      run {
        val dependencies = model.dependencies()
        val dependenciesOrHigherPsiElement = dependencies.psiElement ?: modelPsiElement

        // ref. https://firebase.google.com/docs/crashlytics/upgrade-sdk?platform=android Step 2.3
        // - In your app-level build.gradle, replace the legacy Fabric Crashlytics SDK with the new Firebase Crashlytics SDK.
        //   Make sure you add version 17.0.0 or later (beginning November 15, 2020, this is required for your crash reports to appear
        //   in the Firebase console).
        run {
          var seenFabricSdk = false
          var seenFirebaseSdk = false
          var seenGoogleAnalyticsSdk = false
          dependencies.artifacts().forEach dep@{ dep ->
            when {
              dep.spec.group == "com.crashlytics.sdk.android" && dep.spec.name == "crashlytics" -> {
                val psiElement = dep.psiElement ?: dependenciesOrHigherPsiElement
                val wrappedPsiElement = WrappedPsiElement(psiElement, this, REMOVE_FABRIC_CRASHLYTICS_SDK_USAGE_TYPE)
                val usageInfo = RemoveFabricCrashlyticsSdkUsageInfo(wrappedPsiElement, dependencies, dep)
                usages.add(usageInfo)
                seenFabricSdk = true
              }
              dep.spec.group == "com.google.firebase" && dep.spec.name == "firebase-crashlytics" -> seenFirebaseSdk = true
              dep.spec.group == "com.google.firebase" && dep.spec.name == "google-analytics" -> seenGoogleAnalyticsSdk = true
            }
          }
          // if we currently depend on the Fabric SDK ...
          if (seenFabricSdk) {
            // ... insert a dependency on the Firebase Crashlytics SDK, if not already present ...
            if (!seenFirebaseSdk) {
              val wrappedPsiElement = WrappedPsiElement(dependenciesOrHigherPsiElement, this, ADD_FIREBASE_CRASHLYTICS_SDK_USAGE_TYPE)
              val usageInfo = AddFirebaseCrashlyticsSdkUsageInfo(wrappedPsiElement, dependencies)
              usages.add(usageInfo)
            }
            // ... and insert a dependency on the Google Analytics SDK, as recommended.
            if (!seenGoogleAnalyticsSdk) {
              val wrappedPsiElement = WrappedPsiElement(dependenciesOrHigherPsiElement, this, ADD_GOOGLE_ANALYTICS_SDK_USAGE_TYPE)
              val usageInfo = AddGoogleAnalyticsSdkUsageInfo(wrappedPsiElement, dependencies)
              usages.add(usageInfo)
            }
          }
        }

        // ref. https://firebase.google.com/docs/crashlytics/upgrade-sdk?platform=android Optional Step: Set up Ndk crash reporting
        // - only done if crashlytics.enableNdk is present and enabled in the current project.
        // - In your app-level build.gradle, replace the Fabric NDK dependency with the Firebase Crashlytics NDK dependency. Then,
        //   add the firebaseCrashlytics extension and make sure to enable the nativeSymbolUploadEnabled flag.
        run {
          if (model.crashlytics().enableNdk().getValue(GradlePropertyModel.BOOLEAN_TYPE) == true) {
            // if enableNdk is true (not false or null/non-existent), remove it ...
            run {
              val psiElement = model.crashlytics().enableNdk().psiElement ?: model.crashlytics().psiElement ?: modelPsiElement
              val wrappedPsiElement = WrappedPsiElement(psiElement, this, REMOVE_CRASHLYTICS_ENABLE_NDK_USAGE_TYPE)
              val usageInfo = RemoveCrashlyticsEnableNdkUsageInfo(wrappedPsiElement, model)
              usages.add(usageInfo)
            }
            // ... turn on native symbol upload for the `release` buildType ...
            run {
              val releaseBuildType = model.android().buildTypes().first { it.name() == "release" }
              val psiElement = releaseBuildType.psiElement ?: model.android().psiElement ?: modelPsiElement
              val wrappedPsiElement = WrappedPsiElement(psiElement, this, ADD_FIREBASE_CRASHLYTICS_NATIVE_SYMBOL_UPLOAD_USAGE_TYPE)
              val usageInfo = AddBuildTypeFirebaseCrashlyticsUsageInfo(wrappedPsiElement, releaseBuildType)
              usages.add(usageInfo)
            }
          }

          // replace the Fabric NDK dependency with the Firebase Crashlytics NDK dependency
          var seenFabricNdk = false
          var seenFirebaseCrashlyticsNdk = false
          dependencies.artifacts().forEach dep@{ dep ->
            when {
              dep.spec.group == "com.crashlytics.sdk.android" && dep.spec.name == "crashlytics-ndk" -> {
                val psiElement = dep.psiElement ?: dependenciesOrHigherPsiElement
                val wrappedPsiElement = WrappedPsiElement(psiElement, this, REMOVE_FABRIC_NDK_USAGE_TYPE)
                val usageInfo = RemoveFabricNdkUsageInfo(wrappedPsiElement, dependencies, dep)
                usages.add(usageInfo)
                seenFabricNdk = true
              }
              dep.spec.group == "com.google.firebase" && dep.spec.name == "firebase-crashlytics-ndk" -> seenFirebaseCrashlyticsNdk = true
            }
          }
          if (seenFabricNdk && !seenFirebaseCrashlyticsNdk) {
            val wrappedPsiElement = WrappedPsiElement(dependenciesOrHigherPsiElement, this, ADD_FIREBASE_CRASHLYTICS_NDK_USAGE_TYPE)
            val usageInfo = AddFirebaseCrashlyticsNdkUsageInfo(wrappedPsiElement, dependencies)
            usages.add(usageInfo)
          }
        }
      }
    }
    return usages.toTypedArray()
  }

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = AndroidBundle.message("project.upgrade.fabricCrashlyticsRefactoringProcessor.usageView.header")
    }
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentKind.FABRIC_CRASHLYTICS)

  override fun getCommandName(): String = AndroidBundle.message("project.upgrade.fabricCrashlyticsRefactoringProcessor.commandName")

  override fun getShortDescription() =
    """
       The Fabric SDK is no longer supported as of November 15, 2020.
    """.trimIndent()

  override val readMoreUrlRedirect = ReadMoreUrlRedirect("fabric-crashlytics")

  companion object {
    val COMPATIBLE_WITH = AgpVersion.parse("3.4.0")
    val INCOMPATIBLE_VERSION = AgpVersion.parse("4.1.0-alpha05") // see b/154302886

    val REMOVE_FABRIC_REPOSITORY_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.fabricCrashlyticsRefactoringProcessor.removeFabricRepositoryUsageType"))
    val ADD_GMAVEN_REPOSITORY_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.fabricCrashlyticsRefactoringProcessor.addGmavenRepositoryUsageType"))

    val REMOVE_FABRIC_CLASSPATH_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.fabricCrashlyticsRefactoringProcessor.removeFabricClasspathUsageType"))
    val ADD_GOOGLE_SERVICES_CLASSPATH_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.fabricCrashlyticsRefactoringProcessor.addGoogleServicesClasspathUsageType"))
    val ADD_FIREBASE_CRASHLYTICS_CLASSPATH_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.fabricCrashlyticsRefactoringProcessor.addFirebaseCrashlyticsClasspathUsageType"))

    val REPLACE_FABRIC_PLUGIN_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.fabricCrashlyticsRefactoringProcessor.replaceFabricPluginUsageType"))
    val APPLY_GOOGLE_SERVICES_PLUGIN_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.fabricCrashlyticsRefactoringProcessor.applyGoogleServicesPluginUsageType"))

    val REMOVE_FABRIC_CRASHLYTICS_SDK_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.fabricCrashlyticsRefactoringProcessor.removeFabricCrashlyticsSdkUsageType"))
    val ADD_FIREBASE_CRASHLYTICS_SDK_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.fabricCrashlyticsRefactoringProcessor.addFirebaseCrashlyticsSdkUsageType"))
    val ADD_GOOGLE_ANALYTICS_SDK_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.fabricCrashlyticsRefactoringProcessor.addGoogleAnalyticsSdkUsageType"))

    val REMOVE_FABRIC_NDK_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.fabricCrashlyticsRefactoringProcessor.removeFabricNdkUsageType"))
    val ADD_FIREBASE_CRASHLYTICS_NDK_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.fabricCrashlyticsRefactoringProcessor.addFirebaseCrashlyticsNdkUsageType"))
    val REMOVE_CRASHLYTICS_ENABLE_NDK_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.fabricCrashlyticsRefactoringProcessor.removeCrashlyticsEnableNdkUsageType"))
    val ADD_FIREBASE_CRASHLYTICS_NATIVE_SYMBOL_UPLOAD_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.fabricCrashlyticsRefactoringProcessor.addFirebaseCrashlyticsNativeSymbolUploadUsageType"))
  }
}

class RemoveFabricMavenRepositoryUsageInfo(
  element: WrappedPsiElement,
  private val repositories: RepositoriesModel,
  private val repository: RepositoryModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    repositories.removeRepository(repository)
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.removeFabricMavenRepositoryUsageInfo.tooltipText")
}

// TODO(xof): investigate unifying this with the NoGMavenUsageInfo class above

class AddGoogleMavenRepositoryUsageInfo(
  element: WrappedPsiElement,
  private val repositories: RepositoriesModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    repositories.addGoogleMavenRepository()
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.addGoogleMavenRepositoryUsageInfo.tooltipText")
}

class RemoveFabricClasspathDependencyUsageInfo(
  element: WrappedPsiElement,
  private val dependencies: DependenciesModel,
  private val dependency: DependencyModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    dependencies.remove(dependency)
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.removeFabricClasspathDependencyUsageInfo.tooltipText")
}

class AddGoogleServicesClasspathDependencyUsageInfo(
  element: WrappedPsiElement,
  private val dependencies: DependenciesModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    // TODO(xof): how to find the current version?  Or the version contemporaneous with this AGP/Studio?
    dependencies.addArtifact("classpath", "com.google.gms:google-services:4.3.3")
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.addGoogleServicesClasspathDependencyUsageInfo.tooltipText")
}

class AddFirebaseCrashlyticsClasspathDependencyUsageInfo(
  element: WrappedPsiElement,
  private val dependencies: DependenciesModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    // TODO(xof): how to find the current version?  Or the version contemporaneous with this AGP/Studio?
    dependencies.addArtifact("classpath", "com.google.firebase:firebase-crashlytics-gradle:2.3.0")
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.addFirebaseCrashlyticsClasspathDependencyUsageInfo.tooltipText")
}

class ReplaceFabricPluginUsageInfo(
  element: WrappedPsiElement,
  private val plugin: PluginModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    plugin.name().setValue("com.google.firebase.crashlytics")
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.replaceFabricPluginUsageInfo.tooltipText")
}

class ApplyGoogleServicesPluginUsageInfo(
  element: WrappedPsiElement,
  private val model: GradleBuildModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    model.applyPlugin("com.google.gms.google-services")
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.applyGoogleServicesPluginUsageInfo.tooltipText")
}

class RemoveFabricCrashlyticsSdkUsageInfo(
  element: WrappedPsiElement,
  private val dependencies: DependenciesModel,
  private val dependency: DependencyModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    dependencies.remove(dependency)
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.removeFabricCrashlyticsSdkUsageInfo.tooltipText")
}

class AddFirebaseCrashlyticsSdkUsageInfo(
  element: WrappedPsiElement,
  private val dependencies: DependenciesModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    dependencies.addArtifact("implementation", "com.google.firebase:firebase-crashlytics:17.2.1")
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.addFirebaseCrashlyticsSdkUsageInfo.tooltipText")
}

class AddGoogleAnalyticsSdkUsageInfo(
  element: WrappedPsiElement,
  private val dependencies: DependenciesModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    dependencies.addArtifact("implementation", "com.google.firebase:firebase-analytics:17.5.0")
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.addGoogleAnalyticsSdkUsageInfo.tooltipText")
}

class RemoveFabricNdkUsageInfo(
  element: WrappedPsiElement,
  private val dependencies: DependenciesModel,
  private val dependency: DependencyModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    dependencies.remove(dependency)
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.removeFabricNdkUsageInfo.tooltipText")
}

class AddFirebaseCrashlyticsNdkUsageInfo(
  element: WrappedPsiElement,
  private val dependencies: DependenciesModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    dependencies.addArtifact("implementation", "com.google.firebase:firebase-crashlytics-ndk:17.2.1")
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.addFirebaseCrashlyticsNdkUsageInfo.tooltipText")
}

class RemoveCrashlyticsEnableNdkUsageInfo(
  element: WrappedPsiElement,
  private val model: GradleBuildModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    model.crashlytics().enableNdk().delete()
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.removeCrashlyticsEnableNdkUsageInfo.tooltipText")
}

class AddBuildTypeFirebaseCrashlyticsUsageInfo(
  element: WrappedPsiElement,
  private val buildType: BuildTypeModel
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    buildType.firebaseCrashlytics().nativeSymbolUploadEnabled().setValue(true)
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.addBuildTypeFirebaseCrashlyticsUsageInfo.tooltipText")
}
