/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.databinding.gradle;

import static com.android.tools.idea.databinding.TestDataPaths.PROJECT_WITH_DATA_BINDING_SUPPORT;
import static com.android.tools.idea.databinding.TestDataPaths.PROJECT_WITH_DATA_BINDING_ANDROID_X;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.databinding.DataBindingMode;
import com.android.tools.idea.databinding.TestDataPaths;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunsInEdt;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.android.dom.inspections.AndroidUnknownAttributeInspection;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DataBindingAdapterAttributesTest {
  @Rule
  public final AndroidGradleProjectRule myProjectRule = new AndroidGradleProjectRule();

  @Rule
  public final EdtRule myEdtRule = new EdtRule();

  @Parameterized.Parameters(name = "{0}")
  public static List<DataBindingMode> getParameters() {
    return Lists.newArrayList(DataBindingMode.SUPPORT, DataBindingMode.ANDROIDX);
  }

  @NotNull
  private final String myProjectName;

  public DataBindingAdapterAttributesTest(@NotNull DataBindingMode mode) {
    myProjectName = mode == DataBindingMode.SUPPORT ? PROJECT_WITH_DATA_BINDING_SUPPORT : PROJECT_WITH_DATA_BINDING_ANDROID_X;
  }

  @Before
  public void setUp() {
    myProjectRule.getFixture().setTestDataPath(TestDataPaths.TEST_DATA_ROOT);
    myProjectRule.load(myProjectName);
  }

  /**
   * Checks {@code @BindingAdapter} annotation completion and uses {@link AndroidUnknownAttributeInspection} to check that the attribute
   * has been defined.
   */
  @Test
  @RunsInEdt
  public void assertCompletionAndInspections() {
    myProjectRule.getFixture().enableInspections(AndroidUnknownAttributeInspection.class);

    VirtualFile file = PlatformTestUtil.getOrCreateProjectBaseDir(myProjectRule.getProject()).findFileByRelativePath("app/src/main/res/layout/activity_main.xml");
    myProjectRule.getFixture().openFileInEditor(file);
    Editor editor = myProjectRule.getFixture().getEditor();
    Document document = editor.getDocument();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProjectRule.getProject());
    int offset = document.getText().indexOf("name}\"");
    int line = document.getLineNumber(offset);

    assertThat(document.getText()).doesNotContain("my_binding_attribute");

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(document.getLineEndOffset(line), " my_bind=\"@{}\"");
      documentManager.commitAllDocuments();
    });

    editor.getCaretModel().moveToOffset(document.getText().indexOf("my_bind") + "my_bind".length());
    assertThat(myProjectRule.getFixture().completeBasic()).isNull(); // null because there is only one completion option
    assertThat(document.getText()).contains("my_binding_attribute");

    // The attribute is defined by @BindingAdapter so it shouldn't be marked as unknown
    assertThat(myProjectRule.getFixture().doHighlighting(HighlightSeverity.WARNING)).isEmpty();

    // The next attribute is not defined so it should be marked as a warning
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(document.getLineEndOffset(line), " my_not_attribute=\"\"");
      documentManager.commitAllDocuments();
    });
    assertThat(myProjectRule.getFixture().doHighlighting(HighlightSeverity.WARNING)).isNotEmpty();

    // Check that we do not return duplicate attributes (http://b/67408823)
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(document.getLineEndOffset(line), " android:paddin");
      documentManager.commitAllDocuments();
    });
    editor.getCaretModel().moveToOffset(document.getLineEndOffset(line));
    long paddingCompletions = Arrays.stream(myProjectRule.getFixture().completeBasic())
      .map(LookupElement::getLookupString)
      .filter("padding"::equals)
      .count();
    assertThat(paddingCompletions).isEqualTo(1);
  }
}
