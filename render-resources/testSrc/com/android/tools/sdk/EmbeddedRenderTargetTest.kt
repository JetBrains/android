/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.sdk

import com.android.sdklib.AndroidVersion
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.OptionalLibrary
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.nio.file.Path

class EmbeddedRenderTargetTest {
  private val fakeTarget = object : IAndroidTarget {
    override fun compareTo(other: IAndroidTarget?): Int {
      TODO("Not yet implemented")
    }

    override fun getLocation(): String {
      TODO("Not yet implemented")
    }

    override fun getVendor(): String {
      TODO("Not yet implemented")
    }

    override fun getName(): String {
      TODO("Not yet implemented")
    }

    override fun getFullName(): String {
      TODO("Not yet implemented")
    }

    override fun getClasspathName(): String {
      TODO("Not yet implemented")
    }

    override fun getShortClasspathName(): String {
      TODO("Not yet implemented")
    }

    override fun getVersion() = AndroidVersion(34)

    override fun getVersionName(): String {
      TODO("Not yet implemented")
    }

    override fun getRevision(): Int {
      TODO("Not yet implemented")
    }

    override fun isPlatform(): Boolean {
      TODO("Not yet implemented")
    }

    override fun getParent(): IAndroidTarget? {
      TODO("Not yet implemented")
    }

    override fun getPath(pathId: Int): Path {
      TODO("Not yet implemented")
    }

    override fun getBuildToolInfo(): BuildToolInfo? {
      TODO("Not yet implemented")
    }

    override fun getBootClasspath(): MutableList<String> {
      TODO("Not yet implemented")
    }

    override fun getOptionalLibraries(): MutableList<OptionalLibrary> {
      TODO("Not yet implemented")
    }

    override fun getAdditionalLibraries(): MutableList<OptionalLibrary> {
      TODO("Not yet implemented")
    }

    override fun hasRenderingLibrary(): Boolean {
      TODO("Not yet implemented")
    }

    override fun getSkins(): Array<Path> {
      TODO("Not yet implemented")
    }

    override fun getDefaultSkin(): Path? {
      TODO("Not yet implemented")
    }

    override fun getPlatformLibraries(): Array<String> {
      TODO("Not yet implemented")
    }

    override fun getProperty(name: String?): String {
      TODO("Not yet implemented")
    }

    override fun getProperties(): MutableMap<String, String> {
      TODO("Not yet implemented")
    }

    override fun canRunOn(target: IAndroidTarget?): Boolean {
      TODO("Not yet implemented")
    }

    override fun hashString(): String {
      TODO("Not yet implemented")
    }
  }

  private val isWindows = System.getProperty("os.name").toLowerCase().startsWith("win")

  @Before
  fun setUp() {
    EmbeddedRenderTarget.resetRenderTarget()
  }

  @Test
  fun testWithoutSeparator() {
    val target = EmbeddedRenderTarget.getCompatibilityTarget(fakeTarget) { "/foo" }

    if (isWindows) {
      assertEquals("C:\\foo\\data", target.getPath(IAndroidTarget.DATA).toFile().absolutePath)
      assertEquals("C:\\foo\\data\\framework_res.jar", target.getPath(IAndroidTarget.RESOURCES).toFile().absolutePath)
      assertEquals("C:\\foo\\data\\fonts", target.getPath(IAndroidTarget.FONTS).toFile().absolutePath)
    } else {
      assertEquals("/foo/data", target.getPath(IAndroidTarget.DATA).toFile().absolutePath)
      assertEquals("/foo/data/framework_res.jar", target.getPath(IAndroidTarget.RESOURCES).toFile().absolutePath)
      assertEquals("/foo/data/fonts", target.getPath(IAndroidTarget.FONTS).toFile().absolutePath)
    }
  }

  @Test
  fun testWithSeparator() {
    val target = EmbeddedRenderTarget.getCompatibilityTarget(fakeTarget) { "/foo/" }

    if (isWindows) {
      assertEquals("C:\\foo\\data", target.getPath(IAndroidTarget.DATA).toFile().absolutePath)
      assertEquals("C:\\foo\\data\\framework_res.jar", target.getPath(IAndroidTarget.RESOURCES).toFile().absolutePath)
      assertEquals("C:\\foo\\data\\fonts", target.getPath(IAndroidTarget.FONTS).toFile().absolutePath)
    } else {
      assertEquals("/foo/data", target.getPath(IAndroidTarget.DATA).toFile().absolutePath)
      assertEquals("/foo/data/framework_res.jar", target.getPath(IAndroidTarget.RESOURCES).toFile().absolutePath)
      assertEquals("/foo/data/fonts", target.getPath(IAndroidTarget.FONTS).toFile().absolutePath)
    }
  }

  @Test
  fun testMultilevel() {
    val target = EmbeddedRenderTarget.getCompatibilityTarget(fakeTarget) { "/foo/bar" }

    if (isWindows) {
      assertEquals("C:\\foo\\bar\\data", target.getPath(IAndroidTarget.DATA).toFile().absolutePath)
      assertEquals("C:\\foo\\bar\\data\\framework_res.jar", target.getPath(IAndroidTarget.RESOURCES).toFile().absolutePath)
      assertEquals("C:\\foo\\bar\\data\\fonts", target.getPath(IAndroidTarget.FONTS).toFile().absolutePath)
    } else {
      assertEquals("/foo/bar/data", target.getPath(IAndroidTarget.DATA).toFile().absolutePath)
      assertEquals("/foo/bar/data/framework_res.jar", target.getPath(IAndroidTarget.RESOURCES).toFile().absolutePath)
      assertEquals("/foo/bar/data/fonts", target.getPath(IAndroidTarget.FONTS).toFile().absolutePath)
    }
  }
}