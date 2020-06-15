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

import com.android.tools.idea.projectsystem.*
import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile

interface SourceProviderManager {
  companion object {
    @JvmStatic
    fun getInstance(facet: AndroidFacet) = facet.sourceProviders

    /**
     * Replaces the instances of SourceProviderManager for the given [facet] with a test stub based on a single source set [sourceSet].
     *
     * NOTE: The test instance is automatically discarded on any relevant change to the [facet].
     */
    @JvmStatic
    fun replaceForTest(facet: AndroidFacet, disposable: Disposable, sourceSet: NamedIdeaSourceProvider) =
      SourceProviders.replaceForTest(facet, disposable, sourceSet)

    /**
     * Replaces the instances of SourceProviderManager for the given [facet] with a test stub based on a [manifestFile] only.
     *
     * NOTE: The test instance is automatically discarded on any relevant change to the [facet].
     */
    @JvmStatic
    fun replaceForTest(facet: AndroidFacet, disposable: Disposable, manifestFile: VirtualFile?) =
      SourceProviders.replaceForTest(facet, disposable, manifestFile)
  }
}

@Deprecated("Moved. Use com.android.tools.idea.projectsystem.SourceProvidersImpl")
class SourceProvidersImpl(
  override val mainIdeaSourceProvider: NamedIdeaSourceProvider,
  override val currentSourceProviders: List<NamedIdeaSourceProvider>,
  override val currentUnitTestSourceProviders: List<NamedIdeaSourceProvider>,
  override val currentAndroidTestSourceProviders: List<NamedIdeaSourceProvider>,
  override val allSourceProviders: List<NamedIdeaSourceProvider>,

  @Suppress("OverridingDeprecatedMember")
  override val mainAndFlavorSourceProviders: List<NamedIdeaSourceProvider>
) : SourceProviders {
  override val sources: IdeaSourceProvider = createMergedSourceProvider(currentSourceProviders)
  override val unitTestSources: IdeaSourceProvider = createMergedSourceProvider(currentUnitTestSourceProviders)
  override val androidTestSources: IdeaSourceProvider = createMergedSourceProvider(currentAndroidTestSourceProviders)
}

