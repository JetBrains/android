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
package com.android.tools.idea.lint.model

import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.sync.internal.ProjectDumper
import com.android.tools.idea.gradle.project.sync.internal.head
import com.android.tools.idea.gradle.project.sync.internal.prop
import com.android.tools.idea.projectsystem.isHolderModule
import com.android.tools.lint.model.LintModelAndroidArtifact
import com.android.tools.lint.model.LintModelAndroidLibrary
import com.android.tools.lint.model.LintModelArtifact
import com.android.tools.lint.model.LintModelDependencies
import com.android.tools.lint.model.LintModelDependency
import com.android.tools.lint.model.LintModelExternalLibrary
import com.android.tools.lint.model.LintModelJavaArtifact
import com.android.tools.lint.model.LintModelLibrary
import com.android.tools.lint.model.LintModelLintOptions
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.LintModelModuleLibrary
import com.android.tools.lint.model.LintModelSourceProvider
import com.android.tools.lint.model.LintModelVariant
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import java.io.File

fun ProjectDumper.dumpLintModels(project: Project) {
  nest(File(project.basePath!!), "PROJECT") {
    ModuleManager.getInstance(project)
      .modules
      .sortedBy { it.name }
      .forEach { module ->
        head("MODULE") { module.name }
        nest {
          val androidModuleModel = GradleAndroidModel.get(module)
          // Skip all but holders to prevent needless spam in the snapshots. All modules
          // point to the same facet.
          if (module.isHolderModule() && androidModuleModel != null) {
            val lintModelModule =
              LintModelFactory()
                .create(
                  androidModuleModel.androidProject,
                  androidModuleModel.variants,
                  androidModuleModel.rootDirPath,
                  deep = true
                )
            dump(lintModelModule)
          }
        }
      }
  }
}

private fun ProjectDumper.dump(lintModelModule: LintModelModule) {
  prop("Dir") { lintModelModule.dir.path.toPrintablePath() }
  prop("ModulePath") { lintModelModule.modulePath }
  prop("Type") { lintModelModule.type.toString() }
  prop("MavenName") { lintModelModule.mavenName?.toString() }
  prop("AGPVersion") { lintModelModule.agpVersion?.toString()?.replaceKnownPaths() }
  prop("BuildFolder") { lintModelModule.buildFolder.path.toPrintablePath() }
  lintModelModule.lintRuleJars.forEach { prop("- LintRuleJars") { it.path.toPrintablePath() } }
  prop("ResourcePrefix") { lintModelModule.resourcePrefix }
  lintModelModule.dynamicFeatures.forEach { prop("- DynamicFeatures") { it } }
  lintModelModule.bootClassPath.forEach {
    prop("- BootClassPath") { it.path.toPrintablePath().replaceCurrentSdkVersion() }
  }
  prop("JavaSourceLevel") { lintModelModule.javaSourceLevel }
  prop("CompileTarget") { lintModelModule.compileTarget.replaceCurrentSdkVersion() }
  this.dump(lintModelModule.lintOptions)
  lintModelModule.variants.forEach { dump(it) }
}

private fun ProjectDumper.dump(lintOptions: LintModelLintOptions) {
  head("LintOptions")
  nest {
    lintOptions.disable.forEach { prop("- Disable") { it } }
    lintOptions.enable.forEach { prop("- Enable") { it } }
    lintOptions.check?.forEach { prop("- Check") { it } }
    prop("AbortOnError") { lintOptions.abortOnError.takeIf { it }?.toString() }
    prop("AbsolutePaths") { lintOptions.absolutePaths.takeIf { it }?.toString() }
    prop("NoLines") { lintOptions.noLines.takeIf { it }?.toString() }
    prop("Quiet") { lintOptions.quiet.takeIf { it }?.toString() }
    prop("CheckAllWarnings") { lintOptions.checkAllWarnings.takeIf { it }?.toString() }
    prop("IgnoreWarnings") { lintOptions.ignoreWarnings.takeIf { it }?.toString() }
    prop("WarningsAsErrors") { lintOptions.warningsAsErrors.takeIf { it }?.toString() }
    prop("CheckTestSources") { lintOptions.checkTestSources.takeIf { it }?.toString() }
    prop("IgnoreTestSources") { lintOptions.ignoreTestSources.takeIf { it }?.toString() }
    prop("CheckGeneratedSources") { lintOptions.checkGeneratedSources.takeIf { it }?.toString() }
    prop("ExplainIssues") { lintOptions.explainIssues.takeIf { it }?.toString() }
    prop("ShowAll") { lintOptions.showAll.takeIf { it }?.toString() }
    prop("LintConfig") { lintOptions.lintConfig?.path?.toPrintablePath() }
    prop("TextReport") { lintOptions.textReport.takeIf { it }?.toString() }
    prop("TextOutput") { lintOptions.textOutput?.path?.toPrintablePath() }
    prop("HtmlReport") { lintOptions.htmlReport.takeIf { it }?.toString() }
    prop("HtmlOutput") { lintOptions.htmlOutput?.path?.toPrintablePath() }
    prop("XmlReport") { lintOptions.xmlReport.takeIf { it }?.toString() }
    prop("XmlOutput") { lintOptions.xmlOutput?.path?.toPrintablePath() }
    prop("CheckReleaseBuilds") { lintOptions.checkReleaseBuilds.takeIf { it }?.toString() }
    prop("CheckDependencies") { lintOptions.checkDependencies.takeIf { it }?.toString() }
    prop("BaselineFile") { lintOptions.baselineFile?.path?.toPrintablePath() }
    if (lintOptions.severityOverrides.orEmpty().isNotEmpty()) {
      head("SeverityOverrides")
      nest {
        lintOptions.severityOverrides?.forEach { key, value -> prop(key) { value.toString() } }
      }
    }
  }
}

private fun ProjectDumper.dump(lintModelVariant: LintModelVariant) {
  with(lintModelVariant) {
    head("LintModelVariant") { name }
    nest {
      head("BuildFeatures")
      nest {
        prop("ViewBinding") { lintModelVariant.buildFeatures.viewBinding.toString() }
        prop("CoreLibraryDesugaringEnabled") {
          lintModelVariant.buildFeatures.coreLibraryDesugaringEnabled.toString()
        }
        prop("NamespacingMode") { lintModelVariant.buildFeatures.namespacingMode.toString() }
      }
    }
    nest {
      prop("UseSupportLibraryVectorDrawables") {
        useSupportLibraryVectorDrawables.takeIf { it }?.toString()
      }
      head("MainArtifact")
      nest { dump(mainArtifact) }
      testArtifact?.let { testArtifact ->
        head("TestArtifact")
        nest { dump(testArtifact) }
      }
      androidTestArtifact?.let { androidTestArtifact -> dump(androidTestArtifact) }
      testFixturesArtifact?.let { testFixturesArtifact ->
        head("TestFixturesArtifact")
        nest { dump(testFixturesArtifact) }
      }
      prop("Package") { `package` }
      prop("MinSdkVersion") { minSdkVersion?.toString() }
      prop("TargetSdkVersion") { targetSdkVersion?.toString()?.replaceCurrentSdkVersion() }
      if (resValues.isNotEmpty()) {
        head("ResValues")
        nest { resValues.forEach { (key, value) -> prop(key) { value.toString() } } }
      }
      if (manifestPlaceholders.isNotEmpty()) {
        head("ManifestPlaceholders")
        nest { manifestPlaceholders.forEach { (key, value) -> prop(key) { value.toString() } } }
      }
      resourceConfigurations.forEach { prop("- ResourceConfigurations") { it } }
      proguardFiles.forEach { prop("- ProguardFiles") { it.path.toPrintablePath() } }
      consumerProguardFiles.forEach {
        prop("- ConsumerProguardFiles") { it.path.toPrintablePath() }
      }
      if (sourceProviders.isNotEmpty()) {
        head("SourceProviders")
        nest { sourceProviders.forEach { dump(it) } }
      }
      if (testSourceProviders.isNotEmpty()) {
        head("TestSourceProviders")
        nest { testSourceProviders.forEach { dump(it) } }
      }
      if (testFixturesSourceProviders.isNotEmpty()) {
        head("TestFixturesSourceProviders")
        nest { testFixturesSourceProviders.forEach { dump(it) } }
      }
      prop("Debuggable") { debuggable.takeIf { it }?.toString() }
      prop("Shrinkable") { shrinkable.takeIf { it }?.toString() }

      head("LibraryResolver")
      nest { libraryResolver.getAllLibraries().sortedBy { it.identifier }.forEach { dump(it) } }
    }
  }
}

private fun ProjectDumper.dump(lintModelAndroidArtifact: LintModelAndroidArtifact) {
  with(lintModelAndroidArtifact) {
    prop("ApplicationId") { applicationId }
    generatedResourceFolders.sorted().forEach {
      prop("- GeneratedResourceFolders") { it.path.toPrintablePath() }
    }
    generatedSourceFolders.sorted().forEach {
      prop("- GeneratedSourceFolders") { it.path.toPrintablePath() }
    }
    desugaredMethodsFiles.sorted().forEach {
      prop("- DesugaredMethodFiles") { it.path.toPrintablePath() }
    }
  }
  dump(lintModelAndroidArtifact as LintModelArtifact)
}

private fun ProjectDumper.dump(lintModelJavaArtifact: LintModelJavaArtifact) {
  dump(lintModelJavaArtifact as LintModelArtifact)
}

private fun ProjectDumper.dump(lintModelArtifact: LintModelArtifact) {
  with(lintModelArtifact) {
    head("Dependencies")
    nest { dump(dependencies) }
    classOutputs
      .sortedBy { it.path.toPrintablePath() }
      .forEach { prop("- ClassOutputs") { it.path.toPrintablePath() } }
  }
}

private fun ProjectDumper.dump(lintModelDependencies: LintModelDependencies) {
  fun dump(dependency: LintModelDependency) {
    prop(dependency.artifactName.replaceKnownPaths()) {
      "${dependency.requestedCoordinates?.replaceKnownPaths()} => ${dependency.identifier.replaceKnownPaths()}"
    }
    nest {
      dependency.dependencies.sortedBy { it.artifactName.replaceKnownPaths() }.forEach { dump(it) }
    }
  }

  with(lintModelDependencies) {
    head("CompileDependencies")
    nest {
      compileDependencies.roots
        .sortedBy { it.artifactName.replaceKnownPaths() }
        .forEach { dump(it) }
    }
    head("PackageDependencies")
    nest {
      packageDependencies.roots
        .sortedBy { it.artifactName.replaceKnownPaths() }
        .forEach { dump(it) }
    }
  }
}

private fun ProjectDumper.dump(lintModelSourceProvider: LintModelSourceProvider) {
  with(lintModelSourceProvider) {
    manifestFiles.forEach { prop("- ManifestFiles") { it.path.toPrintablePath() } }
    javaDirectories.forEach { prop("- JavaDirectories") { it.path.toPrintablePath() } }
    resDirectories.forEach { prop("- ResDirectories") { it.path.toPrintablePath() } }
    assetsDirectories.forEach { prop("- AssetsDirectories") { it.path.toPrintablePath() } }
  }
}

private fun ProjectDumper.dump(lintModelLibrary: LintModelLibrary) {
  with(lintModelLibrary) {
    head("LintModelLibrary") { toString().replaceKnownPaths() }
    nest {
      (this@with as? LintModelExternalLibrary)?.jarFiles?.forEach {
        prop("- JarFiles") { it.path.toPrintablePath() }
      }
      prop("Identifier") { identifier.replaceKnownPaths() }
      if (this@with is LintModelAndroidLibrary) {
        prop("Manifest") { manifest.path.toPrintablePath() }
        prop("Folder") { folder?.path?.toPrintablePath() }
        prop("ResFolder") { resFolder.path.toPrintablePath() }
        prop("AssetsFolder") { assetsFolder.path.toPrintablePath() }
      }
      prop("LintJar") { lintJar?.path?.toPrintablePath() }
      if (this@with is LintModelAndroidLibrary) {
        prop("PublicResources") { publicResources.path.toPrintablePath() }
        prop("SymbolFile") { symbolFile.path.toPrintablePath() }
        prop("ExternalAnnotations") { externalAnnotations.path.toPrintablePath() }
        prop("ProguardRules") { proguardRules.path.toPrintablePath() }
      }
      prop("ProjectPath") { (this@with as? LintModelModuleLibrary)?.projectPath }
      prop("ResolvedCoordinates") {
        (this@with as? LintModelExternalLibrary)
          ?.resolvedCoordinates
          ?.toString()
          ?.replaceKnownPaths()
      }
      prop("Provided") { provided.takeIf { it }?.toString() }
    }
  }
}
