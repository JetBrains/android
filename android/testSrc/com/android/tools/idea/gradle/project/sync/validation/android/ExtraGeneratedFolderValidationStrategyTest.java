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
package com.android.tools.idea.gradle.project.sync.validation.android;

import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.gradle.util.GeneratedSourceFolders;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject.syncMessage;
import static com.android.tools.idea.project.messages.MessageType.INFO;
import static com.android.tools.idea.project.messages.MessageType.WARNING;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ExtraGeneratedFolderValidationStrategy}.
 */
public class ExtraGeneratedFolderValidationStrategyTest extends AndroidGradleTestCase {
  @Mock private GeneratedSourceFolders myGeneratedSourceFolders;
  private ExtraGeneratedFolderValidationStrategy myStrategy;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myStrategy = new ExtraGeneratedFolderValidationStrategy(getProject(), myGeneratedSourceFolders);
  }

  public void testValidateWithGeneratedSourceFoldersAreInCorrectLocation() {
    IdeAndroidProject androidProject = mock(IdeAndroidProject.class);
    File aFile = new File("a");
    File bFile = new File("b");
    when(myGeneratedSourceFolders.isFolderGeneratedInCorrectLocation(aFile, androidProject)).thenReturn(true);
    when(myGeneratedSourceFolders.isFolderGeneratedInCorrectLocation(bFile, androidProject)).thenReturn(true);

    AndroidModuleModel androidModel = mockAndroidModel(aFile, bFile);
    when(androidModel.getAndroidProject()).thenReturn(androidProject);

    myStrategy.validate(mock(Module.class), androidModel);
    assertThat(myStrategy.getExtraGeneratedSourceFolderPaths()).isEmpty();
  }

  public void testValidateWithGeneratedSourceFoldersAreInIncorrectLocation() {
    IdeAndroidProject androidProject = mock(IdeAndroidProject.class);
    File aFile = new File("a");
    File bFile = new File("b");
    when(myGeneratedSourceFolders.isFolderGeneratedInCorrectLocation(aFile, androidProject)).thenReturn(false);
    when(myGeneratedSourceFolders.isFolderGeneratedInCorrectLocation(bFile, androidProject)).thenReturn(false);

    AndroidModuleModel androidModel = mockAndroidModel(aFile, bFile);
    when(androidModel.getAndroidProject()).thenReturn(androidProject);

    myStrategy.validate(mock(Module.class), androidModel);
    assertThat(myStrategy.getExtraGeneratedSourceFolderPaths()).containsAllOf(aFile, bFile);
  }

  public void testValidateWithoutExtraFolders() {
    AndroidModuleModel androidModel = mockAndroidModel();

    myStrategy.validate(mock(Module.class), androidModel);
    assertThat(myStrategy.getExtraGeneratedSourceFolderPaths()).isEmpty();
  }

  @NotNull
  private static AndroidModuleModel mockAndroidModel(@NotNull File... extraFolderPaths) {
    AndroidModuleModel androidModel = mock(AndroidModuleModel.class);
    when(androidModel.getExtraGeneratedSourceFolderPaths()).thenReturn(extraFolderPaths);
    return androidModel;
  }

  public void testFixAndReportFoundIssues() {
    List<File> paths = myStrategy.getExtraGeneratedSourceFolderPaths();
    paths.add(new File("z"));
    paths.add(new File("a"));

    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(getProject(), getTestRootDisposable());

    myStrategy.fixAndReportFoundIssues();

    List<SyncMessage> messages = syncMessages.getReportedMessages();
    assertThat(messages).hasSize(3);

    // @formatter:off
    assertAbout(syncMessage()).that(messages.get(0)).hasType(WARNING)
                                                    .hasMessageLine("Folder a", 0);

    assertAbout(syncMessage()).that(messages.get(1)).hasType(WARNING)
                                                    .hasMessageLine("Folder z", 0);

    assertAbout(syncMessage()).that(messages.get(2)).hasType(INFO)
                                                    .hasMessageLine("3rd-party Gradle plug-ins may be the cause", 0);
    // @formatter:on
  }

  public void testFixAndReportFoundIssuesWithoutExtraFolders() {
    myStrategy.getExtraGeneratedSourceFolderPaths().clear();

    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(getProject(), getTestRootDisposable());

    myStrategy.fixAndReportFoundIssues();

    List<SyncMessage> messages = syncMessages.getReportedMessages();
    assertThat(messages).isEmpty();
  }
}