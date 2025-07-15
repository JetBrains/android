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
@file:JvmName("NavTestUtil")

package com.android.tools.idea.naveditor

import com.android.ide.common.repository.GoogleMavenRepository
import com.android.testutils.TestUtils

private const val NAVIGATION_ID = "android.arch.navigation"
private const val SUPPORT_ID = "com.android.support"

private val REPO = TestUtils.getPrebuiltOfflineMavenRepo()
private val NAVIGATION_PATH = "$REPO/android/arch/navigation"
private val SUPPORT_PATH = "$REPO/com/android/support"

private val GMAVEN = object : GoogleMavenRepository(useNetwork = false) {
  override fun readUrlData(url: String, timeout: Int, lastModified: Long): ReadUrlDataResult =
    throw UnsupportedOperationException("Should not attempt to read from network")

  override fun error(throwable: Throwable, message: String?) =
    throw UnsupportedOperationException("Should not have i/o errors")
}

private val RUNTIME_VERSION = GMAVEN.findVersion(NAVIGATION_ID, "navigation-runtime", allowPreview = true)

val navEditorRuntimePaths: Map<String, String>
  get() {
    val commonVersion = GMAVEN.findVersion(NAVIGATION_ID, "navigation-common", allowPreview = true)

    return mapOf("$NAVIGATION_PATH/navigation-runtime/$RUNTIME_VERSION/navigation-runtime-$RUNTIME_VERSION.aar" to
                   "$NAVIGATION_ID:navigation-runtime:$RUNTIME_VERSION",
                 "$NAVIGATION_PATH/navigation-common/$commonVersion/navigation-common-$commonVersion.aar" to
                   "$NAVIGATION_ID:navigation-common:$RUNTIME_VERSION")
  }

val navEditorFragmentPaths: Map<String, String>
  get() {
    val navigationFragmentVersion = GMAVEN.findVersion(NAVIGATION_ID, "navigation-fragment", allowPreview = true)
    val supportFragmentVersion = GMAVEN.findVersion(SUPPORT_ID, "support-fragment")

    return mapOf("$NAVIGATION_PATH/navigation-fragment/$navigationFragmentVersion/navigation-fragment-$navigationFragmentVersion.aar" to
                   "$NAVIGATION_ID:navigation-fragment:$RUNTIME_VERSION",
                 "$SUPPORT_PATH/support-fragment/$supportFragmentVersion/support-fragment-$supportFragmentVersion.aar" to
                   "$SUPPORT_ID:support-fragment:$RUNTIME_VERSION")
  }

val navEditorAarPaths: Map<String, String> = navEditorRuntimePaths.plus(navEditorFragmentPaths)

