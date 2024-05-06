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
package org.jetbrains.android

import com.android.SdkConstants.DOT_JAVA
import com.android.test.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.tests.AdtTestProjectDescriptors
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.visibility.VisibilityInspection
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.LightProjectDescriptor
import com.siyeh.ig.LightJavaInspectionTestCase
import org.intellij.lang.annotations.Language

class FragmentMustBePublicTest : LightJavaInspectionTestCase() {
  private var myVisibilityInspection: VisibilityInspection? = null

  override fun getInspection(): LocalInspectionTool? {
    return myVisibilityInspection!!.sharedLocalInspectionTool
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = AdtTestProjectDescriptors.java()

  override fun setUp() {
    // Compute the workspace root before any IDE code starts messing with user.dir:
    getWorkspaceRoot()
    VfsRootAccess.allowRootAccess(testRootDisposable,
                                  FileUtil.toCanonicalPath(AndroidTestBase.getAndroidPluginHome()))
    myVisibilityInspection = createTool()
    super.setUp()
  }

  override fun tearDown() {
    myVisibilityInspection = null
    super.tearDown()
  }

  private fun createTool(): VisibilityInspection? {
    val inspection = VisibilityInspection()
    inspection.SUGGEST_PRIVATE_FOR_INNERS = true
    inspection.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true
    inspection.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true
    return inspection
  }

  fun testCannotWeaken() {
    myFixture.allowTreeAccessForAllFiles()
    addJavaFile("test/pkg/WeakTest.java", """
      package test.pkg;

      @SuppressWarnings({"unused", "deprecation", "RedundantSuppression", "SpellCheckingInspection"})
      public class WeakTest {
          public static abstract class MyService extends android.app.Service { }
          public abstract static class MyApplication extends android.app.Application { }
          public static class MyAndroidFragment extends android.app.Fragment { }
          public static class MyAndroidxFragment extends androidx.fragment.app.Fragment { }
          public static class MySupportFragment extends android.support.v4.app.Fragment { }
          public abstract static class MyContentProvider extends android.content.ContentProvider { }
          public abstract static class MyReceiver extends android.content.BroadcastReceiver { }
          public abstract static class MyParcelable implements android.os.Parcelable { }
          public abstract static class MyBackupAgent extends android.app.backup.BackupAgent { }
          public static class MyView extends android.view.View { }
          public abstract static class MyActionProvider extends android.view.ActionProvider { }
          <warning descr="Access can be 'private'">public</warning> static class Pojo { }

          private Pojo pojo;
          private MyService myService;
          private MyApplication myApplication;
          private MyAndroidFragment myAndroidFragment;
          private MyAndroidxFragment myAndroidxFragment;
          private MySupportFragment mySupportFragment;
          private MyContentProvider myContentProvider;
          private MyReceiver myReceiver;
          private MyParcelable myParcelable;
          private MyBackupAgent myBackupAgent;
          private MyView myView;
          private MyActionProvider myActionProvider;
      }
      """
    )

    // Stubs; this test doesn't have access to the Android SDK directly

    addStubClass("android.app.Application")
    addStubClass("android.app.Service")
    addStubClass("android.app.Fragment")
    addStubClass("androidx.fragment.app.Fragment")
    addStubClass("android.support.v4.app.Fragment")
    addStubClass("android.content.ContentProvider")
    addStubClass("android.content.BroadcastReceiver")
    addStubClass("android.os.Parcelable", isInterface = true)
    addStubClass("android.app.backup.BackupAgent")
    addStubClass("android.view.View")
    addStubClass("android.view.ActionProvider")

    myFixture.configureByFiles("test/pkg/WeakTest.java")
    myFixture.checkHighlighting()
  }

  private fun addStubClass(fqcn: String, isInterface: Boolean = false) {
    val relativePath = fqcn.replace('.', '/') + DOT_JAVA
    val index = fqcn.lastIndexOf('.')
    val pkg = fqcn.substring(0, index)
    val cls = fqcn.substring(index + 1)
    val text = "package $pkg\npublic ${if (isInterface) "interface" else "class"} $cls { }"
    myFixture.addFileToProject(relativePath, text)
  }

  @Suppress("SameParameterValue")
  private fun addJavaFile(relativePath: String,
                          @Language("JAVA") text: String) {
    myFixture.addFileToProject(relativePath, text.trimIndent())
  }
}