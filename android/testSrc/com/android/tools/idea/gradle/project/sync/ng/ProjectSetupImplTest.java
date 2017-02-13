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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mock;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ProjectSetupImpl}.
 */
public class ProjectSetupImplTest extends IdeaTestCase {
  @Mock private ModuleSetup.Factory myModuleSetupFactory;
  @Mock private ModuleSetup myModuleSetup;
  @Mock private SyncAction.ProjectModels myModels;

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

    when(myModuleSetupFactory.create(project, myModelsProvider)).thenReturn(myModuleSetup);

    myProjectSetup = new ProjectSetupImpl(project, myModelsProvider, myModuleSetupFactory);
  }

  public void testSetUpProject() {
    myProjectSetup.setUpProject(myModels, myIndicator);
    verify(myModuleSetup).setUpModules(myModels, myIndicator);
  }

  public void testSetUpProjectWitError() {
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