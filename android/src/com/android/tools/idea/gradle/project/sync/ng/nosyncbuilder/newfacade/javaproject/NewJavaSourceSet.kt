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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.javaproject

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.javaproject.JavaSourceSet
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.JavaLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.ModuleDependency
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldJavaDependency
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldJavaSourceSet
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library.NewJavaLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library.NewModuleDependency
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.JavaProjectProto
import java.io.File

data class NewJavaSourceSet(
  override val name: String,
  override val sourceDirectories: Collection<File>,
  override val resourcesDirectories: Collection<File>,
  override val classesOutputDirectory: File,
  override val resourcesOutputDirectory: File,
  override val libraryDependencies: Collection<JavaLibrary>,
  override val moduleDependencies: Collection<ModuleDependency>) : JavaSourceSet {

  constructor(oldJavaSourceSet: OldJavaSourceSet) : this(
    oldJavaSourceSet.name,
    oldJavaSourceSet.sourceDirectories,
    oldJavaSourceSet.resourcesDirectories,
    oldJavaSourceSet.classesOutputDirectory,
    oldJavaSourceSet.resourcesOutputDirectory,
    buildLibraryDependencies(oldJavaSourceSet.compileClasspathDependencies),
    buildModuleDependencies(oldJavaSourceSet.compileClasspathDependencies)
  )

  constructor(proto: JavaProjectProto.JavaSourceSet, converter: PathConverter) : this(
    proto.name,
    proto.sourceDirectoriesList.map { converter.fileFromProto(it) },
    proto.resourcesDirectoriesList.map { converter.fileFromProto(it) },
    converter.fileFromProto(proto.classesOutputDirectory),
    converter.fileFromProto(proto.resourcesOutputDirectory),
    proto.libraryDependenciesList.map { NewJavaLibrary(it, converter) },
    proto.moduleDependenciesList.map { NewModuleDependency(it) }

  )
}

private fun buildLibraryDependencies(oldDependencies: Collection<OldJavaDependency>): Collection<JavaLibrary> {
  return oldDependencies.filter { it.project != null }.map { NewJavaLibrary(it) }
}

private fun buildModuleDependencies(oldDependencies: Collection<OldJavaDependency>): Collection<ModuleDependency> {
  return oldDependencies.filter { it.project == null }.map { NewModuleDependency(it) }
}