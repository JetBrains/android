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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.javaproject

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.JavaLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.ModuleDependency
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.JavaProjectProto
import java.io.File

/** Represents a set of sources for given configuration for [JavaProject]. */
interface JavaSourceSet {
  val name: String
  val sourceDirectories: Collection<File>
  val resourcesDirectories: Collection<File>
  val classesOutputDirectory: File
  val resourcesOutputDirectory: File
  val libraryDependencies: Collection<JavaLibrary>
  val moduleDependencies: Collection<ModuleDependency>

  fun toProto(converter: PathConverter): JavaProjectProto.JavaSourceSet = JavaProjectProto.JavaSourceSet.newBuilder()
    .setName(name)
    .addAllSourceDirectories(sourceDirectories.map { converter.fileToProto(it) })
    .addAllResourcesDirectories(resourcesDirectories.map { converter.fileToProto(it) })
    .setClassesOutputDirectory(converter.fileToProto(classesOutputDirectory))
    .setResourcesOutputDirectory(converter.fileToProto(resourcesOutputDirectory))
    .addAllLibraryDependencies(libraryDependencies.map { it.toProto(converter) })
    .addAllModuleDependencies(moduleDependencies.map { it.toProto() })
    .build()!!

}