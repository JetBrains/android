/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.output;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.build.quickFixes.OpenJavaLanguageSpecQuickFix;
import com.android.tools.idea.gradle.project.build.quickFixes.OpenSourceCompatibilityLinkQuickFix;
import com.android.tools.idea.gradle.project.build.quickFixes.OpenTargetCompatibilityLinkQuickFix;
import com.android.tools.idea.gradle.project.build.quickFixes.PickLanguageLevelInPSDQuickFix;
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix;
import com.android.tools.idea.gradle.project.sync.quickFixes.AbstractSetJavaLanguageLevelQuickFix;
import com.android.tools.idea.gradle.project.sync.quickFixes.SetJavaLanguageLevelAllQuickFix;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.BuildIssueEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.pom.java.LanguageLevel;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class AndroidGradlePluginOutputParserTest {
  @Mock private BuildOutputInstantReader myReader;
  @Mock private Consumer<BuildEvent> myConsumer;
  @Nullable private AndroidGradlePluginOutputParser myParser;

  @Before
  public void setUp() {
    initMocks(this);
    myParser = new AndroidGradlePluginOutputParser();
  }

  @Test
  public void testParseWarningFromOutput() {
    String line = "WARNING: Configuration 'compile' is obsolete and has been replaced with 'implementation'.";
    String expected = "Configuration 'compile' is obsolete and has been replaced with 'implementation'.";
    when(myReader.getParentEventId()).thenReturn("BUILD_ID_MOCK");

    ArgumentCaptor<MessageEvent> messageCaptor = ArgumentCaptor.forClass(MessageEvent.class);
    assertTrue(myParser.parse(line, myReader, myConsumer));
    verify(myConsumer).accept(messageCaptor.capture());

    List<MessageEvent> generatedMessages = messageCaptor.getAllValues();
    assertThat(generatedMessages).hasSize(1);
    assertThat(generatedMessages.get(0)).isInstanceOf(MessageEventImpl.class);
    MessageEventImpl fileMessageEvent = (MessageEventImpl)generatedMessages.get(0);
    assertThat(fileMessageEvent.getResult().getDetails()).isEqualTo(expected);
  }

  @Test
  public void testParseJavacWithSource() {
    String line = "MyClass.java:38: warning: [serial] serializable class MyClass has no definition of serialVersionUID";
    when(myReader.getParentEventId()).thenReturn("BUILD_ID_MOCK");
    assertFalse(myParser.parse(line, myReader, myConsumer));
  }

  /**
   * Javac warnings without sources are currently treated as AGP warnings as there is no reliable way to distinguish them from each other.
   */
  @Test
  public void testParseJavacWithoutSource() {
    String line = "warning: [serial] serializable class MyClass has no definition of serialVersionUID";
    when(myReader.getParentEventId()).thenReturn("BUILD_ID_MOCK");
    assertTrue(myParser.parse(line, myReader, myConsumer));
  }

  @Test
  public void testParseAGPResourceWarning() {
    String line = "warning: string 'snowball' has no default translation.\n";
    when(myReader.getParentEventId()).thenReturn("BUILD_ID_MOCK");
    assertTrue(myParser.parse(line, myReader, myConsumer));
  }

  @Test
  public void testParseAGPError() {
    String line = "ERROR: Something went wrong!\n";
    when(myReader.getParentEventId()).thenReturn("BUILD_ID_MOCK");
    assertTrue(myParser.parse(line, myReader, myConsumer));
  }

  @Test
  public void testParseJavaError() {
    String line = "MyClass.java:23 error: Something went REALLY wrong!\n";
    when(myReader.getParentEventId()).thenReturn("BUILD_ID_MOCK");
    assertFalse(myParser.parse(line, myReader, myConsumer));
  }
}
