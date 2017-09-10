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
package com.android.tools.idea.gradle.project.sync.setup.module.idea.java;

import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.common.CompilerSettingsSetup;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;
import org.mockito.Mock;

import java.io.File;

import static com.android.tools.idea.testing.ProjectFiles.createFolderInProjectRoot;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link CompilerOutputModuleSetupStep}.
 */
public class CompilerOutputModuleSetupStepTest extends IdeaTestCase {
  @Mock private IdeModifiableModelsProvider myModelsProvider;
  @Mock private ModifiableRootModel myRootModel;
  @Mock private ExtIdeaCompilerOutput myCompilerOutput;
  @Mock private JavaModuleModel myJavaModel;
  @Mock private CompilerSettingsSetup myCompilerSettingsSetup;

  private File myBuildFolderPath;
  private CompilerOutputModuleSetupStep mySetupStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    when(myModelsProvider.getModifiableRootModel(getModule())).thenReturn(myRootModel);
    when(myJavaModel.getCompilerOutput()).thenReturn(myCompilerOutput);

    VirtualFile buildFolder = createFolderInProjectRoot(getProject(), "build");
    myBuildFolderPath = virtualToIoFile(buildFolder);
    when(myJavaModel.getBuildFolderPath()).thenReturn(myBuildFolderPath);

    mySetupStep = new CompilerOutputModuleSetupStep(myCompilerSettingsSetup);
  }

  public void testDoSetupModule() {
    File mainClassesFolderPath = new File(myBuildFolderPath, join("out", "main"));
    File testClassesFolderPath = new File(myBuildFolderPath, join("out", "test"));

    when(myCompilerOutput.getMainClassesDir()).thenReturn(mainClassesFolderPath);
    when(myCompilerOutput.getTestClassesDir()).thenReturn(testClassesFolderPath);

    // Project has to be "buildable".
    when(myJavaModel.isBuildable()).thenReturn(true);

    mySetupStep.doSetUpModule(getModule(), myModelsProvider, myJavaModel, null, null);

    verify(myCompilerSettingsSetup).setOutputPaths(myRootModel, mainClassesFolderPath, testClassesFolderPath);
  }

  // See: http://b/65513580
  public void testDoSetupModuleWhenBuildOutputIsNullAndModuleIsBuildable() {
    // Make it explicit that compiler output is empty.
    when(myCompilerOutput.getMainClassesDir()).thenReturn(null);
    when(myCompilerOutput.getTestClassesDir()).thenReturn(null);

    // Project has to be "buildable".
    when(myJavaModel.isBuildable()).thenReturn(true);

    mySetupStep.doSetUpModule(getModule(), myModelsProvider, myJavaModel, null, null);

    File mainClassesFolderPath = new File(myBuildFolderPath, join("classes", "main"));
    File testClassesFolderPath = new File(myBuildFolderPath, join("classes", "test"));

    verify(myCompilerSettingsSetup).setOutputPaths(myRootModel, mainClassesFolderPath, testClassesFolderPath);
  }

  // See: http://b/65513580
  public void testDoSetupModuleWhenBuildOutputIsNullAndModuleIsNotBuildable() {
    // Make it explicit that compiler output is empty.
    when(myCompilerOutput.getMainClassesDir()).thenReturn(null);
    when(myCompilerOutput.getTestClassesDir()).thenReturn(null);

    // Project is not "buildable".
    when(myJavaModel.isBuildable()).thenReturn(false);

    mySetupStep.doSetUpModule(getModule(), myModelsProvider, myJavaModel, null, null);

    verify(myCompilerSettingsSetup, never()).setOutputPaths(any(), any(), any());
  }

  public void testIsInvokedOnSkippedSync() {
    assertTrue(mySetupStep.invokeOnSkippedSync());
  }
}