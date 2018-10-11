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
package com.android.tools.idea.logcat;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import javax.swing.ListModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class AndroidLogcatViewTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private AndroidLogcatView myLogcatView;

  @Before
  public void newAndroidLogcatView() {
    ApplicationManager.getApplication().invokeAndWait(() -> myLogcatView = new AndroidLogcatView(myRule.getProject(), new DeviceContext()));
  }

  @After
  public void disposeOfLogcatView() {
    Disposer.dispose(myLogcatView);
  }

  @Test
  public void updateDefaultFilters() {
    myLogcatView.createEditFiltersComboBox();
    myLogcatView.updateDefaultFilters(null);

    ListModel<AndroidLogcatFilter> model = myLogcatView.getEditFiltersComboBoxModel();

    assertEquals(3, model.getSize());

    int index = 0;

    assertEquals(AndroidLogcatView.FAKE_SHOW_ONLY_SELECTED_APPLICATION_FILTER, model.getElementAt(index++));
    assertEquals(AndroidLogcatView.NO_FILTERS_ITEM, model.getElementAt(index++));

    // noinspection UnusedAssignment
    assertEquals(AndroidLogcatView.EDIT_FILTER_CONFIGURATION_ITEM, model.getElementAt(index++));
  }
}
