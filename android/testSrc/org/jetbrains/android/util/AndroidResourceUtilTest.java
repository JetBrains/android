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

import com.android.resources.ResourceType;
import com.android.tools.idea.res.ResourceHelper;
import com.google.common.collect.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class AndroidResourceUtilTest extends AndroidTestCase {
  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    addModuleWithAndroidFacet(projectBuilder, modules, "lib", true);
  }

  public void testCaseSensitivityInChangeColorResource() {
    VirtualFile xmlFile = myFixture.copyFileToProject("util/colors_before.xml", "res/values/colors.xml");
    VirtualFile resDir = xmlFile.getParent().getParent();
    List<String> dirNames = ImmutableList.of("values");
    assertTrue(AndroidResourceUtil.changeValueResource(getProject(), resDir, "myColor", ResourceType.COLOR, "#000000", "colors.xml",
                                                       dirNames, false));
    assertFalse(AndroidResourceUtil.changeValueResource(getProject(), resDir, "mycolor", ResourceType.COLOR, "#FFFFFF", "colors.xml",
                                                        dirNames, false));
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

  public void testFindResourceFields() {
    myFixture.copyFileToProject("util/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");

    PsiField[] fields = AndroidResourceUtil.findResourceFields(myFacet, "string", "hello", false);

    for (PsiField field : fields) {
      assertEquals("hello", field.getName());
      assertEquals("p2", field.getContainingFile().getContainingDirectory().getName());
    }
    assertEquals(1, fields.length);
  }

  public void testFindResourceFieldsWithMultipleResourceNames() {
    myFixture.copyFileToProject("util/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");

    PsiField[] fields = AndroidResourceUtil.findResourceFields(
      myFacet, "string", ImmutableList.of("hello", "goodbye"), false);

    Set<String> fieldNames = Sets.newHashSet();
    for (PsiField field : fields) {
      fieldNames.add(field.getName());
      assertEquals("p2", field.getContainingFile().getContainingDirectory().getName());
    }
    assertEquals(ImmutableSet.of("hello", "goodbye"), fieldNames);
    assertEquals(2, fields.length);
  }

  /** Tests that "inherited" resource references are found (R fields in generated in dependent modules). */
  public void testFindResourceFieldsWithInheritance() throws Exception {
    myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");
    Module libModule = myAdditionalModules.get(0);
    // Remove the current manifest (has wrong package name) and copy a manifest with proper package into the lib module.
    deleteManifest(libModule);
    myFixture.copyFileToProject("util/lib/AndroidManifest.xml", "additionalModules/lib/AndroidManifest.xml");
    // Copy an empty R class with the proper package into the lib module.
    myFixture.copyFileToProject("util/lib/R.java", "additionalModules/lib/gen/p1/p2/lib/R.java");
    // Add some lib string resources.
    myFixture.copyFileToProject("util/lib/strings.xml", "additionalModules/lib/res/values/strings.xml");

    PsiField[] fields = AndroidResourceUtil.findResourceFields(
      AndroidFacet.getInstance(libModule), "string", "lib_hello", false /* onlyInOwnPackages */);

    Set<String> dirNames = Sets.newHashSet();
    for (PsiField field : fields) {
      assertEquals("lib_hello", field.getName());
      dirNames.add(field.getContainingFile().getContainingDirectory().getName());
    }
    assertEquals(ImmutableSet.of("p2", "lib"), dirNames);
    assertEquals(2, fields.length);
  }

  /** Tests that a module without an Android Manifest can still import a lib's R class */
  public void testIsRJavaFileImportedNoManifest() throws Exception {
    Module libModule = myAdditionalModules.get(0);
    // Remove the current lib manifest (has wrong package name) and copy a manifest with proper package into the lib module.
    deleteManifest(libModule);
    myFixture.copyFileToProject("util/lib/AndroidManifest.xml", "additionalModules/lib/AndroidManifest.xml");
    // Copy an empty R class with the proper package into the lib module.
    VirtualFile libRFile = myFixture.copyFileToProject("util/lib/R.java", "additionalModules/lib/gen/p1/p2/lib/R.java");
    // Add some lib string resources.
    myFixture.copyFileToProject("util/lib/strings.xml", "additionalModules/lib/res/values/strings.xml");
    // Remove the manifest from the main module.
    deleteManifest(myModule);

    // The main module doesn't get a generated R class and inherit fields (lack of manifest)
    PsiField[] mainFields = AndroidResourceUtil.findResourceFields(
      AndroidFacet.getInstance(myModule), "string", "lib_hello", false /* onlyInOwnPackages */);
    assertEmpty(mainFields);

    // However, if the main module happens to get a handle on the lib's R class
    // (e.g., via "import p1.p2.lib.R;"), then that R class should be recognized
    // (e.g., for goto navigation).
    PsiManager psiManager = PsiManager.getInstance(getProject());
    PsiFile libRClassFile = psiManager.findFile(libRFile);
    assertNotNull(libRClassFile);
    assertTrue(AndroidResourceUtil.isRJavaFile(myFacet, libRClassFile));
  }

  public void testValidResourceFileName() {
    assertEquals("ic_my_icon", AndroidResourceUtil.getValidResourceFileName("ic_My-icon"));
  }
}
