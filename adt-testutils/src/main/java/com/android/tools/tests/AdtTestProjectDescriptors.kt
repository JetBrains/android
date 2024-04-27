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
package com.android.tools.tests

import com.android.test.testutils.TestUtils
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import java.io.File
import java.nio.file.Path

/**
 * The base project descriptor for Android Studio tests.
 *
 * Includes configuration for a Java language level and JDK.
 */
open class AdtTestProjectDescriptor(
  /** The Java language version to configure for the project. */
  val javaLanguageVersion: LanguageLevel = LanguageLevel.HIGHEST,
  /** The path to the JDK to use for the project. Defaults to a mock JDK. */
  val jdkPath: Path = TestUtils.getMockJdk(),
) : DefaultLightProjectDescriptor() {

  private val jdk by lazy { IdeaTestUtil.createMockJdk("java 1.7", jdkPath.toString()) }
  override fun getSdk(): Sdk = jdk

  final override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    this.configureModule(module, model)
  }

  open fun configureModule(module: Module, model: ModifiableRootModel) {
    model.getModuleExtension(LanguageLevelModuleExtension::class.java)?.apply {
      languageLevel = javaLanguageVersion
    }
  }

  fun configureModule(module: Module) {
    ModuleRootManager.getInstance(module).modifiableModel.apply {
      configureModule(module, this)
    }.commit()
  }

  open fun configureFixture(fixtureBuilder: JavaModuleFixtureBuilder<*>) {
    fixtureBuilder.apply {
      setLanguageLevel(javaLanguageVersion)
      addJdk(jdkPath.toString())
    }
  }

  open fun withJavaVersion(version: LanguageLevel): AdtTestProjectDescriptor =
    AdtTestProjectDescriptor(version, jdkPath)

  open fun withJdkPath(path: Path): AdtTestProjectDescriptor =
    AdtTestProjectDescriptor(javaLanguageVersion, path)
}

/** A project descriptor that configures the Kotlin standard library. */
class KotlinAdtTestProjectDescriptor(
  javaLanguageVersion: LanguageLevel = LanguageLevel.HIGHEST,
  jdkPath: Path = TestUtils.getMockJdk(),
  private val libraryFilesProvider: () -> Map<OrderRootType, Collection<File>>
) : AdtTestProjectDescriptor(javaLanguageVersion, jdkPath) {
  private val libraryFiles by lazy {
    libraryFilesProvider().also {
      for ((rootType, files) in it) {
        assert(files.isNotEmpty()) { "No files provided for root type: ${rootType.name()}" }
        files.forEach { file ->
          assert(file.exists()) { "Library file doesn't exist: ${file.absolutePath}" }
        }
      }
    }
  }

  override fun configureModule(module: Module, model: ModifiableRootModel) {
    super.configureModule(module, model)

    if (libraryFiles.isNotEmpty()) {
      model.moduleLibraryTable.modifiableModel.apply {
        createLibrary(LIBRARY_NAME).modifiableModel.apply {
          for ((rootType, files) in libraryFiles) {
            files.forEach {
              addRoot(VfsUtil.getUrlForLibraryRoot(it), rootType)
            }
          }
        }.commit()
      }.commit()
    }
  }

  override fun configureFixture(fixtureBuilder: JavaModuleFixtureBuilder<*>) {
    super.configureFixture(fixtureBuilder)

    if (libraryFiles.isNotEmpty()) {
      fixtureBuilder.addLibrary(
        LIBRARY_NAME,
        libraryFiles.mapValues { (_, files) ->
          files.map { it.toString() }.toTypedArray()
        }
      )
    }
  }

  override fun withJavaVersion(version: LanguageLevel): KotlinAdtTestProjectDescriptor =
    KotlinAdtTestProjectDescriptor(version, jdkPath, libraryFilesProvider)

  override fun withJdkPath(path: Path): KotlinAdtTestProjectDescriptor =
    KotlinAdtTestProjectDescriptor(javaLanguageVersion, path, libraryFilesProvider)

  companion object {
    const val LIBRARY_NAME = "kotlin-stdlib-for-test"
  }
}

object AdtTestProjectDescriptors {
  // Note: These functions create a new project descriptor every time, to avoid triggering the
  // project-reuse logic in LightPlatformTestCase.doSetup.

  /** Creates a project descriptor for Java-only projects. */
  @JvmStatic
  fun java() = AdtTestProjectDescriptor()

  /** Creates a project descriptor for Kotlin projects, with a binary stdlib. */
  @JvmStatic
  fun kotlin() = KotlinAdtTestProjectDescriptor {
    mapOf(OrderRootType.CLASSES to listOf(AdtTestKotlinArtifacts.kotlinStdlib))
  }

  /**
   * Creates a project descriptor for Kotlin projects, using a combined binary and source stdlib.
   *
   * This should only be used if you need PSI for symbols in the stdlib. Otherwise, prefer the
   * faster [kotlin] descriptor.
   */
  @JvmStatic
  fun kotlinWithStdlibSources() = KotlinAdtTestProjectDescriptor {
    mapOf(
      OrderRootType.CLASSES to listOf(
        AdtTestKotlinArtifacts.kotlinStdlib,
      ),
      OrderRootType.SOURCES to listOf(
        AdtTestKotlinArtifacts.kotlinStdlibSources,
        AdtTestKotlinArtifacts.kotlinStdlibCommonSources,
      )
    )
  }

  /**
   * Creates a sensible default project descriptor.
   */
  @JvmStatic
  @JvmName("defaultDescriptor")  // default is a reserved word in Java
  fun default(): AdtTestProjectDescriptor {
    // b/294248298: Tests using K2 Analysis API need Kotlin stdlib to be available, or analysis crashes.
    // Therefore, for tests with the K2 plugin, we default to a Kotlin descriptor that will load the stdlib.
    //
    // The KotlinPluginModeProvider.isK2Mode() function depends on application-level service lookup, which requires that the IJ
    // application is loaded. Therefore, we need to load TestApplicationManager here, since project descriptor
    // selection happens very early on, and the application might not otherwise be ready.
    TestApplicationManager.getInstance()
    return if (KotlinPluginModeProvider.isK2Mode()) kotlin() else java()
  }
}
