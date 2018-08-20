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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.FileProto
import org.apache.commons.io.FilenameUtils
import org.gradle.internal.impldep.org.apache.commons.lang.StringUtils
import java.io.File
import java.nio.file.Paths

const val GLOBAL_LIBRARY_MAP_CACHE_PATH = "global_library_map.json"
const val ANDROID_PROJECT_CACHE_PATH = "android_project.json"
const val VARIANTS_CACHE_DIR_PATH = "variants"
const val GRADLE_PROJECT_CACHE_PATH = "gradle_project.json"
const val JAVA_PROJECT_CACHE_PATH = "java_project.json"
const val OFFLINE_REPO_PATH = "offline_repo"
const val BUNDLE_PATH = "bundles"

fun projectNameFromProjectLocation(location: String) = StringUtils.substringAfterLast(location, File.separator)!!
fun getProjectCacheDir(activity: String, minApi: Int, targetApi: Int) = "${activity}_min_${minApi}_build_and_target_${targetApi}"

fun artifactAddressToRelativePath(artifactAddress: String): String {
  val (pkg, name, version) = artifactAddress.dropLast(4).split(':')

  return pkg.replace('.', File.separatorChar) + File.separator + name + File.separator + version
}

fun newProtoFile(relativePath: String, relativeTo: PathConverter.DirType) = FileProto.File.newBuilder()
  .setRelativePath(relativePath)
  .setRelativeTo(FileProto.File.RelativeTo.valueOf(relativeTo.name))
  .build()!!
