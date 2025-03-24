/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android.dom

import com.android.SdkConstants
import com.android.tools.idea.testing.getIntentionAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope

class CreateMissingClassFixTest : AndroidDomTestCase("dom/manifest") {
  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    myFixture.addFileToProject(
      "src/p1/p2/EmptyClass.java", // language=JAVA
      "package p1.p2; public class EmptyClass {}",
    )
  }

  override fun providesCustomManifest(): Boolean {
    // Actually, this test doesn't provide a custom manifest, but saying we do allows us to check
    // for it missing as a side-effect of having the base-class not create one.
    return true
  }

  override fun getPathToCopy(testFileName: String?): String? {
    return null
  }

  @Throws(Exception::class)
  fun testMissingActivityClass() {
    val file = copyFileToProject("activity_missing_class.xml", SdkConstants.ANDROID_MANIFEST_XML)
    myFixture.configureFromExistingVirtualFile(file)

    val action = myFixture.getIntentionAction("Create class 'MyActivity'")
    assertNotNull(action)

    action!!.invoke(getProject(), myFixture.getEditor(), myFixture.getFile())
    val psiClass =
      JavaPsiFacade.getInstance(getProject())
        .findClass("p1.p2.MyActivity", GlobalSearchScope.allScope(getProject()))

    // Class has been created
    assertNotNull(psiClass)
  }

  @Throws(Exception::class)
  fun testMissingApplicationClass() {
    val file = copyFileToProject("application_missing_class.xml", SdkConstants.ANDROID_MANIFEST_XML)
    myFixture.configureFromExistingVirtualFile(file)

    val action = myFixture.getIntentionAction("Create class 'MyApplication'")
    assertNotNull(action)

    action!!.invoke(getProject(), myFixture.getEditor(), myFixture.getFile())
    val psiClass =
      JavaPsiFacade.getInstance(getProject())
        .findClass("p1.p2.MyApplication", GlobalSearchScope.allScope(getProject()))

    assertNotNull(psiClass)
  }
}
