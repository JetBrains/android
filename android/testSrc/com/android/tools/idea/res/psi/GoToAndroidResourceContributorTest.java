/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res.psi;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.intellij.ide.util.gotoByName.GotoSymbolModel2;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.util.ui.UIUtil;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link GoToAndroidResourceContributor}.
 */
public class GoToAndroidResourceContributorTest extends AndroidTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addFileToProject("res/values/strings.xml", "" +
                               "<resources>\n" +
                               "  <string name=\"my_string\">My string</string>\n" +
                               "</resources>");
    myFixture.addFileToProject("res/layout/my_layout.xml", "" +
                               "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                               "             xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                               "             android:id=\"@+id/item_detail_container\"\n" +
                               "             android:layout_width=\"match_parent\"\n" +
                               "             android:layout_height=\"match_parent\"\n" +
                               "             tools:context=\".ItemDetailActivity\"\n" +
                               "             tools:ignore=\"MergeRootFrame\" >\n" +
                               "\n" +
                               "    <Button\n" +
                               "        android:id=\"@+id/my_button\"\n" +
                               "        android:layout_width=\"match_parent\"\n" +
                               "        android:layout_height=\"wrap_content\"\n" +
                               "        android:text=\"Sign in\" />\n" +
                               "\n" +
                               "</FrameLayout>");
  }

  @NotNull
  private PsiElement navigate(@NotNull String name, @NotNull String pattern) {
    GotoSymbolModel2 model = new GotoSymbolModel2(myFixture.getProject());
    Object[] searchResults = model.getElementsByName(name, false, pattern);
    assertThat(searchResults).hasLength(1);
    Object result = searchResults[0];
    assertThat(result).isInstanceOf(NavigationItem.class);
    assertThat(((NavigationItem)result).getPresentation().getIcon(false)).isNotNull();
    UIUtil.dispatchAllInvocationEvents();
    ((NavigationItem)result).navigate(true);
    FileEditorManager editorManager = FileEditorManager.getInstance(myFixture.getProject());
    Editor editor = editorManager.getSelectedTextEditor();
    EditorTestUtil.waitForLoading(editor);
    int offset = editor.getCaretModel().getOffset();
    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(myFixture.getProject()).getPsiFile(document);
    assertThat(file).isNotNull();
    PsiElement element = file.findElementAt(offset);
    if (element instanceof XmlToken) {
      element = element.getParent();
    }
    assertThat(element).isNotNull();
    return element;
  }

  public void testGoToString() {
    PsiElement element = navigate("my_string", "my_s");
    assertThat(element.getText()).isEqualTo("\"my_string\"");
    assertThat(element.getParent().getParent().getText()).isEqualTo("<string name=\"my_string\">My string</string>");
  }

  public void testGoToId() {
    PsiElement element = navigate("my_button", "my_b");
    assertThat(element.getText()).isEqualTo("\"@+id/my_button\"");
  }

  public void testGoToLayout() {
    PsiElement element = navigate("my_layout", "my_l");
    assertThat(element).isInstanceOf(XmlTag.class);
    assertThat(((XmlTag)element).getName()).isEqualTo("FrameLayout");
  }

  /**
   * Tries to emulate what {@link com.intellij.ide.actions.searcheverywhere.TrivialElementsEqualityProvider} is doing to deduplicate the
   * result list. Unfortunately some of the types involved are not public, so we cannot do exactly the same.
   */
  public void testEquality() {
    GoToAndroidResourceContributor contributor = new GoToAndroidResourceContributor();
    List<NavigationItem> result = new ArrayList<>();
    contributor.addItems(myModule, "my_layout", result);
    contributor.addItems(myModule, "my_layout", result);
    assertThat(result).hasSize(2);
    assertThat(ImmutableSet.copyOf(result)).hasSize(1);
  }
}
