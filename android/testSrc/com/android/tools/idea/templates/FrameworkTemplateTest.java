/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.templates;

import static com.android.tools.idea.templates.TemplateMetadata.ATTR_JAVA_VERSION;
import static com.android.tools.idea.templates.TemplateTestUtils.createNewProjectState;
import static com.android.tools.idea.templates.TemplateTestUtils.createRenderingContext;
import static com.android.tools.idea.templates.TemplateTestUtils.getDefaultModuleTemplate;

import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.android.sdk.AndroidSdkData;

/**
 * Template test special cases.
  */
public class FrameworkTemplateTest extends TemplateTestBase {
  public void testJdk7() throws Exception {
    if (DISABLED) {
      return;
    }
    AndroidSdkData sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    assertNotNull(sdkData);

    if (!IdeSdks.getInstance().isJdk7Supported(sdkData)) {
      System.out.println("JDK 7 not supported by current SDK manager: not testing");
      return;
    }
    IAndroidTarget[] targets = sdkData.getTargets();
    IAndroidTarget target = targets[targets.length - 1];
    Map<String, Object> overrides = new HashMap<>();
    overrides.put(ATTR_JAVA_VERSION, "1.7");
    TestNewProjectWizardState state = createNewProjectState(true, sdkData, getDefaultModuleTemplate());

    // TODO: Allow null activity state!
    File activity = findTemplate("activities", "BasicActivity");
    TestTemplateWizardState activityState = state.getActivityTemplateState();
    assertNotNull(activity);
    activityState.setTemplateLocation(activity);

    checkApiTarget(19, 19, target, state, "Test17", null, overrides, ImmutableMap.of());
  }

  public void testTemplateFormatting() throws Exception {
    Template template = Template.createFromPath(new File(getTestDataPath(), FileUtil.join("templates", "TestTemplate")).getCanonicalFile());
    RenderingContext context = createRenderingContext(
      template, myFixture.getProject(), new File(myFixture.getTempDirPath()), new File("dummy"));
    template.render(context, false);
    FileDocumentManager.getInstance().saveAllDocuments();
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    VirtualFile desired = fileSystem.findFileByIoFile(new File(getTestDataPath(),
                                                               FileUtil.join("templates", "TestTemplate", "MergedStringsFile.xml")));
    assertNotNull(desired);
    VirtualFile actual = fileSystem.findFileByIoFile(new File(myFixture.getTempDirPath(),
                                                              FileUtil.join("values", "TestTargetResourceFile.xml")));
    assertNotNull(actual);
    desired.refresh(false, false);
    actual.refresh(false, false);
    PlatformTestUtil.assertFilesEqual(desired, actual);
  }

  public void testRelatedParameters() {
    Template template = Template.createFromPath(new File(getTestDataPath(), FileUtil.join("templates", "TestTemplate")));
    TemplateMetadata templateMetadata = template.getMetadata();
    assertNotNull(templateMetadata);
    Parameter layoutName = templateMetadata.getParameter("layoutName");
    Parameter activityClass = templateMetadata.getParameter("activityClass");
    Parameter mainFragment = templateMetadata.getParameter("mainFragment");
    Parameter activityTitle = templateMetadata.getParameter("activityTitle");
    Parameter detailsActivity = templateMetadata.getParameter("detailsActivity");
    Parameter detailsLayoutName = templateMetadata.getParameter("detailsLayoutName");
    assertSameElements(templateMetadata.getRelatedParams(layoutName), detailsLayoutName);
    assertSameElements(templateMetadata.getRelatedParams(activityClass), detailsActivity, mainFragment);
    assertSameElements(templateMetadata.getRelatedParams(mainFragment), detailsActivity, activityClass);
    assertEmpty(templateMetadata.getRelatedParams(activityTitle));
    assertSameElements(templateMetadata.getRelatedParams(detailsActivity), activityClass, mainFragment);
    assertSameElements(templateMetadata.getRelatedParams(detailsLayoutName), layoutName);
  }
}
