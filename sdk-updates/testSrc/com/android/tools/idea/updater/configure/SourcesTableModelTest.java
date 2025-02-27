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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.repository.api.ConstantSourceProvider;
import com.android.repository.api.RemoteListSourceProvider;
import com.android.repository.api.RepoManager;
import com.android.repository.api.SimpleRepositorySource;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.impl.sources.LocalSourceProvider;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.sources.RemoteSiteType;
import com.android.testutils.file.InMemoryFileSystems;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ModalityState;
import com.intellij.testFramework.ApplicationRule;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public class SourcesTableModelTest {
  private SourcesTableModel myModel;

  @ClassRule
  public static final ApplicationRule myApplicationRule = new ApplicationRule();

  @Before
  public void setUp() throws Exception {
    Semaphore initDone = new Semaphore(0);
    myModel = new SourcesTableModel(() -> {}, () -> initDone.release(), ModalityState.nonModal());
    myModel.setRefreshCallback(() -> {});
    SdkUpdaterConfigurable configurable = mock(SdkUpdaterConfigurable.class);
    Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
    RepoManager repoManager = new RepoManagerImpl(sdkRoot);
    AndroidSdkHandler sdkHandler = new AndroidSdkHandler(sdkRoot, sdkRoot.getRoot().resolve("android"), repoManager);
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
    assertTrue(initDone.tryAcquire(5, TimeUnit.SECONDS));
  }

  @After
  public void tearDown() {
    myModel = null;
  }

  @Test
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

  @Test
  public void testAddSource() throws Exception {
    runAndWaitForUpdate(() -> myModel.createSource("http://www.example.com/2", "z new site", null));
    assertEquals(5, myModel.getRowCount());
    //noinspection unchecked
    assertEquals("z new site", myModel.getColumnInfos()[1].valueOf(myModel.getItem(1)));
  }

  @Test
  public void testDisable() {
    assertTrue(myModel.getItem(0).mySource.isEnabled());
    myModel.setValueAt(false, 0, 0);
    assertFalse(myModel.getItem(0).mySource.isEnabled());
    myModel.setValueAt(true, 0, 0);
    assertTrue(myModel.getItem(0).mySource.isEnabled());
  }

  @Test
  public void testEnableAll() throws Exception {
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

    // Remove all editable sources and check that when all sources are non-editable
    // (for example, this is the OOTB use-case), then deselect all does nothing even when called (b/111496462).
    refreshCalled.set(false);
    assertTrue(myModel.hasEditableRows());
    runAndWaitForUpdate(() -> myModel.removeRow(0));
    runAndWaitForUpdate(() -> myModel.removeRow(0));
    assertFalse(myModel.hasEditableRows());
    myModel.setAllEnabled(false);
    assertTrue(myModel.getItem(0).mySource.isEnabled());
  }

  @Test
  public void testRemoveSource() throws Exception {
    //noinspection unchecked
    assertEquals("test local source", myModel.getColumnInfos()[1].valueOf(myModel.getItem(0)));
    runAndWaitForUpdate(() -> myModel.removeRow(0));
    assertEquals(3, myModel.getRowCount());
    //noinspection unchecked
    assertNotEquals("test local source", myModel.getColumnInfos()[1].valueOf(myModel.getItem(0)));
    // not editable
    runAndWaitForUpdate(() -> myModel.removeRow(0));
    assertEquals(3, myModel.getRowCount());
  }

  private void runAndWaitForUpdate(@NotNull Runnable action) throws Exception {
    Semaphore loadingFinishedCalled = new Semaphore(0);
    myModel.setLoadingFinishedCallback(() -> loadingFinishedCalled.release());
    action.run();
    assertTrue(loadingFinishedCalled.tryAcquire(2, TimeUnit.SECONDS));
    myModel.setLoadingFinishedCallback(() -> {});
  }
}
