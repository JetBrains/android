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

import com.android.tools.idea.gradle.project.sync.ng.ProjectSetup.ProjectSetupImpl;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlyProjectModels;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlyProjectModelsSetup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.JavaProjectTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mock;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ProjectSetupImpl}.
 */
public class ProjectSetupImplTest extends JavaProjectTestCase {
  @Mock private ModuleSetup.Factory myModuleSetupFactory;
  @Mock private SyncProjectModelsSetup myModuleSetup;
  @Mock private VariantOnlyProjectModelsSetup myVariantOnlyModuleSetup;
  @Mock private SyncProjectModels myModels;
  @Mock private VariantOnlyProjectModels myVariantOnlyModels;

  private IdeModifiableModelsProviderStub myModelsProvider;
  private ProgressIndicator myIndicator;

  private ProjectSetupImpl myProjectSetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    myModelsProvider = new IdeModifiableModelsProviderStub(project);
    myIndicator = new EmptyProgressIndicator();

    when(myModuleSetupFactory.createForFullSync(project, myModelsProvider)).thenReturn(myModuleSetup);
    when(myModuleSetupFactory.createForVariantOnlySync(project, myModelsProvider)).thenReturn(myVariantOnlyModuleSetup);

    myProjectSetup = new ProjectSetupImpl(project, myModelsProvider, myModuleSetupFactory);
  }

  public void testSetUpProject() {
    myProjectSetup.setUpProject(myModels, myIndicator);
    verify(myModuleSetup).setUpModules(myModels, myIndicator);
  }

  public void testSetUpProjectWithError() {
    RuntimeException error = new RuntimeException("Test");
    doThrow(error).when(myModuleSetup).setUpModules(myModels, myIndicator);

    try {
      myProjectSetup.setUpProject(myModels, myIndicator);
      fail("Expecting RuntimeException");
    }
    catch (RuntimeException expected) {
      assertSame(error, expected);
    }

    verify(myModuleSetup).setUpModules(myModels, myIndicator);
    myModelsProvider.assertDisposeWasInvoked();
  }

  public void testCommit() {
    myProjectSetup.commit();
    myModelsProvider.assertCommitWasInvoked();
  }

  public void testCommitWithError() {
    RuntimeException error = new RuntimeException("Test");
    myModelsProvider.setCommitError(error);

    try {
      myProjectSetup.commit();
      fail("Expecting RuntimeException");
    }
    catch (RuntimeException expected) {
      assertSame(error, expected);
    }

    myModelsProvider.assertCommitWasInvoked();
    myModelsProvider.assertDisposeWasInvoked();
  }

  public void testSetUpProjectWithVariantOnlySync() {
    myProjectSetup.setUpProject(myVariantOnlyModels, myIndicator);
    verify(myVariantOnlyModuleSetup).setUpModules(myVariantOnlyModels, myIndicator);
  }

  private static class IdeModifiableModelsProviderStub extends IdeModifiableModelsProviderImpl {
    private boolean myCommitInvoked;
    private boolean myDisposeInvoked;
    @Nullable private RuntimeException myCommitError;

    IdeModifiableModelsProviderStub(@NotNull Project project) {
      super(project);
    }

    @Override
    public void commit() {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      myCommitInvoked = true;
      if (myCommitError != null) {
        throw myCommitError;
      }
    }

    @Override
    public void dispose() {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      myDisposeInvoked = true;
    }

    void assertCommitWasInvoked() {
      assertTrue(myCommitInvoked);
    }

    void assertDisposeWasInvoked() {
      assertTrue(myDisposeInvoked);
    }

    void setCommitError(@Nullable RuntimeException commitError) {
      myCommitError = commitError;
    }
  }
}