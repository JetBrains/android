/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation.model;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.navigation.NavigationEditor;
import com.android.tools.idea.editors.navigation.NavigationEditorUtils;
import com.android.tools.idea.editors.navigation.macros.Analyser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

/**
 * Base class for NavigationEditor's parsing infrastructure.
 * <p/>
 * Derived from {@link org.jetbrains.android.AndroidRenameTest}
 */
public abstract class NavigationEditorTest extends AndroidTestCase {
  public NavigationEditorTest() {
    super(false);
  }

  protected abstract String getPath();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyDirectoryToProject(getPath(), ".");
  }

  protected NavigationModel getNavigationModel(String deviceQualifier) {
    Project project = myFixture.getProject();
    Module module = myFixture.getModule();
    VirtualFile navFile =
      NavigationEditorUtils
        .getNavigationFile(project.getBaseDir(), module.getName(), deviceQualifier, NavigationEditor.NAVIGATION_FILE_NAME);
    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(navFile);
    Analyser analyser = new Analyser(module);
    return analyser.getNavigationModel(configuration);
  }

  protected void assertDerivationCounts(String deviceQualifier, int expectedStateCount, int expectedTransitionCount) {
    NavigationModel model = getNavigationModel(deviceQualifier);
    assertEquals(model.getStates().size(), expectedStateCount);
    assertEquals(model.getTransitions().size(), expectedTransitionCount);
  }
}
