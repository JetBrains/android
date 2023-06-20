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
import org.jetbrains.org.objectweb.asm.Opcodes

/**
 * [ClassVisitor] that ensures that every class version number is between [minVersion] and [maxVersion].
 */
class VersionClassTransform(delegate: ClassVisitor,
                            private val maxVersion: Int,
                            private val minVersion: Int) : ClassVisitor(Opcodes.ASM9, delegate), ClassVisitorUniqueIdProvider {
  override val uniqueId: String = "${VersionClassTransform::class.qualifiedName},$maxVersion,$minVersion"

  override fun visit(version: Int,
                     access: Int,
                     name: String,
                     signature: String?,
                     superName: String?,
                     interfaces: Array<String>?) =
    super.visit(version.coerceIn(minVersion, maxVersion), access, name, signature, superName, interfaces)
}