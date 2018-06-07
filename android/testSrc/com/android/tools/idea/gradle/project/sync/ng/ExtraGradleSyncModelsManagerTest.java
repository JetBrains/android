/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ExtraGradleSyncModelsManager}.
 */
public class ExtraGradleSyncModelsManagerTest {
  @Mock private ExtraGradleSyncJavaModels myJavaExtension1;
  @Mock private ExtraGradleSyncJavaModels myJavaExtension2;
  @Mock private GradleModuleModels myModuleModels;
  @Mock private Module myModule;
  @Mock private IdeModifiableModelsProvider myModelsProvider;

  private ExtraGradleSyncModelsManager myExtraModelsManager;

  private static class MockAppEngineProject {
  }

  @Before
  public void setUp() {
    initMocks(this);
    when(myJavaExtension1.getModelTypes()).thenReturn(Collections.singleton(MockAppEngineProject.class));

    myExtraModelsManager =
      new ExtraGradleSyncModelsManager(Arrays.asList(myJavaExtension1, myJavaExtension2));
  }

  @Test
  public void testGetExtraModels() {
    assertThat(myExtraModelsManager.getJavaModelTypes()).containsExactly(MockAppEngineProject.class);
  }

  @Test
  public void testSetupJavaModuleExtraModels() {
    myExtraModelsManager.applyModelsToModule(myModuleModels, myModule, myModelsProvider);
    verify(myJavaExtension1, times(1)).applyModelsToModule(myModuleModels, myModule, myModelsProvider);
    verify(myJavaExtension2, times(1)).applyModelsToModule(myModuleModels, myModule, myModelsProvider);
  }
}
