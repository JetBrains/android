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
package com.android.tools.idea.editing.actions


import com.android.SdkConstants
import com.android.test.testutils.TestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val BASE_PATH = "actions/moveActivity/"

@RunWith(JUnit4::class)
class AndroidMoveActivityTest {
  @get:Rule
  var androidProjectRule: AndroidProjectRule = AndroidProjectRule.onDisk()

  @get:Rule
  var name: TestName = TestName()

  private val myFixture by lazy {
    androidProjectRule.fixture.apply {
      testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/android/editing/testData").toString()
    } as JavaCodeInsightTestFixture
  }

  private val myProject by lazy { androidProjectRule.project }

  @Test
  fun moveActivity_referenceIsUpdatedInManifest() {
    createActivity()
    myFixture.addFileToProject(
      SdkConstants.FN_ANDROID_MANIFEST_XML,
      getManifestText(activityName = "p1.p2.MyActivity", manifestPackage = "p3"))

    moveClassToPackage("p1.p2.MyActivity", "p3")

    myFixture.checkResult(
      SdkConstants.FN_ANDROID_MANIFEST_XML,
      getManifestText(activityName = ".MyActivity", manifestPackage = "p3"),
      true)
  }

  @Test
  fun moveActivity_referenceIsUpdatedInManifestWithDifferentPackage() {
    createActivity()
    myFixture.addFileToProject(
      SdkConstants.FN_ANDROID_MANIFEST_XML,
      getManifestText(activityName = ".MyActivity", manifestPackage = "p1.p2"))

    moveClassToPackage("p1.p2.MyActivity", "p1.p3")

    myFixture.checkResult(
      SdkConstants.FN_ANDROID_MANIFEST_XML,
      getManifestText(activityName = "p1.p3.MyActivity", manifestPackage = "p1.p2"),
      true)
  }

  private fun doMoveActivityByRenamingPackageTest(packageName: String, newPackageName: String) {
    // Create MyActivity
    createActivity()

    // Copy and open manifest file
    val manifestFile = myFixture.copyFileToProject(BASE_PATH + name.methodName + ".xml", SdkConstants.FN_ANDROID_MANIFEST_XML)
    myFixture.configureFromExistingVirtualFile(manifestFile)

    // Find the package to rename
    val packageToRename = runReadAction { myFixture.javaFacade.findPackage(packageName) }
    assertThat(packageToRename).isNotNull()

    // Do the rename of the package
    ApplicationManager.getApplication().invokeAndWait {
      RenameProcessor(myProject, packageToRename!!, newPackageName, true, true).run()
    }

    // Verify that the manifest was updated as expected.
    myFixture.checkResultByFile(BASE_PATH + name.methodName + "_after.xml")
  }

  @Test
  fun moveActivityByRenamingPackage1() {
    doMoveActivityByRenamingPackageTest("p1.p2", "p3")
  }

  @Test
  fun moveActivityByRenamingPackage2() {
    doMoveActivityByRenamingPackageTest("p1.p2", "p3")
  }

  @Test
  fun moveActivityByRenamingPackage3() {
    doMoveActivityByRenamingPackageTest("p1", "p3")
  }

  @Test
  fun moveActivityByRenamingPackage4() {
    doMoveActivityByRenamingPackageTest("p1.p2", "p3")
  }

  @Test
  fun moveActivityByRenamingPackage5() {
    doMoveActivityByRenamingPackageTest("p1", "p3")
  }

  private fun createActivity() {
    myFixture.addFileToProject(
      "src/p1/p2/MyActivity.java",
      //language=Java
      """
      package p1.p2;
      public class MyActivity extends android.app.Activity {}
      """.trimIndent()
    )
  }

  private fun getManifestText(activityName: String, manifestPackage: String) = """
    <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$manifestPackage">
        <application>
            <activity android:name="$activityName"/>
        </application>
    </manifest>
  """.trimIndent()

  private fun moveClassToPackage(className: String, newParentPackageName: String) {
    // Create any class in the new package so we can get a PsiPackage below.
    myFixture.addClass("""
      package $newParentPackageName;
      class Foo {}
    """.trimIndent())

    val psiClass = runReadAction { myFixture.javaFacade.findClass(className, GlobalSearchScope.projectScope(myProject)) }

    val newParentPackage = runReadAction { myFixture.javaFacade.findPackage(newParentPackageName) }
    assertThat(newParentPackage).isNotNull()

    val dirs = newParentPackage!!.directories
    assertThat(dirs).hasLength(1)

    val processor = runReadAction {
      MoveClassesOrPackagesProcessor(myProject,
                                     arrayOf(psiClass),
                                     SingleSourceRootMoveDestination(PackageWrapper.create(newParentPackage), dirs.single()),
                                     true,
                                     false,
                                     null)
    }

    ApplicationManager.getApplication().invokeAndWait { processor.run() }
  }
}
