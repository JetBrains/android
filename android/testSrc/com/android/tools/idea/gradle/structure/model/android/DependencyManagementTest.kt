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
package com.android.tools.idea.gradle.structure.model.android

import com.android.builder.model.AndroidProject.ARTIFACT_MAIN
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.testing.TestProjectPaths.PSD_DEPENDENCY
import com.intellij.openapi.project.Project
import org.hamcrest.CoreMatchers.*
import org.junit.Assert.assertThat

class DependencyManagementTest : DependencyTestCase() {

  private lateinit var resolvedProject: Project
  private lateinit var project: PsProject

  override fun setUp() {
    super.setUp()
    loadProject(PSD_DEPENDENCY)
    reparse()
  }

  private fun reparse() {
    resolvedProject = myFixture.project
    project = PsProject(resolvedProject)
  }

  fun testParsedDependencies() {
    run {
      val appModule = project.findModuleByName("app") as PsAndroidModule
      assertThat(appModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
      assertThat(appModule.dependencies.findModuleDependency(":mainModule"), notNullValue())
      val libModule = project.findModuleByName("mainModule") as PsAndroidModule
      val lib10 = libModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      val lib091 = libModule.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1")
      assertThat(lib10.testScopes(), hasItems("implementation", "debugImplementation"))
      assertThat(lib091.testScopes(), hasItems("releaseImplementation"))
      assertThat(libModule.dependencies.findLibraryDependency("com.example.libs:lib2:1.0"), nullValue())
      assertThat(libModule.dependencies.findLibraryDependency("com.example.jlib:lib3:1.0"), nullValue())
      assertThat(libModule.dependencies.findLibraryDependency("com.example.jlib:lib4:1.0"), nullValue())
    }
    run {
      val libModule = project.findModuleByName("modulePlus") as PsAndroidModule
      val lib1 = libModule.dependencies.findLibraryDependency("com.example.libs:lib1:0.+")
      assertThat(lib1.testScopes(), hasItems("implementation"))
    }
  }

  fun testResolvedDependencies() {
    val libModule = project.findModuleByName("mainModule") as PsAndroidModule

    run {
      val artifact = libModule.findVariant("paidDebug")!!.findArtifact(ARTIFACT_MAIN)
      val dependencies = PsAndroidArtifactDependencyCollection(artifact!!)
      val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      val lib2 = dependencies.findLibraryDependency("com.example.libs:lib2:1.0")
      val lib3 = dependencies.findLibraryDependency("com.example.jlib:lib3:1.0")
      val lib4 = dependencies.findLibraryDependency("com.example.jlib:lib4:1.0")
      assertThat(lib1.testDeclared(), hasItems(true))
      assertThat(lib2.testDeclared(), hasItems(false))
      assertThat(lib3.testDeclared(), hasItems(false))
      assertThat(lib4.testDeclared(), hasItems(false))
    }

    run {
      val artifact = libModule.findVariant("paidRelease")!!.findArtifact(ARTIFACT_MAIN)
      val dependencies = PsAndroidArtifactDependencyCollection(artifact!!)
      val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      val lib2 = dependencies.findLibraryDependency("com.example.libs:lib2:1.0")
      val lib3 = dependencies.findLibraryDependency("com.example.jlib:lib3:1.0")
      val lib4 = dependencies.findLibraryDependency("com.example.jlib:lib4:1.0")
      assertThat(lib1.testDeclared(), hasItems(true))
      assertThat(lib2.testDeclared(), hasItems(false))
      assertThat(lib3.testDeclared(), hasItems(false))
      assertThat(lib4.testDeclared(), hasItems(false))
    }
  }

  fun testParsedModelMatching() {
    val libModule = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(libModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())

    val artifact = libModule.findVariant("paidDebug")!!.findArtifact(ARTIFACT_MAIN)
    val dependencies = PsAndroidArtifactDependencyCollection(artifact!!)
    val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
    assertThat(lib1.testDeclared(), hasItems(true))
    assertThat(lib1.testScopes(), hasItems("implementation:debugImplementation"))
  }

  fun testPromotedParsedModelMatching() {
    val libModule = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(libModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
    assertThat(libModule.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1"), notNullValue())

    val artifact = libModule.findVariant("paidRelease")!!.findArtifact(ARTIFACT_MAIN)
    val dependencies = PsAndroidArtifactDependencyCollection(artifact!!)
    val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
    assertThat(lib1.testDeclared(), hasItems(true))
    // Despite requesting a different version the 'releaseImplementation' configuration should be included in the promoted
    // version of the resolved dependency since it is where it tries to contribute to.
    assertThat(lib1.testScopes(), hasItems("implementation:releaseImplementation"))
  }

  fun testPlusParsedModelMatching() {
    val libModule = project.findModuleByName("modulePlus") as PsAndroidModule
    assertThat(libModule.dependencies.findLibraryDependency("com.example.libs:lib1:0.+"), notNullValue())

    val artifact = libModule.findVariant("release")!!.findArtifact(ARTIFACT_MAIN)
    val dependencies = PsAndroidArtifactDependencyCollection(artifact!!)
    val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1")
    assertThat(lib1.testDeclared(), hasItems(true))
    assertThat(lib1.testScopes(), hasItems("implementation"))
  }

  fun testParsedDependencyPromotions() {
    val libModule = project.findModuleByName("mainModule") as PsAndroidModule
    run {
      val lib1 = libModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      val lib2 = libModule.dependencies.findLibraryDependency("com.example.libs:lib2:1.0")
      val lib3 = libModule.dependencies.findLibraryDependency("com.example.jlib:lib3:1.0")
      val lib4 = libModule.dependencies.findLibraryDependency("com.example.jlib:lib4:1.0")
      assertThat(lib1.testDeclared(), hasItems(true))
      assertThat(lib1.testHasPromotedVersion(), hasItems(false))
      assertThat(lib2, nullValue())
      assertThat(lib3, nullValue())
      assertThat(lib4, nullValue())
    }
    run {
      val artifact = libModule.findVariant("paidRelease")!!.findArtifact(ARTIFACT_MAIN)
      val dependencies = PsAndroidArtifactDependencyCollection(artifact!!)
      val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      val lib2 = dependencies.findLibraryDependency("com.example.libs:lib2:1.0")
      val lib3 = dependencies.findLibraryDependency("com.example.jlib:lib3:1.0")
      val lib4 = dependencies.findLibraryDependency("com.example.jlib:lib4:1.0")
      assertThat(lib1.testDeclared(), hasItems(true))
      assertThat(lib2.testDeclared(), hasItems(false))
      assertThat(lib3.testDeclared(), hasItems(false))
      assertThat(lib4.testDeclared(), hasItems(false))
      assertThat(lib1.testHasPromotedVersion(), hasItems(true))
      assertThat(lib2.testHasPromotedVersion(), hasItems(false))
      assertThat(lib3.testHasPromotedVersion(), hasItems(false))
      assertThat(lib4.testHasPromotedVersion(), hasItems(false))
    }
    run {
      val artifact = libModule.findVariant("paidDebug")!!.findArtifact(ARTIFACT_MAIN)
      val dependencies = PsAndroidArtifactDependencyCollection(artifact!!)
      val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      val lib2 = dependencies.findLibraryDependency("com.example.libs:lib2:1.0")
      val lib3 = dependencies.findLibraryDependency("com.example.jlib:lib3:1.0")
      val lib4 = dependencies.findLibraryDependency("com.example.jlib:lib4:1.0")
      assertThat(lib1, notNullValue())
      assertThat(lib2, notNullValue())
      assertThat(lib3, notNullValue())
      assertThat(lib4, notNullValue())
      assertThat(lib1.testDeclared(), hasItems(true))
      assertThat(lib2.testDeclared(), hasItems(false))
      assertThat(lib3.testDeclared(), hasItems(false))
      assertThat(lib4.testDeclared(), hasItems(false))
      assertThat(lib1.testHasPromotedVersion(), hasItems(false))
      assertThat(lib2.testHasPromotedVersion(), hasItems(false))
      assertThat(lib3.testHasPromotedVersion(), hasItems(false))
      assertThat(lib4.testHasPromotedVersion(), hasItems(false))
    }
  }

  fun testAddLibraryDependency() {
    var module = project.findModuleByName("moduleA") as PsAndroidModule
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
    module.addLibraryDependency("com.example.libs:lib1:1.0", listOf("implementation"))
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())

    module.addLibraryDependency("com.example.libs:lib2:1.0", listOf("implementation"))
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib2:1.0"), notNullValue())

    project.applyChanges()
    requestSyncAndWait()
    reparse()

    module = project.findModuleByName("moduleA") as PsAndroidModule
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
    assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib2:1.0"), notNullValue())
  }

  fun testAddModuleDependency() {
    var module = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(module.dependencies.findModuleDependency(":moduleA"), nullValue())
    module.addModuleDependency(":moduleA", listOf("implementation"))
    assertThat(module.dependencies.findModuleDependency(":moduleA"), notNullValue())

    module.addModuleDependency(":moduleB", listOf("implementation"))
    assertThat(module.dependencies.findModuleDependency(":moduleA"), notNullValue())
    assertThat(module.dependencies.findModuleDependency(":moduleB"), notNullValue())

    project.applyChanges()
    requestSyncAndWait()
    reparse()

    module = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(module.dependencies.findModuleDependency(":moduleA"), notNullValue())
    assertThat(module.dependencies.findModuleDependency(":moduleB"), notNullValue())
  }

  fun testAddJavaModuleDependency() {
    var module = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(module.dependencies.findModuleDependency(":jModuleK"), nullValue())
    module.addModuleDependency(":jModuleK", listOf("implementation"))
    assertThat(module.dependencies.findModuleDependency(":jModuleK"), notNullValue())

    project.applyChanges()
    requestSyncAndWait()
    reparse()

    module = project.findModuleByName("mainModule") as PsAndroidModule
    assertThat(module.dependencies.findModuleDependency(":jModuleK"), notNullValue())
  }

  // TODO(solodkyy): Implement support for Java to Java module dependencies.
  fun /*test*/AddJavaModuleDependencyToJavaModule() {
//    var module = project.findModuleByName("jModuleK") as PsJavaModule
//    assertThat(module.findModuleDependency(":jModuleL"), nullValue())
//    module.addModuleDependency(":jModuleL", listOf("implementation"))
//    assertThat(module.findModuleDependency(":jModuleL"), notNullValue())
//
//    project.applyChanges()
//    requestSyncAndWait()
//    reparse()
//
//    module = project.findModuleByName("jModuleL") as PsJavaModule
//    assertThat(module.findModuleDependency(":jModuleL"), notNullValue())
  }
}

private fun PsAndroidDependencyCollection.findLibraryDependency(compactNotation: String): List<PsLibraryAndroidDependency>? =
  PsArtifactDependencySpec.create(compactNotation)?.let { spec ->
    findLibraryDependencies(
      spec.group,
      spec.name
    )
      .filter { it.spec.version == spec.version }
      .let { if (it.isEmpty()) null else it }
  }

private fun List<PsLibraryAndroidDependency>?.testScopes(): List<String> = orEmpty().map { it.configurationNames.joinToString(":") }
private fun List<PsLibraryAndroidDependency>?.testDeclared() : List<Boolean> = orEmpty().map { it.isDeclared }
private fun List<PsLibraryAndroidDependency>?.testHasPromotedVersion() : List<Boolean> = orEmpty().map { it.hasPromotedVersion() }
