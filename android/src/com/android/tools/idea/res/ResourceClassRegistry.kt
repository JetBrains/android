/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceRepository
import com.android.tools.res.ResourceRepositoryManager
import com.android.tools.res.ids.ResourceClassGenerator
import com.android.tools.res.ids.ResourceIdManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TimeoutCachedValue
import org.jetbrains.annotations.TestOnly
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val PACKAGE_TIMEOUT = 5.minutes

/** Returns whether this is an R class or one of its inner classes. */
private fun String.isRClassName() = endsWith(".R") || substringAfterLast('.').startsWith("R$")

/** A project-wide registry for class lookup of resource classes (R classes). */
@Service(Service.Level.PROJECT)
class ResourceClassRegistry @TestOnly constructor(private val packageTimeout: Duration) {
  constructor() : this(PACKAGE_TIMEOUT)

  private val repoMap = WeakHashMap<ResourceRepository, ResourceRepositoryInfo>()
  private var packages: TimeoutCachedValue<Set<String>> = createPackageCache()

  /**
   * Adds definition of a new R class to the registry. The R class will contain resources from the
   * given repo in the given namespace and will be generated when the [findClassDefinition] is
   * called with a class name that matches the [packageName] and the `repo` resource repository can
   * be found in the [StudioResourceRepositoryManager] passed to [findClassDefinition].
   *
   * Note that the [ResourceClassRegistry] is a project-level component, so the same R class may be
   * generated in different ways depending on the repository used. In non-namespaced project, the
   * repository is the full [AppResourceRepository] of the module in question. In namespaced
   * projects the repository is a [com.android.resources.aar.AarResourceRepository] of just the AAR
   * contents.
   */
  fun addLibrary(
    repo: ResourceRepository,
    idManager: ResourceIdManager,
    packageName: String?,
    namespace: ResourceNamespace,
  ) {
    if (packageName.isNullOrEmpty()) return
    var info = repoMap[repo]
    if (info == null) {
      info = ResourceRepositoryInfo(repo, idManager, namespace)
      repoMap[repo] = info
      // Explicit cleanup for Disposable instead of waiting for GC.
      if (repo is Disposable && !Disposer.tryRegister(repo) { removeRepository(repo) }) {
        removeRepository(repo)
        return
      }
    }
    info.packages.add(packageName)
    packages = createPackageCache() // Invalidate cache.
  }

  /** Looks up a class definition for the given name, if possible */
  fun findClassDefinition(
    className: String,
    repositoryManager: ResourceRepositoryManager,
  ): ByteArray? {
    if (!className.isRClassName()) return null
    val pkg = className.substringBeforeLast(".", "")
    if (pkg in packages.get()) {
      val namespace = ResourceNamespace.fromPackageName(pkg)
      val repositories = repositoryManager.getAppResourcesForNamespace(namespace)
      return findClassGenerator(repositories, className)?.generate(className)
    }
    return null
  }

  /**
   * Ideally, this method would not exist. But there are potential bugs in the caching mechanism.
   * So, the method should be called when rendering fails due to hard-to-explain causes like
   * NoSuchFieldError.
   *
   * @see ResourceIdManager.resetDynamicIds
   */
  fun clearCache() {
    repoMap.clear()
    packages = createPackageCache()
  }

  private fun findClassGenerator(
    repositories: List<ResourceRepository>,
    className: String,
  ): ResourceClassGenerator? {
    return repositories
      .asSequence()
      .mapNotNull { repoMap[it]?.resourceClassGenerator }
      .reduceOrNull { _, _ ->
        // There is a package name collision between libraries. Throw NoClassDefFoundError
        // exception.
        throw NoClassDefFoundError(
          "$className class could not be loaded because of package name collision between libraries"
        )
      }
  }

  private fun createPackageCache() =
    TimeoutCachedValue(packageTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS) {
      buildSet { repoMap.values.forEach { this.addAll(it.packages) } }
    }

  private fun removeRepository(repo: ResourceRepository) {
    repoMap.remove(repo)
    packages = createPackageCache()
  }

  private class ResourceRepositoryInfo(
    repo: ResourceRepository,
    idManager: ResourceIdManager,
    namespace: ResourceNamespace,
  ) {
    val resourceClassGenerator = ResourceClassGenerator.create(idManager, repo, namespace)
    val packages = mutableSetOf<String>()
  }

  companion object {
    /** Lazily instantiates a registry with the target project. */
    @JvmStatic fun get(project: Project): ResourceClassRegistry = project.service()
  }
}
