/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.model.impl

import java.io.File

/** This is essentially a value class, but we don't want File as the backing field as it's not supported by the workspace model. */
@Suppress("FileEqualsUsage")
data class FileImpl(val pathString: String): File(pathString) {
  override fun toString(): String = super.toString()
  // equals/hashCode is purposely delegated to super so FileImpl equals File.
  // This is to ensure compatibility where a File is used as a key (i.e. in a map) or an identifier.
  override fun equals(other: Any?) = super.equals(other)
  override fun hashCode() = super.hashCode()
}

fun File.toImpl() = FileImpl(path)
fun List<File>.toImpl() = map { it.toImpl() }
fun <T> Map<T, File>.toImpl() = mapValues { (key, value) -> value.toImpl() }