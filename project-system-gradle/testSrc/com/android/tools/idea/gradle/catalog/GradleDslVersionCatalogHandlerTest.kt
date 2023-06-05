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
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.psi.PsiManager
import com.intellij.testFramework.findReferenceByText
import junit.framework.TestCase
import junit.framework.TestCase.assertNotNull

class GradleDslVersionCatalogHandlerTest: AndroidGradleTestCase()  {
  fun testGetVersionCatalogFiles() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)
    val catalogMap = GradleDslVersionCatalogHandler().getVersionCatalogFiles(project)

    assertThat(catalogMap).hasSize(2)
    assertThat(catalogMap.values.map { it.exists() }.all { it }).isTrue()
    assertThat(catalogMap.values.map { it.name }).isEqualTo(listOf("libs.versions.toml", "libsTest.versions.toml"))

    assertThat(catalogMap.keys).isEqualTo(setOf("libs", "libsTest"))
  }

  fun testExternallyHandledExtension() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)
    val handler = GradleDslVersionCatalogHandler()
    val catalogAliases = handler.getExternallyHandledExtension(project)

    assertThat(catalogAliases).isEqualTo(setOf("libs", "libsTest"))
  }

  fun testGetAccessorSmoke() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)

    val root = StandardFileSystems.local().findFileByPath(project.basePath!!)!!
    val source = root.findFileByRelativePath("app/build.gradle")!!
    val psiFile = PsiManager.getInstance(project).findFile(source)!!
    val context = psiFile.findReferenceByText("libs.guava")
    assertNotNull(context)

    val handler = GradleDslVersionCatalogHandler()

    val accessor = handler.getAccessorClass(psiFile, "libs")
    val names = accessor.methods.map { it.name }.toSet()
    assertThat(names).contains("getGuava")
    assertThat(names).doesNotContain("getJunit") // from second catalog
  }

  fun testGetAccessorDependenciesSecondCatalog() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)

    val root = StandardFileSystems.local().findFileByPath(project.basePath!!)!!
    val source = root.findFileByRelativePath("app/build.gradle")!!
    val psiFile = PsiManager.getInstance(project).findFile(source)!!
    val context = psiFile.findReferenceByText("libs.guava")
    assertNotNull(context)

    val handler = GradleDslVersionCatalogHandler()

    val accessor = handler.getAccessorClass(psiFile, "libsTest")
    val names = accessor.methods.map { it.name }.toSet()
    assertThat(names).contains("getJunit")
    assertThat(names).doesNotContain("getGuava")
  }
}