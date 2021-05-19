/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.rendering.classloading

import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.commons.ClassRemapper
import org.jetbrains.org.objectweb.asm.commons.Remapper

/**
 * [Remapper] that renames all class references to certain packages and adds them the given prefix.
 * The names used are JVM internal names and thus separated with "/".
 */
private class RepackageRemapper(private val packagePrefixes: Collection<String>,
                                private val remappedPrefix: String) : Remapper() {
  override fun map(internalName: String): String {
    if (packagePrefixes.any { internalName.startsWith(it) }) {
      return "$remappedPrefix$internalName"
    }

    return internalName
  }
}

/**
 * Utility method to transform the strings containing the package names in their regular from "a.b.c" to its
 * disk representation "a/b/c".
 */
private fun String.fromPackageNameToBinaryName(): String = replace(".", "/")

/**
 * [ClassVisitor] that repackages certain classes with a new package name. This allows to have the same class in two separate
 * namespaces so it can
 */
class RepackageTransform(delegate: ClassVisitor,
                         packagePrefixes: Collection<String>,
                         remappedPrefix: String) :
  ClassRemapper(delegate,
                RepackageRemapper(packagePrefixes.map { it.fromPackageNameToBinaryName() }, remappedPrefix.fromPackageNameToBinaryName()))