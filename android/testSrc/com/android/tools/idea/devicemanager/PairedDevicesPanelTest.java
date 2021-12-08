/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.adtui.stdui.EmptyStatePanel;
import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.idea.devicemanager.physicaltab.Key;
import com.android.tools.idea.devicemanager.physicaltab.SerialNumber;
import com.android.tools.idea.wearpairing.ConnectionState;
import com.android.tools.idea.wearpairing.PairingDevice;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.android.tools.idea.wearpairing.WearPairingManager.PairingState;
import com.android.tools.idea.wearpairing.WearPairingManager.PhoneWearPair;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import java.util.function.Predicate;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PairedDevicesPanelTest {
  @Rule
  public final TestRule myEdtRule = new EdtRule();

  private Disposable myParent;
  private WearPairingManager myWearPairingManager;

  @Before
  public void initParent() {
    myParent = Disposer.newDisposable("PairedDevicesPanelTest");
  }

  @Before
  public void mockWearPairingManager() {
    myWearPairingManager = mock(WearPairingManager.class);
  }

  @Before
  public void setUpActionManager() {
    ActionManagerEx actionManager = mock(ActionManagerImpl.class);
    when(actionManager.createActionToolbar(anyString(), any(ActionGroup.class), anyBoolean())).thenCallRealMethod();
    when(actionManager.createActionToolbar(anyString(), any(ActionGroup.class), anyBoolean(), anyBoolean())).thenCallRealMethod();

    MockApplication mockApplication = new MockApplication(myParent);
    mockApplication.registerService(ActionManager.class, actionManager);

    ApplicationManager.setApplication(mockApplication, myParent);
  }

  @After
  public void disposeOfParent() {
    Disposer.dispose(myParent);
  }

  @Test
  @RunsInEdt
  public void emptyTableForNoPairedDevice() throws Exception {
    // Arrange
    FakeUi fakeUi = createFakeUi(new SerialNumber("86UX00F4R"));

    // Act
    EmptyStatePanel emptyStatePanel = fakeUi.findComponent(EmptyStatePanel.class, (Predicate<EmptyStatePanel>)panel -> true);

    // Assert
    assertThat(emptyStatePanel).isNotNull();
    assertThat(emptyStatePanel.getReasonText()).isEqualTo("Device is not paired to companion device.");
  }

  @Test
  @RunsInEdt
  public void oneRowColumnForSinglePairing() throws Exception {
    // Arrange
    Key phoneKey = new SerialNumber("86UX00F4R");
    PairingDevice phoneDevice = new PairingDevice(phoneKey.toString(), "My Phone", 30, false, false, true, ConnectionState.ONLINE);
    PairingDevice wearDevice = new PairingDevice("WearId", "My Wear", 30, false, true, true, ConnectionState.ONLINE);

    PhoneWearPair phoneWearPair = new PhoneWearPair(phoneDevice, wearDevice);
    phoneWearPair.setPairingStatus(PairingState.CONNECTED);
    when(myWearPairingManager.getPairedDevices(phoneKey.toString())).thenReturn(phoneWearPair);

    FakeUi fakeUi = createFakeUi(phoneKey);

    // Act
    JTable table = fakeUi.findComponent(JTable.class, (Predicate<JTable>)t -> true);

    // Assert
    assertThat(table).isNotNull();
    assertThat(table.getColumnCount()).isEqualTo(2);
    assertThat(table.getRowCount()).isEqualTo(1);
    assertThat(table.getColumnName(0)).isEqualTo("Device");
    assertThat(table.getColumnName(1)).isEqualTo("Status");
    assertThat(table.getModel().getValueAt(0, 1)).isEqualTo("Connected");
  }

  private @NotNull FakeUi createFakeUi(@NotNull Key key) throws InterruptedException {
    PairedDevicesPanel pairedDevicesPanel = new PairedDevicesPanel(key, myParent, myWearPairingManager);
    pairedDevicesPanel.setSize(640, 480);

    FakeUi fakeUi = new FakeUi(pairedDevicesPanel);
    fakeUi.layoutAndDispatchEvents();

    return fakeUi;
  }
}
