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
package com.android.tools.idea.gradle.project.sync.validation.common;

import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject.syncMessage;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.createTempDirectory;
import static com.intellij.openapi.util.io.FileUtil.createTempFile;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link UniquePathModuleValidatorStrategy}.
 */
public class UniquePathModuleValidatorStrategyTest extends AndroidGradleTestCase {
  @Mock private Module myModule1;
  @Mock private Module myModule2;
  @Mock private Module myModule3;

  private UniquePathModuleValidatorStrategy myStrategy;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    myStrategy = new UniquePathModuleValidatorStrategy(project);
  }

  @NotNull
  private static File doCreateTempFolder(@NotNull String name) throws IOException {
    return createTempDirectory(name, null, true /* delete on exit */);
  }

  public void testValidate() throws IOException {
    File repeatedFolder = doCreateTempFolder("repeated");
    File module1File = doCreateTempFile(repeatedFolder, "module1");
    File module2File = doCreateTempFile(repeatedFolder, "module2");

    File uniqueFolder = doCreateTempFolder("unique");
    File module3File = doCreateTempFile(uniqueFolder, "module3");

    when(myModule1.getModuleFilePath()).thenReturn(module1File.getPath());
    when(myModule2.getModuleFilePath()).thenReturn(module2File.getPath());
    when(myModule3.getModuleFilePath()).thenReturn(module3File.getPath());

    myStrategy.validate(myModule1);
    myStrategy.validate(myModule2);
    myStrategy.validate(myModule3);

    Multimap<String, Module> modulesByPath = myStrategy.getModulesByPath();
    assertThat(modulesByPath).hasSize(3);

    Collection<Module> modulesWithRepeatedPath = modulesByPath.get(repeatedFolder.getPath());
    assertThat(modulesWithRepeatedPath).hasSize(2);
    assertThat(modulesWithRepeatedPath).containsAllOf(myModule1, myModule2);

    Collection<Module> modulesWithUniquePath = modulesByPath.get(uniqueFolder.getPath());
    assertThat(modulesWithUniquePath).hasSize(1);
    assertThat(modulesWithUniquePath).contains(myModule3);
  }

  @NotNull
  private static File doCreateTempFile(@NotNull File parent, @NotNull String name) throws IOException {
    return createTempFile(parent, name, null, true /* create */, true/* delete on exit */);
  }

  public void testFixAndReportFoundIssues() throws Exception {
    Project project = getProject();
    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project);

    Multimap<String, Module> modulesByPath = myStrategy.getModulesByPath();
    modulesByPath.putAll("path", Lists.newArrayList(myModule1, myModule2));

    when(myModule1.getName()).thenReturn("module1");
    when(myModule2.getName()).thenReturn("module2");

    myStrategy.fixAndReportFoundIssues();

    SyncMessage message = syncMessages.getFirstReportedMessage();
    assertNotNull(message);
    assertAbout(syncMessage()).that(message).hasMessageLine("The modules ['module1', 'module2'] point to the same directory in the file system.", 0);
  }

  public void testFixAndReportFoundIssuesWithUniquePaths() throws Exception {
    Project project = getProject();
    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project);

    Multimap<String, Module> modulesByPath = myStrategy.getModulesByPath();
    modulesByPath.putAll("path1", Lists.newArrayList(myModule1));
    modulesByPath.putAll("path2", Lists.newArrayList(myModule2));

    myStrategy.fixAndReportFoundIssues();

    SyncMessage message = syncMessages.getFirstReportedMessage();
    assertNull(message);
  }
}