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
interface IdeLibrary {

  /** Returns the artifact location.  */
  val artifact: File

  /**
   * Returns the location of the lint jar. The file may not point to an existing file.
   *
   * Only valid for Android Library
   */
  val lintJar: String?

  /**
   * Returns whether the dependency is on the compile class path but is not on the runtime class
   * path.
   */
  val isProvided: Boolean
}

interface IdeArtifactLibrary: IdeLibrary {
  /**
   * Returns the artifact address in a unique way.
   *
   *
   * This is either a module path for sub-modules (with optional variant name), or a maven
   * coordinate for external dependencies.
   */
  val artifactAddress: String
}

interface IdeAndroidLibrary: IdeArtifactLibrary {
  /**
   * Returns the location of the unzipped bundle folder.
   */
  val folder: File?

  /**
   * Returns the location of the manifest relative to the folder.
   */
  val manifest: String

  /**
   * The list of jar files for compilation.
   */
  val compileJarFiles: List<String>

  /**
   * The list of jar files for runtime/packaging.
   * This corresponds the the AAR main jar file and the localJars.
   */
  val runtimeJarFiles: List<String>

  /**
   * Returns the location of the res folder. The file may not point to an existing folder.
   */
  val resFolder: String

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
  val assetsFolder: String

  /**
   * Returns the location of the jni libraries folder. The file may not point to an existing folder.
   */
  val jniFolder: String

    /**
   * Returns the location of the aidl import folder. The file may not point to an existing folder.
   */
  val aidlFolder: String

  /**
   * Returns the location of the renderscript import folder. The file may not point to an existing folder.
   */
  val renderscriptFolder: String

  /**
   * Returns the location of the proguard files. The file may not point to an existing file.
   */
  val proguardRules: String

  /**
   * Returns the location of the external annotations zip file (which may not exist).
   */
  val externalAnnotations: String

  /**
   * Returns the location of an optional file that lists the only resources that should be
   * considered public. The file may not point to an existing file.
   */
  val publicResources: String

  /**
   * Returns the location of the text symbol file
   */
  val symbolFile: String
}

interface IdeJavaLibrary: IdeArtifactLibrary

interface IdeModuleLibrary: IdeLibrary {
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
  val buildId: String?
}
