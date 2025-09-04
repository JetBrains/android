/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.findVariant
import com.android.tools.idea.gradle.structure.model.android.psTestWithProject
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.util.toIoFile
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.RunsInEdt
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class CatalogDependencyManagementTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testCatalogScopesForVersionVariable() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY_CATALOG)
    projectRule.psTestWithProject(preparedProject) {
      val appModule = project.findModuleByName("moduleCatalog") as PsAndroidModule

      val dependency = appModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
      val scope = dependency!![0].versionScope()
      assertThat(scope.getVariable("coreVersion")?.value, equalTo("0.9.1".asParsed<Any>()))
      assertThat(scope.getVariable("anotherVersion")?.value, equalTo("0.9.2".asParsed<Any>()))
      assertThat(scope.getVariable("wrongVersion")?.value, equalTo("wrongVersion".asParsed<Any>()))

      assertThat(
        scope.map { it.name }.toSet(),
        equalTo(
          setOf("coreVersion", "anotherVersion", "wrongVersion")))

      val dependency2 = appModule.dependencies.findLibraryDependency("com.example.jlib:lib4:0.6")
      val scope2 = dependency2!![0].versionScope()
      // expecting variables for compact notation as well as we can do literal to map transformation on the fly
      assertThat(
        scope2.map { it.name }.toSet(),
        equalTo(
          setOf("coreVersion", "anotherVersion", "wrongVersion")))
    }
  }

  @Test
  fun testVariablesScopesForDependencyInRoot() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY_CATALOG)
    projectRule.psTestWithProject(preparedProject) {
      val appModule = project.findModuleByName("moduleCatalog") as PsAndroidModule
      val dependency = appModule.dependencies.findLibraryDependency("com.android.support:appcompat-v7:+")
      val scope = dependency!![0].versionScope()
      assertThat(scope.getVariable("var06")?.value, equalTo("0.6".asParsed<Any>()))
      assertThat(scope.getVariable("var10")?.value, equalTo("1.0".asParsed<Any>()))
      // this will be filtered out as value is not in set of all possible library versions
      assertThat(scope.getVariable("varLib")?.value, equalTo("com.android.support:appcompat-v7:+".asParsed<Any>()))
      assertThat(
        scope.map { it.name }.toSet(),
        equalTo(
          setOf("var06", "var10", "varLib")))
    }
  }

  @Test
  fun testAddCatalogLibraryDependency() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY_CATALOG)
    projectRule.psTestWithProject(preparedProject) {
      var module = project.findModuleByName("moduleCatalog") as PsAndroidModule
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
      module.addLibraryDependency("com.example.libs:lib1:1.0".asParsed(), "implementation")
      assertThat(module.isModified, equalTo(true))
      assertThat(project.isModified, equalTo(true))
      val dependency = module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      assertThat(dependency, notNullValue())
      val buildDependencyModel = dependency!![0]
      assertThat(buildDependencyModel, notNullValue())
      assertThat((buildDependencyModel.parsedModel.completeModel().rawElement as GradleDslLiteral).unresolvedValue, equalTo("libs.lib1"))

      val catalogModel = project.parsedModel.versionCatalogsModel.getVersionCatalogModel("libs")!!
      val declaration = catalogModel.libraryDeclarations().getAll()["lib1"]
      assertThat(declaration, notNullValue())
      assertThat(declaration!!.compactNotation(), equalTo("com.example.libs:lib1:1.0"))


      run {
        val resolvedDependencies = module.findVariant("release")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
      }

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      // check if we have lib1 declared in catalog with version declared in versions section
      val updatedCatalogModel = project.parsedModel.versionCatalogsModel.getVersionCatalogModel("libs")!!
      val libsText = VfsUtil.loadText(updatedCatalogModel.virtualFile)
      assertThat(libsText, containsString("{ group = \"com.example.libs\", name = \"lib1\", version.ref = \""))
      val matcher = "\\{ group = \"com.example.libs\", name = \"lib1\", version.ref = \"(.*)\" }".toRegex().find(libsText)
      assertThat(matcher, notNullValue())
      assertThat(matcher!!.groupValues.size, equalTo(2))
      assertThat(libsText, containsString("${matcher.groupValues[1]} = \"1.0\""))

      module = project.findModuleByName("moduleCatalog") as PsAndroidModule
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())

      run {
        val resolvedDependencies = module.findVariant("release")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
      }

      val catalogModel2 = project.parsedModel.versionCatalogsModel.getVersionCatalogModel("libs")!!
      val declaration2 = catalogModel2.libraryDeclarations().getAll()["lib1"]
      assertThat(declaration2, notNullValue())
      assertThat(declaration2!!.compactNotation(), equalTo("com.example.libs:lib1:1.0"))

    }
  }

  @Test
  fun testAddCatalogLibraryDependencyWhenLibraryDeclaredInCatalog() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY_CATALOG)
    projectRule.psTestWithProject(preparedProject) {
      val catalogModel = project.parsedModel.versionCatalogsModel.getVersionCatalogModel("libs")!!
      catalogModel.virtualFile.toIoFile().appendText("\nlib1O_old = \"com.example.libs:lib1:1.0\"\n")
      requestSyncAndWait()
      reparse()

      var module = project.findModuleByName("moduleCatalog") as PsAndroidModule
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
      module.addLibraryDependency("com.example.libs:lib1:1.0".asParsed(), "implementation")
      assertThat(module.isModified, equalTo(true))
      assertThat(project.isModified, equalTo(true))
      val dependency = module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      assertThat(dependency, notNullValue())
      val buildDependencyModel = dependency!![0]
      assertThat(buildDependencyModel, notNullValue())
      assertThat((buildDependencyModel.parsedModel.completeModel().rawElement as GradleDslLiteral).unresolvedValue, equalTo("libs.lib1O.old"))

      assertThat(catalogModel.libraryDeclarations().getAll()["lib1"], nullValue())

      run {
        val resolvedDependencies = module.findVariant("release")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
      }

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      module = project.findModuleByName("moduleCatalog") as PsAndroidModule
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())

      run {
        val resolvedDependencies = module.findVariant("release")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
      }

      assertThat(catalogModel.libraryDeclarations().getAll()["lib1"], nullValue())
    }
  }

  @Test
  fun testCatalogDependencyCanExtractVariableIsFalse() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
      val module = project.findModuleByName("app") as PsAndroidModule
      run {
        val resolvedDependencies = module.findVariant("release")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        val catalogDep = resolvedDependencies?.findLibraryDependencies("com.google.guava", "guava")?.singleOrNull()?.declaredDependencies
        assertThat(catalogDep!!.size, equalTo(1))
        assertFalse(catalogDep[0].canExtractVariable())

        val plainDep = resolvedDependencies.findLibraryDependencies("com.android.support",
                                                                    "appcompat-v7").singleOrNull()?.declaredDependencies
        assertThat(plainDep!!.size, equalTo(1))
        assertTrue(plainDep[0].canExtractVariable())
      }
    }
  }
}