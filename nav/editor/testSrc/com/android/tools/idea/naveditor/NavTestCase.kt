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

import com.android.test.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.naveditor.scene.TestableThumbnailManager
import com.android.tools.idea.testing.TestProjectPaths.NAVIGATION_EDITOR_BASIC
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.io.ZipUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.AndroidTestCase
import java.io.File
import java.nio.file.Paths

@Deprecated("Use NavTestRule instead")
abstract class NavTestCase(private val projectDirectory: String = NAVIGATION_EDITOR_BASIC) : AndroidTestCase() {
  // The normal test root disposable is disposed after Timer leak checking is done, which can cause problems.
  // We'll dispose this one first, so it should be used instead of getTestRootDisposable().
  protected lateinit var myRootDisposable: Disposable

  public override fun setUp() {
    super.setUp()

    @Suppress("ObjectLiteralToLambda") // Otherwise a static instance is created and used between tests.
    myRootDisposable = object : Disposable {
      override fun dispose() {}
    }
    myFixture.copyDirectoryToProject("$projectDirectory/app/src/main/java", "src")
    myFixture.copyDirectoryToProject("$projectDirectory/app/src/main/res", "res")
    myFixture.copyFileToProject("$projectDirectory/app/src/main/AndroidManifest.xml", "AndroidManifest.xml")

    for ((prebuilt, libName) in navEditorAarPaths.entries) {
      val tempDir = FileUtil.createTempDirectory("NavigationTest", null)
      val aar = File(prebuilt)
      ZipUtil.extract(aar, tempDir, null)
      val unzippedClasses = FileUtil.createTempDirectory("unzipClasses", null)
      ZipUtil.extract(File(tempDir, "classes.jar"), unzippedClasses, null)

      val virtualFileList = mutableListOf(VfsUtil.findFileByIoFile(unzippedClasses, true))
      val resFile = File(tempDir, "res")
      if (resFile.exists()) {
        virtualFileList.add(VfsUtil.findFileByIoFile(resFile, true))
      }

      val library = PsiTestUtil.addProjectLibrary(myModule, libName, virtualFileList, emptyList<VirtualFile>())
      myAdditionalModules.forEach {
        ModuleRootModificationUtil.addDependency(it, library)
      }

      myFixture.testDataPath = testDataPath
    }

    TestableThumbnailManager.register(myFacet)
  }

  override fun tearDown() {
    try {
      UIUtil.dispatchAllInvocationEvents ()
      Disposer.dispose(myRootDisposable)
      deleteManifest()
    } catch (t: Throwable){
      addSuppressedException(t)
    } finally {
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
      get() = "$navEditorPluginHome/testData"

    // Now that the Android plugin is kept in a separate place, we need to look in a relative position instead
    private val navEditorPluginHome: String
      get() {
        val adtPath = resolveWorkspacePath("tools/adt/idea/nav/editor").toString()
        return if (File(adtPath).exists()) {
          adtPath
        }
        else AndroidTestBase.getAndroidPluginHome()
      }

    @JvmStatic
    fun findVirtualProjectFile(project: Project, relativePath: String): VirtualFile? {
      val path = Paths.get(project.basePath!!, relativePath).normalize()
      return VfsUtil.findFileByIoFile(File(path.toString()), true)
    }
  }
}