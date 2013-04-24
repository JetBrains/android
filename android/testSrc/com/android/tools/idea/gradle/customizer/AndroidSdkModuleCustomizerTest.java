/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.AndroidSdkModuleCustomizer.AndroidPlatformParser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import junit.framework.TestCase;
import org.jetbrains.android.sdk.AndroidPlatform;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link AndroidSdkModuleCustomizer}.
 */
public class AndroidSdkModuleCustomizerTest extends TestCase {
  private Module myModule;
  private Project myProject;
  private ModuleRootManager myModuleRootManager;
  private ModifiableRootModel myModel;
  private ProjectJdkTable myProjectJdkTable;
  private AndroidPlatformParser myParser;

  private AndroidSdkModuleCustomizer myCustomizer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myModule = createMock(Module.class);
    myProject = createMock(Project.class);
    myModuleRootManager = createMock(ModuleRootManager.class);
    myModel = createMock(ModifiableRootModel.class);
    myProjectJdkTable = createMock(ProjectJdkTable.class);
    myParser = createMock(AndroidPlatformParser.class);
    myCustomizer = new AndroidSdkModuleCustomizer(myProjectJdkTable, myParser);
  }

  public void testCustomizeModuleWithNullAndroidProject() {
    replay(myModule, myModuleRootManager, myModel, myProjectJdkTable, myParser);

    // Nothing should be customized since IdeaAndroidProject is null.
    myCustomizer.customizeModule(myModule, myProject, null);

    verify(myModule, myModuleRootManager, myModel, myProjectJdkTable, myParser);
  }

  public void testCustomizeModuleWithoutAndroidSdk() {
    Sdk sdk = createMock(Sdk.class);
    Sdk[] sdks = { sdk };

    expect(myProjectJdkTable.getAllJdks()).andReturn(sdks);
    expect(myParser.parse(sdk)).andReturn(null);

    replay(myModule, myModuleRootManager, myModel, myProjectJdkTable, myParser);

    // Nothing should be customized since there are no Android SDKs.
    myCustomizer.customizeModule(myModule, myProject, createMock(IdeaAndroidProject.class));

    verify(myModule, myModuleRootManager, myModel, myProjectJdkTable, myParser);
  }

  public void testCustomizeModule() {
    Sdk sdk = createMock(Sdk.class);
    Sdk[] sdks = { sdk };

    expect(myProjectJdkTable.getAllJdks()).andReturn(sdks);

    AndroidPlatform platform = createMock(AndroidPlatform.class);
    expect(myParser.parse(sdk)).andReturn(platform);

    expect(myModule.getComponent(ModuleRootManager.class)).andReturn(myModuleRootManager);
    expect(myModuleRootManager.getModifiableModel()).andReturn(myModel);

    // The Android SDK should be set in the module.
    myModel.setSdk(sdk);
    expectLastCall();

    myModel.commit();
    expectLastCall();

    replay(myModule, myModuleRootManager, myModel, myProjectJdkTable, myParser);

    myCustomizer.customizeModule(myModule, myProject, createMock(IdeaAndroidProject.class));

    verify(myModule, myModuleRootManager, myModel, myProjectJdkTable, myParser);
  }
}
