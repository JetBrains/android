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

import org.gradle.tooling.BuildController;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link SyncAction}.
 */
public class SyncActionTest {
  @Mock private SyncProjectModels.Factory myModelsFactory;
  @Mock private SyncProjectModels myModels;
  @Mock private BuildController myBuildController;

  private SyncAction mySyncAction;

  @Before
  public void setUp() {
    initMocks(this);

    Set<Class<?>> extraAndroidModelTypes = new HashSet<>();
    Set<Class<?>> extraJavaModelTypes = new HashSet<>();
    SyncActionOptions options = new SyncActionOptions();

    when(myModelsFactory.create(extraAndroidModelTypes, extraAndroidModelTypes, options)).thenReturn(myModels);

    mySyncAction = new SyncAction(extraAndroidModelTypes, extraJavaModelTypes, options, myModelsFactory);
  }

  @Test
  public void execute() {
    SyncProjectModels models = mySyncAction.execute(myBuildController);
    assertSame(myModels, models);

    verify(myModels).populate(myBuildController);
  }
}