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
import com.intellij.openapi.project.Project;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;

import static org.junit.Assert.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link SyncExecutor}.
 */
public class SyncExecutorTest {
  @Mock private Project myProject;
  @Mock private CommandLineArgs myCommandLineArgs;
  @Mock private SyncErrorHandlerManager mySyncErrorHandlerManager;

  private ExtraGradleSyncModelsManager myExtraGradleSyncModelsManager;
  private SyncExecutor mySyncExecutor;

  @Before
  public void setUp() {
    initMocks(this);
    myExtraGradleSyncModelsManager = new ExtraGradleSyncModelsManager(new ArrayList<>());
    mySyncExecutor = new SyncExecutor(myProject, myExtraGradleSyncModelsManager, myCommandLineArgs, mySyncErrorHandlerManager);
  }

  @After
  public void tearDown() {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.clearOverride();
  }

  @Test
  public void singleVariantSyncShouldBeDisabled() {
    assertFalse(StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.get());
  }

  @Test
  public void createSyncActionWhenSingleVariantSyncIsEnabled() {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);

    SyncAction action = mySyncExecutor.createSyncAction();
    assertSame(myExtraGradleSyncModelsManager.getJavaModelTypes(), action.getExtraJavaModelTypes());

    SyncActionOptions options = action.getOptions();
    assertTrue(options.isSingleVariantSyncEnabled());
  }

  @Test
  public void createSyncActionWhenSingleVariantSyncIsDisabled() {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(false);

    SyncAction action = mySyncExecutor.createSyncAction();
    assertSame(myExtraGradleSyncModelsManager.getJavaModelTypes(), action.getExtraJavaModelTypes());

    SyncActionOptions options = action.getOptions();
    assertFalse(options.isSingleVariantSyncEnabled());
  }
}