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
package com.android.tools.idea.updater.configure;

import com.android.repository.api.ConstantSourceProvider;
import com.android.repository.api.RemoteListSourceProvider;
import com.android.repository.api.RepoManager;
import com.android.repository.api.SimpleRepositorySource;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.impl.sources.LocalSourceProvider;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.sources.RemoteSiteType;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ModalityState;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SourcesTableModelTest extends AndroidTestCase {
  private SourcesTableModel myModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    AtomicBoolean initDone = new AtomicBoolean();
    myModel = new SourcesTableModel(() -> {}, () -> initDone.set(true), ModalityState.current());
    myModel.setRefreshCallback(() -> {});
    SdkUpdaterConfigurable configurable = mock(SdkUpdaterConfigurable.class);
    MockFileOp fop = new MockFileOp();
    RepoManager repoManager = new RepoManagerImpl(fop);
    AndroidSdkHandler sdkHandler = new AndroidSdkHandler(new File("/sdk"), new File("/android"), fop, repoManager);
    LocalSourceProvider localSourceProvider = sdkHandler.getUserSourceProvider(new FakeProgressIndicator());
    localSourceProvider.getSources(null, new FakeProgressIndicator(), false);
    localSourceProvider.addSource(new SimpleRepositorySource(
      "http://example.com", "test local source", true,
      ImmutableList.of(AndroidSdkHandler.getAddonModule(), AndroidSdkHandler.getSysImgModule()),
      localSourceProvider));
    RemoteSiteType.SysImgSiteType sysImgSite = mock(RemoteSiteType.SysImgSiteType.class);
    RemoteListSourceProvider remoteProvider = mock(RemoteListSourceProvider.class);
    when(sysImgSite.getProvider()).thenReturn(remoteProvider);
    when(sysImgSite.isEnabled()).thenReturn(true);
    RemoteSiteType.AddonSiteType addonSite = mock(RemoteSiteType.AddonSiteType.class);
    when(addonSite.getProvider()).thenReturn(remoteProvider);
    when(addonSite.isEnabled()).thenReturn(true);
    when(remoteProvider.getSources(any(), any(), anyBoolean())).thenReturn(ImmutableList.of(sysImgSite, addonSite));
    RemoteListSourceProvider.GenericSite genericSite = mock(RemoteListSourceProvider.GenericSite.class);
    ConstantSourceProvider constantProvider = mock(ConstantSourceProvider.class);
    when(genericSite.getProvider()).thenReturn(constantProvider);
    when(genericSite.isEnabled()).thenReturn(true);
    when(constantProvider.getSources(any(), any(), anyBoolean())).thenReturn(ImmutableList.of(genericSite));
    repoManager.registerSourceProvider(localSourceProvider);
    repoManager.registerSourceProvider(constantProvider);
    repoManager.registerSourceProvider(remoteProvider);
    when(configurable.getSdkHandler()).thenReturn(sdkHandler);
    when(configurable.getRepoManager()).thenReturn(repoManager);

    myModel.setConfigurable(configurable);
    myModel.reset();
    while (!initDone.get()) {
      UIUtil.dispatchAllInvocationEvents();
    }
  }

  @Override
  protected void tearDown() throws Exception {
    myModel = null;
    super.tearDown();
  }

  public void testEditable() {
    assertEquals(4, myModel.getRowCount());
    assertFalse(myModel.getItem(0).isModified());
    assertFalse(myModel.getItem(1).isModified());
    assertFalse(myModel.getItem(2).isModified());
    assertFalse(myModel.getItem(3).isModified());

    assertTrue(myModel.isEditable(0));
    assertFalse(myModel.isEditable(1));
    assertFalse(myModel.isEditable(2));
    assertFalse(myModel.isEditable(3));

    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 3; j++) {
        assertEquals(i == 0 && j == 0, myModel.isCellEditable(i, j));
      }
    }
  }

  public void testAddSource() {
    runAndWaitForUpdate(() -> myModel.createSource("http://www.example.com/2", "z new site", null));
    assertEquals(5, myModel.getRowCount());
    assertEquals("z new site", myModel.getColumnInfos()[1].valueOf(myModel.getItem(1)));
  }

  public void testDisable() {
    assertTrue(myModel.getItem(0).mySource.isEnabled());
    myModel.setValueAt(false, 0, 0);
    assertFalse(myModel.getItem(0).mySource.isEnabled());
    myModel.setValueAt(true, 0, 0);
    assertTrue(myModel.getItem(0).mySource.isEnabled());
  }

  public void testEnableAll() {
    runAndWaitForUpdate(() -> myModel.createSource("http://www.example.com/2", "z new site", null));
    assertTrue(myModel.getItem(0).mySource.isEnabled());
    assertTrue(myModel.getItem(1).mySource.isEnabled());
    assertTrue(myModel.getItem(3).mySource.isEnabled());
    AtomicBoolean refreshCalled = new AtomicBoolean();
    myModel.setRefreshCallback(() -> assertTrue(refreshCalled.compareAndSet(false, true)));
    myModel.setAllEnabled(false);
    assertTrue(refreshCalled.compareAndSet(true, false));
    assertFalse(myModel.getItem(0).mySource.isEnabled());
    assertFalse(myModel.getItem(1).mySource.isEnabled());
    // Not editable, so shouldn't be changed.
    assertTrue(myModel.getItem(3).mySource.isEnabled());
    myModel.setAllEnabled(true);
    assertTrue(refreshCalled.get());
    assertTrue(myModel.getItem(0).mySource.isEnabled());
    assertTrue(myModel.getItem(1).mySource.isEnabled());
    assertTrue(myModel.getItem(3).mySource.isEnabled());
  }

  public void testRemoveSource() {
    assertEquals("test local source", myModel.getColumnInfos()[1].valueOf(myModel.getItem(0)));
    runAndWaitForUpdate(() -> myModel.removeRow(0));
    assertEquals(3, myModel.getRowCount());
    assertFalse("test local source".equals(myModel.getColumnInfos()[1].valueOf(myModel.getItem(0))));
    // not editable
    runAndWaitForUpdate(() -> myModel.removeRow(0));
    assertEquals(3, myModel.getRowCount());
  }

  private void runAndWaitForUpdate(@NotNull Runnable action) {
    AtomicBoolean refreshCalled = new AtomicBoolean();
    myModel.setRefreshCallback(() -> assertTrue(refreshCalled.compareAndSet(false, true)));
    action.run();
    long startTime = System.currentTimeMillis();
    while (!refreshCalled.get()) {
      UIUtil.dispatchAllInvocationEvents();
      if (startTime + TimeUnit.SECONDS.toMillis(2) < System.currentTimeMillis()) {
        fail("timed out waiting for update to complete");
      }
    }
    myModel.setRefreshCallback(() -> {});
  }
}
