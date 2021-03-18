/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.project

import com.android.SdkConstants
import com.android.SdkConstants.FD_RES
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.SdkConstants.FN_RESOURCE_STATIC_LIBRARY
import com.android.SdkConstants.FN_RESOURCE_TEXT
import com.android.ide.common.repository.GradleCoordinate
import com.android.manifmerger.ManifestSystemProperty
import com.android.projectmodel.ExternalLibrary
import com.android.projectmodel.Library
import com.android.projectmodel.RecursiveResourceFolder
import com.android.tools.idea.apk.ApkFacet
import com.android.tools.idea.model.AndroidManifestIndex
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.queryPackageNameFromManifestIndex
import com.android.tools.idea.navigator.getSubmodules
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.CapabilityNotSupported
import com.android.tools.idea.projectsystem.CapabilityStatus
import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.CodeShrinker
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.ManifestOverrides
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.projectsystem.SampleDataDirectoryProvider
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.MainContentRootSampleDataDirectoryProvider
import com.android.tools.idea.run.AndroidDeviceSpec
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.FileSystemApkProvider
import com.android.tools.idea.run.NonGradleApkProvider
import com.android.tools.idea.run.NonGradleApplicationIdProvider
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.util.toPathString
import com.android.utils.reflection.qualifiedName
import com.google.common.collect.ImmutableList
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.util.text.nullize
import org.jetbrains.android.dom.manifest.cachedValueFromPrimaryManifest
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import java.io.File
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private val PACKAGE_NAME = Key.create<CachedValue<String?>>("merged.manifest.package.name")
private val LOG: Logger get() = logger<DefaultModuleSystem>()

/** Creates a map for the given pairs, filtering out null values. */
private fun <K, V> notNullMapOf(vararg pairs: Pair<K, V?>): Map<K, V> {
  return pairs.asSequence()
    .filter { it.second != null }
    .toMap() as Map<K, V>
}

class DefaultModuleSystem(override val module: Module) :
  AndroidModuleSystem,
  ClassFileFinder by ModuleBasedClassFileFinder(module),
  SampleDataDirectoryProvider by MainContentRootSampleDataDirectoryProvider(module) {

  override fun canRegisterDependency(type: DependencyType): CapabilityStatus {
    return CapabilityNotSupported()
  }

  override fun registerDependency(coordinate: GradleCoordinate) {
    registerDependency(coordinate, DependencyType.IMPLEMENTATION)
  }

  override fun registerDependency(coordinate: GradleCoordinate, type: DependencyType) {
  }

  override fun getRegisteredDependency(coordinate: GradleCoordinate): GradleCoordinate? = null

  override fun getResolvedDependency(coordinate: GradleCoordinate): GradleCoordinate? {
    // TODO(b/79883422): Replace the following code with the correct logic for detecting .aar dependencies.
    // The following if / else if chain maintains previous support for supportlib and appcompat until
    // we can determine it's safe to take away.
    if (SdkConstants.SUPPORT_LIB_ARTIFACT == "${coordinate.groupId}:${coordinate.artifactId}") {
      val entries = ModuleRootManager.getInstance(module).orderEntries
      for (orderEntry in entries) {
        if (orderEntry is LibraryOrderEntry) {
          val classes = orderEntry.getRootFiles(OrderRootType.CLASSES)
          for (file in classes) {
            if (file.name == "android-support-v4.jar") {
              return GoogleMavenArtifactId.SUPPORT_V4.getCoordinate("+")
            }
          }
        }
      }
    }
    else if (SdkConstants.APPCOMPAT_LIB_ARTIFACT == "${coordinate.groupId}:${coordinate.artifactId}") {
      val entries = ModuleRootManager.getInstance(module).orderEntries
      for (orderEntry in entries) {
        if (orderEntry is ModuleOrderEntry) {
          val moduleForEntry = orderEntry.module
          if (moduleForEntry == null || moduleForEntry == module) {
            continue
          }
          AndroidFacet.getInstance(moduleForEntry) ?: continue
          val packageName = moduleForEntry.getModuleSystem().getPackageName()
          if ("android.support.v7.appcompat" == packageName) {
            return GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
          }
        }
      }
    }

    return null
  }

  // We don't offer maven artifact support for JPS projects because there aren't any use cases that requires this feature.
  // JPS also import their dependencies as modules and don't translate very well to the original maven artifacts.
  override fun analyzeDependencyCompatibility(dependenciesToAdd: List<GradleCoordinate>)
    : Triple<List<GradleCoordinate>, List<GradleCoordinate>, String> {
    return Triple(emptyList(), dependenciesToAdd, "")
  }

  override fun getResourceModuleDependencies() = AndroidUtils.getAllAndroidDependencies(module, true).map(AndroidFacet::getModule)

  override fun getDirectResourceModuleDependents(): List<Module> = ModuleManager.getInstance(module.project).getModuleDependentModules(module)

  override fun getResolvedLibraryDependencies(includeExportedTransitiveDeps: Boolean): Collection<Library> {
    val libraries = mutableListOf<Library>()

    val orderEnumerator = ModuleRootManager.getInstance(module)
      .orderEntries()
      .librariesOnly()

    if (includeExportedTransitiveDeps) {
      orderEnumerator.recursively().exportedOnly()
    }

    orderEnumerator.forEachLibrary { library ->
        // Typically, a library xml looks like the following:
        //     <CLASSES>
        //      <root url="file://$USER_HOME$/.gradle/caches/transforms-1/files-1.1/appcompat-v7-27.1.1.aar/e2434af65905ee37277d482d7d20865d/res" />
        //      <root url="jar://$USER_HOME$/.gradle/caches/transforms-1/files-1.1/appcompat-v7-27.1.1.aar/e2434af65905ee37277d482d7d20865d/jars/classes.jar!/" />
        //    </CLASSES>
        val roots = library.getFiles(OrderRootType.CLASSES)

        // all libraries are assumed to have a non-empty name
        val classesJar = roots.firstOrNull { it.name == SdkConstants.FN_CLASSES_JAR }?.toPathString()
        val libraryName = library.name ?: return@forEachLibrary true

        // For testing purposes we create libraries with a res.apk root (legacy projects don't have those). Recognize them here and
        // create ExternalLibrary as necessary.
        val resFolderRoot = roots.firstOrNull { it.name == FD_RES }?.toPathString()
        val resApkRoot = roots.firstOrNull { it.name == FN_RESOURCE_STATIC_LIBRARY }?.toPathString()
        val (resFolder, resApk) = when {
          resApkRoot != null -> Pair(resApkRoot.parentOrRoot.resolve(FD_RES), resApkRoot)
          resFolderRoot != null -> Pair(resFolderRoot, resFolderRoot.parentOrRoot.resolve(FN_RESOURCE_STATIC_LIBRARY))
          else -> return@forEachLibrary true
        }

        libraries.add(ExternalLibrary(
          address = libraryName,
          manifestFile = resFolder.parentOrRoot.resolve(FN_ANDROID_MANIFEST_XML),
          classJars = if (classesJar == null) emptyList() else listOf(classesJar),
          resFolder = RecursiveResourceFolder(resFolder),
          symbolFile = resFolder.parentOrRoot.resolve(FN_RESOURCE_TEXT),
          resApkFile = resApk
        ))

        true // continue processing.
    }

    return ImmutableList.copyOf(libraries)
  }

  override fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate> {
    return emptyList()
  }

  override fun canGeneratePngFromVectorGraphics(): CapabilityStatus {
    return CapabilityNotSupported()
  }

  override fun getPackageName(): String? {
    val facet = AndroidFacet.getInstance(module) ?: return null
    var rawPackageName: String? = null

    if (AndroidManifestIndex.indexEnabled()) {
      rawPackageName = DumbService.getInstance(module.project)
        .runReadActionInSmartMode(Computable { getPackageNameFromIndex(facet) })
    }

    return rawPackageName ?: getPackageNameByParsingPrimaryManifest(facet)
  }

  override fun getApplicationIdProvider(runConfiguration: RunConfiguration): ApplicationIdProvider {
    return NonGradleApplicationIdProvider(
      AndroidFacet.getInstance(module) ?: throw IllegalStateException("Cannot find AndroidFacet. Module: ${module.name}"))
  }

  override fun getNotRuntimeConfigurationSpecificApplicationIdProviderForLegacyUse(): ApplicationIdProvider {
    return NonGradleApplicationIdProvider(
      AndroidFacet.getInstance(module) ?: throw IllegalStateException("Cannot find AndroidFacet. Module: ${module.name}"))
  }

  override fun getApkProvider(runConfiguration: RunConfiguration, targetDeviceSpec: AndroidDeviceSpec?): ApkProvider? {
    val forTests: Boolean = (runConfiguration as? AndroidRunConfigurationBase)?.isTestConfiguration ?: false
    val facet = AndroidFacet.getInstance(module)!!
    val applicationIdProvider = getApplicationIdProvider(runConfiguration)
    if (forTests) {
      return NonGradleApkProvider(facet, applicationIdProvider, null)
    }
    val apkFacet = ApkFacet.getInstance(module)
    return when {
      apkFacet != null -> FileSystemApkProvider(apkFacet.module, File(apkFacet.configuration.APK_PATH))
      runConfiguration is AndroidRunConfiguration -> NonGradleApkProvider(facet, applicationIdProvider, runConfiguration.ARTIFACT_NAME)
      else -> null
    }
  }

  private fun getPackageNameByParsingPrimaryManifest(facet: AndroidFacet): String? {
    val cachedValue = facet.cachedValueFromPrimaryManifest {
      packageName.nullize(true)
    }
    return facet.putUserDataIfAbsent(PACKAGE_NAME, cachedValue).value
  }

  private fun getPackageNameFromIndex(facet: AndroidFacet): String? {
    if (DumbService.isDumb(module.project)) {
      return null
    }
    return try {
      facet.queryPackageNameFromManifestIndex()
    }
    catch (e: IndexNotReadyException) {
      // TODO(147116755): runReadActionInSmartMode doesn't work if we already have read access.
      //  We need to refactor the callers of this to require a *smart*
      //  read action, at which point we can remove this try-catch.
      LOG.debug(e)
      null
    }
  }

  override fun getManifestOverrides(): ManifestOverrides {
    val androidModel = module.androidFacet?.let(AndroidModel::get) ?: return ManifestOverrides()
    val directOverrides = notNullMapOf(
      ManifestSystemProperty.MIN_SDK_VERSION to androidModel.minSdkVersion?.apiString,
      ManifestSystemProperty.TARGET_SDK_VERSION to androidModel.targetSdkVersion?.apiString,
      ManifestSystemProperty.VERSION_CODE to androidModel.versionCode?.takeIf { it > 0 }?.toString(),
      ManifestSystemProperty.PACKAGE to androidModel.applicationId
    )
    return ManifestOverrides(directOverrides)
  }

  override fun getResolveScope(scopeType: ScopeType): GlobalSearchScope {
    val includeTests = when (scopeType) {
      ScopeType.MAIN -> false
      ScopeType.ANDROID_TEST, ScopeType.UNIT_TEST -> true
      else -> error("unknown scope type")
    }
    return module.getModuleWithDependenciesAndLibrariesScope(includeTests)
  }

  override val submodules: Collection<Module>
    get() = getSubmodules(module.project, module)

  private companion object Keys {
    val usesCompose: Key<Boolean> = Key.create(::usesCompose.qualifiedName)
    val isRClassTransitive: Key<Boolean> = Key.create(::isRClassTransitive.qualifiedName)
    val codeShrinker: Key<CodeShrinker?> = Key.create(::codeShrinker.qualifiedName)
    val isMlModelBindingEnabled: Key<Boolean> = Key.create(::isMlModelBindingEnabled.qualifiedName)
  }

  override var usesCompose: Boolean by UserData(Keys.usesCompose, false)

  override var isRClassTransitive: Boolean by UserData(Keys.isRClassTransitive, true)

  override var codeShrinker: CodeShrinker? by UserData(Keys.codeShrinker, null)

  override var isMlModelBindingEnabled: Boolean by UserData(Keys.isMlModelBindingEnabled, false)
}

/**
 * Property delegate that uses the facet's user data to store information.
 *
 * During testing, this allows us to enable IDE features which are not supported by JPS builds but can still be tested without relying on
 * the Gradle model.
 */
private class UserData<T>(
  val key: Key<T>,
  val defaultValue: T
) : ReadWriteProperty<DefaultModuleSystem, T> {

  override fun getValue(thisRef: DefaultModuleSystem, property: KProperty<*>): T {
    return thisRef.module.androidFacet?.getUserData(key) ?: defaultValue
  }

  override fun setValue(thisRef: DefaultModuleSystem, property: KProperty<*>, value: T) {
    val facet = thisRef.module.androidFacet ?: error("Not an Android module")
    facet.putUserData(key, value)
  }
}
