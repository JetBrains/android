/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.folding;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

public class ResourceFoldingBuilderTest extends AndroidTestCase {

  public void testJavaStrings() throws Throwable { performTest(".java"); }
  public void testJavaStrings2() throws Throwable { performTest(".java"); }
  public void testJavaDimens() throws Throwable { performTest(".java"); }
  public void testXmlString() throws Throwable { performTest(".xml"); }
  public void testPlurals() throws Throwable { performTest(".java"); }
  public void testStaticImports() throws Throwable { performTest(".java"); }

  private void performTest(String extension) throws Throwable {
    myFixture.copyFileToProject("/R.java", "src/p1/p2/R.java");
    myFixture.copyFileToProject("/folding/values.xml", "res/values/values.xml");

    final String fileName = getTestName(true) + extension;
    final VirtualFile file = myFixture.copyFileToProject("/folding/" + fileName, "src/p1/p2/" + fileName);

    myFixture.testFoldingWithCollapseStatus(file.getPath());
  }
}
