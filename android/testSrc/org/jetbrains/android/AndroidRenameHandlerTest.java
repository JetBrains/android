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
package org.jetbrains.android;

import static org.junit.Assert.*;

import com.android.SdkConstants;
import com.android.tools.idea.res.ResourceGroupVirtualDirectory;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.testFramework.MapDataContext;
import com.intellij.util.WaitFor;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AndroidRenameHandlerTest {

  @Rule
  public AndroidProjectRule rule = AndroidProjectRule.onDisk().initAndroid(true);

  @Test
  public void renameResourceGroup() {
    rule.fixture.setTestDataPath(AndroidTestBase.getTestDataPath());
    rule.fixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML);
    PsiFile file1 = rule.fixture.addFileToProject("res/drawable/icon.png", "");
    PsiFile file2 = rule.fixture.addFileToProject("res/drawable-xhdpi/icon.xml", "");

    AndroidRenameHandler handler = new AndroidRenameHandler();
    PsiElement[] psiElements = {
      PsiDirectoryFactory
        .getInstance(rule.getProject())
        .createDirectory(new ResourceGroupVirtualDirectory("icon", ImmutableList.of(file1, file2)))
    };

    ApplicationManager.getApplication().invokeAndWait(() -> handler.invoke(rule.getProject(), psiElements, new MapDataContext(
      ImmutableMap.of(PsiElementRenameHandler.DEFAULT_NAME, "foo")
    )));
    assertEquals("foo.png", file1.getName());
    assertEquals("foo.xml", file2.getName());
  }
}