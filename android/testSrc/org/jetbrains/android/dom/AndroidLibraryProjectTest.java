/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.usageView.UsageInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.jetbrains.android.AndroidFindUsagesTest;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.inspections.AndroidDomInspection;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLibraryProjectTest extends AndroidTestCase {
  private static final String BASE_PATH = "libModule/";

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(BASE_PATH + "LibAndroidManifest.xml", "additionalModules/lib/" + SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyDirectoryToProject("res", "res");
    myFixture.copyDirectoryToProject(BASE_PATH + "res", "additionalModules/lib/res");

    myFixture.enableInspections(AndroidDomInspection.class);
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    addModuleWithAndroidFacet(projectBuilder, modules, "lib", PROJECT_TYPE_LIBRARY, true);
  }

  public void testHighlighting() {
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestManifest(), "res/layout/" + getTestManifest());
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.doHighlighting();
    myFixture.checkHighlighting(true, false, true);
  }

  public void testHighlighting1() {
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestManifest(), "res/layout/" + getTestManifest());
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.doHighlighting();
    myFixture.checkHighlighting(true, false, true);
  }

  public void testHighlighting2() {
    myFixture.copyFileToProject(BASE_PATH + "LibAndroidManifest.xml", "additionalModules/lib/" + SdkConstants.FN_ANDROID_MANIFEST_XML);
    VirtualFile manifestFile = myFixture.copyFileToProject(BASE_PATH + getTestManifest(), SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyDirectoryToProject(BASE_PATH + "res", "additionalModules/lib/res");
    myFixture.configureFromExistingVirtualFile(manifestFile);
    myFixture.doHighlighting();
    myFixture.checkHighlighting(true, false, true);
  }

  public void testJavaHighlighting() {
    String to = "additionalModules/lib/src/p1/p2/lib/" + getTestName(true) + ".java";
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".java", to);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.doHighlighting();
    myFixture.checkHighlighting(true, true, true);
  }

  public void testCompletion() {
    doTestCompletion();
  }

  public void testCompletion1() {
    doTestCompletion();
  }

  public void testCustomAttrCompletion() {
    myFixture.copyFileToProject(BASE_PATH + "LibView.java", "additionalModules/lib/src/p1/p2/lib/LibView.java");
    myFixture.copyFileToProject(BASE_PATH + "lib_attrs.xml", "additionalModules/lib/res/values/lib_attrs.xml");
    doTestCompletion();
  }

  private void doTestCompletion() {
    String to = "res/layout/" + getTestManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestManifest(), to);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after.xml");
  }

  public void testJavaNavigation() {
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".java", "src/p1/p2/Java.java");
    myFixture.configureFromExistingVirtualFile(file);

    PsiElement[] targets =
      GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
    assertNotNull(targets);
    assertEquals(1, targets.length);
    PsiElement targetElement = targets[0];
    assertInstanceOf(targetElement, PsiFile.class);
    assertEquals("main.xml", ((PsiFile)targetElement).getName());
  }

  public void testFileResourceFindUsages() throws Throwable {
    doFindUsagesTest("xml", "additionalModules/lib/res/layout/", "additionalModules/lib/res/drawable/picture1.png");
  }

  public void testFileResourceFindUsages1() throws Throwable {
    doFindUsagesTest("xml", "res/layout/", "additionalModules/lib/res/drawable/picture1.png");
  }

  public void testFileResourceFindUsagesFromJava() throws Throwable {
    doFindUsagesTest("java", "src/p1/p2/", "additionalModules/lib/res/drawable/picture1.png");
  }

  public void testFileResourceFindUsagesFromJava1() throws Throwable {
    doFindUsagesTest("java", "src/p1/p2/lib/", "additionalModules/lib/res/drawable/picture1.png");
  }

  public void testFileResourceFindUsagesFromJava2() throws Throwable {
    doFindUsagesTest("java", "additionalModules/lib/src/p1/p2/lib/", "additionalModules/lib/res/drawable/picture1.png");
  }

  public void testValueResourceFindUsages() throws Throwable {
    doFindUsagesTest("xml", "additionalModules/lib/res/layout/", "additionalModules/lib/res/values/strings.xml");
  }

  public void testValueResourceFindUsages1() throws Throwable {
    doFindUsagesTest("xml", "res/layout/", "additionalModules/lib/res/values/strings.xml");
  }

  private void doFindUsagesTest(String extension, String dir, String resourceDeclarationFilePath) throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesClass.java",
                                "src/p1/p2/FindUsagesClass.java");
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesClass1.java",
                                "additionalModules/lib/src/p1/p2/lib/FindUsagesClass.java");
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesStyles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject(BASE_PATH + "picture1.png", "additionalModules/lib/res/drawable/picture1.png");

    String path = getTestName(false) + "." + extension;
    String newFilePath = dir + path;
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + path, newFilePath);

    refreshProjectFiles();

    DumbService.getInstance(getProject()).runReadActionInSmartMode(() -> {
      Collection<UsageInfo> usages = AndroidFindUsagesTest.findUsages(file, myFixture);
      List<UsageInfo> result = new ArrayList<>();
      for (UsageInfo usage : usages) {
        if (!usage.isNonCodeUsage) {
          result.add(usage);
        }
      }

      List<String> files = new ArrayList<>(Arrays.asList(newFilePath,
                                                         "res/values/styles.xml",
                                                         "additionalModules/lib/src/p1/p2/lib/FindUsagesClass.java",
                                                         "src/p1/p2/FindUsagesClass.java"));
      if (StudioFlags.RESOLVE_USING_REPOS.get()) {
        files.add(resourceDeclarationFilePath);
      }
      assertThat(buildFileList(result)).containsExactlyElementsIn(files);
    });

  }

  @NotNull
  private String getTestManifest() {
    return getTestName(true) + ".xml";
  }

  private List<String> buildFileList(Collection<UsageInfo> infos) {
    final List<String> result = new ArrayList<>();
    VirtualFile tempDir = LocalFileSystem.getInstance().findFileByPath(myFixture.getTempDirPath());

    for (UsageInfo info : infos) {
      final PsiFile file = info.getFile();
      final VirtualFile vFile = file != null ? file.getVirtualFile() : null;
      String path = VfsUtilCore.findRelativePath(tempDir, vFile, '/');
      result.add(path);
    }

    return result;
  }
}
