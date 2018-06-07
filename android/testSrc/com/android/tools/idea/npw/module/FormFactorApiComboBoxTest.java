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
package com.android.tools.idea.npw.module;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.google.common.collect.Lists;
import com.intellij.ide.util.ProjectPropertiesComponentImpl;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.mock.MockApplicationEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static com.android.tools.idea.npw.module.FormFactorApiComboBox.getPropertiesComponentMinSdkKey;
import static org.junit.Assert.assertEquals;

public class FormFactorApiComboBoxTest {
  private Disposable myDisposable;

  @Before
  public void setUp() throws Exception {
    //noinspection Convert2Lambda Since otherwise it doesn't create new instances.
    myDisposable = new Disposable() {
      @Override
      public void dispose() {}
    };

    MockApplicationEx instance = new MockApplicationEx(myDisposable);
    instance.registerService(PropertiesComponent.class, ProjectPropertiesComponentImpl.class);
    ApplicationManager.setApplication(instance, myDisposable);
  }

  @After
  public void tearDown() throws Exception {
    Disposer.dispose(myDisposable);
  }

  @Test
  public void testDefaultSelectedItem() {
    FormFactor formFactor = FormFactor.MOBILE;
    assertEquals("none", PropertiesComponent.getInstance().getValue(getPropertiesComponentMinSdkKey(formFactor), "none"));

    List<AndroidVersionsInfo.VersionItem> items = Lists.newArrayList(
      createMockVersionItem(String.valueOf(formFactor.defaultApi - 1)),
      createMockVersionItem(String.valueOf( formFactor.defaultApi)),      // Default is at position 1
      createMockVersionItem(String.valueOf(formFactor.defaultApi + 1)),
      createMockVersionItem(String.valueOf(formFactor.defaultApi + 2))
    );

    FormFactorApiComboBox apiComboBox = new FormFactorApiComboBox();

    apiComboBox.init(formFactor, items);
    assertEquals(1, apiComboBox.getSelectedIndex());

    // Make sure the default does not change if the list is reloaded
    apiComboBox.init(formFactor, items);
    assertEquals(1, apiComboBox.getSelectedIndex());

    apiComboBox.init(formFactor, Lists.reverse(items));
    assertEquals(2, apiComboBox.getSelectedIndex());

    items.remove(1);
    apiComboBox.init(formFactor, items);
    assertEquals(0, apiComboBox.getSelectedIndex());

    apiComboBox.setSelectedIndex(2);
    String savedApi = PropertiesComponent.getInstance().getValue(getPropertiesComponentMinSdkKey(formFactor), "none");
    assertEquals(items.get(2).getMinApiLevelStr(), savedApi);
  }

  private static AndroidVersionsInfo.VersionItem createMockVersionItem(String apiLevelStr) {
    AndroidVersionsInfo.VersionItem item = Mockito.mock(AndroidVersionsInfo.VersionItem.class);
    Mockito.when(item.getMinApiLevelStr()).thenReturn(apiLevelStr);

    return item;
  }
}
