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
package com.android.tools.idea.rendering.classloading

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Functions
import com.google.common.hash.Hashing
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

private object EmptyClassVisitor: ClassVisitor(Opcodes.ASM9)

/**
 * Interface to be implemented by [ClassVisitor]s to ensure that the transformation applied is stable. If two [ClassVisitor]s return the
 * same uniqueId, they should output the same for the same input.
 */
interface ClassVisitorUniqueIdProvider {
  val uniqueId: String
}

/**
 * Returns the unique id for the given [ClassVisitor]. If the ClassVisitor, implements [ClassVisitorUniqueIdProvider], then it is used to
 * obtain the transformation id. If the [ClassVisitor] does not implement it, the instance id of the [ClassVisitor] will be used since
 * we can not guarantee that two different instances apply the same transformation.
 */
private fun ClassVisitor.uniqueId(): String = if (this is ClassVisitorUniqueIdProvider)
  uniqueId
else
  "${this::class.qualifiedName}:${System.identityHashCode(this).toString(16)}"

/**
 * Class that represents a group of [ClassVisitor] to be applied to an input class that will generated a transformed output.
 *
 * A [ClassTransform] also contains an id that allows identifying the transformations done by this transform. If the id of two class transforms
 * is the same, the transformation applied by both is the same.
 */
class ClassTransform(private val transforms: List<java.util.function.Function<ClassVisitor, ClassVisitor>>) {
  @VisibleForTesting
  val debugId: String
    get() = java.util.function.Function<Pair<String, ClassVisitor>, Pair<String, ClassVisitor>> {
      transforms.fold(it) {
        acc, visitor ->
          val newVisitor = visitor.apply(acc.second)
          if (newVisitor != acc.second) {
            "${newVisitor.uniqueId()}\n${acc.first}" to newVisitor
          } else {
            // The provider was the identity, skip this in the uniqueId output.
            acc.first to newVisitor
          }
      }
    }.apply("" to EmptyClassVisitor).first

  val id: String by lazy {
    @Suppress("UnstableApiUsage")
    Hashing.goodFastHash(64).hashString(debugId, Charsets.UTF_8).toString()
  }

  operator fun invoke(visitor: ClassVisitor): ClassVisitor =
    java.util.function.Function<ClassVisitor, ClassVisitor> { transforms.fold(it) { acc, visitor -> visitor.apply(acc) } }.apply(visitor)
  operator fun plus(f2: ClassTransform) = ClassTransform(transforms + f2.transforms)
  operator fun plus(f2: List<java.util.function.Function<ClassVisitor, ClassVisitor>>) = ClassTransform(transforms + f2)

  companion object {
    @JvmStatic
    val identity = ClassTransform(listOf(Functions.identity()))
  }
}

/**
 * Combines two [ClassTransform]s into a new one containing the transformations of both combined.
 */
fun combine(f1: ClassTransform, f2: ClassTransform) = f1 + f2

/**
 * Converts a list of [ClassVisitor] transformations into a transformation applied to all the visitors sequentially.
 */
@SafeVarargs
fun toClassTransform(vararg transforms: java.util.function.Function<ClassVisitor, ClassVisitor>): ClassTransform = ClassTransform(transforms.toList())

/**
 * Utility method to transform the strings containing the package names in their regular from "a.b.c" to its
 * disk representation "a/b/c".
 */
fun String.fromPackageNameToBinaryName(): String = replace(".", "/")

/**
 * Utility method to transform the strings from the disk representation "a/b/c" to the package names in their regular from "a.b.c".
 */
fun String.fromBinaryNameToPackageName(): String = replace("/", ".")