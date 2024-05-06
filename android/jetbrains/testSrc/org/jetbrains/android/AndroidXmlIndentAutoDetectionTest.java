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
package org.jetbrains.android;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.autodetect.FormatterBasedLineIndentInfoBuilder;
import com.intellij.psi.codeStyle.autodetect.LineIndentInfo;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.util.containers.ContainerUtil;
import java.io.IOException;
import java.util.List;
import org.jetbrains.android.formatter.AndroidXmlFormattingModelBuilder;

/**
 * Test indent detection for android XML files.
 */
public class AndroidXmlIndentAutoDetectionTest extends AndroidTestCase {

  private static final String BASE_PATH = "indent/";

  // Realistically, we only run the indent detection once, but it's good to know if the answer in dumb mode is the same
  // as in smart mode, to avoid heavy work on the AWT thread on startup if we can. See: http://b.android.com/229316
  public void testValuesInAndOutOfDumbMode() {
    VirtualFile resFile1 = myFixture.copyFileToProject(BASE_PATH + "values.xml", "res/values/a.xml");
    VirtualFile resFile2 = myFixture.copyFileToProject(BASE_PATH + "values.xml", "res/values/b.xml");
    List<Integer> expectedIndentSpacing =
      ImmutableList.of(
        -1, 0, -1,
        4,
        4, 8, 8, 4, // <style></style> block
        4,
        4,
        4, -1, -1, 8, 12, 8, 8, 4, // <style> block
        4, 8, 8, 4, // <attr> block
        4, 8, 8, 8, 4, // <declare-styleable> block
        -1,
        -1, -1, -1, // bunch of tabbed elements
        0);
    List<LineIndentInfo> indentInfosDumb = DumbModeTestUtils.computeInDumbModeSynchronously(getProject(), () -> getIndentInfos(resFile1));
    List<LineIndentInfo> indentInfosSmart = getIndentInfos(resFile2);
    assertSameIndents(indentInfosDumb, indentInfosSmart, expectedIndentSpacing);
  }

  public void testLayoutsInAndOutOfDumbMode() {
    VirtualFile resFile1 = myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/a.xml");
    VirtualFile resFile2 = myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/b.xml");
    List<Integer> expectedIndentSpacing =
      ImmutableList.of(
        -1, 0, -1, -1,
        2, 4, -1, -1, -1, 4, -1, -1, -1, -1, 2, // LinearLayout w/ 2 TextView
        2, -1, -1, -1, // TextView
        2, -1, -1, // TextView
        2, -1, // TextView
        0);
    List<LineIndentInfo> indentInfosDumb = DumbModeTestUtils.computeInDumbModeSynchronously(getProject(), () -> getIndentInfos(resFile1));
    List<LineIndentInfo> indentInfosSmart = getIndentInfos(resFile2);
    assertSameIndents(indentInfosDumb, indentInfosSmart, expectedIndentSpacing);
  }

  public void testManifestInAndOutOfDumbMode() throws IOException {
    deleteManifest();
    VirtualFile manifestFile = myFixture.copyFileToProject(BASE_PATH + "manifest.xml", "AndroidManifest.xml");
    List<Integer> expectedIndentSpacing =
      ImmutableList.of(
        -1, 0, -1,
        2,
        2,
        2,
        2, -1, -1, -1, 4, 6, 8, 8, 6, 4, 2,
        0);
    List<LineIndentInfo> indentInfosDumb = DumbModeTestUtils.computeInDumbModeSynchronously(getProject(), () -> getIndentInfos(manifestFile));
    List<LineIndentInfo> indentInfosSmart = getIndentInfos(manifestFile);
    assertSameIndents(indentInfosDumb, indentInfosSmart, expectedIndentSpacing);
  }

  private static void assertSameIndents(List<LineIndentInfo> infos1,
                                        List<LineIndentInfo> infos2,
                                        List<Integer> expectedIndentSpaces) {
    List<Integer> indentSizes1 = ContainerUtil.map(infos1, LineIndentInfo::getIndentSize);
    List<Boolean> isNormalIndent1 = ContainerUtil.map(infos1, LineIndentInfo::isLineWithNormalIndent);
    List<Boolean> isLineWithTabs1 = ContainerUtil.map(infos1, LineIndentInfo::isLineWithTabs);

    List<Integer> indentSizes2 = ContainerUtil.map(infos2, LineIndentInfo::getIndentSize);
    List<Boolean> isNormalIndent2 = ContainerUtil.map(infos2, LineIndentInfo::isLineWithNormalIndent);
    List<Boolean> isLineWithTabs2 = ContainerUtil.map(infos2, LineIndentInfo::isLineWithTabs);

    assertThat(indentSizes1).isEqualTo(indentSizes2);
    assertThat(isNormalIndent1).isEqualTo(isNormalIndent2);
    assertThat(isLineWithTabs1).isEqualTo(isLineWithTabs2);

    assertThat(indentSizes1).isEqualTo(expectedIndentSpaces);
  }

  private List<LineIndentInfo> getIndentInfos(VirtualFile resourceFile) {
    PsiFile file = PsiManager.getInstance(getProject()).findFile(resourceFile);
    assertNotNull(file);
    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
    FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);

    assertThat(document).isNotNull();
    assertThat(builder).isNotNull();
    // Make sure that resource file is actually detected as an android resource and uses the android builder.
    assertThat(builder).isInstanceOf(AndroidXmlFormattingModelBuilder.class);

    FormattingModel model =
      builder.createModel(FormattingContext.create(file, CodeStyleSettingsManager.getSettings(getProject())));
    Block block = model.getRootBlock();
    return new FormatterBasedLineIndentInfoBuilder(document, block, null).build();
  }
}
