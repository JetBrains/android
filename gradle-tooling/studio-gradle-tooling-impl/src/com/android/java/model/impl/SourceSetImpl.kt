/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.java.model.impl

import com.android.java.model.JavaLibrary
import com.android.java.model.SourceSet
import java.io.File
import java.io.Serializable

/**
 * Implementation of the [SourceSet] interface.
 */
data class SourceSetImpl(private val myName: String,
                         private val mySourceDirectories: Collection<File>,
                         private val myResourcesDirectories: Collection<File>,
                         private val myClassesOutputDirectories: Collection<File>,
                         private val myResourcesOutputDirectory: File?,
                         private val myCompileClasspathDependencies: Collection<JavaLibrary>,
                         private val myRuntimeClasspathDependencies: Collection<JavaLibrary>) : SourceSet, Serializable {
  override fun getName(): String {
    return myName
  }

  override fun getSourceDirectories(): Collection<File> {
    return mySourceDirectories
  }

  override fun getResourcesDirectories(): Collection<File> {
    return myResourcesDirectories
  }

  override fun getClassesOutputDirectories(): Collection<File> {
    return myClassesOutputDirectories
  }

  override fun getResourcesOutputDirectory(): File? {
    return myResourcesOutputDirectory
  }

  override fun getCompileClasspathDependencies(): Collection<JavaLibrary> {
    return myCompileClasspathDependencies
  }

  override fun getRuntimeClasspathDependencies(): Collection<JavaLibrary> {
    return myRuntimeClasspathDependencies
  }
}
