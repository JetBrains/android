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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.naveditor.scene.TestableThumbnailManager
import com.android.tools.idea.testing.TestProjectPaths.NAVIGATION_EDITOR_BASIC
import com.google.common.base.Preconditions
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.io.ZipUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.AndroidTestCase
import java.io.File

abstract class NavTestCase : AndroidTestCase() {

  public override fun setUp() {
    super.setUp()

    myFixture.copyDirectoryToProject("$NAVIGATION_EDITOR_BASIC/app/src/main/java", "src")
    myFixture.copyDirectoryToProject("$NAVIGATION_EDITOR_BASIC/app/src/main/res", "res")
    myFixture.copyFileToProject("$NAVIGATION_EDITOR_BASIC/app/src/main/AndroidManifest.xml", "AndroidManifest.xml")

    if (!StudioFlags.IN_MEMORY_R_CLASSES.get()) {
      myFixture.copyDirectoryToProject("$NAVIGATION_EDITOR_BASIC/app/gen", "gen")
    }

    val tempDir = FileUtil.createTempDirectory("NavigationTest", null)
    val classesDir = FileUtil.createTempDirectory("NavigationTestClasses", null)
    for ((i, prebuilt) in navEditorAarPaths.withIndex()) {
      val aar = File(PathManager.getHomePath(), prebuilt)
      ZipUtil.extract(aar, tempDir, null)
      val classes = File(classesDir, "classes$i.jar")
      Preconditions.checkState(File(tempDir, "classes.jar").renameTo(classes))
      PsiTestUtil.addLibrary(myFixture.module, classes.path)

      myFixture.testDataPath = tempDir.path

      val values = File(tempDir, "res/values/values.xml")
      if (values.exists()) {
        Preconditions.checkState(values.renameTo(File(tempDir, "res/values/values$i.xml")))
      }

      myFixture.copyDirectoryToProject("res", "res")
      myFixture.testDataPath = testDataPath
    }

    TestableThumbnailManager.register(myFacet, project)
    StudioFlags.ENABLE_NAV_EDITOR.override(true)
  }

  override fun tearDown() {
    try {
      deleteManifest()
      StudioFlags.ENABLE_NAV_EDITOR.clearOverride()
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

    const val TAG_NAVIGATION = "navigation"

    val testDataPath: String
      get() = "$designerPluginHome/testData"

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
