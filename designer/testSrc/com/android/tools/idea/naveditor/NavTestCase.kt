/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor

import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.naveditor.scene.TestableThumbnailManager
import com.android.tools.idea.naveditor.scene.ThumbnailManager
import com.android.tools.idea.testing.TestProjectPaths.NAVIGATION_EDITOR_BASIC
import com.google.common.base.Preconditions
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.io.ZipUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.navigation.NavigationSchema
import java.io.File

abstract class NavTestCase : AndroidTestCase() {
  // The normal test root disposable is disposed after Timer leak checking is done, which can cause problems.
  // We'll dispose this one first, so it should be used instead of getTestRootDisposable().
  protected lateinit var myRootDisposable: Disposable

  public override fun setUp() {
    super.setUp()

    @Suppress("ObjectLiteralToLambda") // Otherwise a static instance is created and used between tests.
    myRootDisposable = object : Disposable {
      override fun dispose() {}
    }
    myFixture.copyDirectoryToProject(NAVIGATION_EDITOR_BASIC + "/app/src/main/java", "src")
    myFixture.copyDirectoryToProject(NAVIGATION_EDITOR_BASIC + "/app/src/main/res", "res")
    myFixture.copyFileToProject(NAVIGATION_EDITOR_BASIC + "/app/src/main/AndroidManifest.xml", "AndroidManifest.xml")
    val tempDir = FileUtil.createTempDirectory("NavigationTest", null)
    val classesDir = FileUtil.createTempDirectory("NavigationTestClasses", null)
    for ((i, prebuilt) in PREBUILT_AAR_PATHS.withIndex()) {
      val aar = File(PathManager.getHomePath(), prebuilt)
      ZipUtil.extract(aar, tempDir, null)
      val classes = File(classesDir, "classes$i.jar")
      Preconditions.checkState(File(tempDir, "classes.jar").renameTo(classes))
      PsiTestUtil.addLibrary(myFixture.module, classes.path)

      myFixture.testDataPath = tempDir.path
      myFixture.copyDirectoryToProject("res", "res")

      myFixture.testDataPath = testDataPath
    }

    TestableThumbnailManager.register(myFacet)
    System.setProperty(NavigationSchema.ENABLE_NAV_PROPERTY, "true")
  }

  override fun tearDown() {
    try {
      Disposer.dispose(myRootDisposable)
      val thumbnailManager = ThumbnailManager.getInstance(myFacet)
      (thumbnailManager as? TestableThumbnailManager)?.deregister()
      deleteManifest()
    }
    finally {
      super.tearDown()
    }
  }

  protected fun model(name: String, f: () -> ComponentDescriptor): SyncNlModel {
    return modelBuilder(name, f).build()
  }

  protected fun modelBuilder(name: String, f: () -> ComponentDescriptor): ModelBuilder {
    return NavModelBuilderUtil.model(name, myFacet, myFixture, f)
  }

  companion object {

    val TAG_NAVIGATION = "navigation"
    private val PREBUILT_AAR_PATHS = arrayOf(
        "../../prebuilts/tools/common/m2/repository/android/arch/navigation/runtime/0.6.0-alpha1/runtime-0.6.0-alpha1.aar",
        "../../prebuilts/tools/common/m2/repository/com/android/support/support-fragment/27.0.2/support-fragment-27.0.2.aar")

    val testDataPath: String
      get() = designerPluginHome + "/testData"

    // Now that the Android plugin is kept in a separate place, we need to look in a relative position instead
    private val designerPluginHome: String
      get() {
        val adtPath = PathManager.getHomePath() + "/../adt/idea/designer"
        return if (File(adtPath).exists()) {
          adtPath
        }
        else AndroidTestBase.getAndroidPluginHome()
      }
  }
}
