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
import org.jetbrains.org.objectweb.asm.commons.ClassRemapper
import org.jetbrains.org.objectweb.asm.commons.Remapper

private const val TRACKING_THREAD_LOCAL_FQCN = "com/android/layoutlib/reflection/TrackingThreadLocal"

/**
 * This bytecode manipulation transform replaces all the references to [java.lang.ThreadLocal] with
 * [com.android.layoutlib.reflection.TrackingThreadLocal]. See [com.android.layoutlib.reflection.TrackingThreadLocal] for more details.
 */
class ThreadLocalRenameTransform(val delegate: ClassVisitor) : ClassRemapper(delegate, ThreadLocalRemapper)

private object ThreadLocalRemapper : Remapper() {
  override fun map(internalName: String?): String {
    if (internalName == "java/lang/ThreadLocal") {
      return TRACKING_THREAD_LOCAL_FQCN
    }
    return super.map(internalName)
  }
}