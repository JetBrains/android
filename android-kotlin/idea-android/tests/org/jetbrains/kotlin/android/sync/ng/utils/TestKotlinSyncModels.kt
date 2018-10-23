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
package org.jetbrains.kotlin.android.sync.ng.utils

import org.jetbrains.kotlin.gradle.model.AllOpen
import org.jetbrains.kotlin.gradle.model.CompilerArguments
import org.jetbrains.kotlin.gradle.model.ExperimentalFeatures
import org.jetbrains.kotlin.gradle.model.KotlinAndroidExtension
import org.jetbrains.kotlin.gradle.model.KotlinProject
import org.jetbrains.kotlin.gradle.model.NoArg
import org.jetbrains.kotlin.gradle.model.SamWithReceiver
import org.jetbrains.kotlin.gradle.model.SourceSet
import org.junit.Assert.assertTrue
import java.io.File

/**
 * Base method used to configure the Kotlin Model for testing. This file provides some sane defaults for all the fields in the model with
 * the ability to configure them on demand for individual tests. The defaults are taken from a basic Kotlin project.
 */
fun kotlinModel(moduleName: String, moduleDirectory: File, block: BaseTestKotlinModel.() -> Unit): KotlinProject =
  BaseTestKotlinModel(moduleName, moduleDirectory).apply(block)

open class BaseTestKotlinModel(final override var name: String, private val moduleDirectory: File) : KotlinProject {
  override var expectedByDependencies: Collection<String> = listOf()
  override var experimentalFeatures: ExperimentalFeatures = object : ExperimentalFeatures {
    override val coroutines: String?
      get() = "WARN"
  }
  override var kotlinVersion: String = "1.2.71"
  override var modelVersion: Long = 1
  override var projectType: KotlinProject.ProjectType = KotlinProject.ProjectType.PLATFORM_JVM
  protected open val internalSourceSets: MutableMap<String, BaseTestKotlinSourceSet> = mutableMapOf(
    "main" to BaseTestKotlinSourceSet("main", moduleDirectory, name),
    "test" to BaseTestKotlinSourceSet("test", moduleDirectory, name))
  override val sourceSets: Collection<SourceSet>
    get() = internalSourceSets.values

  fun sourceSet(name: String, block: BaseTestKotlinSourceSet.() -> Unit) {
    internalSourceSets.getOrElse(name) { BaseTestKotlinSourceSet(name, moduleDirectory, name) }.apply(block)
  }
}

open class BaseTestKotlinSourceSet(final override val name: String, protected val moduleDirectory: File, moduleName: String) : SourceSet {
  private fun createClassesOutputDirectory() = createModuleFile(moduleDirectory, "build", "classes", "kotlin", name)

  override var classesOutputDirectory: File = createClassesOutputDirectory()
  override val compilerArguments: BaseTestCompilerArguments = BaseTestCompilerArguments(moduleName,
                                                                                        createClassesOutputDirectory().absolutePath)
  override val friendSourceSets: MutableList<String> = mutableListOf()
  override val resourcesDirectories: MutableList<File> = mutableListOf(
    createModuleFile(moduleDirectory, "src", name, "resources"))
  override var resourcesOutputDirectory: File = createModuleFile(moduleDirectory, "build", "resources", name)
  override val sourceDirectories: MutableList<File> = mutableListOf(createModuleFile(moduleDirectory, "src", name, "java"),
                                                                    createModuleFile(moduleDirectory, "src", name, "kotlin"))
  override var type: SourceSet.SourceSetType = SourceSet.SourceSetType.PRODUCTION

  fun compilerArgs(block: BaseTestCompilerArguments.() -> Unit) {
    compilerArguments.apply(block)
  }
}

open class BaseTestCompilerArguments(moduleName: String, classesOutputDirectory: String) : CompilerArguments {
  override val currentArguments: MutableList<String> = mutableListOf(
    "-Xadd-compiler-builtins",
    "-d", classesOutputDirectory,
    "-jvm-target", "1.8",
    "-Xload-builtins-from-dependencies",
    "-module-name", moduleName,
    "-no-reflect",
    "-no-stdlib")
  override val defaultArguments: MutableList<String> = mutableListOf(
    "-Xadd-compiler-builtins",
    "-d", classesOutputDirectory,
    "-jvm-target", "1.8",
    "-Xload-builtins-from-dependencies",
    "-module-name", moduleName,
    "-no-reflect",
    "-no-stdlib")
  override val compileClasspath: MutableList<File> = mutableListOf()
}

fun allOpen(name: String, block: BaseTestAllOpenModel.() -> Unit): AllOpen = BaseTestAllOpenModel(name).apply(block)

open class BaseTestAllOpenModel(override var name: String) : AllOpen {
  override val annotations: MutableList<String> = mutableListOf()
  override var modelVersion: Long = 1
  override val presets: MutableList<String> = mutableListOf()
}

fun noArg(name: String, block: BaseTestNoArgModel.() -> Unit): NoArg = BaseTestNoArgModel(name).apply(block)

open class BaseTestNoArgModel(override var name: String) : NoArg {
  override var isInvokeInitializers: Boolean = false
  override var modelVersion: Long = 1
  override val presets: MutableList<String> = mutableListOf()
  override val annotations: MutableList<String> = mutableListOf()
}

fun samWithReceiver(name: String, block: BaseTestSamWithReceiverModel.() -> Unit): SamWithReceiver = BaseTestSamWithReceiverModel(
  name).apply(block)

open class BaseTestSamWithReceiverModel(override val name: String) : SamWithReceiver {
  override val annotations: MutableList<String> = mutableListOf()
  override var modelVersion: Long = 1
  override val presets: MutableList<String> = mutableListOf()
}

fun createModuleFile(moduleDirectory: File, vararg pathComponents: String): File =
  File(moduleDirectory,
       pathComponents.joinToString(File.separator)).also {
    if (!it.exists()) {
      assertTrue("Can't create directory: ${it.absolutePath}", it.mkdirs())
    }
  }

/**
 * Android base models. These provide standard defaults for Android models.
 */
fun androidKotlinModel(moduleName: String, moduleDirectory: File, block: BaseTestKotlinModel.() -> Unit): KotlinProject =
  AndroidTestKotlinModel(moduleName, moduleDirectory).apply(block)

class AndroidTestKotlinModel(name: String, moduleDirectory: File) : BaseTestKotlinModel(name, moduleDirectory) {
  override val internalSourceSets: MutableMap<String, BaseTestKotlinSourceSet> = mutableMapOf(
    "release" to AndroidTestKotlinSourceSet("release", moduleDirectory, name),
    "debug" to AndroidTestKotlinSourceSet("debug", moduleDirectory, name),
    "releaseUnitTest" to AndroidTestKotlinSourceSet("releaseUnitTest", moduleDirectory, name),
    "debugUnitTest" to AndroidTestKotlinSourceSet("debugUnitTest", moduleDirectory, name),
    "debugAndroidTest" to AndroidTestKotlinSourceSet("debugAndroidTest", moduleDirectory, name))
  override val sourceSets: Collection<SourceSet>
    get() = internalSourceSets.values
}

class AndroidTestKotlinSourceSet(name: String, moduleDirectory: File, moduleName: String)
  : BaseTestKotlinSourceSet(name, moduleDirectory, moduleName) {
  private fun createClassesOutputDirectory() = createModuleFile(moduleDirectory, "build", "tmp", "kotlin-classes", name)

  override var type: SourceSet.SourceSetType =
    if (name.contains("test", true)) SourceSet.SourceSetType.TEST else SourceSet.SourceSetType.PRODUCTION
  override val sourceDirectories: MutableList<File> = mutableListOf()
  override val resourcesDirectories: MutableList<File> = mutableListOf()
  override var classesOutputDirectory: File = createClassesOutputDirectory()
  override var resourcesOutputDirectory: File = createClassesOutputDirectory()
  override val compilerArguments: BaseTestCompilerArguments
    = AndroidTestCompilerArguments(moduleName, createClassesOutputDirectory().absolutePath)
}

open class AndroidTestCompilerArguments(moduleName: String, classesOutputDirectory: String)
  : BaseTestCompilerArguments(moduleName, classesOutputDirectory) {
  private fun getTestClassPath() = mutableListOf("/some/really/cool/jar.jar", "/some/other/really/cool/jar.jar")
  override val currentArguments: MutableList<String> = mutableListOf(
    "-Xadd-compiler-builtins",
    "-classpath", getTestClassPath().joinToString(":"),
    "-d", classesOutputDirectory,
    "-Xload-builtins-from-dependencies",
    "-module-name", moduleName,
    "-no-reflect",
    "-no-stdlib",
    "-no-jdk",
    "-Xplugin=/some/path/to/extensions/plugin",
    "-P", "plugin:org.jetbrains.kotlin.android:configuration=some1337hash")
  override val defaultArguments: MutableList<String> = mutableListOf(
    "-Xadd-compiler-builtins",
    "-Xload-builtins-from-dependencies",
    "-module-name", moduleName,
    "-no-reflect",
    "-no-stdlib",
    "-Xplugin=/some/path/to/extensions/plugin",
    "-P", "plugin:org.jetbrains.kotlin.android:configuration=some1337hash")
  override val compileClasspath: MutableList<File> = getTestClassPath().map { File(it) }.toMutableList()
}

fun kotlinAndroidExtension(name: String, block: TestKotlinAndroidExtension.() -> Unit) = TestKotlinAndroidExtension(name).apply(block)

class TestKotlinAndroidExtension(override val name: String): KotlinAndroidExtension {
  override val defaultCacheImplementation: String? = "hashMap"
  override val isExperimental: Boolean = false
  override val modelVersion: Long = 7L
}
