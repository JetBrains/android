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
import com.android.java.model.LibraryVersion
import java.io.File
import java.io.Serializable

/**
 * Implementation of the [JavaLibrary] interface.
 */
data class JavaLibraryImpl(
  private val myProject: String?,
  private val myBuildId: String?,
  private val myName: String,
  private val myJarFile: File?,
  private val myLibraryVersion: LibraryVersion?) : JavaLibrary, Serializable {

  override fun getProject(): String? {
    return myProject
  }

  override fun getBuildId(): String? {
    return myBuildId
  }

  override fun getName(): String {
    return myName
  }

  override fun getJarFile(): File? {
    return myJarFile
  }

  override fun getSource(): File? {
    return null
  }

  override fun getJavadoc(): File? {
    return null
  }

  override fun getLibraryVersion(): LibraryVersion? {
    return myLibraryVersion
  }
}
