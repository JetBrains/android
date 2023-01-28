/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.model

import java.io.File

/**
 * Represent a variant/module/artifact dependency.
 */
sealed interface IdeLibrary {
  /**
   * Returns the location of the lint jar. The file may not point to an existing file.
   *
   * Only valid for Android Library
   */
  val lintJar: File?
}

sealed interface IdeArtifactLibrary : IdeLibrary {
  /**
   * Returns the artifact address in a unique way.
   *
   *
   * This is either a module path for sub-modules (with optional variant name), or a maven
   * coordinate for external dependencies.
   */
  val artifactAddress: String

  /**
   * The name to be used to represent the library in the IDE.
   */
  val name: String

  /**
   * Returns the location of the sources jar.
   * This is only available from AGP version 8.1.0-alpha08.
   */
  val srcJar: File?

  /**
   * Returns the location of the java doc jar.
   * This is only available from AGP version 8.1.0-alpha08.
   */
  val docJar: File?

  /**
   * Returns the location of the samples jar.
   * This is only available from AGP version 8.1.0-alpha08.
   */
  val samplesJar: File?
}

interface IdeAndroidLibrary : IdeArtifactLibrary {
  /** Returns the artifact location.  */
  val artifact: File?

  /**
   * Returns the location of the unzipped bundle folder.
   */
  val folder: File?

  /**
   * Returns the location of the manifest relative to the folder.
   */
  val manifest: File

  /**
   * The list of jar files for compilation.
   */
  val compileJarFiles: List<File>

  /**
   * The list of jar files for runtime/packaging.
   * This corresponds the the AAR main jar file and the localJars.
   */
  val runtimeJarFiles: List<File>

  /**
   * Returns the location of the res folder. The file may not point to an existing folder.
   */
  val resFolder: File

  /**
   * Returns the location of the namespaced resources static library (res.apk). Null if the library is not namespaced.
   *
   * TODO(b/109854607): When rewriting dependencies, this should be populated with the
   * rewritten artifact, which will not be in the exploded AAR directory.
   */
  val resStaticLibrary: File?

  /**
   * Returns the location of the assets folder. The file may not point to an existing folder.
   */
  val assetsFolder: File

  /**
   * Returns the location of the jni libraries folder. The file may not point to an existing folder.
   */
  val jniFolder: File

  /**
   * Returns the location of the aidl import folder. The file may not point to an existing folder.
   */
  val aidlFolder: File

  /**
   * Returns the location of the renderscript import folder. The file may not point to an existing folder.
   */
  val renderscriptFolder: File

  /**
   * Returns the location of the proguard files. The file may not point to an existing file.
   */
  val proguardRules: File

  /**
   * Returns the location of the external annotations zip file (which may not exist).
   */
  val externalAnnotations: File

  /**
   * Returns the location of an optional file that lists the only resources that should be
   * considered public. The file may not point to an existing file.
   */
  val publicResources: File

  /**
   * Returns the location of the text symbol file
   */
  val symbolFile: File
}

interface IdeJavaLibrary : IdeArtifactLibrary {
  /** Returns the artifact location.  */
  val artifact: File
}

/**
 * A source set in an IDE module group.
 */
interface IdeModuleSourceSet {
  val sourceSetName: String
  val canBeConsumed: Boolean
}

/**
 * An Android or Java well-known source set in an IDE module group.
 *
 * Android source sets names are pre-defined and cannot be changed in Gradle configuration by users. In Java and KMP worlds source set
 * naming is more flexible. Note tha in case of source set name collision the original intent is assumed.
 */
enum class IdeModuleWellKnownSourceSet(
  override val sourceSetName: String,
  override val canBeConsumed: Boolean
) : IdeModuleSourceSet {
  /**
   * An Android source set or a special source set in Java/KMP, which is built by default Gradle tasks and on which other
   * project would depend on unless intentionally changed in the Gradle configuration.
   */
  MAIN("main", true),

  /**
   * A source set with text fixtures supported by the Android Gradle plugin and 'java-test-fixtures' plugin.
   */
  TEST_FIXTURES("testFixtures", true),

  UNIT_TEST("unitTest", false),
  ANDROID_TEST("androidTest", false);

  companion object {
    fun fromName(name: String): IdeModuleWellKnownSourceSet? = values().firstOrNull { it.sourceSetName == name }
  }
}

interface IdeModuleLibrary : IdeLibrary {
  /**
   * Returns the gradle path.
   */
  val projectPath: String

  /**
   * Returns an optional variant name if the consumed artifact of the library is associated to
   * one.
   */
  val variant: String?

  /**
   * Returns the build id.
   */
  val buildId: String

  /**
   * Returns the sourceSet associated with the library.
   */
  val sourceSet: IdeModuleSourceSet
}

interface IdeUnknownLibrary: IdeLibrary {
  val key: String
}

