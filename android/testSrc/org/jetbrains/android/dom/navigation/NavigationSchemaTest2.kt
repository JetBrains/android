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
package org.jetbrains.android.dom.navigation

import com.android.SdkConstants
import com.android.tools.idea.naveditor.navEditorAarPaths
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.io.ZipUtil
import org.jetbrains.android.AndroidTestCase
import java.io.File

class NavigationSchemaTest2 : AndroidTestCase() {
  fun testNoLibrary() {
    try {
      NavigationSchema.createIfNecessary(myModule)
      fail("Expected ClassNotFoundException")
    }
    catch (expected: ClassNotFoundException) {
      // success
    }
  }

  // see b/119310867
  fun testIncompleteLibrary() {
    myFixture.copyDirectoryToProject("navschematest", "src")

    for (prebuiltPath in navEditorAarPaths) {
      if ("navigation-fragment" in prebuiltPath) {
        continue
      }
      val aar = File(PathManager.getHomePath(), prebuiltPath)
      val tempDir = FileUtil.createTempDirectory("NavigationSchemaTest", null)
      ZipUtil.extract(aar, tempDir, null)
      PsiTestUtil.addLibrary(myFixture.module, File(tempDir, "classes.jar").path)
    }
    NavigationSchema.createIfNecessary(myModule)

    val schema = NavigationSchema.get(myModule)
    val activity = findClass(SdkConstants.CLASS_ACTIVITY)!!
    val navGraph = findClass("androidx.navigation.NavGraph")!!

    assertSameElements(schema.getDestinationClassesForTag("activity"), activity)
    assertSameElements(schema.getDestinationClassesForTag("activity_sub"), activity)
    assertSameElements(schema.getDestinationClassesForTag("navigation"), navGraph)
    assertSameElements(schema.getDestinationClassesForTag("navigation_sub"), navGraph)
    assertEmpty(schema.getDestinationClassesForTag("other_1"))
    assertEmpty(schema.getDestinationClassesForTag("other_2"))
  }

  private fun findClass(className: String): PsiClass? {
    val javaPsiFacade = JavaPsiFacade.getInstance(project)
    return javaPsiFacade.findClass(className, GlobalSearchScope.allScope(project))
  }
}