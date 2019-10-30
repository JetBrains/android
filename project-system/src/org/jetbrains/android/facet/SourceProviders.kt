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

import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.intellij.openapi.vfs.VirtualFile

/**
 * A project service providing access to various collections of source providers of a project.
 *
 * See [SourceProviderManager] to get instances of [SourceProviders].
 */
interface SourceProviders {

  val mainIdeaSourceProvider: IdeaSourceProvider

  val mainManifestFile: VirtualFile?

  /**
   * Returns a list of source providers, in the overlay order (meaning that later providers
   * override earlier providers when they redefine resources) for the currently selected variant.
   *
   * The overlay source order is defined by the underlying build system.
   */
  val currentSourceProviders: List<IdeaSourceProvider>

  /**
   * Returns a list of source providers for all test artifacts (e.g. both `test/` and `androidTest/` source sets), in increasing
   * precedence order.
   *
   * @see currentSourceProviders
   */
  val currentTestSourceProviders: List<IdeaSourceProvider>

  /**
   * Returns a list of all IDEA source providers, for the given facet, in the overlay order
   * (meaning that later providers override earlier providers when they redefine resources.)
   *
   *
   * Note that the list will never be empty; there is always at least one source provider.
   *
   *
   * The overlay source order is defined by the underlying build system.
   *
   * This method should be used when only on-disk source sets are required. It will return
   * empty source sets for all other source providers (since VirtualFiles MUST exist on disk).
   */
  val allSourceProviders: List<IdeaSourceProvider>

  /**
   * Returns a list of source providers which includes the main source provider and
   * product flavor specific source providers.
   *
   * DEPRECATED: This is method is added here to support android-kotlin-extensions which
   * for compatibility reasons require this particular subset of source providers.
   */
  @Deprecated("Do not use. This is unlikely to be what anybody needs.")
  val mainAndFlavorSourceProviders: List<IdeaSourceProvider>
}