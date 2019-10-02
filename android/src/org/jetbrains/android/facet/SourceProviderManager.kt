/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android.facet

import com.android.builder.model.SourceProvider
import com.android.utils.reflection.qualifiedName
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NotNullLazyKey
import com.intellij.openapi.vfs.VirtualFile

interface SourceProviderManager {
  companion object {
    private val KEY: NotNullLazyKey<SourceProviderManager, AndroidFacet> = NotNullLazyKey.create(
      ::KEY.qualifiedName,
      ::SourceProviderManagerImpl
    )

    @JvmStatic
    fun getInstance(facet: AndroidFacet) = KEY.getValue(facet)

    @JvmStatic
    fun replaceForTest(facet: AndroidFacet,
                       disposable: Disposable,
                       mock: SourceProviderManager) {
      facet.putUserData(KEY, mock)
      Disposer.register(disposable, Disposable { facet.putUserData(KEY, null) })
    }
  }

  val mainIdeaSourceProvider: IdeaSourceProvider

  val mainManifestFile: VirtualFile?
}

private class SourceProviderManagerImpl(val facet: AndroidFacet) : SourceProviderManager {

  private var mainSourceSet: SourceProvider? = null
  private var mainIdeaSourceSet: IdeaSourceProvider? = null
  private var mainIdeaSourceSetCreatedFor: SourceProvider? = null

  /**
   * Returns the main source provider for the project. For projects that are not backed by a Gradle model, this method returns a
   * [SourceProvider] wrapper which provides information about the old project.
   */
  private val mainSourceProvider: SourceProvider
    get() {
      return facet.configuration.model?.defaultSourceProvider
             ?: mainSourceSet
             ?: LegacySourceProvider(facet).also { mainSourceSet = it }
    }

  override val mainIdeaSourceProvider: IdeaSourceProvider
    get() {
      if (!facet.requiresAndroidModel()) {
        if (mainIdeaSourceSet == null) {
          mainIdeaSourceSet = IdeaSourceProvider.createForLegacyProject(facet)
          mainIdeaSourceSetCreatedFor = null
        }
      }
      else {
        val mainSourceSet = mainSourceProvider
        if (mainIdeaSourceSet == null || mainIdeaSourceSetCreatedFor != mainSourceSet) {
          mainIdeaSourceSet = IdeaSourceProvider.toIdeaProvider(mainSourceSet)
          mainIdeaSourceSetCreatedFor = mainSourceSet
        }
      }

      return mainIdeaSourceSet!!
    }

  override val mainManifestFile: VirtualFile? get() {
    // When opening a project, many parts of the IDE will try to read information from the manifest. If we close the project before
    // all of this finishes, we may end up creating disposable children of an already disposed facet. This is a rather hard problem in
    // general, but pretending there was no manifest terminates many code paths early.
    return if (facet.isDisposed) null else return mainIdeaSourceProvider.manifestFile
  }
}
