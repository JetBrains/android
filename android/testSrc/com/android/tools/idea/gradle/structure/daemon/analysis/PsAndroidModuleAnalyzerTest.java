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
package com.android.tools.idea.gradle.structure.daemon.analysis;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.navigation.PsNavigationPath;
import org.junit.Test;

import static com.android.builder.model.SyncIssue.SEVERITY_ERROR;
import static com.android.tools.idea.gradle.structure.model.PsIssue.Type.ERROR;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PsAndroidModuleAnalyzer}.
 */
public class PsAndroidModuleAnalyzerTest {
  @Test
  public void createIssueFrom() {
    SyncIssue syncIssue = mock(SyncIssue.class);
    when(syncIssue.getMessage()).thenReturn("Conflict with dependency 'com.google.guava:guava'. Resolved versions for app (16.0) " +
                                            "and test app (16.0.1) differ. See http://g.co/androidstudio/app-test-app-conflict for " +
                                            "details.");
    when(syncIssue.getSeverity()).thenReturn(SEVERITY_ERROR);
    PsNavigationPath path = mock(PsNavigationPath.class);

    PsIssue issue = PsAndroidModuleAnalyzer.createIssueFrom(syncIssue, path, null);
    assertThat(issue.getText()).isEqualTo("Conflict with dependency 'com.google.guava:guava'. Resolved versions for app (16.0) " +
                                          "and test app (16.0.1) differ. See " +
                                          "<a href='http://g.co/androidstudio/app-test-app-conflict'>http://g.co/androidstudio/app-test-app-conflict</a> " +
                                          "for details.");
    assertThat(issue.getType()).isEqualTo(ERROR);
  }

}