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

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.common.model.NlComponent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public final class SearchItemHandlerTest {
  private ViewEditor myEditor;
  private NlComponent newChild;

  private ViewHandler myHandler;

  @Before
  public void setUp() {
    myEditor = Mockito.mock(ViewEditor.class);
    newChild = Mockito.mock(NlComponent.class);

    myHandler = new SearchItemHandler();
  }

  @Test
  public void onCreateApiLevelIs10() {
    Mockito.when(myEditor.getMinSdkVersion()).thenReturn(new AndroidVersion(10, null));
    myHandler.onCreate(myEditor, null, newChild, InsertType.CREATE);

    Mockito.verify(newChild).setAndroidAttribute("actionViewClass", "android.support.v7.widget.SearchView");
  }

  @Test
  public void onCreateApiLevelIs11() {
    Mockito.when(myEditor.getMinSdkVersion()).thenReturn(new AndroidVersion(11, null));
    myHandler.onCreate(myEditor, null, newChild, InsertType.CREATE);

    Mockito.verify(newChild).setAndroidAttribute("actionViewClass", "android.widget.SearchView");
  }
}
