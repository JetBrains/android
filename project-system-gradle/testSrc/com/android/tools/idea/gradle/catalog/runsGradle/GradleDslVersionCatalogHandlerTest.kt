/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.catalog.runsGradle

import com.android.tools.idea.gradle.catalog.GradleDslVersionCatalogHandler
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.writeText
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.light.LightClass
import com.intellij.psi.util.PropertyUtilBase.getPropertyName
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.findReferenceByText
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class GradleDslVersionCatalogHandlerTest  {

  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()
  private val project get() = projectRule.project

  @Test
  fun testGetVersionCatalogFiles() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)
    val catalogMap = GradleDslVersionCatalogHandler().getVersionCatalogFiles(project)

    assertThat(catalogMap).hasSize(2)
    assertThat(catalogMap.values.map { it.exists() }.all { it }).isTrue()
    assertThat(catalogMap.values.map { it.name }).isEqualTo(listOf("libs.versions.toml", "libsTest.versions.toml"))

    assertThat(catalogMap.keys).isEqualTo(setOf("libs", "libsTest"))
  }

  @Test
  fun testExternallyHandledExtension() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)
    val handler = GradleDslVersionCatalogHandler()
    val catalogAliases = handler.getExternallyHandledExtension(project)

    assertThat(catalogAliases).isEqualTo(setOf("libs", "libsTest"))
  }

  @Test
  fun testGetAccessorDependenciesMultiCatalog() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)

    val root = StandardFileSystems.local().findFileByPath(project.basePath!!)!!
    val source = root.findFileByRelativePath("app/build.gradle")!!
    val psiFile = PsiManager.getInstance(project).findFile(source)!!
    val context = psiFile.findReferenceByText("libs.guava")
    assertNotNull(context)

    val handler = GradleDslVersionCatalogHandler()

    val accessor1 = handler.getAccessorClass(psiFile, "libs")!!
    val names1 = accessor1.methods.map { it.name }.toSet()
    assertThat(names1).contains("getGuava")
    assertThat(names1).doesNotContain("getJunit") // from libsTest

    val accessor2 = handler.getAccessorClass(psiFile, "libsTest")!!
    val names2 = accessor2.methods.map { it.name }.toSet()
    assertThat(names2).contains("getJunit")
    assertThat(names2).doesNotContain("getGuava") // from libs
  }

  @Test
  fun testGetAllAccessorsMultiCatalog() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)

    val root = StandardFileSystems.local().findFileByPath(project.basePath!!)!!
    val source = root.findFileByRelativePath("app/build.gradle")!!
    val psiFile = PsiManager.getInstance(project).findFile(source)!!
    val context = psiFile.findReferenceByText("libs.guava")
    assertNotNull(context)

    val handler = GradleDslVersionCatalogHandler()

    val accessor: PsiClass = handler.getAccessorClass(psiFile, "libs")!!
    val dependencies = extractDependenciesInGradleFormat(accessor)
    assertThat(dependencies).containsExactly("constraint.layout", "guava", "androidx.room.ktx",
                                             "plugins.android.application", "plugins.kotlinAndroid",
                                             "versions.constraint.layout", "versions.guava", "versions.gradlePlugins.agp",
                                             "bundles.both")
  }

  @Test
  fun testAccessorUnderscoreNotation() {
    testWithCustomCatalogFile("""
      [versions]
      constraint_layout = "1.0.2"
      gradlePlugins_agp = "8.0.0-beta01"

      [libraries]
      constraint_layout = { module = "com.android.support.constraint:constraint-layout", version.ref = "constraint_layout" }

      [plugins]
      android_application = { id = "com.android.application", version.ref = "gradlePlugins_agp" }

      [bundles]
      both_bundle = ["constraint_layout"]
    """.trimIndent(), setOf("constraint.layout",
                            "plugins.android.application",
                            "versions.constraint.layout", "versions.gradlePlugins.agp",
                            "bundles.both.bundle"))
  }

  @Test
  fun testAccessorHyphenNotation() {
    testWithCustomCatalogFile("""
      [versions]
      constraint-layout = "1.0.2"
      gradlePlugins-agp = "8.0.0-beta01"

      [libraries]
      constraint-layout = { module = "com.android.support.constraint:constraint-layout", version.ref = "constraint-layout" }

      [plugins]
      android-application = { id = "com.android.application", version.ref = "gradlePlugins-agp" }

      [bundles]
      both-bundle = ["constraint-layout"]
    """.trimIndent(), setOf("constraint.layout",
                            "plugins.android.application",
                            "versions.constraint.layout", "versions.gradlePlugins.agp",
                            "bundles.both.bundle"))
  }

  @Test
  fun testAccessorDotNotation() {
    testWithCustomCatalogFile("""
      [versions]
      constraint.layout = "1.0.2"
      gradlePlugins.agp = "8.0.0-beta01"

      [libraries]
      constraint.layout = { module = "com.android.support.constraint:constraint-layout", version.ref = "constraint.layout" }

      [plugins]
      android.application = { id = "com.android.application", version.ref = "gradlePlugins.agp" }

      [bundles]
      both.bundle = ["constraint.layout"]
    """.trimIndent(), setOf("constraint.layout",
                            "plugins.android.application", "versions.constraint.layout",
                            "versions.gradlePlugins.agp", "bundles.both.bundle"))
  }

  @Test
  fun testAccessorExtNotation() {
    testWithCustomCatalogFile("""
      [versions]
      constraint_layout = "1.0.2"
      gradlePlugins_agp = "8.0.0-beta01"

      [libraries]
      constraint = { module = "com.android.support.constraint:constraint-layout", version.ref = "constraint_layout" }
      constraint_ext = { module = "com.android.support.constraint:constraint-layout", version.ref = "constraint_layout" }

      [plugins]
      android.application = { id = "com.android.application", version.ref = "gradlePlugins_agp" }
      android.application_ext = { id = "com.android.application", version.ref = "gradlePlugins_agp" }
    """.trimIndent(), setOf("constraint", "constraint.ext",
                            "plugins.android.application", "plugins.android.application.ext",
                            "versions.constraint.layout", "versions.gradlePlugins.agp",
                            "bundles.both.bundle"))

  }

  private fun testWithCustomCatalogFile(catalogContent:String, dependencies:Set<String>){
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG)

    val root = StandardFileSystems.local().findFileByPath(project.basePath!!)!!
    val catalog = root.findFileByRelativePath("gradle/libs.versions.toml")!!
    ApplicationManager.getApplication().runWriteAction {
      catalog.writeText(catalogContent)
    }
    val source = root.findFileByRelativePath("app/build.gradle")!!
    val psiFile = PsiManager.getInstance(project).findFile(source)!!
    val context = psiFile.findReferenceByText("libs.guava")
    assertNotNull(context)

    val handler = GradleDslVersionCatalogHandler()

    val accessor: PsiClass = handler.getAccessorClass(psiFile, "libs")!!
    val dependencies = extractDependenciesInGradleFormat(accessor)
    assertThat(dependencies).containsExactly(*dependencies.toTypedArray())
  }

  private fun extractDependenciesInGradleFormat(accessor: PsiClass): List<String> {
    fun extract(type: PsiClass?, path: List<String>): List<String> {
      return if (type is LightClass) {
        val methods = type.methods.toList()
        val result = mutableListOf<String>()
        for (method in methods) {
          if (method.name == "asProvider") {
            result.add(path.joinToString (separator="." ))
          }
          else {
            result.addAll(extract(method.returnType.resolve(), path + listOf(getPropertyName(method.name)!!)))
          }
        }
        result
      }
      else {
        listOf(path.joinToString(separator = "."))
      }

    }
    return extract(accessor, listOf())
  }

}