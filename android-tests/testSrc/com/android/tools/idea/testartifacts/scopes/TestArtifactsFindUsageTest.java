/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.scopes;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class TestArtifactsFindUsageTest extends TestArtifactsTestCase {
  private static final String CLASS_TEXT = "class My<caret>Class {};";
  private static final String USAGE_TEXT = "import MyClass;";

  private VirtualFile myUnitTestFile;
  private VirtualFile myAndroidTestFile;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myUnitTestFile = setUnitTestFileContent("Test.java", USAGE_TEXT);
    myAndroidTestFile = setAndroidTestFileContent("AndroidTest.java", USAGE_TEXT);
  }

  // Flaky test, reactivate when investigated (http://b.android.com/226541)
  public void /*test*/FindUsagesInBothTests() throws Exception {
    setCommonFileContent("MyClass.java", CLASS_TEXT);
    Collection<UsageInfo> usages = myFixture.findUsages(myFixture.getElementAtCaret());
    assertSize(2, usages);
  }

  // Flaky test, reactivate when investigated (http://b.android.com/226541)
  public void /*test*/FindUsagesOnlyInUnitTest() throws Exception {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    setUnitTestFileContent("MyClass.java", CLASS_TEXT);
    Collection<UsageInfo> usages = myFixture.findUsages(myFixture.getElementAtCaret());

    assertSize(1, usages);
    UsageInfo usage = ContainerUtil.getFirstItem(usages);
    assertNotNull(usage);
    assertEquals(myUnitTestFile, usage.getFile());
  }

  // Flaky test, reactivate when investigated (http://b.android.com/226541)
  public void /*test*/FindUsagesInOnlyAndroidTest() throws Exception {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    setAndroidTestFileContent("MyClass.java", CLASS_TEXT);
    Collection<UsageInfo> usages = myFixture.findUsages(myFixture.getElementAtCaret());

    assertSize(1, usages);
    UsageInfo usage = ContainerUtil.getFirstItem(usages);
    assertNotNull(usage);
    assertEquals(myAndroidTestFile, usage.getFile());
  }

  private static void assertEquals(@NotNull VirtualFile a, @Nullable PsiFile b) {
    assertNotNull(b);
    assertEquals(a, b.getVirtualFile());
  }
}
