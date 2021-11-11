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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.diagnostic.logging.LogFilterListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import java.awt.event.ItemListener;
import javax.swing.ListModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class AndroidLogcatViewTest {
  private static final int PID = 123;
  private static final String PACKAGE_NAME = "com.package.name";

  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private AndroidLogcatView myLogcatView;
  private final DeviceContext myDeviceContext = new DeviceContext();
  private final Disposable myDisposable = Disposer.newDisposable();

  @Before
  public void newAndroidLogcatView() {
    ApplicationManager.getApplication()
      .invokeAndWait(() -> myLogcatView = new AndroidLogcatView(myRule.getProject(), myDeviceContext, myDisposable));
  }

  @After
  public void disposeOfLogcatView() {
    Disposer.dispose(myDisposable);
  }

  @Test
  public void updateDefaultFilters() {
    myLogcatView.createEditFiltersComboBox();
    myLogcatView.updateDefaultFilters(null);

    ListModel<AndroidLogcatFilter> model = myLogcatView.getEditFiltersComboBoxModel();

    assertThat(model.getSize()).isEqualTo(3);

    assertThat(model.getElementAt(0)).isEqualTo(new SelectedProcessFilter(null));
    assertThat(model.getElementAt(1)).isEqualTo(AndroidLogcatView.NO_FILTERS_ITEM);
    assertThat(model.getElementAt(2)).isEqualTo(AndroidLogcatView.EDIT_FILTER_CONFIGURATION_ITEM);
  }

  @Test
  public void updateDefaultFilters_selectedApplicationFilter() {
    myLogcatView.createEditFiltersComboBox();
    ClientData clientData = createMockClientData(PID, PACKAGE_NAME);
    myLogcatView.updateDefaultFilters(clientData);

    ListModel<AndroidLogcatFilter> model = myLogcatView.getEditFiltersComboBoxModel();

    assertThat(model.getElementAt(0)).isEqualTo(new SelectedProcessFilter(clientData));
  }

  @Test
  public void updateDefaultFilters_doesNotTriggerItemChangeEvent() {
    ComboBox<AndroidLogcatFilter> comboBox = myLogcatView.createEditFiltersComboBox();
    ItemListener mockItemListener = mock(ItemListener.class);
    comboBox.addItemListener(mockItemListener);

    myLogcatView.updateDefaultFilters(null);

    verify(mockItemListener, never()).itemStateChanged(any());
  }

  @Test
  public void clientSelected_updatesFilter() {
    myLogcatView.createEditFiltersComboBox();
    ListModel<AndroidLogcatFilter> model = myLogcatView.getEditFiltersComboBoxModel();
    Client client = createMockClient(PID, PACKAGE_NAME);

    myDeviceContext.fireClientSelected(client);

    assertThat(model.getElementAt(0)).isEqualTo(new SelectedProcessFilter(client.getClientData()));
  }

  @Test
  public void clientSelected_appliesFilter() {
    myLogcatView.createEditFiltersComboBox();
    Client client = createMockClient(PID, PACKAGE_NAME);
    LogFilterListener mockFilterListener = mock(LogFilterListener.class);
    myLogcatView.getLogFilterModel().addFilterListener(mockFilterListener);

    myDeviceContext.fireClientSelected(client);

    verify(mockFilterListener).onTextFilterChange();
  }


  @SuppressWarnings("SameParameterValue")
  private static Client createMockClient(int pid, String packageName) {
    Client mockClient = mock(Client.class);
    ClientData mockClientData = createMockClientData(pid, packageName);
    when(mockClient.getClientData()).thenReturn(mockClientData);
    return mockClient;
  }

  private static ClientData createMockClientData(int pid, String packageName) {
    ClientData mock = mock(ClientData.class);
    when(mock.getPid()).thenReturn(pid);
    when(mock.getPackageName()).thenReturn(packageName);
    return mock;
  }
}
