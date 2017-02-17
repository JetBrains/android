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
package com.android.tools.idea.navigator.nodes.apk.java;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.mockito.Mock;

import static com.android.tools.idea.navigator.nodes.apk.java.SimpleApplicationContents.getMyActivityApkClass;
import static com.android.tools.idea.navigator.nodes.apk.java.SimpleApplicationContents.getMyActivityFile;
import static com.android.tools.idea.navigator.nodes.apk.java.SimpleApplicationContents.getUnitTestFile;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link PackageNode}.
 */
public class PackageNodeTest extends AndroidGradleTestCase {
  @Mock private ViewSettings mySettings;

  private PackageNode myNode;
  private ApkPackage myPackage;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();

    ApkClass activityApkClass = getMyActivityApkClass();
    myPackage = activityApkClass.getParent();

    myNode = new PackageNode(project, myPackage, mySettings, new ClassFinder(project));
  }

  public void testUpdate() {
    PresentationData presentation = new PresentationData("", "", null, null);

    when(mySettings.isFlattenPackages()).thenReturn(true);
    myNode.update(presentation);
    assertEquals(myPackage.getFqn(), presentation.getPresentableText());

    when(mySettings.isFlattenPackages()).thenReturn(false);
    myNode.update(presentation);
    assertEquals(myPackage.getName(), presentation.getPresentableText());

    when(mySettings.isHideEmptyMiddlePackages()).thenReturn(true);
    myNode.update(presentation);
    assertEquals(myPackage.getFqn(), presentation.getPresentableText());
  }

  public void testContains() throws Exception {
    loadSimpleApplication();
    Module module = myModules.getAppModule();

    VirtualFile activityFile = getMyActivityFile(module);
    assertTrue(myNode.contains(activityFile));

    VirtualFile testFile = getUnitTestFile(module);
    assertTrue(myNode.contains(testFile));
  }
}