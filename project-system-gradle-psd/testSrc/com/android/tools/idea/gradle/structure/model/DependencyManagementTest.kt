/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.ReverseDependency
import com.android.tools.idea.gradle.structure.model.android.findModuleDependency
import com.android.tools.idea.gradle.structure.model.android.findVariant
import com.android.tools.idea.gradle.structure.model.android.psTestWithProject
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.containers.nullize
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.CoreMatchers.sameInstance
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class DependencyManagementTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testParsedDependencies() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      run {
        val appModule = project.findModuleByName("app") as PsAndroidModule
        assertThat(appModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
        val moduleDependency = appModule.dependencies.findModuleDependency(":mainModule")
        assertThat(moduleDependency, notNullValue())
        assertThat(moduleDependency?.joinedConfigurationNames, equalTo("implementation"))
        val libModule = project.findModuleByName("mainModule") as PsAndroidModule
        val lib10 = libModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
        val lib091 = libModule.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1")
        assertThat(lib10.testDeclaredScopes(), hasItems("implementation", "debugImplementation"))
        assertThat(lib091.testDeclaredScopes(), hasItems("releaseImplementation"))
        assertThat(libModule.dependencies.findLibraryDependency("com.example.libs:lib2:1.0"), nullValue())
        assertThat(libModule.dependencies.findLibraryDependency("com.example.jlib:lib3:1.0"), nullValue())
        assertThat(libModule.dependencies.findLibraryDependency("com.example.jlib:lib4:1.0"), nullValue())
      }
      run {
        val libModule = project.findModuleByName("modulePlus") as PsAndroidModule
        val lib1 = libModule.dependencies.findLibraryDependency("com.example.libs:lib1:0.+")
        assertThat(lib1.testDeclaredScopes(), hasItems("releaseImplementation"))

        val module1 = libModule.dependencies.findModuleDependencies(":jModuleK")
        assertThat(module1.testDeclaredScopes(), hasItems("implementation"))
      }
      run {
        val jLibModule = project.findModuleByName("jModuleK") as PsJavaModule

        val lib1 = jLibModule.dependencies.findLibraryDependency("com.example.jlib:lib4:0.6")
        assertThat(lib1.testDeclaredScopes(), hasItems("implementation"))
        val lib2 = jLibModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
        assertThat(lib2.testDeclaredScopes(), hasItems("implementation"))

        val module1 = jLibModule.dependencies.findModuleDependencies(":jModuleL")
        assertThat(module1.testDeclaredScopes(), hasItems("implementation"))
      }
    }
  }

  @Test
  fun testParsedDependencies_variables() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      run {
        val libModule = project.findModuleByName("mainModule") as PsAndroidModule
        val lib306 = libModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.6")
        assertThat(lib306.testDeclaredScopes(), equalTo(listOf("freeImplementation")))
        val depLib306 = lib306!![0]
        assertThat<ParsedValue<String>>(depLib306.version, equalTo(ParsedValue.Set.Parsed("0.6", DslText.Reference("var06"))))
      }
      run {
        val libModule = project.findModuleByName("jModuleK") as PsJavaModule
        val lib3091 = libModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
        assertThat(lib3091.testDeclaredScopes(), equalTo(listOf("implementation")))
        val depLib3091 = lib3091!![0]
        assertThat(depLib3091.spec.compactNotation(), equalTo("com.example.jlib:lib3:0.9.1"))
        // TODO(b/111174250): Assert values of not yet existing properties.
      }
      run {
        val libModule = project.findModuleByName("jModuleL") as PsJavaModule
        val lib310 = libModule.dependencies.findLibraryDependency("com.example.jlib:lib3:1.0")
        assertThat(lib310.testDeclaredScopes(), equalTo(listOf("implementation")))
        val depLib310 = lib310!![0]
        assertThat(depLib310.spec.compactNotation(), equalTo("com.example.jlib:lib3:1.0"))
        // TODO(b/111174250): Assert values of not yet existing properties.
      }
    }
  }

  @Test
  fun testResolvedDependencies() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      val libModule = project.findModuleByName("mainModule") as PsAndroidModule
      val jLibModule = project.findModuleByName("jModuleK") as PsJavaModule

      run {
        val artifact = libModule.findVariant("paidDebug")!!.findArtifact(IdeArtifactName.MAIN)
        val dependencies = artifact!!.dependencies
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
        val artifact = libModule.findVariant("paidRelease")!!.findArtifact(IdeArtifactName.MAIN)
        val dependencies = artifact!!.dependencies
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
        val dependencies = jLibModule.resolvedDependencies
        val lib3 = dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
        val lib4old = dependencies.findLibraryDependency("com.example.jlib:lib4:0.6")
        val lib4new = dependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1")
        assertThat(lib3.testDeclared(), hasItems(true))
        assertThat(lib4old, nullValue())
        assertThat(lib4new.testDeclared(), hasItems(true))
      }
    }
  }

  @Test
  fun testResolvedJarDependencies() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      val libModule = project.findModuleByName("moduleA") as PsAndroidModule
      val jLibModule = project.findModuleByName("jModuleK") as PsJavaModule

      run {
        val artifact = libModule.findVariant("release")!!.findArtifact(IdeArtifactName.MAIN)
        val dependencies = artifact!!.dependencies
        val lib1 = dependencies.findJarDependencies("../lib/libsam1-1.1.jar").nullize()
        val lib2 = dependencies.findJarDependencies("../lib/libsam2-1.1.jar").nullize()
        assertThat(lib1, notNullValue())
        assertThat(lib2, notNullValue())
      }

      run {
        val dependencies = jLibModule.resolvedDependencies
        val lib = dependencies.findJarDependencies("libs/jarlib-1.1.jar").nullize()
        assertThat(lib, notNullValue())
      }
    }
  }

  @Test
  fun testParsedModelMatching() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      run {
        val libModule = project.findModuleByName("mainModule") as PsAndroidModule
        assertThat(libModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())

        val artifact = libModule.findVariant("paidDebug")!!.findArtifact(IdeArtifactName.MAIN)
        val dependencies = artifact!!.dependencies
        val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
        assertThat(lib1.testDeclared(), hasItems(true))
        assertThat(lib1.testMatchingScopes(), hasItems("implementation:debugImplementation"))
      }

      run {
        // TODO(b/110774403): Properly support test scopes in Java modules.
        val jLibModule = project.findModuleByName("jModuleM") as PsJavaModule
        assertThat(jLibModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), notNullValue())

        val dependencies = jLibModule.resolvedDependencies
        val lib3 = dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
        assertThat(lib3.testDeclared(), hasItems(true))
        assertThat(lib3.testMatchingScopes(), hasItems("implementation"))
      }
    }
  }

  @Test
  fun testPromotedParsedModelMatching() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      run {
        val libModule = project.findModuleByName("mainModule") as PsAndroidModule
        assertThat(libModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
        assertThat(libModule.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1"), notNullValue())

        val artifact = libModule.findVariant("paidRelease")!!.findArtifact(IdeArtifactName.MAIN)
        val dependencies = artifact!!.dependencies
        val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
        assertThat(lib1.testDeclared(), hasItems(true))
        // Despite requesting a different version the 'releaseImplementation' configuration should be included in the promoted
        // version of the resolved dependency since it is where it tries to contribute to.
        assertThat(lib1.testMatchingScopes(), hasItems("implementation:releaseImplementation"))
      }

      run {
        // TODO(b/110774403): Properly support test scopes in Java modules.
        val jLibModule = project.findModuleByName("jModuleK") as PsJavaModule
        assertThat(jLibModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), notNullValue())

        val dependencies = jLibModule.resolvedDependencies
        val lib3 = dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
        assertThat(lib3.testDeclared(), hasItems(true))
        assertThat(lib3.testMatchingScopes(), hasItems("implementation"))

        val lib4 = dependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1")
        assertThat(lib4.testDeclared(), hasItems(true))
        assertThat(lib4.testMatchingScopes(), hasItems("implementation"))
        assertThat(lib4?.first()?.declaredDependencies?.first()?.version, equalTo<ParsedValue<String>>("0.6".asParsed()))
      }
    }
  }

  @Test
  fun testPlusParsedModelMatching() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      val libModule = project.findModuleByName("modulePlus") as PsAndroidModule
      assertThat(libModule.dependencies.findLibraryDependency("com.example.libs:lib1:0.+"), notNullValue())

      val artifact = libModule.findVariant("release")!!.findArtifact(IdeArtifactName.MAIN)
      val dependencies = artifact!!.dependencies
      val lib1 = dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1")
      assertThat(lib1.testDeclared(), hasItems(true))
      assertThat(lib1.testMatchingScopes(), hasItems("implementation:releaseImplementation"))
    }
  }

  @Test
  fun testParsedDependencyPromotions() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      val libModule = project.findModuleByName("mainModule") as PsAndroidModule
      run {
        val lib1 = libModule.dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
        val lib2 = libModule.dependencies.findLibraryDependency("com.example.libs:lib2:1.0")
        val lib3 = libModule.dependencies.findLibraryDependency("com.example.jlib:lib3:1.0")
        val lib4 = libModule.dependencies.findLibraryDependency("com.example.jlib:lib4:1.0")
        assertThat(lib1.testDeclared(), hasItems(true))
        assertThat(lib2, nullValue())
        assertThat(lib3, nullValue())
        assertThat(lib4, nullValue())
      }
      run {
        val artifact = libModule.findVariant("paidRelease")!!.findArtifact(IdeArtifactName.MAIN)
        val dependencies = artifact!!.dependencies
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
        val artifact = libModule.findVariant("paidDebug")!!.findArtifact(IdeArtifactName.MAIN)
        val dependencies = artifact!!.dependencies
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
  }

  @Test
  fun testRemoveLibraryDependency() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      var module = project.findModuleByName("mainModule") as PsAndroidModule
      var jModule = project.findModuleByName("jModuleK") as PsJavaModule
      val lib10 = module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0")
      val lib3 = jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
      assertThat(lib10, notNullValue())
      assertThat(lib3, notNullValue())
      val numberOfMatchingDependenciesInModule = 2
      val numberOfMatchingDependenciesInJModule = 1
      assertThat(lib10!!.size, equalTo(numberOfMatchingDependenciesInModule))
      assertThat(lib3!!.size, equalTo(numberOfMatchingDependenciesInJModule))
      var notifications = 0
      module.addDependencyChangedListener(projectRule.testRootDisposable) { if (it is PsModule.DependencyRemovedEvent) notifications++ }
      lib10.forEach {
        module.removeDependency(it)
      }
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
      assertThat(notifications, equalTo(numberOfMatchingDependenciesInModule))

      notifications = 0
      jModule.addDependencyChangedListener(projectRule.testRootDisposable) { if (it is PsModule.DependencyRemovedEvent) notifications++ }
      lib3.forEach {
        jModule.removeDependency(it)
      }
      assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), nullValue())
      assertThat(notifications, equalTo(numberOfMatchingDependenciesInJModule))

      run {
        val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
        assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib2:1.0"), notNullValue())
      }

      run {
        val resolvedDependencies = jModule.resolvedDependencies
        assertThat(resolvedDependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), notNullValue())
      }

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      module = project.findModuleByName("mainModule") as PsAndroidModule
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib2:1.0"), nullValue())

      jModule = project.findModuleByName("jModuleK") as PsJavaModule
      assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), nullValue())

      run {
        val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
        assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib2:1.0"), nullValue())
      }

      run {
        val resolvedDependencies = jModule.resolvedDependencies
        assertThat(resolvedDependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), nullValue())
      }
    }
  }

  @Test
  fun testRemoveJarDependency() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      var module = project.findModuleByName("moduleA") as PsAndroidModule
      var jModule = project.findModuleByName("jModuleK") as PsJavaModule
      val libs = module.dependencies.findJarDependencies("../lib").nullize()
      val lib = jModule.dependencies.findJarDependencies("libs").nullize()
      assertThat(libs, notNullValue())
      assertThat(lib, notNullValue())
      assertThat(libs?.size, equalTo(1))
      assertThat(lib?.size, equalTo(1))
      var notifications = 0
      module.addDependencyChangedListener(projectRule.testRootDisposable) { if (it is PsModule.DependencyRemovedEvent) notifications++ }
      libs?.forEach {
        module.removeDependency(it)
      }
      assertThat(module.dependencies.findJarDependencies("../lib").nullize(), nullValue())
      assertThat(notifications, equalTo(1))

      notifications = 0
      jModule.addDependencyChangedListener(projectRule.testRootDisposable) { if (it is PsModule.DependencyRemovedEvent) notifications++ }
      lib?.forEach {
        jModule.removeDependency(it)
      }
      assertThat(jModule.dependencies.findJarDependencies("libs/jarlib-1.1.jar").nullize(), nullValue())
      assertThat(notifications, equalTo(1))

      run {
        val resolvedDependencies = module.findVariant("release")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findJarDependencies("../lib/libsam1-1.1.jar").nullize(), notNullValue())
        assertThat(resolvedDependencies?.findJarDependencies("../lib/libsam2-1.1.jar").nullize(), notNullValue())
      }

      run {
        val resolvedDependencies = jModule.resolvedDependencies
        assertThat(resolvedDependencies.findJarDependencies("libs/jarlib-1.1.jar").nullize(), notNullValue())
      }

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      module = project.findModuleByName("moduleA") as PsAndroidModule
      assertThat(module.dependencies.findJarDependencies("../lib").nullize(), nullValue())

      jModule = project.findModuleByName("jModuleK") as PsJavaModule
      assertThat(jModule.dependencies.findJarDependencies("libs/jarlib-1.1.jar").nullize(), nullValue())

      run {
        val resolvedDependencies = module.findVariant("release")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findJarDependencies("../lib/libsam1-1.1.jar").nullize(), nullValue())
        assertThat(resolvedDependencies?.findJarDependencies("../lib/libsam2-1.1.jar").nullize(), nullValue())
      }

      run {
        val resolvedDependencies = jModule.resolvedDependencies
        assertThat(resolvedDependencies.findJarDependencies("libs/jarlib-1.1.jar").nullize(), nullValue())
      }
    }
  }

  @Test
  fun testAddLibraryDependency() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      var module = project.findModuleByName("moduleA") as PsAndroidModule
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
      module.addLibraryDependency("com.example.libs:lib1:1.0".asParsed(), "implementation")
      assertThat(module.isModified, equalTo(true))
      assertThat(project.isModified, equalTo(true))
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())

      module.addLibraryDependency("com.example.libs:lib2:1.0".asParsed(), "implementation")
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib2:1.0"), notNullValue())

      var jModule = project.findModuleByName("jModuleM") as PsJavaModule
      assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:1.0"), nullValue())

      jModule.addLibraryDependency("com.example.jlib:lib4:1.0".asParsed(), "implementation")
      assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:1.0"), notNullValue())

      run {
        val resolvedDependencies = module.findVariant("release")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0"), nullValue())
        assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib2:1.0"), nullValue())
      }

      run {
        val resolvedDependencies = jModule.resolvedDependencies
        assertThat(resolvedDependencies.findLibraryDependency("com.example.jlib:lib4:1.0"), nullValue())
      }

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      module = project.findModuleByName("moduleA") as PsAndroidModule
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib2:1.0"), notNullValue())

      jModule = project.findModuleByName("jModuleM") as PsJavaModule
      assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:1.0"), notNullValue())

      run {
        val resolvedDependencies = module.findVariant("release")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0"), notNullValue())
        assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib2:1.0"), notNullValue())
      }

      run {
        val resolvedDependencies = jModule.resolvedDependencies
        assertThat(resolvedDependencies.findLibraryDependency("com.example.jlib:lib4:1.0"), notNullValue())
      }
    }
  }

  @Test
  fun testAddJarDependency() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      var module = project.findModuleByName("moduleC") as PsAndroidModule
      var jModule = project.findModuleByName("jModuleM") as PsJavaModule
      val jarPath = "../lib/libsam1-1.1.jar"
      val jarPath2 = "../lib/libsam2-1.1.jar"
      val libDirPath = "../lib"

      assertThat(module.dependencies.findJarDependencies(jarPath).nullize(), nullValue())
      module.addJarFileDependency(jarPath, "implementation")
      assertThat(module.isModified, equalTo(true))
      assertThat(project.isModified, equalTo(true))
      assertThat(module.dependencies.findJarDependencies(jarPath).nullize(), notNullValue())

      assertThat(module.dependencies.findJarDependencies(libDirPath).nullize(), nullValue())
      module.addJarFileTreeDependency(libDirPath, includes = setOf("*sam2*.jar"), excludes = setOf(), configurationName = "implementation")
      assertThat(module.dependencies.findJarDependencies(jarPath).nullize(), notNullValue())
      assertThat(module.dependencies.findJarDependencies(libDirPath).nullize(), notNullValue())

      assertThat(jModule.dependencies.findJarDependencies(libDirPath).nullize(), nullValue())
      jModule.addJarFileTreeDependency(libDirPath, setOf(), setOf(), "implementation")
      assertThat(jModule.dependencies.findJarDependencies(libDirPath).nullize(), notNullValue())

      run {
        val resolvedDependencies = module.findVariant("release")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findJarDependencies(libDirPath).nullize(), nullValue())
        assertThat(resolvedDependencies?.findJarDependencies(jarPath).nullize(), nullValue())
        assertThat(resolvedDependencies?.findJarDependencies(jarPath2).nullize(), nullValue())
        assertThat(resolvedDependencies?.findJarDependencies(module.rootDir?.resolve(libDirPath)?.canonicalPath!!).nullize(), nullValue())
        assertThat(resolvedDependencies?.findJarDependencies(module.rootDir?.resolve(jarPath)?.canonicalPath!!).nullize(), nullValue())
        assertThat(resolvedDependencies?.findJarDependencies(module.rootDir?.resolve(jarPath2)?.canonicalPath!!).nullize(), nullValue())
      }

      run {
        val resolvedDependencies = jModule.resolvedDependencies
        assertThat(resolvedDependencies.findJarDependencies(libDirPath).nullize(), nullValue())
        assertThat(resolvedDependencies.findJarDependencies(jarPath).nullize(), nullValue())
        assertThat(resolvedDependencies.findJarDependencies(jarPath2).nullize(), nullValue())
        assertThat(resolvedDependencies.findJarDependencies(jModule.rootDir?.resolve(libDirPath)?.canonicalPath!!).nullize(), nullValue())
        assertThat(resolvedDependencies.findJarDependencies(jModule.rootDir?.resolve(jarPath)?.canonicalPath!!).nullize(), nullValue())
        assertThat(resolvedDependencies.findJarDependencies(jModule.rootDir?.resolve(jarPath2)?.canonicalPath!!).nullize(), nullValue())
      }

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      module = project.findModuleByName("moduleC") as PsAndroidModule
      assertThat(module.dependencies.findJarDependencies(jarPath).nullize(), notNullValue())
      assertThat(module.dependencies.findJarDependencies(libDirPath).nullize(), notNullValue())

      jModule = project.findModuleByName("jModuleM") as PsJavaModule
      assertThat(jModule.dependencies.findJarDependencies(libDirPath).nullize(), notNullValue())

      run {
        val resolvedDependencies = module.findVariant("release")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        // Note that file tree dependencies are resolved into individual jar library dependencies relative to the module root.
        assertThat(resolvedDependencies?.findJarDependencies(libDirPath).nullize(), nullValue())
        assertThat(resolvedDependencies?.findJarDependencies(jarPath).nullize(), notNullValue())
        assertThat(resolvedDependencies?.findJarDependencies(jarPath2).nullize(), notNullValue())
      }

      run {
        val resolvedDependencies = jModule.resolvedDependencies
        // Note that file tree dependencies are resolved into individual jar library dependencies relative to the module root.
        assertThat(resolvedDependencies.findJarDependencies(libDirPath).nullize(), nullValue())
        assertThat(resolvedDependencies.findJarDependencies(jarPath).nullize(), notNullValue())
        assertThat(resolvedDependencies.findJarDependencies(jarPath2).nullize(), notNullValue())
      }
    }
  }

  @Test
  fun testAddVarVersionedLibraryDependency() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      run {
        val module = project.findModuleByName("moduleA") as PsAndroidModule
        assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:0.6"), nullValue())
        module.addLibraryDependency(
          ParsedValue.Set.Parsed("com.example.libs:lib1:0.6", DslText.InterpolatedString("com.example.libs:lib1:\$var06")),
          "implementation"
        )

        val addedDep = module.dependencies.findLibraryDependency("com.example.libs:lib1:0.6")
        assertThat(addedDep, notNullValue())
        assertThat<ParsedValue<String>>(addedDep!![0].version, equalTo(ParsedValue.Set.Parsed("0.6", DslText.Reference("var06"))))

        val resolvedDependencies = module.findVariant("release")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:0.6"), nullValue())
      }

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      run {
        val module = project.findModuleByName("moduleA") as PsAndroidModule

        val addedDep = module.dependencies.findLibraryDependency("com.example.libs:lib1:0.6")
        assertThat(addedDep, notNullValue())
        assertThat<ParsedValue<String>>(addedDep!![0].version, equalTo(ParsedValue.Set.Parsed("0.6", DslText.Reference("var06"))))

        val resolvedDependencies = module.findVariant("release")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:0.6"), notNullValue())
      }
    }
  }

  @Test
  fun testChangeLibraryDependencyScope() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      val oldConfig = "implementation"
      val newConfig = "api"

      var module = project.findModuleByName("mainModule") as PsAndroidModule
      val libraryDependency = module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", oldConfig)!!.first()
      assertThat(libraryDependency, notNullValue())
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", newConfig), nullValue())

      module.modifyDependencyConfiguration(libraryDependency, newConfigurationName = newConfig)

      assertThat(module.isModified, equalTo(true))
      assertThat(project.isModified, equalTo(true))

      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", oldConfig), nullValue())
      assertThat(
        module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", newConfig)?.firstOrNull(),
        sameInstance(libraryDependency)
      )

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      module = project.findModuleByName("mainModule") as PsAndroidModule

      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", oldConfig), nullValue())
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", newConfig), notNullValue())
    }
  }

  @Test
  fun testEditLibraryDependencyVersion() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      var module = project.findModuleByName("mainModule") as PsAndroidModule
      var jModule = project.findModuleByName("jModuleM") as PsJavaModule

      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", "implementation"), notNullValue())
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1", "implementation"), nullValue())

      assertThat(module.dependencies.findLibraryDependency("com.example.jlib:lib3:0.6", "freeImplementation"), notNullValue())
      assertThat(module.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1", "freeImplementation"), nullValue())
      assertThat(module.variables.getVariable("var06"), nullValue())
      assertThat(module.parent.variables.getVariable("var06")?.value, equalTo<ParsedValue<Any>?>("0.6".asParsed()))

      assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), notNullValue())
      assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:1.0"), nullValue())

      run {
        val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        val lib1 = resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0")
        assertThat(lib1, notNullValue())
        assertThat(lib1?.first()?.spec?.version, equalTo("1.0"))
      }

      run {
        val resolvedDependencies = jModule.resolvedDependencies
        val lib3 = resolvedDependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
        assertThat(lib3, notNullValue())
        assertThat(lib3?.first()?.spec?.version, equalTo("0.9.1"))
      }

      module.setLibraryDependencyVersion(
        PsArtifactDependencySpec.create("com.example.libs:lib1:1.0")!!, "implementation", "0.9.1", updateVariable = false
      )
      module.setLibraryDependencyVersion(
        PsArtifactDependencySpec.create("com.example.jlib:lib3:0.6")!!, "freeImplementation", "0.9.1", updateVariable = true
      )
      jModule.setLibraryDependencyVersion(
        PsArtifactDependencySpec.create("com.example.jlib:lib3:0.9.1")!!, "implementation", "1.0", updateVariable = false
      )

      assertThat(module.isModified, equalTo(true))
      assertThat(jModule.isModified, equalTo(true))
      assertThat(project.isModified, equalTo(true))

      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", "implementation"), nullValue())
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1", "implementation"), notNullValue())

      assertThat(module.dependencies.findLibraryDependency("com.example.jlib:lib3:0.6", "freeImplementation"), nullValue())
      assertThat(module.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1", "freeImplementation"), notNullValue())
      assertThat(module.variables.getVariable("var06"), nullValue())
      assertThat(module.parent.variables.getVariable("var06")?.value, equalTo<ParsedValue<Any>?>("0.9.1".asParsed()))

      assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), nullValue())
      assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:1.0"), notNullValue())

      run {
        val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        val lib1 = resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0")
        assertThat(lib1, notNullValue())
        assertThat(lib1?.first()?.spec?.version, equalTo("1.0"))
      }

      run {
        val resolvedDependencies = jModule.resolvedDependencies
        val lib3 = resolvedDependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1")
        assertThat(lib3, notNullValue())
        assertThat(lib3?.first()?.spec?.version, equalTo("0.9.1"))
      }

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      module = project.findModuleByName("mainModule") as PsAndroidModule
      jModule = project.findModuleByName("jModuleM") as PsJavaModule

      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", "implementation"), nullValue())
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1", "implementation"), notNullValue())

      assertThat(module.dependencies.findLibraryDependency("com.example.jlib:lib3:0.6", "freeImplementation"), nullValue())
      assertThat(module.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1", "freeImplementation"), notNullValue())
      assertThat(module.variables.getVariable("var06"), nullValue())
      assertThat(module.parent.variables.getVariable("var06")?.value, equalTo<ParsedValue<Any>?>("0.9.1".asParsed()))

      assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:0.9.1"), nullValue())
      assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib3:1.0"), notNullValue())

      run {
        val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        val lib1 = resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:0.9.1")
        assertThat(lib1, notNullValue())
        assertThat(lib1?.first()?.spec?.version, equalTo("0.9.1"))
      }

      run {
        val resolvedDependencies = jModule.resolvedDependencies
        val lib3 = resolvedDependencies.findLibraryDependency("com.example.jlib:lib3:1.0")
        assertThat(lib3, notNullValue())
        assertThat(lib3?.first()?.spec?.version, equalTo("1.0"))
      }
    }
  }

  @Test
  fun testEditLibraryDependencyVersionProperty() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      var module = project.findModuleByName("mainModule") as PsAndroidModule
      var jModule = project.findModuleByName("jModuleK") as PsJavaModule

      val declaredDependency = module.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1", "releaseImplementation")
      val jDeclaredDependency = jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:0.6", "implementation")

      assertThat(declaredDependency, notNullValue())
      assertThat(declaredDependency?.size, equalTo(1))
      assertThat(
        module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", "releaseImplementation"), nullValue()
      )

      assertThat(jDeclaredDependency, notNullValue())
      assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1"), nullValue())

      run {
        val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        val lib1 = resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0")
        assertThat(lib1.testHasPromotedVersion(), equalTo(listOf(true)))
        assertThat(lib1, notNullValue())
        assertThat(lib1?.first()?.spec?.version, equalTo("1.0"))
      }

      run {
        val resolvedDependencies = jModule.resolvedDependencies
        val lib4 = resolvedDependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1")
        // TODO(b/110778597): Implement library version promotion analysis for Java modules.
        // assertThat(lib4.testHasPromotedVersion(), equalTo(listOf(true)))
        assertThat(lib4, notNullValue())
        assertThat(lib4?.first()?.spec?.version, equalTo("0.9.1"))
      }

      declaredDependency!![0].version = "1.0".asParsed()
      jDeclaredDependency!![0].version = "0.9.1".asParsed()

      assertThat(module.isModified, equalTo(true))
      assertThat(jModule.isModified, equalTo(true))
      assertThat(project.isModified, equalTo(true))

      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", "releaseImplementation"), notNullValue())
      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1", "releaseImplementation"), nullValue())

      assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1", "implementation"), notNullValue())
      assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:0.6", "implementation"), nullValue())

      run {
        val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        val lib1 = resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0")
        assertThat(lib1.testHasPromotedVersion(), equalTo(listOf(false)))
        assertThat(lib1, notNullValue())
        assertThat(lib1?.first()?.spec?.version, equalTo("1.0"))
      }

      run {
        val resolvedDependencies = jModule.resolvedDependencies
        val lib4 = resolvedDependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1")
        // TODO(b/110778597): Implement library version promotion analysis for Java modules.
        // assertThat(lib4.testHasPromotedVersion(), equalTo(listOf(false)))
        assertThat(lib4, notNullValue())
        assertThat(lib4?.first()?.spec?.version, equalTo("0.9.1"))
      }

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      module = project.findModuleByName("mainModule") as PsAndroidModule
      jModule = project.findModuleByName("jModuleK") as PsJavaModule

      assertThat(module.dependencies.findLibraryDependency("com.example.libs:lib1:0.9.1", "releaseImplementation"), nullValue())
      assertThat(
        module.dependencies.findLibraryDependency("com.example.libs:lib1:1.0", "releaseImplementation"), notNullValue()
      )

      assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1", "implementation"), notNullValue())
      assertThat(jModule.dependencies.findLibraryDependency("com.example.jlib:lib4:0.6", "implementation"), nullValue())

      run {
        val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        val lib1 = resolvedDependencies?.findLibraryDependency("com.example.libs:lib1:1.0")
        // TODO(b/110778597): Implement library version promotion analysis for Java modules.
        // assertThat(lib1.testHasPromotedVersion(), equalTo(listOf(false)))
        assertThat(lib1, notNullValue())
        assertThat(lib1?.first()?.spec?.version, equalTo("1.0"))
      }

      run {
        val resolvedDependencies = jModule.resolvedDependencies
        val lib4 = resolvedDependencies.findLibraryDependency("com.example.jlib:lib4:0.9.1")
        assertThat(lib4.testHasPromotedVersion(), equalTo(listOf(false)))
        assertThat(lib4, notNullValue())
        assertThat(lib4?.first()?.spec?.version, equalTo("0.9.1"))
      }
    }
  }

  @Test
  fun testAddModuleDependency() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      var module = project.findModuleByName("mainModule") as PsAndroidModule
      assertThat(module.dependencies.findModuleDependency(":moduleA"), nullValue())
      module.addModuleDependency(":moduleA", "implementation")
      assertThat(module.dependencies.findModuleDependency(":moduleA"), notNullValue())

      assertThat(module.isModified, equalTo(true))
      assertThat(project.isModified, equalTo(true))

      module.addModuleDependency(":moduleB", "implementation")
      assertThat(module.dependencies.findModuleDependency(":moduleA"), notNullValue())
      assertThat(module.dependencies.findModuleDependency(":moduleB"), notNullValue())

      run {
        val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findModuleDependency(":moduleA"), nullValue())
        assertThat(resolvedDependencies?.findModuleDependency(":moduleB"), nullValue())
      }

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      module = project.findModuleByName("mainModule") as PsAndroidModule
      assertThat(module.dependencies.findModuleDependency(":moduleA"), notNullValue())
      assertThat(module.dependencies.findModuleDependency(":moduleB"), notNullValue())

      run {
        val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findModuleDependency(":moduleA"), notNullValue())
        assertThat(resolvedDependencies?.findModuleDependency(":moduleB"), notNullValue())
      }
    }
  }

  @Test
  fun testAddJavaModuleDependency() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      var module = project.findModuleByName("mainModule") as PsAndroidModule
      assertThat(module.dependencies.findModuleDependency(":jModuleK"), nullValue())
      module.addModuleDependency(":jModuleK", "implementation")
      assertThat(module.dependencies.findModuleDependency(":jModuleK"), notNullValue())

      run {
        val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findModuleDependency(":jModuleK"), nullValue())
      }

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      module = project.findModuleByName("mainModule") as PsAndroidModule
      assertThat(module.dependencies.findModuleDependency(":jModuleK"), notNullValue())

      run {
        val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        assertThat(resolvedDependencies?.findModuleDependency(":jModuleK"), notNullValue())
      }
    }
  }

  @Test
  fun testCatalogDependencyCanExtractVariableIsFalse() {
    StudioFlags.GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT.override(true)
    try {
      val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
      projectRule.psTestWithProject(preparedProject) {
        val module = project.findModuleByName("app") as PsAndroidModule
        run {
          val resolvedDependencies = module.findVariant("release")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
          val catalogDep = resolvedDependencies?.findLibraryDependencies("com.google.guava", "guava")?.singleOrNull()?.declaredDependencies
          assertThat(catalogDep!!.size,equalTo(1))
          assertFalse(catalogDep[0].canExtractVariable())

          val plainDep = resolvedDependencies.findLibraryDependencies("com.android.support", "appcompat-v7").singleOrNull()?.declaredDependencies
          assertThat(plainDep!!.size,equalTo(1))
          assertTrue(plainDep[0].canExtractVariable())
        }
      }
    }
    finally {
      StudioFlags.COMPOSE_PREVIEW_ELEMENT_PICKER.clearOverride()
    }
  }

  @Test
  fun testAddJavaModuleDependencyToJavaModule() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      var module = project.findModuleByName("jModuleK") as PsJavaModule
      assertThat(module.dependencies.findModuleDependency(":jModuleM"), nullValue())
      module.addModuleDependency(":jModuleM", "implementation")
      assertThat(module.dependencies.findModuleDependency(":jModuleM"), notNullValue())

      assertThat(module.isModified, equalTo(true))
      assertThat(project.isModified, equalTo(true))

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      module = project.findModuleByName("jModuleK") as PsJavaModule
      assertThat(module.dependencies.findModuleDependency(":jModuleM"), notNullValue())
    }
  }

  data class TestReverseDependency(val from: String, val to: String, val resolved: String, val kind: String, val isPromoted: Boolean)

  private fun ReverseDependency.toTest() =
    TestReverseDependency(
      when (this) {
        is ReverseDependency.Declared -> dependency.configurationName
        is ReverseDependency.Transitive -> requestingResolvedDependency.spec.toString()
      },
      spec.toString(), resolvedSpec.toString(), javaClass.simpleName, isPromoted)

  @Test
  fun testReverseDependencies() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      val module = project.findModuleByName("mainModule") as PsAndroidModule
      run {
        val resolvedDependencies = module.findVariant("freeRelease")?.findArtifact(IdeArtifactName.MAIN)?.dependencies
        val lib3 = resolvedDependencies?.findLibraryDependencies("com.example.jlib", "lib3")?.singleOrNull()?.getReverseDependencies()
        val lib2 = resolvedDependencies?.findLibraryDependencies("com.example.libs", "lib2")?.singleOrNull()?.getReverseDependencies()
        val lib1 = resolvedDependencies?.findLibraryDependencies("com.example.libs", "lib1")?.singleOrNull()?.getReverseDependencies()

        assertThat(
          lib3?.map { it.toTest() }?.toSet(),
          equalTo(
            setOf(
              TestReverseDependency(
                from = "com.example.libs:lib2:1.0", to = "com.example.jlib:lib3:1.0", resolved = "com.example.jlib:lib3:1.0",
                kind = "Transitive", isPromoted = false
              ),
              TestReverseDependency(
                from = "freeImplementation", to = "com.example.jlib:lib3:0.6", resolved = "com.example.jlib:lib3:1.0",
                kind = "Declared", isPromoted = true
              )
            )
          )
        )

        assertThat(
          lib2?.map { it.toTest() }?.toSet(),
          equalTo(
            setOf(
              TestReverseDependency(
                from = "com.example.libs:lib1:1.0", to = "com.example.libs:lib2:1.0", resolved = "com.example.libs:lib2:1.0",
                kind = "Transitive", isPromoted = false
              )
            )
          )
        )

        assertThat(
          lib1?.map { it.toTest() }?.toSet(),
          equalTo(
            setOf(
              TestReverseDependency(
                from = "implementation", to = "com.example.libs:lib1:1.0", resolved = "com.example.libs:lib1:1.0",
                kind = "Declared", isPromoted = false
              ),
              TestReverseDependency(
                from = "releaseImplementation", to = "com.example.libs:lib1:0.9.1", resolved = "com.example.libs:lib1:1.0",
                kind = "Declared", isPromoted = true
              )
            )
          )
        )
      }
    }
  }

  @Suppress("ReplaceSingleLineLet")
  @Test
  fun testReverseJarDependencies() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      // TODO(b/119400704) Implement proper reverse dependencies support for Jar dependencies.
      val lib1JarPath = "../lib/libsam1-1.1.jar"
      val lib2JarPath = "../lib/libsam2-1.1.jar"
      val libJarPath = "libs/jarlib-1.1.jar"
      fun getResolvedDependenciesOfReleaseArtifactFor(name: String) =
        (project.findModuleByName(name) as? PsAndroidModule)
          ?.findVariant("release")?.findArtifact(IdeArtifactName.MAIN)?.dependencies

      fun getResolvedDependenciesFor(name: String) =
        (project.findModuleByName(name) as? PsJavaModule)
          ?.resolvedDependencies

      getResolvedDependenciesOfReleaseArtifactFor("moduleA").let { resolvedDependencies ->
        assertThat(resolvedDependencies?.findJarDependencies(lib1JarPath)?.singleOrNull()?.declaredDependencies?.size, equalTo(1))
        assertThat(resolvedDependencies?.findJarDependencies(lib2JarPath)?.singleOrNull()?.declaredDependencies?.size, equalTo(1))
      }

      getResolvedDependenciesOfReleaseArtifactFor("moduleB").let { resolvedDependencies ->
        assertThat(resolvedDependencies?.findJarDependencies(lib1JarPath)?.singleOrNull()?.declaredDependencies?.size, equalTo(1))
      }

      getResolvedDependenciesFor("jModuleK").let { resolvedDependencies ->
        assertThat(resolvedDependencies?.findJarDependencies(libJarPath)?.singleOrNull()?.declaredDependencies?.size, equalTo(1))
      }

      getResolvedDependenciesFor("jModuleZ").let { resolvedDependencies ->
        assertThat(resolvedDependencies?.findJarDependencies(lib1JarPath)?.singleOrNull()?.declaredDependencies?.size, equalTo(1))
        assertThat(resolvedDependencies?.findJarDependencies(lib2JarPath)?.singleOrNull()?.declaredDependencies?.size, equalTo(1))
      }
    }
  }

  @Test
  fun testFlavorConfigurationWorkaround() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      var appModule = project.findModuleByName("app") as PsAndroidModule
      val mainModule = appModule.dependencies.findModuleDependency(":mainModule")
      appModule.modifyDependencyConfiguration(mainModule!!, "paidReleaseImplementation")

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      appModule = project.findModuleByName("app") as PsAndroidModule
      assertThat(appModule.dependencies.findModuleDependency(":mainModule")?.configurationName, equalTo("paidReleaseImplementation"))
    }
  }

  @Test
  fun testFlavorConfigurationWorkaroundRemoval() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    projectRule.psTestWithProject(preparedProject) {
      var flavorModule = project.findModuleByName("moduleFlavor") as PsAndroidModule
      val libspd = flavorModule.dependencies.findJarDependencies("libspd").firstOrNull()
      assertThat(libspd, notNullValue())

      assertThat(flavorModule.parsedModel!!.configurations().all().find { it.name().equals("paidDebugImplementation") }, notNullValue())
      flavorModule.modifyDependencyConfiguration(libspd!!, "implementation")
      assertThat(flavorModule.parsedModel!!.configurations().all().find { it.name().equals("paidDebugImplementation") }, nullValue())

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      flavorModule = project.findModuleByName("moduleFlavor") as PsAndroidModule
      assertThat(flavorModule.parsedModel!!.configurations().all().find { it.name().equals("paidDebugImplementation") }, nullValue())

      val libspr = flavorModule.dependencies.findJarDependencies("libspr").firstOrNull()
      assertThat(libspr, notNullValue())

      assertThat(flavorModule.parsedModel!!.configurations().all().find { it.name().equals("paidReleaseImplementation") }, notNullValue())
      flavorModule.modifyDependencyConfiguration(libspr!!, "implementation")
      assertThat(flavorModule.parsedModel!!.configurations().all().find { it.name().equals("paidReleaseImplementation") }, nullValue())

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      flavorModule = project.findModuleByName("moduleFlavor") as PsAndroidModule
      assertThat(flavorModule.parsedModel!!.configurations().all().find { it.name().equals("paidReleaseImplementation") }, nullValue())

      val libsfd = flavorModule.dependencies.findJarDependencies("libsfd").firstOrNull()
      assertThat(libsfd, notNullValue())

      assertThat(flavorModule.parsedModel!!.configurations().all().find { it.name().equals("freeDebugImplementation") }, notNullValue())
      flavorModule.modifyDependencyConfiguration(libsfd!!, "implementation")
      assertThat(flavorModule.parsedModel!!.configurations().all().find { it.name().equals("freeDebugImplementation") }, notNullValue())

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      flavorModule = project.findModuleByName("moduleFlavor") as PsAndroidModule
      assertThat(flavorModule.parsedModel!!.configurations().all().find { it.name().equals("freeDebugImplementation") }, notNullValue())

      val libsfr = flavorModule.dependencies.findJarDependencies("libsfr").firstOrNull()
      assertThat(libsfr, notNullValue())

      assertThat(flavorModule.parsedModel!!.configurations().all().find { it.name().equals("freeReleaseImplementation") }, notNullValue())
      flavorModule.modifyDependencyConfiguration(libsfr!!, "implementation")
      assertThat(flavorModule.parsedModel!!.configurations().all().find { it.name().equals("freeReleaseImplementation") }, notNullValue())

      project.applyChanges()
      requestSyncAndWait()
      reparse()

      flavorModule = project.findModuleByName("moduleFlavor") as PsAndroidModule
      assertThat(flavorModule.parsedModel!!.configurations().all().find { it.name().equals("freeReleaseImplementation") }, notNullValue())

      //TODO(b/134372808): test for not removing a configuration with a user-provided comment (and nothing else) in the block

      // Basic configurations don't require an entry in the configurations block
      assertThat(flavorModule.parsedModel!!.configurations().all().find { it.name().equals("implementation") }, nullValue())
    }
  }
}

private fun <T> PsDeclaredDependencyCollection<*, T, *, *>.findLibraryDependency(
  compactNotation: String,
  configuration: String? = null
): List<T>?
  where T : PsDeclaredDependency,
        T : PsLibraryDependency =
  PsArtifactDependencySpec.create(compactNotation)?.let { spec ->
    findLibraryDependencies(
      spec.group,
      spec.name
    )
      .filter { it.spec.version == spec.version && it.configurationName == (configuration ?: it.configurationName) }
      .let { if (it.isEmpty()) null else it }
  }

private fun <T> PsResolvedDependencyCollection<*, *, T, *, *>.findLibraryDependency(compactNotation: String): List<T>?
  where T : PsResolvedDependency,
        T : PsLibraryDependency =
  PsArtifactDependencySpec.create(compactNotation)?.let { spec ->
    findLibraryDependencies(
      spec.group,
      spec.name
    )
      .filter { it.spec.version == spec.version }
      .let { if (it.isEmpty()) null else it }
  }

private fun List<PsResolvedDependency>?.testMatchingScopes(): List<String> =
  orEmpty().map { resolvedDependency -> resolvedDependency.getParsedModels().joinToString(":") { it.configurationName() } }

private fun List<PsDeclaredDependency>?.testDeclaredScopes(): List<String> = orEmpty().map { it.parsedModel.configurationName() }

private fun List<PsModel>?.testDeclared(): List<Boolean> = orEmpty().map { it.isDeclared }
private fun List<PsResolvedLibraryDependency>?.testHasPromotedVersion(): List<Boolean> = orEmpty().map { it.hasPromotedVersion() }
private fun <T : Any> T.asParsed() = ParsedValue.Set.Parsed(this, DslText.Literal)
