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

import static com.android.tools.idea.testing.SnapshotComparisonTestHelpersKt.normalizeHtmlForTests;
import static com.android.tools.idea.testing.SnapshotComparisonTestUtilsKt.assertIsEqualToSnapshot;
import static com.android.tools.idea.testing.TestProjectPaths.DEPENDENT_MODULES;
import static com.android.tools.idea.testing.TestProjectPaths.MULTIPLE_MODULE_DEPEND_ON_AAR;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.AndroidTestUtils;
import com.android.tools.idea.testing.SnapshotComparisonTest;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import java.io.File;
import java.io.IOException;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;

@RunsInEdt
public class AndroidJavaDocWithGradleTest implements SnapshotComparisonTest {
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();
  @Rule
  public RuleChain rule = RuleChain.outerRule(projectRule).around(new EdtRule());
  @Rule
  public TestName nameRule = new TestName();

  @Override
  public @NotNull String getName() {
    return nameRule.getMethodName();
  }

  @NotNull
  private VirtualFile findFile(@NotNull String path) {
    File filePath = new File(projectRule.getProject().getBasePath(), FileUtil.toSystemDependentName(path));
    VirtualFile file = findFileByIoFile(filePath, true);
    assertThat(file).named("File '" + path + "' not found.").isNotNull();
    return file;
  }

  private void checkJavadoc(@NotNull CodeInsightTestFixture fixture) {
    Project project = projectRule.getProject();
    PsiElement originalElement = fixture.getFile().findElementAt(fixture.getEditor().getCaretModel().getOffset());
    assert originalElement != null;
    final PsiElement docTargetElement = DocumentationManager.getInstance(project).findTargetElement(
      fixture.getEditor(), fixture.getFile(), originalElement);
    assert docTargetElement != null;
    DocumentationProvider provider = DocumentationManager.getProviderFromElement(docTargetElement);
    String doc = provider.generateDoc(docTargetElement, originalElement);
    String normalizedDoc = normalizeHtmlForTests(project, doc != null ? doc : "");
    assertIsEqualToSnapshot(this, normalizedDoc, "");
  }

  @Test
  public void testResource() {
    projectRule.loadProject(DEPENDENT_MODULES);
    CodeInsightTestFixture fixture = projectRule.getFixture();
    fixture.configureFromExistingVirtualFile(findFile("app/src/main/res/values/colors.xml"));
    checkJavadoc(fixture);
  }

  @Test
  public void testResourcesInAar() {
    String activityPath = "app/src/main/java/com/example/google/androidx/MainActivity.kt";
    Function1<@NotNull File, @NotNull Unit> patch = (root) -> {
      try {
        File file = new File(root, activityPath);
        String text = FileUtil.loadFile(file);
        String newText = text.replace("val color = androidx.appcompat.R.color.abc_tint_default",
                                      "val divider = androidx.appcompat.R.attr.actionBarDivider");
        FileUtil.writeToFile(file, newText);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      return Unit.INSTANCE;
    };
    projectRule.loadProject(MULTIPLE_MODULE_DEPEND_ON_AAR, null, null, null, patch);
    CodeInsightTestFixture fixture = projectRule.getFixture();
    fixture.configureFromExistingVirtualFile(findFile(activityPath));
    AndroidTestUtils.moveCaret(fixture, "androidx.appcompat.R.attr.actionBarDivider|");
    checkJavadoc(fixture);
  }

  @NotNull
  @Override
  public String getSnapshotDirectoryWorkspaceRelativePath() {
    return "tools/adt/idea/android/editing/documentation/testData/javadoc/snapshots";
  }
}
