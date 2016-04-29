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
package org.jetbrains.android.refactoring;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

public class UnusedResourcesTest extends AndroidTestCase {
  private static final String BASE_PATH = "refactoring/unusedResources/";

  public void test() throws Exception {
    //deleteManifest();
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    VirtualFile layout2 = myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout2.xml");
    myFixture.copyFileToProject(BASE_PATH + "TestCode.java", "src/p1/p2/TestCode.java");

    boolean skipIds = true;
    UnusedResourcesHandler.invoke(getProject(), null, null, true, skipIds);

    myFixture.checkResultByFile("res/values/strings.xml", BASE_PATH + "strings_after.xml", true);
    myFixture.checkResultByFile("res/layout/layout.xml", BASE_PATH + "layout_after.xml", true);
    assertFalse(layout2.exists());
  }
}
