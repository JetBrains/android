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

import org.jetbrains.org.objectweb.asm.ClassVisitor

/**
 * Converts a list of [ClassVisitor] transformations into a transformation applied to all the visitors sequentially.
 */
fun multiTransformOf(vararg transforms: (ClassVisitor) -> (ClassVisitor)): java.util.function.Function<ClassVisitor, ClassVisitor> =
  java.util.function.Function<ClassVisitor, ClassVisitor> { transforms.fold(it) { acc, visitor -> visitor(acc) } }

fun combine(f1: java.util.function.Function<ClassVisitor, ClassVisitor>, f2: java.util.function.Function<ClassVisitor, ClassVisitor>) =
  java.util.function.Function<ClassVisitor, ClassVisitor> { f2.apply(f1.apply(it)) }