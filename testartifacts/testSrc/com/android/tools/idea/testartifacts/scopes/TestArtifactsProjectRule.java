/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_MULTIPROJECT;
import static com.intellij.openapi.util.io.FileUtil.createIfDoesntExist;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class TestArtifactsProjectRule implements TestRule {
  AndroidGradleProjectRule projectRule;
  RuleChain ruleChain;

  public TestArtifactsProjectRule() {
    this.projectRule = new AndroidGradleProjectRule();
    this.ruleChain = RuleChain.outerRule(projectRule).around(new EdtRule());
  }

  @Override
  public Statement apply(Statement base, Description description) {
    Statement statement = new Statement() {
      @Override
      public void evaluate() throws Throwable {
        // Do not run tests on Windows (see http://b.android.com/222904)
        assumeTrue(!SystemInfo.isWindows);
        projectRule.loadProject(TEST_ARTIFACTS_MULTIPROJECT);
        base.evaluate();
      }
    };
    return ruleChain.apply(statement, description);
  }

  public @NotNull CodeInsightTestFixture getFixture() {
    return projectRule.getFixture();
  }

  public @NotNull Project getProject() {
    return projectRule.getProject();
  }

  public @NotNull VirtualFile setFileContent(@NotNull String path, @NotNull String content) throws Exception {
    File file = new File(getProject().getBasePath(), path.replace('/', File.separatorChar));
    createIfDoesntExist(file);
    VirtualFile virtualFile = findFileByIoFile(file, true);
    assertNotNull(virtualFile);
    getFixture().saveText(virtualFile, content);
    getFixture().configureFromExistingVirtualFile(virtualFile);
    AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(getProject());
    return virtualFile;
  }

  public @NotNull VirtualFile setUnitTestFileContent(@NotNull String filename, @NotNull String content) throws Exception {
    return setFileContent("module1/src/test/java/" + filename, content);
  }

  public @NotNull VirtualFile setAndroidTestFileContent(@NotNull String filename, @NotNull String content) throws Exception {
    return setFileContent("module1/src/androidTest/java/" + filename, content);
  }

  public @NotNull VirtualFile setCommonFileContent(@NotNull String filename, @NotNull String content) throws Exception {
    return setFileContent("module1/src/main/java/" + filename, content);
  }
}
