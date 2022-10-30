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
package com.android.tools.idea.javadoc;

import static com.android.tools.idea.testing.SnaphotComparisonTestHelpersKt.normalizeHtmlForTests;
import static com.android.tools.idea.testing.SnapshotComparisonTestUtilsKt.assertIsEqualToSnapshot;
import static com.android.tools.idea.testing.TestProjectPaths.DEPENDENT_MODULES;
import static com.android.tools.idea.testing.TestProjectPaths.MULTIPLE_MODULE_DEPEND_ON_AAR;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidTestUtils;
import com.android.tools.idea.testing.SnapshotComparisonTest;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import java.io.File;
import org.jetbrains.annotations.NotNull;

public class AndroidJavaDocWithGradleTest extends AndroidGradleTestCase implements SnapshotComparisonTest {
  @NotNull
  private VirtualFile findFile(@NotNull String path) {
    File filePath = new File(getProject().getBasePath(), FileUtil.toSystemDependentName(path));
    VirtualFile file = findFileByIoFile(filePath, true);
    assertNotNull("File '" + path + "' not found.", file);
    return file;
  }

  private void checkJavadoc(@NotNull String targetPath) {
    VirtualFile f = findFile(targetPath);
    myFixture.configureFromExistingVirtualFile(f);
    PsiElement originalElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    assert originalElement != null;
    final PsiElement docTargetElement = DocumentationManager.getInstance(getProject()).findTargetElement(
      myFixture.getEditor(), myFixture.getFile(), originalElement);
    assert docTargetElement != null;
    DocumentationProvider provider = DocumentationManager.getProviderFromElement(docTargetElement);
    String doc = provider.generateDoc(docTargetElement, originalElement);
    String normalizedDoc = normalizeHtmlForTests(getProject(), doc != null ? doc : "");
    assertIsEqualToSnapshot(this, normalizedDoc, "");
  }

  public void testResource() throws Exception {
    if (SystemInfoRt.isWindows) {
      return; // TODO(b/228880357) fix high failure rate on Windows
    }
    loadProject(DEPENDENT_MODULES);

    checkJavadoc("/app/src/main/res/values/colors.xml"
    );
  }

  public void testResourcesInAar() throws Exception {
    loadProject(MULTIPLE_MODULE_DEPEND_ON_AAR);

    String activityPath = "app/src/main/java/com/example/google/androidx/MainActivity.kt";
    VirtualFile virtualFile = ProjectUtil.guessProjectDir(getProject()).findFileByRelativePath(activityPath);
    myFixture.openFileInEditor(virtualFile);

    // Resource from Aar define in module R class.
    AndroidTestUtils.moveCaret(myFixture, "androidx.appcompat.R.color.abc_tint_default|");
    myFixture.type("\n    androidx.appcompat.R.attr.actionBarDivider");
    checkJavadoc(activityPath);
  }

  @NotNull
  @Override
  public String getSnapshotDirectoryWorkspaceRelativePath() {
    return "tools/adt/idea/android/testData/javadoc/snapshots";
  }
}
