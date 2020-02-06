/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.data.service;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.JAVA_MODULE_MODEL;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetup;
import com.android.tools.idea.testing.IdeComponents;
import com.android.tools.idea.testing.ProjectFiles;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.PlatformTestCase;
import java.util.Collection;
import java.util.Collections;
import org.mockito.Mock;

/**
 * Tests for {@link JavaModuleModelDataService}.
 */
public class JavaModuleModelDataServiceTest extends PlatformTestCase {
  @Mock private JavaModuleSetup myModuleSetup;
  @Mock private GradleSyncState mySyncState;
  @Mock private ModuleSetupContext.Factory myModuleSetupContextFactory;
  @Mock private ModuleSetupContext myModuleSetupContext;

  private IdeModifiableModelsProvider myModelsProvider;
  private JavaModuleModelDataService myService;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    new IdeComponents(getProject()).replaceProjectService(GradleSyncState.class, mySyncState);
    myModelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    myService = new JavaModuleModelDataService(myModuleSetupContextFactory, myModuleSetup);
  }

  public void testGetTargetDataKey() {
    assertSame(JAVA_MODULE_MODEL, myService.getTargetDataKey());
  }

  public void testImportData() {
    String appModuleName = "app";
    Module appModule = createModule(appModuleName);

    JavaModuleModel model = mock(JavaModuleModel.class);
    when(model.getModuleName()).thenReturn(appModuleName);

    DataNode<JavaModuleModel> dataNode = new DataNode<>(JAVA_MODULE_MODEL, model, null);
    Collection<DataNode<JavaModuleModel>> dataNodes = Collections.singleton(dataNode);

    when(myModuleSetupContextFactory.create(appModule, myModelsProvider)).thenReturn(myModuleSetupContext);
    myService.importData(dataNodes, null, getProject(), myModelsProvider);

    verify(myModuleSetup).setUpModule(myModuleSetupContext, model);
  }

  public void testImportDataWithoutModels() {
    Module appModule = ProjectFiles.createModule(getProject(), "app");
    FacetManager.getInstance(appModule).createFacet(JavaFacet.getFacetType(), JavaFacet.getFacetName(), null);
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());

    myService.importData(Collections.emptyList(), getProject(), modelsProvider, Collections.emptyMap());
    assertNull(FacetManager.getInstance(appModule).findFacet(JavaFacet.getFacetTypeId(), JavaFacet.getFacetName()));
  }
}
