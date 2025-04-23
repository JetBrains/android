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
import com.android.projectmodel.ExternalAndroidLibrary
import com.android.projectmodel.ExternalLibraryImpl
import com.android.projectmodel.RecursiveResourceFolder
import com.android.tools.idea.apk.ApkFacet
import com.android.tools.idea.flags.StudioFlags.ENABLE_APK_PROJECT_SYSTEM
import com.android.tools.idea.model.queryPackageNameFromManifestIndex
import com.android.tools.idea.navigator.getSubmodules
import com.android.tools.idea.projectsystem.AndroidModulePathsImpl
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.CodeShrinker
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.ManifestOverrides
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.projectsystem.SampleDataDirectoryProvider
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.rendering.StudioModuleDependencies
import com.android.tools.idea.res.AndroidDependenciesCache
import com.android.tools.idea.res.MainContentRootSampleDataDirectoryProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.NonGradleApplicationIdProvider
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.util.toPathString
import com.android.tools.idea.util.uiSafeRunReadActionInSmartMode
import com.android.tools.module.ModuleDependencies
import com.android.utils.reflection.qualifiedName
import com.google.common.collect.ImmutableList
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil.getTextByBinaryPresentation
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.text.nullize
import org.jetbrains.android.facet.AndroidFacet
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private val PACKAGE_NAME_KEY = Key.create<CachedValue<String?>>("main.manifest.package.name")
private val LOG: Logger get() = Logger.getInstance("DefaultModuleSystem.kt")

class DefaultModuleSystem(override val module: Module) :
  AndroidModuleSystem,
  SampleDataDirectoryProvider by MainContentRootSampleDataDirectoryProvider(module) {

  override val moduleClassFileFinder: ClassFileFinder = ProductionModuleClassFileFinder(module)

  override fun registerDependency(coordinate: GradleCoordinate, type: DependencyType) = Unit

  override fun getRegisteredDependency(coordinate: GradleCoordinate): GradleCoordinate? = null

  override fun getResolvedDependency(coordinate: GradleCoordinate, scope: DependencyScopeType): GradleCoordinate? = null

  override fun getResourceModuleDependencies() = AndroidDependenciesCache.getAllAndroidDependencies(module, true).map(AndroidFacet::getModule)

  override fun getDirectResourceModuleDependents(): List<Module> = ModuleManager.getInstance(module.project).getModuleDependentModules(module)

  override fun getAndroidLibraryDependencies(scope: DependencyScopeType): Collection<ExternalAndroidLibrary> {
    val libraries = mutableListOf<ExternalAndroidLibrary>()

    ModuleRootManager.getInstance(module)
      .orderEntries()
      .librariesOnly()
      // Don't iterate *all* project modules *recursively*, as this is O(n*n) complexity, where n is the modules count.
      .recursively()
      .exportedOnly()
      .forEachLibrary { library ->
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

        libraries.add(ExternalLibraryImpl(
          address = libraryName,
          manifestFile = resFolder.parentOrRoot.resolve(FN_ANDROID_MANIFEST_XML),
          resFolder = RecursiveResourceFolder(resFolder),
          symbolFile = resFolder.parentOrRoot.resolve(FN_RESOURCE_TEXT),
          resApkFile = resApk
        ))

        true // continue processing.
      }

    return ImmutableList.copyOf(libraries)
  }

  override fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate> =
    listOf(
      NamedModuleTemplate(
        "main",
        AndroidModulePathsImpl(
          ModuleRootManager.getInstance(module).sourceRoots.first().parent.toIoFile(),
          null,
          ModuleRootManager.getInstance(module).sourceRoots.first().toIoFile(),
          null,
          null,
          null,
          emptyList(),
          emptyList()
        )
      )
    )

  override fun getPackageName(): String? {
    return getPackageName(module)
  }

  override fun getApplicationIdProvider(): ApplicationIdProvider {
    return NonGradleApplicationIdProvider(
      AndroidFacet.getInstance(module) ?: throw IllegalStateException("Cannot find AndroidFacet. Module: ${module.name}"))
  }

  override fun getManifestOverrides(): ManifestOverrides {
    return ManifestOverrides()
  }

  override fun getResolveScope(scopeType: ScopeType): GlobalSearchScope {
    val includeTests = when (scopeType) {
      ScopeType.MAIN, ScopeType.TEST_FIXTURES -> false
      ScopeType.ANDROID_TEST, ScopeType.UNIT_TEST, ScopeType.SCREENSHOT_TEST -> true
    }
    return module.getModuleWithDependenciesAndLibrariesScope(includeTests)
  }

  override val submodules: Collection<Module>
    get() = getSubmodules(module.project, module)

  private companion object Keys {
    val usesCompose: Key<Boolean> = Key.create(::usesCompose.qualifiedName<DefaultModuleSystem>())
    val isRClassTransitive: Key<Boolean> = Key.create(::isRClassTransitive.qualifiedName<DefaultModuleSystem>())
    val codeShrinker: Key<CodeShrinker?> = Key.create(::codeShrinker.qualifiedName<DefaultModuleSystem>())
    val isMlModelBindingEnabled: Key<Boolean> = Key.create(::isMlModelBindingEnabled.qualifiedName<DefaultModuleSystem>())
    val applicationRClassConstantIds: Key<Boolean> = Key.create(::applicationRClassConstantIds.qualifiedName<DefaultModuleSystem>())
    val testRClassConstantIds: Key<Boolean> = Key.create(::testRClassConstantIds.qualifiedName<DefaultModuleSystem>())
    val useAndroidX: Key<Boolean> = Key.create(::useAndroidX.qualifiedName<DefaultModuleSystem>())
  }

  override var usesCompose: Boolean by UserData(Keys.usesCompose, false)

  override var isRClassTransitive: Boolean by UserData(Keys.isRClassTransitive, true)

  override var codeShrinker: CodeShrinker? by UserData(Keys.codeShrinker, null)

  override var isMlModelBindingEnabled: Boolean by UserData(Keys.isMlModelBindingEnabled, false)

  override val isDebuggable: Boolean
    get() = when {
      ENABLE_APK_PROJECT_SYSTEM.get() -> false
      else -> ApkFacet.getInstance(module)?.configuration?.DEBUGGABLE == "true"
    }

  override var applicationRClassConstantIds: Boolean by UserData(Keys.applicationRClassConstantIds, true)

  override var testRClassConstantIds: Boolean by UserData(Keys.testRClassConstantIds, true)

  override var useAndroidX: Boolean by UserData(Keys.useAndroidX, false)

  override val moduleDependencies: ModuleDependencies
    get() = StudioModuleDependencies(module)
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

fun getPackageName(module: Module): String? {
  val facet = AndroidFacet.getInstance(module) ?: return null
  // Reading from indexes may be slow and in non-blocking read actions we prefer to give priority to
  // write actions.
  ProgressManager.checkCanceled()
  return uiSafeRunReadActionInSmartMode(module.project) { getPackageNameFromIndex(facet) }
         ?: getPackageNameByParsingPrimaryManifest(facet)
}

private fun getPackageNameFromIndex(facet: AndroidFacet): String? {
  if (DumbService.isDumb(facet.module.project)) {
    return null
  }
  return try {
    facet.queryPackageNameFromManifestIndex()
  }
  catch (e: IndexNotReadyException) {
    // TODO(147116755): runReadActionInSmartMode doesn't work if we already have read access.
    //                  We need to refactor the callers of this to require a *smart* read
    //                  action, at which point we can remove this try-catch.
    LOG.debug(e)
    null
  }
}

private fun getPackageNameByParsingPrimaryManifest(facet: AndroidFacet): String? {
  val manifestFile = SourceProviderManager.getInstance(facet).mainManifestFile ?: return null
  val cachedValue: CachedValue<String?> = CachedValuesManager.getManager(facet.module.project).createCachedValue {
    val packageName = readPackageNameFromManifest(manifestFile)
    return@createCachedValue CachedValueProvider.Result.create(packageName, manifestFile)
  }
  return facet.putUserDataIfAbsent(PACKAGE_NAME_KEY, cachedValue).value
}

private fun readPackageNameFromManifest(manifestFile: VirtualFile): String? {
  try {
    val parser = KXmlParser().apply {
      setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
      setInput(StringReader(runCancellableReadAction { getText(manifestFile) }))
    }
    if (parser.nextTag() == XmlPullParser.START_TAG) {
      return parser.getAttributeValue(null, "package").nullize(nullizeSpaces = true)
    }
  }
  catch (e: Exception) {
    LOG.warn(e)
  }
  return null
}

/**
 * Returns potentially unsaved contents of [manifestFile].
 */
private fun getText(manifestFile: VirtualFile): String {
  val document = FileDocumentManager.getInstance().getCachedDocument(manifestFile)
                 ?: return getTextByBinaryPresentation(manifestFile.contentsToByteArray(), manifestFile).toString()
  return document.text
}

private fun <T> runCancellableReadAction(computable: Computable<T>): T {
  if (ApplicationManager.getApplication().isReadAccessAllowed) {
    return computable.compute()
  }
  return ReadAction.nonBlocking(computable::compute).executeSynchronously()
}
