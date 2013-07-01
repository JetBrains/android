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
package com.android.tools.idea.gradle.service;

import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.testFramework.IdeaTestCase;

import java.util.List;

import static org.easymock.EasyMock.*;

/**
 * Tests for {@link AndroidProjectDataService}.
 */
public class AndroidProjectDataServiceTest extends IdeaTestCase {
  private static final String DEBUG = "debug";

  private AndroidProjectStub myAndroidProject;
  private IdeaAndroidProject myIdeaAndroidProject;
  private ModuleCustomizer myCustomizer1;
  private ModuleCustomizer myCustomizer2;

  private AndroidProjectDataService service;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myAndroidProject = new AndroidProjectStub(myModule.getName());
    myAndroidProject.addVariant(DEBUG);
    myAndroidProject.addBuildType(DEBUG);
    String rootDirPath = myAndroidProject.getRootDir().getAbsolutePath();
    myIdeaAndroidProject = new IdeaAndroidProject(myAndroidProject.getName(), rootDirPath, myAndroidProject, DEBUG);
    myCustomizer1 = createMock(ModuleCustomizer.class);
    myCustomizer2 = createMock(ModuleCustomizer.class);
    service = new AndroidProjectDataService(myCustomizer1, myCustomizer2);
  }

  @Override
  protected void tearDown() throws Exception {
    if (myAndroidProject != null) {
      myAndroidProject.dispose();
    }
    super.tearDown();
  }

  public void testImportData() {
    List<DataNode<IdeaAndroidProject>> nodes = Lists.newArrayList();
    Key<IdeaAndroidProject> key = AndroidProjectKeys.IDE_ANDROID_PROJECT;
    nodes.add(new DataNode<IdeaAndroidProject>(key, myIdeaAndroidProject, null));

    assertEquals(key, service.getTargetDataKey());

    // ModuleCustomizers should be called.
    myCustomizer1.customizeModule(myModule, myProject, myIdeaAndroidProject);
    expectLastCall();

    myCustomizer2.customizeModule(myModule, myProject, myIdeaAndroidProject);
    expectLastCall();

    replay(myCustomizer1, myCustomizer2);

    service.importData(nodes, myProject, true);

    verify(myCustomizer1, myCustomizer2);
  }
}
