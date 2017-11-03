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
package com.android.tools.idea;

import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.AndroidTestCase;

import static com.google.common.truth.Truth.assertThat;

public class AndroidPsiUtilsTest extends AndroidTestCase {

  public void testGetDeclaredContextFqcnWithoutContext() throws Exception {
    XmlFile file = (XmlFile)myFixture.addFileToProject("layout/simple.xml", "<root></root>");
    String context = AndroidPsiUtils.getDeclaredContextFqcn(myModule, file);
    assertThat(context).isNull();
  }

  public void testGetDeclaredContextFqcnWithRelativeContext() throws Exception {
    XmlFile file = (XmlFile)myFixture.addFileToProject("a.xml", "<root " +
                                                                "  xmlns:tools=\"http://schemas.android.com/tools\" " +
                                                                "  tools:context=\".MainActivity\">" +
                                                                "</root>");
    String context = AndroidPsiUtils.getDeclaredContextFqcn(myModule, file);
    assertThat(context).isEqualTo("p1.p2.MainActivity");
  }

  public void testGetDeclaredContextFqcnWithFullyQualifiedContext() throws Exception {
    XmlFile file = (XmlFile)myFixture.addFileToProject("a.xml", "<root " +
                                                                "  xmlns:tools=\"http://schemas.android.com/tools\" " +
                                                                "  tools:context=\"com.example.android.MainActivity\">" +
                                                                "</root>");
    String context = AndroidPsiUtils.getDeclaredContextFqcn(myModule, file);
    assertThat(context).isEqualTo("com.example.android.MainActivity");
  }
}
