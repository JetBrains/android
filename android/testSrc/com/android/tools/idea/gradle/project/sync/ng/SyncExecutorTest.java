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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandlerManager;
import com.intellij.testFramework.JavaProjectTestCase;
import org.mockito.Mock;

import java.util.ArrayList;

import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link SyncExecutor}.
 */
public class SyncExecutorTest extends JavaProjectTestCase {
  @Mock private CommandLineArgs myCommandLineArgs;
  @Mock private SyncErrorHandlerManager mySyncErrorHandlerManager;
  @Mock private SelectedVariantCollector mySelectedVariantCollector;
  @Mock private SelectedVariants mySelectedVariants;

  private ExtraGradleSyncModelsManager myExtraGradleSyncModelsManager;
  private SyncExecutor mySyncExecutor;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myExtraGradleSyncModelsManager = new ExtraGradleSyncModelsManager(new ArrayList<>());
    mySyncExecutor = new SyncExecutor(myProject, myExtraGradleSyncModelsManager, myCommandLineArgs, mySyncErrorHandlerManager,
                                      mySelectedVariantCollector);

    when(mySelectedVariantCollector.collectSelectedVariants()).thenReturn(mySelectedVariants);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.clearOverride();
      StudioFlags.COMPOUND_SYNC_ENABLED.clearOverride();
    }
    finally {
      super.tearDown();
    }
  }

  public void testSingleVariantSyncShouldBeDisabledByDefault() {
    assertFalse(StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.get());
  }

  public void testCreateSyncActionWhenSingleVariantSyncIsEnabled() {
    SyncAction action = mySyncExecutor.createSyncAction(false, true);
    assertSame(myExtraGradleSyncModelsManager.getJavaModelTypes(), action.getExtraJavaModelTypes());

    SyncActionOptions options = action.getOptions();
    assertTrue(options.isSingleVariantSyncEnabled());
    assertSame(mySelectedVariants, options.getSelectedVariants());
  }

  public void testCreateSyncActionWhenSingleVariantSyncIsDisabled() {
    SyncAction action = mySyncExecutor.createSyncAction(false, false);
    assertSame(myExtraGradleSyncModelsManager.getJavaModelTypes(), action.getExtraJavaModelTypes());

    SyncActionOptions options = action.getOptions();
    assertFalse(options.isSingleVariantSyncEnabled());
    assertNull(options.getSelectedVariants());
  }

  public void testCompoundSyncShouldBeDisabledByDefault() {
    assertFalse(StudioFlags.COMPOUND_SYNC_ENABLED.get());
  }

  public void testCreateSyncActionWithCompoundSync() {
    SyncAction action = mySyncExecutor.createSyncAction(true, true);
    assertTrue(action.getOptions().shouldGenerateSources());
  }
}