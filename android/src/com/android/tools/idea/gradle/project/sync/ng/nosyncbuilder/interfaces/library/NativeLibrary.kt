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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.LibraryProto
import java.io.File

interface NativeLibrary : Library {
  /** The ABI of the library. */
  val abi: String
  /** The name of the toolchain used to compile the native library. */
  val toolchainName: String
  /* A list of compiler flags for C code. */
  val cCompilerFlags: Collection<String>
  /* A list of compiler flags for C++ code. */
  val cppCompilerFlags: Collection<String>
  /* TODO: document */
  val debuggableLibraryFolders: Collection<File>

  fun toProto(converter: PathConverter) = LibraryProto.NativeLibrary.newBuilder()
    .setLibrary(LibraryProto.Library.newBuilder().setArtifactAddress(artifactAddress))
    .setAbi(abi)
    .setToolchainName(toolchainName)
    .addAllCCompilerFlags(cCompilerFlags)
    .addAllCppCompilerFlags(cppCompilerFlags)
    .addAllDebuggableLibraryFolders(debuggableLibraryFolders.map { converter.fileToProto(it) }) // TODO add dir argument
    .build()!!
}
