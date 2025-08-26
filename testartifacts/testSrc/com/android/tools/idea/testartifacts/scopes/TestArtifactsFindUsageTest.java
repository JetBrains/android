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

import static com.google.common.truth.Truth.assertThat;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@RunsInEdt
public class TestArtifactsFindUsageTest {
  private static final String CLASS_TEXT = "class My<caret>Class {};";
  private static final String USAGE_TEXT = "import MyClass;";

  private VirtualFile myUnitTestFile;
  private VirtualFile myAndroidTestFile;

  @Rule
  public TestArtifactsProjectRule rule = new TestArtifactsProjectRule();

  @Before
  public void setup() throws Exception {
    myUnitTestFile = rule.setUnitTestFileContent("Test.java", USAGE_TEXT);
    myAndroidTestFile = rule.setAndroidTestFileContent("AndroidTest.java", USAGE_TEXT);
  }

  @Test
  public void testFindUsagesInBothTests() throws Exception {
    rule.setCommonFileContent("MyClass.java", CLASS_TEXT);
    Collection<UsageInfo> usages = rule.getFixture().findUsages(rule.getFixture().getElementAtCaret());
    assertThat(usages).hasSize(2);
  }

  @Test
  public void testFindUsagesOnlyInUnitTest() throws Exception {
    rule.setUnitTestFileContent("MyClass.java", CLASS_TEXT);
    Collection<UsageInfo> usages = rule.getFixture().findUsages(rule.getFixture().getElementAtCaret());

    assertThat(usages).hasSize(1);
    UsageInfo usage = ContainerUtil.getFirstItem(usages);
    assertThat(usage).isNotNull();
    assertThat(usage.getVirtualFile()).isEqualTo(myUnitTestFile);
  }

  @Test
  public void testFindUsagesInOnlyAndroidTest() throws Exception {
    rule.setAndroidTestFileContent("MyClass.java", CLASS_TEXT);
    Collection<UsageInfo> usages = rule.getFixture().findUsages(rule.getFixture().getElementAtCaret());

    assertThat(usages).hasSize(1);
    UsageInfo usage = ContainerUtil.getFirstItem(usages);
    assertThat(usage).isNotNull();
    assertThat(usage.getVirtualFile()).isEqualTo(myAndroidTestFile);
  }
}
