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
package com.android.tools.idea.gradle.project.build.output;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FileMessageEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.output.BuildOutputInstantReader;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link CmakeOutputParser}.
 */
@SuppressWarnings({"resource", "IOResourceOpenedButNotSafelyClosed"})
public class CmakeOutputParserTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Mock public Consumer<? super BuildEvent> messageConsumer;
  private CmakeOutputParser parser;
  private File sourceFile;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    parser = new CmakeOutputParser();
    sourceFile = temporaryFolder.newFile();
  }

  @Test
  public void testPosixFilePatternMatcherForErrorFileAndLineNumberError() {
    int lineNumber = 123;
    int columnNumber = 456;
    String filePath = "/path/to/file.type";
    String error = "CMake Error at %s:%d:%d";
    error = String.format(Locale.getDefault(), error, filePath, lineNumber, columnNumber);

    Matcher matcher = CmakeOutputParser.errorFileAndLineNumber.matcher(error);
    assertTrue("[match file path]", matcher.matches());

    String matchedPath = matcher.group(2);
    CmakeOutputParser.ErrorFields fields =
      CmakeOutputParser.matchErrorFileAndLineNumberErrorParts(matcher, error);
    assertEquals("[source path]", filePath.trim(), matchedPath);
    assertEquals("[line number]", lineNumber, fields.lineNumber);
    assertEquals("[column number]", columnNumber, fields.columnNumber);
  }

  @Test
  public void testPosixFilePatternMatcherForFileAndLineNumberError() {
    int lineNumber = 123;
    int columnNumber = 456;
    String filePath = "/path/to/file.type";
    String error = "%s:%d:%d";
    error = String.format(Locale.getDefault(), error, filePath, lineNumber, columnNumber);

    Matcher matcher = CmakeOutputParser.fileAndLineNumber.matcher(error);
    assertTrue("[match file path]", matcher.matches());

    String matchedPath = matcher.group(1);
    CmakeOutputParser.ErrorFields fields =
      CmakeOutputParser.matchFileAndLineNumberErrorParts(matcher, error);
    assertEquals("[source path]", filePath.trim(), matchedPath);
    assertEquals("[line number]", lineNumber, fields.lineNumber);
    assertEquals("[column number]", columnNumber, fields.columnNumber);
  }

  @Test
  public void testWindowsFilePatternMatcherForErrorFileAndLineNumberError() {
    int lineNumber = 123;
    int columnNumber = 456;
    String filePath = "C:\\Path\\to\\file.type";
    String error = "CMake Error at %s:%d:%d";
    error = String.format(Locale.getDefault(), error, filePath, lineNumber, columnNumber);

    Matcher matcher = CmakeOutputParser.errorFileAndLineNumber.matcher(error);
    assertTrue("[match file path]", matcher.matches());

    String matchedPath = matcher.group(2);
    CmakeOutputParser.ErrorFields fields =
      CmakeOutputParser.matchErrorFileAndLineNumberErrorParts(matcher, error);
    assertEquals("[source path]", filePath.trim(), matchedPath);
    assertEquals("[line number]", lineNumber, fields.lineNumber);
    assertEquals("[column number]", columnNumber, fields.columnNumber);
  }

  @Test
  public void testWindowsFilePatternMatcherForFileAndLineNumberError() {
    int lineNumber = 123;
    int columnNumber = 456;
    String filePath = "C:\\Path\\to\\file.type";
    String error = "%s:%d:%d";
    error = String.format(Locale.getDefault(), error, filePath, lineNumber, columnNumber);

    Matcher matcher = CmakeOutputParser.fileAndLineNumber.matcher(error);
    assertTrue("[match file path]", matcher.matches());

    String matchedPath = matcher.group(1);
    CmakeOutputParser.ErrorFields fields =
      CmakeOutputParser.matchFileAndLineNumberErrorParts(matcher, error);
    assertEquals("[source path]", filePath.trim(), matchedPath);
    assertEquals("[line number]", lineNumber, fields.lineNumber);
    assertEquals("[column number]", columnNumber, fields.columnNumber);
  }

  private static void verifyFileMessageEvent(BuildEvent buildEvent,
                                             String expectedPath,
                                             String group,
                                             MessageEvent.Kind expectedKind,
                                             int expectedLineNumber,
                                             int expectedColumnNumber) {
    assertThat(buildEvent).isInstanceOf(FileMessageEvent.class);

    FileMessageEvent messageEvent = (FileMessageEvent)buildEvent;

    assertThat(messageEvent.getKind()).isEqualTo(expectedKind);

    assertThat(expectedPath).isEqualTo(messageEvent.getFilePosition().getFile().getAbsolutePath());

    assertThat(messageEvent.getFilePosition().getStartLine()).isEqualTo(expectedLineNumber - 1);
    assertThat(messageEvent.getFilePosition().getStartColumn()).isEqualTo(expectedColumnNumber - 1);
    assertThat(messageEvent.getGroup()).isEqualTo(group);
  }

  @SuppressWarnings("SameParameterValue")
  private static void verifyMessageEvent(BuildEvent buildEvent, MessageEvent.Kind expectedKind, String group) {
    assertThat(buildEvent).isInstanceOf(MessageEvent.class);
    assertThat(buildEvent).isNotInstanceOf(FileMessageEvent.class);

    MessageEvent messageEvent = (MessageEvent)buildEvent;

    assertThat(messageEvent.getKind()).isEqualTo(expectedKind);
    assertThat(messageEvent.getGroup()).isEqualTo(group);
  }

  @Test
  public void testMultilineCmakeWarningInFileWithoutLineNumberOrColumn() {
    String prefix = "CMake Warning: Warning in cmake code at\n";
    String filePath = sourceFile.getAbsolutePath();
    String fileAndLineNumber = String.format(Locale.getDefault(), "%s::\n", filePath);
    String err = prefix + fileAndLineNumber;
    BuildOutputInstantReader reader = new TestBuildOutputInstantReader(err);

    ArgumentCaptor<BuildEvent> captor = ArgumentCaptor.forClass(BuildEvent.class);

    assertThat(parser.parse(reader.readLine(), reader, messageConsumer)).isTrue();

    verify(messageConsumer).accept(captor.capture());

    assertThat(captor.getAllValues()).hasSize(1);

    verifyFileMessageEvent(captor.getAllValues().get(0), filePath, "CMake warnings", MessageEvent.Kind.WARNING, 0, 0);
  }

  @Test
  public void testMultilineCmakeWarningInFileWithLineNumber() {
    String prefix = "CMake Warning: Warning in cmake code at\n";
    int lineNumber = 13;
    String filePath = sourceFile.getAbsolutePath();
    String fileAndLineNumber =
      String.format(Locale.getDefault(), "%s:%d:\n", filePath, lineNumber);
    String err = prefix + fileAndLineNumber;
    BuildOutputInstantReader reader = new TestBuildOutputInstantReader(err);

    ArgumentCaptor<BuildEvent> captor = ArgumentCaptor.forClass(BuildEvent.class);

    assertThat(parser.parse(reader.readLine(), reader, messageConsumer)).isTrue();

    verify(messageConsumer).accept(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);

    verifyFileMessageEvent(captor.getAllValues().get(0), filePath, "CMake warnings", MessageEvent.Kind.WARNING, lineNumber, 0);
  }

  @Test
  public void testMultilineCmakeWarningInFileWithLineNumberAndColumnNumber() {
    String prefix = "CMake Warning: Warning in cmake code at\n";
    int lineNumber = 13;
    int columnNumber = 42;
    String filePath = sourceFile.getAbsolutePath();
    String fileAndLineNumber =
      String.format(
        Locale.getDefault(), "%s:%d:%d\n", filePath, lineNumber, columnNumber);
    String err = prefix + fileAndLineNumber;
    BuildOutputInstantReader reader = new TestBuildOutputInstantReader(err);

    ArgumentCaptor<BuildEvent> captor = ArgumentCaptor.forClass(BuildEvent.class);

    assertThat(parser.parse(reader.readLine(), reader, messageConsumer)).isTrue();

    verify(messageConsumer).accept(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);

    verifyFileMessageEvent(captor.getAllValues().get(0), filePath, "CMake warnings", MessageEvent.Kind.WARNING, lineNumber, columnNumber);
  }

  @Test
  public void testMultilineCmakeErrorInFileWithoutLineNumberOrColumn() {
    String prefix = "CMake Error: Error in cmake code at\n";
    String filePath = sourceFile.getAbsolutePath();
    String fileAndLineNumber = String.format(Locale.getDefault(), "%s::\n", filePath);
    String err = prefix + fileAndLineNumber;
    BuildOutputInstantReader reader = new TestBuildOutputInstantReader(err);

    ArgumentCaptor<BuildEvent> captor = ArgumentCaptor.forClass(BuildEvent.class);

    assertThat(parser.parse(reader.readLine(), reader, messageConsumer)).isTrue();

    verify(messageConsumer).accept(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);

    verifyFileMessageEvent(captor.getAllValues().get(0), filePath, "CMake errors", MessageEvent.Kind.ERROR, 0, 0);
  }

  @Test
  public void testMultilineCmakeErrorInFileWithLineNumber() {
    String prefix = "CMake Error: Error in cmake code at\n";
    int lineNumber = 13;
    String filePath = sourceFile.getAbsolutePath();
    String fileAndLineNumber =
      String.format(Locale.getDefault(), "%s:%d:\n", filePath, lineNumber);
    String warning = "CMake Warning: warning message\n";
    String err = warning + prefix + fileAndLineNumber;
    BuildOutputInstantReader reader = new TestBuildOutputInstantReader(err);

    ArgumentCaptor<BuildEvent> captor = ArgumentCaptor.forClass(BuildEvent.class);

    assertThat(parser.parse(reader.readLine(), reader, messageConsumer)).isTrue();
    assertThat(parser.parse(reader.readLine(), reader, messageConsumer)).isTrue();

    verify(messageConsumer, times(2)).accept(captor.capture());
    assertThat(captor.getAllValues()).hasSize(2);

    verifyMessageEvent(captor.getAllValues().get(0), MessageEvent.Kind.WARNING, "CMake warnings");
    verifyFileMessageEvent(captor.getAllValues().get(1), filePath, "CMake errors", MessageEvent.Kind.ERROR, lineNumber, 0);
  }

  @Test
  public void testMultilineCmakeErrorInFileWithLineNumberAndColumnNumber() {
    String prefix = "CMake Error: Error in cmake code at\n";
    int lineNumber = 13;
    int columnNumber = 42;
    String filePath = sourceFile.getAbsolutePath();
    String fileAndLineNumber =
      String.format(
        Locale.getDefault(), "%s:%d:%d\n", filePath, lineNumber, columnNumber);
    String err = prefix + fileAndLineNumber;
    BuildOutputInstantReader reader = new TestBuildOutputInstantReader(err);

    ArgumentCaptor<BuildEvent> captor = ArgumentCaptor.forClass(BuildEvent.class);

    assertThat(parser.parse(reader.readLine(), reader, messageConsumer)).isTrue();

    verify(messageConsumer).accept(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);

    verifyFileMessageEvent(captor.getAllValues().get(0), filePath, "CMake errors", MessageEvent.Kind.ERROR, lineNumber, columnNumber);
  }

  @Test
  public void testSingleLineCmakeErrorInFileWithoutLineNumberOrColumn() {
    String prefix = "CMake Error: Error in cmake code at ";
    String filePath = sourceFile.getAbsolutePath();
    String fileAndLineNumber = String.format(Locale.getDefault(), "%s::\n", filePath);
    String err = prefix + fileAndLineNumber;
    BuildOutputInstantReader reader = new TestBuildOutputInstantReader(err);

    ArgumentCaptor<BuildEvent> captor = ArgumentCaptor.forClass(BuildEvent.class);

    assertThat(parser.parse(reader.readLine(), reader, messageConsumer)).isTrue();

    verify(messageConsumer).accept(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);

    verifyFileMessageEvent(captor.getAllValues().get(0), filePath, "CMake errors", MessageEvent.Kind.ERROR, 0, 0);
  }

  @Test
  public void testSingleLineCmakeErrorInFileWithLineNumber() {
    String prefix = "CMake Error: Error in cmake code at ";
    String filePath = sourceFile.getAbsolutePath();
    int lineNumber = 13;
    String fileAndLineNumber =
      String.format(Locale.getDefault(), "%s:%d:\n", filePath, lineNumber);
    String err = prefix + fileAndLineNumber;
    BuildOutputInstantReader reader = new TestBuildOutputInstantReader(err);

    ArgumentCaptor<BuildEvent> captor = ArgumentCaptor.forClass(BuildEvent.class);

    assertThat(parser.parse(reader.readLine(), reader, messageConsumer)).isTrue();

    verify(messageConsumer).accept(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);

    verifyFileMessageEvent(captor.getAllValues().get(0), filePath, "CMake errors", MessageEvent.Kind.ERROR, lineNumber, 0);
  }

  @Test
  public void testSingleLineCmakeErrorInFileWithLineNumberAndColumn() {
    String prefix = "CMake Error: Error in cmake code at ";
    String filePath = sourceFile.getAbsolutePath();
    int lineNumber = 13;
    int columnNumber = 42;
    String fileAndLineNumber =
      String.format(
        Locale.getDefault(), "%s:%d:%d\n", filePath, lineNumber, columnNumber);
    String err = prefix + fileAndLineNumber;
    BuildOutputInstantReader reader = new TestBuildOutputInstantReader(err);

    ArgumentCaptor<BuildEvent> captor = ArgumentCaptor.forClass(BuildEvent.class);

    assertThat(parser.parse(reader.readLine(), reader, messageConsumer)).isTrue();

    verify(messageConsumer).accept(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);

    verifyFileMessageEvent(captor.getAllValues().get(0), filePath, "CMake errors", MessageEvent.Kind.ERROR, lineNumber, columnNumber);
  }

  @Test
  public void testSingleLineCmakeWarningInFileWithoutLineNumberOrColumn() {
    String prefix = "CMake Warning: Warning in cmake code at ";
    String filePath = sourceFile.getAbsolutePath();
    String fileAndLineNumber = String.format(Locale.getDefault(), "%s::\n", filePath);
    String err = prefix + fileAndLineNumber;
    BuildOutputInstantReader reader = new TestBuildOutputInstantReader(err);

    ArgumentCaptor<BuildEvent> captor = ArgumentCaptor.forClass(BuildEvent.class);

    assertThat(parser.parse(reader.readLine(), reader, messageConsumer)).isTrue();

    verify(messageConsumer).accept(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);

    verifyFileMessageEvent(captor.getAllValues().get(0), filePath, "CMake warnings", MessageEvent.Kind.WARNING, 0, 0);
  }

  @Test
  public void testSingleLineCmakeWarningInFileWithLineNumber() {
    String prefix = "CMake Warning: Warning in cmake code at ";
    String filePath = sourceFile.getAbsolutePath();
    int lineNumber = 13;
    String fileAndLineNumber =
      String.format(Locale.getDefault(), "%s:%d:\n", filePath, lineNumber);
    String err = prefix + fileAndLineNumber;
    BuildOutputInstantReader reader = new TestBuildOutputInstantReader(err);

    ArgumentCaptor<BuildEvent> captor = ArgumentCaptor.forClass(BuildEvent.class);

    assertThat(parser.parse(reader.readLine(), reader, messageConsumer)).isTrue();

    verify(messageConsumer).accept(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);

    verifyFileMessageEvent(captor.getAllValues().get(0), filePath, "CMake warnings", MessageEvent.Kind.WARNING, lineNumber, 0);
  }

  @Test
  public void testSingleLineCmakeWarningInFileWithLineNumberAndColumn() {
    String prefix = "CMake Warning: Warning in cmake code at ";
    String filePath = sourceFile.getAbsolutePath();
    int lineNumber = 13;
    int columnNumber = 42;
    String fileAndLineNumber =
      String.format(
        Locale.getDefault(), "%s:%d:%d\n", filePath, lineNumber, columnNumber);
    String err = prefix + fileAndLineNumber;
    BuildOutputInstantReader reader = new TestBuildOutputInstantReader(err);

    ArgumentCaptor<BuildEvent> captor = ArgumentCaptor.forClass(BuildEvent.class);

    assertThat(parser.parse(reader.readLine(), reader, messageConsumer)).isTrue();

    verify(messageConsumer).accept(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);

    verifyFileMessageEvent(captor.getAllValues().get(0), filePath, "CMake warnings", MessageEvent.Kind.WARNING, lineNumber, columnNumber);
  }

  @Test
  public void testLongErrorMessage() throws IOException {
    File makefile = temporaryFolder.newFile("CMakeLists.txt");
    String errorMessage = "CMake Error at %s:49 (message): %s";
    String loremIpsum =
      "Lorem ipsum dolor\n"
      + "amet, consectetur adipiscing elit.  Etiam ac aliquam lacus.  Nullam suscipit nisl\n"
      + "vitae sodales varius.  Donec eu enim ante.  Maecenas congue ante a nibh tristique,\n"
      + "in sagittis velit suscipit.  Ut hendrerit molestie augue quis sodales.  Praesent ac\n"
      + "consectetur est.  Duis at auctor neque.";

    errorMessage =
      String.format(
        Locale.getDefault(), errorMessage, makefile.getAbsolutePath(), loremIpsum);

    BuildOutputInstantReader reader = new TestBuildOutputInstantReader(errorMessage);

    ArgumentCaptor<BuildEvent> captor = ArgumentCaptor.forClass(BuildEvent.class);

    assertThat(parser.parse(reader.readLine(), reader, messageConsumer)).isTrue();

    verify(messageConsumer).accept(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);

    verifyFileMessageEvent(captor.getAllValues().get(0), makefile.getAbsolutePath(), "CMake errors", MessageEvent.Kind.ERROR, 49, 0);
  }
}
