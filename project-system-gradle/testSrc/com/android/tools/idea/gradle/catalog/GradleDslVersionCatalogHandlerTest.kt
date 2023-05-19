/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.catalog

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.psi.PsiManager

class GradleDslVersionCatalogHandlerTest: AndroidGradleTestCase()  {
  fun testGetVersionCatalogFiles() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)
    val catalogMap = GradleDslVersionCatalogHandler().getVersionCatalogFiles(project)

    Truth.assertThat(catalogMap).hasSize(2)
    Truth.assertThat(catalogMap.values.map { it.exists() }.all { it }).isTrue()
    Truth.assertThat(catalogMap.values.map { it.name }).isEqualTo(listOf("libs.versions.toml", "libsTest.versions.toml"))

    Truth.assertThat(catalogMap.keys).isEqualTo(setOf("libs", "libsTest"))
  }

  fun testExternallyHandledExtension() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)
    val handler = GradleDslVersionCatalogHandler()
    val catalogAliases = handler.getExternallyHandledExtension(project)

    Truth.assertThat(catalogAliases).isEqualTo(setOf("libs", "libsTest"))
  }

  fun testGetAccessorSmoke() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)

    val root = StandardFileSystems.local().findFileByPath(project.basePath!!)!!
    val source = root.findFileByRelativePath("gradle/libs.versions.toml")!!
    val psiFile = PsiManager.getInstance(project).findFile(source)!!
    val handler = GradleDslVersionCatalogHandler()

    val accessor = handler.getAccessorClass(psiFile, "libs")
    Truth.assertThat(accessor.methods.map { it.name }.toSet()).isEqualTo(setOf("getPlugins", "getVersions", "getBundles"))
  }
}