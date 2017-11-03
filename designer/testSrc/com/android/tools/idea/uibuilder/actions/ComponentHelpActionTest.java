/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions;

import com.android.tools.idea.uibuilder.util.JavaDocViewer;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiClass;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ComponentHelpActionTest extends AndroidTestCase {
  @Mock
  private DataContext myDataContext;
  @Mock
  private JavaDocViewer myJavaDocViewer;
  @Mock
  private AnActionEvent myEvent;
  @Captor
  private ArgumentCaptor<PsiClass> myPsiClassCaptor;
  @Captor
  private ArgumentCaptor<DataContext> myContextCaptor;
  private ComponentHelpAction myAction;
  private String myTagName;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    when(myEvent.getDataContext()).thenReturn(myDataContext);
    myAction = new ComponentHelpAction(getProject(), () -> myTagName);
    registerApplicationComponent(JavaDocViewer.class, myJavaDocViewer);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
      // tests through IJ)
      myDataContext = null;
      myJavaDocViewer = null;
      myEvent = null;
      myPsiClassCaptor = null;
      myContextCaptor = null;
      myAction = null;
      myTagName = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testNullTagName() {
    myTagName = null;
    myAction.actionPerformed(myEvent);
    verifyZeroInteractions(myJavaDocViewer);
  }

  public void testUnknownTagName() {
    myTagName = "UnknownComponentTagName";
    myAction.actionPerformed(myEvent);
    verifyZeroInteractions(myJavaDocViewer);
  }

  public void testButton() {
    myTagName = "Button";
    myAction.actionPerformed(myEvent);
    verify(myJavaDocViewer).showExternalJavaDoc(myPsiClassCaptor.capture(), eq(myDataContext));
    assertThat(myPsiClassCaptor.getValue().getQualifiedName()).isEqualTo("android.widget.Button");
  }

  public void testTextureView() {
    myTagName = "TextureView";
    myAction.actionPerformed(myEvent);
    verify(myJavaDocViewer).showExternalJavaDoc(myPsiClassCaptor.capture(), eq(myDataContext));
    assertThat(myPsiClassCaptor.getValue().getQualifiedName()).isEqualTo("android.view.TextureView");
  }

  public void testWebView() {
    myTagName = "WebView";
    myAction.actionPerformed(myEvent);
    verify(myJavaDocViewer).showExternalJavaDoc(myPsiClassCaptor.capture(), eq(myDataContext));
    assertThat(myPsiClassCaptor.getValue().getQualifiedName()).isEqualTo("android.webkit.WebView");
  }

  public void testFullyQualifiedButton() {
    myTagName = "android.widget.Button";
    myAction.actionPerformed(myEvent);
    verify(myJavaDocViewer).showExternalJavaDoc(myPsiClassCaptor.capture(), eq(myDataContext));
    assertThat(myPsiClassCaptor.getValue().getQualifiedName()).isEqualTo("android.widget.Button");
  }
}
