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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.*;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static com.google.common.truth.Truth.assertThat;

public class AndroidResourceUtilTest extends AndroidTestCase {
  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    addModuleWithAndroidFacet(projectBuilder, modules, "lib", PROJECT_TYPE_LIBRARY);
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
    Collections.sort(list, AndroidResourceUtil::compareResourceFiles);
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
    Collections.sort(list2, AndroidResourceUtil::compareResourceFiles);
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

    AndroidFacet facet = AndroidFacet.getInstance(libModule);
    assertThat(facet).isNotNull();
    PsiField[] fields = AndroidResourceUtil.findResourceFields(facet, "string", "lib_hello", false /* onlyInOwnPackages */);

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
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertThat(facet).isNotNull();
    PsiField[] mainFields = AndroidResourceUtil.findResourceFields(facet, "string", "lib_hello", false /* onlyInOwnPackages */);
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
    assertEquals("my_file_name.png", AndroidResourceUtil.getValidResourceFileName("My File-Name.png"));
  }

  public void testEnsureNamespaceImportedAddAuto() {
    XmlFile xmlFile = ensureNamespaceImported("<LinearLayout/>", AUTO_URI, null);
    assertThat(xmlFile.getText()).isEqualTo("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />");
  }

  public void testEnsureNamespaceImportedAddAutoWithPrefixSuggestion() {
    XmlFile xmlFile = ensureNamespaceImported("<LinearLayout/>", AUTO_URI, "sherpa");
    assertThat(xmlFile.getText()).isEqualTo("<LinearLayout xmlns:sherpa=\"http://schemas.android.com/apk/res-auto\" />");
  }

  public void testEnsureNamespaceImportedDoNotAddAutoIfAlreadyThere() {
    @SuppressWarnings("XmlUnusedNamespaceDeclaration")
    XmlFile xmlFile = ensureNamespaceImported("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />", AUTO_URI, null);
    assertThat(xmlFile.getText()).isEqualTo("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />");
  }

  public void testEnsureNamespaceImportedDoNotAddAutoIfAlreadyThereWithPrefixSuggestion() {
    @SuppressWarnings("XmlUnusedNamespaceDeclaration")
    XmlFile xmlFile = ensureNamespaceImported("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />", AUTO_URI, "sherpa");
    assertThat(xmlFile.getText()).isEqualTo("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />");
  }

  public void testEnsureNamespaceImportedAddEmptyNamespaceForStyleAttribute() {
    XmlFile xmlFile = ensureNamespaceImported("<LinearLayout/>", "", null);
    assertThat(xmlFile.getText()).isEqualTo("<LinearLayout/>");
  }

  private XmlFile ensureNamespaceImported(@Language("XML") @NotNull String text, @NotNull String namespaceUri, @Nullable String suggestedPrefix) {
    XmlFile xmlFile = (XmlFile)myFixture.configureByText("res/layout/layout.xml", text);

    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
      AndroidResourceUtil.ensureNamespaceImported(xmlFile, namespaceUri, suggestedPrefix);
    }), "", "");

    return xmlFile;
  }

  public void testCreateFrameLayoutFileResource() throws Exception {
    XmlFile file = AndroidResourceUtil.createFileResource("linear", getLayoutFolder(), FRAME_LAYOUT, ResourceType.LAYOUT.getName(), false);
    assertThat(file.getName()).isEqualTo("linear.xml");
    assertThat(file.getText()).isEqualTo("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                         "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                         "    android:layout_width=\"match_parent\" android:layout_height=\"match_parent\">\n" +
                                         "\n" +
                                         "</FrameLayout>");
  }

  public void testCreateLinearLayoutFileResource() throws Exception {
    XmlFile file = AndroidResourceUtil.createFileResource("linear", getLayoutFolder(), LINEAR_LAYOUT, ResourceType.LAYOUT.getName(), false);
    assertThat(file.getName()).isEqualTo("linear.xml");
    assertThat(file.getText()).isEqualTo("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                         "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                         "    android:orientation=\"vertical\" android:layout_width=\"match_parent\"\n" +
                                         "    android:layout_height=\"match_parent\">\n" +
                                         "\n" +
                                         "</LinearLayout>");
  }

  public void testCreateLayoutFileResource() throws Exception {
    XmlFile file = AndroidResourceUtil.createFileResource("layout", getLayoutFolder(), TAG_LAYOUT, ResourceType.LAYOUT.getName(), false);
    assertThat(file.getName()).isEqualTo("layout.xml");
    assertThat(file.getText()).isEqualTo("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                         "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                                         "\n" +
                                         "</layout>");
  }

  @NotNull
  private PsiDirectory getLayoutFolder() {
    PsiFile file = myFixture.configureByText("res/layout/main.xml", "<LinearLayout/>");
    PsiDirectory folder = file.getParent();
    assertThat(folder).isNotNull();
    return folder;
  }
}
