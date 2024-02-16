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
package com.android.tools.idea.tests.gui.framework.heapassertions

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.HeavyPlatformTestCase
import java.util.WeakHashMap

class HeapAssertionsTest : HeavyPlatformTestCase() {

  override fun setUp() {
    super.setUp()
    extraRoots.clear()
    extraRoots.add(this) // add object to GC roots explicitly so that companion object is reachable
  }

  // b/112311209
  fun ignore_test_reachableDisposed() {
    instance = MyDisposable("one")
    try {
      checkReachableDisposed(true)
    }
    catch (e: HeapAssertionError) {
      println(e)
      fail("Nothing has been disposed yet")
    }
    Disposer.dispose(instance!!)
    try {
      checkReachableDisposed(true)
      fail("Failed to detect disposed instance")
    }
    catch (e: HeapAssertionError) {
      assert(e.message!!.contains(
        "Found class com.android.tools.idea.tests.gui.framework.heapassertions.HeapAssertionsTest\$MyDisposable"))
    }
    finally {
      instance = null
    }
  }

  fun test_weakHashMap() {
    val dummy = Any()
    leakyMap = WeakHashMap<Any, () -> Any>()
    leakyMap!![dummy] = { dummy } // lambda leaks the key (the leaky listener pattern)

    try {
      checkWeakSoftRefMaps(true)
      fail("Failed to detect leaky weak hash map")
    }
    catch (e: HeapAssertionError) {
      assert(e.message!!.contains(
        "reached via 'com.android.tools.idea.tests.gui.framework.heapassertions.HeapAssertionsTest\$test_weakHashMap\$1.\$dummy'"
      ))
    }
    finally {
      leakyMap = null
    }
  }

  companion object { // the companion object will naturally be a GC root.
    var instance: Disposable? = null
    var leakyMap: MutableMap<Any, () -> Any>? = null
  }

  inner class MyDisposable(val name: String) : Disposable {
    init {
      Disposer.register(testRootDisposable, this)
    }

    override fun dispose() {}
  }
}
