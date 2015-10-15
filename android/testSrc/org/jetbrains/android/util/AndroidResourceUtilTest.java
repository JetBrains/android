/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.util;

import com.android.tools.idea.rendering.ResourceHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.AndroidTestCase;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AndroidResourceUtilTest extends AndroidTestCase {
  public void testCaseSensitivityInChangeColorResource() {
    myFixture.copyFileToProject("util/colors_before.xml", "res/values/colors.xml");
    List<String> dirNames = ImmutableList.of("values");
    assertTrue(AndroidResourceUtil.changeColorResource(myFacet, "myColor", "#000000", "colors.xml", dirNames));
    assertFalse(AndroidResourceUtil.changeColorResource(myFacet, "mycolor", "#FFFFFF", "colors.xml", dirNames));
    myFixture.checkResultByFile("res/values/colors.xml", "util/colors_after.xml", true);
  }

  public void testCompareResourceFiles() throws Exception {
    PsiFile f1 = myFixture.addFileToProject("res/values/filename.xml", "");
    PsiFile f2 = myFixture.addFileToProject("res/values-en/filename.xml", "");
    PsiFile f3 = myFixture.addFileToProject("res/values-en-rUS/filename.xml", "");
    PsiFile f4 = myFixture.addFileToProject("res/values-v21/filename.xml", "");
    PsiFile f5 = myFixture.addFileToProject("res/values/filename2.png", "");
    PsiFile f6 = myFixture.addFileToProject("res/values-en/filename2.xml", "");
    PsiFile f7 = myFixture.addFileToProject("AndroidManifest2.xml", "");
    PsiFile f8 = myFixture.addFileToProject("res/values-en/other.png", "");
    String expected = "AndroidManifest2.xml\n" +
                      "values/filename.xml\n" +
                      "values-v21/filename.xml\n" +
                      "values-en/filename.xml\n" +
                      "values-en/filename2.xml\n" +
                      "values-en-rUS/filename.xml\n" +
                      "values/filename2.png\n" +
                      "values-en/other.png\n";

    List<PsiFile> list = Lists.newArrayList(f1, f2, f3, f4, f5, f6, f7, f8);
    Collections.sort(list, new Comparator<PsiFile>() {
      @Override
      public int compare(PsiFile file1, PsiFile file2) {
        return AndroidResourceUtil.compareResourceFiles(file1, file2);
      }
    });
    StringBuilder sb1 = new StringBuilder();
    for (PsiFile file : list) {
      if (file.getParent() != null && ResourceHelper.getFolderType(file) != null) {
        sb1.append(file.getParent().getName()).append("/");
      }
      sb1.append(file.getName());
      sb1.append("\n");
    }
    assertEquals(expected, sb1.toString());

    List<VirtualFile> list2 = Lists.newArrayList(f1.getVirtualFile(), f2.getVirtualFile(), f3.getVirtualFile(),
                                                 f4.getVirtualFile(), f5.getVirtualFile(), f6.getVirtualFile(),
                                                 f7.getVirtualFile(), f8.getVirtualFile());
    Collections.sort(list2, new Comparator<VirtualFile>() {
      @Override
      public int compare(VirtualFile file1, VirtualFile file2) {
        return AndroidResourceUtil.compareResourceFiles(file1, file2);
      }
    });
    StringBuilder sb2 = new StringBuilder();
    for (VirtualFile file : list2) {
      if (file.getParent() != null && ResourceHelper.getFolderType(file) != null) {
        sb2.append(file.getParent().getName()).append("/");
      }
      sb2.append(file.getName());
      sb2.append("\n");
    }

    assertEquals(expected, sb2.toString());

    // Make sure comparators are always consistent
    //   AndroidResourceUtil.compareResourceFiles
    for (int i = 0; i < list2.size(); i++) {
      for (int j = i; j < list2.size(); j++) { // j=i, not j=i+1 because we want to compare with self too!
        PsiFile e1 = list.get(i);
        PsiFile e2 = list.get(j);
        int result1 = AndroidResourceUtil.compareResourceFiles(e1, e2);
        int result2 = AndroidResourceUtil.compareResourceFiles(e2, e1);
        assertEquals(result1, -result2);

        VirtualFile e3 = list2.get(i);
        VirtualFile e4 = list2.get(j);
        result1 = AndroidResourceUtil.compareResourceFiles(e3, e4);
        result2 = AndroidResourceUtil.compareResourceFiles(e4, e3);
        assertEquals(result1, -result2);
      }
    }
  }
}
