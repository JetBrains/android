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

import com.google.common.collect.Sets;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ExtraSyncModelExtensionManager}.
 */
public class ExtraSyncModelExtensionManagerTest {
  @Mock private ExtraJavaSyncModelExtension myJavaExtension1;
  @Mock private ExtraJavaSyncModelExtension myJavaExtension2;
  @Mock private SyncAction.ModuleModels myModuleModels;
  @Mock private Project myProject;
  @Mock private Module myModule;
  @Mock private IdeModifiableModelsProvider myModelsProvider;
  private ExtraSyncModelExtensionManager myExtraSyncModelExtensionManager;

  private static class MockAppEngineProject {
  }

  @Before
  public void setUp() {
    initMocks(this);
    when(myJavaExtension1.getExtraProjectModelClasses()).thenReturn(Sets.newHashSet(MockAppEngineProject.class));

    myExtraSyncModelExtensionManager =
      new ExtraSyncModelExtensionManager(Arrays.asList(myJavaExtension1, myJavaExtension2));
  }

  @Test
  public void testGetExtraModels() {
    assertThat(myExtraSyncModelExtensionManager.getExtraJavaModels()).containsExactly(MockAppEngineProject.class);
  }

  @Test
  public void testSetupJavaModuleExtraModels() {
    myExtraSyncModelExtensionManager.setupExtraJavaModels(myModuleModels, myProject, myModule, myModelsProvider);
    verify(myJavaExtension1, times(1)).setupExtraModels(myModuleModels, myProject, myModule, myModelsProvider);
    verify(myJavaExtension2, times(1)).setupExtraModels(myModuleModels, myProject, myModule, myModelsProvider);
  }
}
