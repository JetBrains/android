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
package com.android.tools.idea.uibuilder.menu;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.AUTO_URI;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlComponentBackend;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class SearchItemHandlerTest {
  private NlModel myModel;
  private ViewEditor myEditor;
  private NlComponent newChild;
  private NlComponentBackend myBackend;

  private ViewHandler myHandler;

  @Rule
  public AndroidProjectRule rule = AndroidProjectRule.inMemory();

  @Before
  public void setUp() {
    myModel = mock(NlModel.class);
    myEditor = mock(ViewEditor.class);
    newChild = mock(NlComponent.class);
    myBackend = mock(NlComponentBackend.class);
    when(myModel.getProject()).thenReturn(rule.getProject());
    when(newChild.getModel()).thenReturn(myModel);
    when(newChild.getBackend()).thenReturn(myBackend);

    myHandler = new SearchItemHandler();
  }

  @After
  public void tearDown() {
    myEditor = null;
    myModel = null;
    newChild = null;
    myHandler = null;
  }

  @Test
  public void onCreateApiLevelIs10() {
    when(myEditor.getMinSdkVersion()).thenReturn(new AndroidVersion(10, null));
    myHandler.onCreate(myEditor, null, newChild, InsertType.CREATE);

    Mockito.verify(newChild).setAttribute(ANDROID_URI, "actionViewClass", "android.support.v7.widget.SearchView");
  }

  @Test
  public void onCreateApiLevelIs11() {
    when(myEditor.getMinSdkVersion()).thenReturn(new AndroidVersion(11, null));
    myHandler.onCreate(myEditor, null, newChild, InsertType.CREATE);

    Mockito.verify(newChild).setAttribute(ANDROID_URI, "actionViewClass", "android.widget.SearchView");
  }

  @Test
  public void onCreateWithAppCompat() {
    when(myEditor.getMinSdkVersion()).thenReturn(new AndroidVersion(27, null));
    when(myEditor.moduleDependsOnAppCompat()).thenReturn(true);
    myHandler.onCreate(myEditor, null, newChild, InsertType.CREATE);

    Mockito.verify(newChild).setAttribute(AUTO_URI, "actionViewClass", "android.widget.SearchView");
  }
}
