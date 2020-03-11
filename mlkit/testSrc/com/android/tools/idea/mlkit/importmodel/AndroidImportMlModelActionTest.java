/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit.importmodel;

import static org.mockito.Mockito.when;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.projectsystem.AndroidModulePaths;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.testFramework.TestActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AndroidImportMlModelActionTest extends AndroidTestCase {

  @Mock
  private AndroidModulePaths myDebugPaths;

  private NamedModuleTemplate myDebugTemplate;
  private AndroidImportMlModelAction myAction;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    MockitoAnnotations.initMocks(this);

    StudioFlags.MLKIT_TFLITE_MODEL_FILE_TYPE.override(true);

    myAction = new AndroidImportMlModelAction();

    myDebugTemplate = new NamedModuleTemplate("debug", myDebugPaths);
    when(myDebugPaths.getMlModelsDirectories()).thenReturn(Arrays.asList(new File("/project/debug/ml")));
  }

  @Override
  public void tearDown() throws Exception {
    StudioFlags.MLKIT_TFLITE_MODEL_FILE_TYPE.clearOverride();

    super.tearDown();
  }

  public void testUpdateProjectIsNull() {
    TestActionEvent event = new TestActionEvent(SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT.getName(), null));

    myAction.update(event);

    assertFalse(event.getPresentation().isEnabled());
  }

  public void testGetModuleMlDirectory_onlyOneTemplate_selectIt() {
    List<NamedModuleTemplate> namedModuleTemplates = new ArrayList<>();
    namedModuleTemplates.add(myDebugTemplate);

    assertEquals(myAction.getModuleMlDirectory(namedModuleTemplates).getPath(), "/project/debug/ml");
  }

  public void testGetModuleMlDirectory_noMlDirectory_shouldNotCrash() {
    when(myDebugPaths.getMlModelsDirectories()).thenReturn(Collections.emptyList());
    List<NamedModuleTemplate> namedModuleTemplates = new ArrayList<>();
    namedModuleTemplates.add(myDebugTemplate);

    assertEquals(myAction.getModuleMlDirectory(namedModuleTemplates), null);
  }
}
