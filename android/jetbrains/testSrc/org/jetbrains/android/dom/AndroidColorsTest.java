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
package org.jetbrains.android.dom;

import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection;
import com.intellij.openapi.vfs.VirtualFile;

public class AndroidColorsTest extends AndroidDomTestCase {
  public AndroidColorsTest() {
    super("dom/color");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/values/" + testFileName;
  }

  public void testColorNoTypos() throws Throwable {
    copyFileToProject("color_layout.xml", "res/layout/color_layout.xml");
    VirtualFile virtualFile = copyFileToProject("colors_value.xml");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.enableInspections(new GrazieSpellCheckingInspection());
    myFixture.checkHighlighting(true, true, true);
  }
}
