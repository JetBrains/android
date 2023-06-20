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
package com.android.tools.idea.bleak

class JniBleakHelper: BleakHelper() {

  private val Any.isPrimitiveArray: Boolean
    get() = javaClass.isArray && javaClass.componentType.isPrimitive

  override fun computeLoadedClasses(): List<Any> {
    return allLoadedClasses0().toList()
  }

  override fun pauseThreads() {
    pauseThreads0(Thread.currentThread().name)
  }

  override fun resumeThreads() {
    resumeThreads0(Thread.currentThread().name)
  }

  companion object {

    init {
      try {
        System.loadLibrary("jnibleakhelper")
      } catch (e: UnsatisfiedLinkError) {
        println("Couldn't load BLeak JNI library")
      }
    }

    @JvmStatic private external fun allLoadedClasses0(): Array<Any>
    @JvmStatic private external fun gcRoots(): Array<Any>
    @JvmStatic private external fun pauseThreads0(testThreadName: String)
    @JvmStatic private external fun resumeThreads0(testThreadName: String)
  }
}
