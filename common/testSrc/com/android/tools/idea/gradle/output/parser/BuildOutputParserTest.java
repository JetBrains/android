/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.output.parser;

import com.android.annotations.Nullable;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.blame.parser.PatternAwareOutputParser;
import com.android.testutils.TestResources;
import com.google.common.base.Charsets;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;

import static com.android.SdkConstants.*;
import static com.android.utils.SdkUtils.createPathComment;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeFalse;

/**
 * Tests for {@link BuildOutputParser}.
 *
 * These tests MUST be executed on Windows too.
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "StringBufferReplaceableByString"})
public class BuildOutputParserTest {
  private static final String NEWLINE = SystemProperties.getLineSeparator();
  private static final String CWD_PATH = new File("").getAbsolutePath();

  private File sourceFile;
  private String sourceFilePath;
  private BuildOutputParser parser;

  @Before
  public void setUp() throws Exception {
    parser = new BuildOutputParser(ServiceLoader.load(PatternAwareOutputParser.class));
  }

  @After
  public void tearDown() throws Exception {
    if (sourceFile != null) {
      sourceFile.delete();
    }
  }

  @Test
  public void parseDisplayingUnhandledMessages() {
    String output = " **--- HELLO WORLD ---**";
    List<Message> Messages = parser.parseGradleOutput(output);
    assertEquals(1, Messages.size());
    Message message = Messages.get(0);
    assertEquals(output, message.getText());
    assertEquals(Message.Kind.SIMPLE, message.getKind());
  }

  @Test
  public void parseParsedBuildIssue() throws IOException {
    // Do not run tests on Windows (see http://b.android.com/222904)
    assumeFalse(SystemInfo.isWindows);

    String output = "AGPBI: {\"kind\":\"ERROR\",\"text\":\"" +
                    "No resource identifier found for attribute \\u0027a\\u0027 in package" +
                    " \\u0027android\\u0027\",\"sourcePath\":\"/usr/local/google/home/cmw/" +
                    "udacity/Sunshine/app/src/main/res/menu/detail.xml\",\"position\":" +
                    "{\"startLine\":5},\"original\":\"\"}";
    List<Message> Messages = parser.parseGradleOutput(output);
    assertEquals("Expect one message.", 1, Messages.size());
    Message message = Messages.iterator().next();
    assertEquals("No resource identifier found for attribute 'a' in package 'android'", message.getText());
    assertEquals(Message.Kind.ERROR, message.getKind());
    assertEquals("/usr/local/google/home/cmw/udacity/Sunshine/app/src/main/res/menu/detail.xml", message.getSourcePath());
    assertEquals(new SourcePosition(5, -1, -1), message.getSourceFilePositions().get(0).getPosition());
  }

  @Test
  public void parseAaptOutputWithRange1() throws IOException {
    createTempXmlFile();
    writeToFile("<manifest xmlns:android='http://schemas.android.com/apk/res/android'",
                "    android:versionCode='12' android:versionName='2.0' package='com.android.tests.basic'>",
                "  <uses-sdk android:minSdkVersion='16' android:targetSdkVersion='16'/>",
                "  <application android:icon='@drawable/icon' android:label='@string/app_name2'>");
    String messageText = "No resource found that matches the given name (at 'label' with value " + "'@string/app_name2').";
    String err = sourceFilePath + ":4: error: Error: " + messageText;
    Collection<Message> messages = parser.parseGradleOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 4, 61);
  }

  @Test
  public void parseAaptOutputWithRange2() throws IOException {
    // Check that when the actual aapt error occurs on a line later than the original error line,
    // the forward search which looks for a value match does not stop on an earlier line that
    // happens to have the same value prefix
    createTempXmlFile();
    writeToFile("<manifest xmlns:android='http://schemas.android.com/apk/res/android'",
                "    android:versionCode='12' android:versionName='2.0' package='com.android.tests.basic'>",
                "  <uses-sdk android:minSdkVersion='16' android:targetSdkVersion='16'/>",
                "  <application android:icon='@drawable/icon' android:label=", "      '@string/app_name2'>");
    String messageText = "No resource found that matches the given name (at 'label' with value " + "'@string/app_name2').";
    String err = sourceFilePath + ":4: error: Error: " + messageText;
    Collection<Message> messages = parser.parseGradleOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 5, 8);
  }

  @Test
  public void parseAaptOutputWithRange3() throws IOException {
    // Check that when we have a duplicate resource error, we highlight both the original property
    // and the original definition.
    // This tests the second, duplicate declaration ration.
    createTempXmlFile();
    writeToFile("<resources xmlns:android='http://schemas.android.com/apk/res/android'>",
                "  <style name='repeatedStyle1'>",
                "    <item name='android:gravity'>left</item>",
                "  </style>",
                "  <style name='repeatedStyle1'>",
                "    <item name='android:gravity'>left</item>");
    String messageText = "Resource entry repeatedStyle1 already has bag item android:gravity.";
    String err = sourceFilePath + ":6: error: " + messageText;
    Collection<Message> messages = parser.parseGradleOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 6, 17);
  }

  @Test
  public void parseAaptOutputWithRange4() throws IOException {
    // Check that when we have a duplicate resource error, we highlight both the original property
    // and the original definition.
    // This tests the original definition. Note that we don't have enough position info so we simply
    // highlight the whitespace portion of the line.
    createTempXmlFile();
    writeToFile("<resources xmlns:android='http://schemas.android.com/apk/res/android'>",
                "  <style name='repeatedStyle1'>",
                "    <item name='android:gravity'>left</item>");
    String messageText = "Originally defined here.";
    String err = sourceFilePath + ":3: " + messageText;
    Collection<Message> messages = parser.parseGradleOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 3, 5);
  }

  @Test
  public void parseAaptOutputWithRange5() throws IOException {
    // Check for aapt error which occurs when the attribute name in an item style declaration is
    // non-existent.
    createTempXmlFile();
    writeToFile("<resources xmlns:android='http://schemas.android.com/apk/res/android'>",
                "  <style name='wrongAttribute'>",
                "    <item name='nonexistent'>left</item>");
    String messageText = "No resource found that matches the given name: attr 'nonexistent'.";
    String err = sourceFilePath + ":3: error: Error: " + messageText;
    Collection<Message> messages = parser.parseGradleOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 3, 17);
  }

  @Test
  public void parseAaptOutputWithRange6() throws IOException {
    // Test missing resource name.
    createTempXmlFile();
    writeToFile("<resources xmlns:android='http://schemas.android.com/apk/res/android'>",
                "  <style>");
    String messageText = "A 'name' attribute is required for <style>";
    String err = sourceFilePath + ":2: error: " + messageText;
    Collection<Message> messages = parser.parseGradleOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 2, 3);
  }

  @Test
  public void parseAaptOutputWithRange7() throws IOException {
    createTempXmlFile();
    writeToFile("<resources xmlns:android='http://schemas.android.com/apk/res/android'>",
                "  <item>");
    String messageText = "A 'type' attribute is required for <item>";
    String err = sourceFilePath + ":2: error: " + messageText;
    Collection<Message> messages = parser.parseGradleOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 2, 3);
  }

  @Test
  public void parseAaptOutputWithRange8() throws IOException {
    createTempXmlFile();
    writeToFile("<resources xmlns:android='http://schemas.android.com/apk/res/android'>",
                "  <item>");
    String messageText = "A 'name' attribute is required for <item>";
    String err = sourceFilePath + ":2: error: " + messageText;
    Collection<Message> messages = parser.parseGradleOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 2, 3);
  }

  @Test
  public void parseAaptOutputWithRange9() throws IOException {
    createTempXmlFile();
    writeToFile("<resources xmlns:android='http://schemas.android.com/apk/res/android'>",
                "  <style name='test'>",
                "        <item name='android:layout_width'></item>");
    String messageText = "String types not allowed (at 'android:layout_width' with value '').";
    String err = sourceFilePath + ":3: error: " + messageText;
    Collection<Message> messages = parser.parseGradleOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 3, 21);
  }

  @Test
  public void parseAaptOutputWithRange10() throws IOException {
    createTempXmlFile();
    writeToFile("<FrameLayout", "    xmlns:android='http://schemas.android.com/apk/res/android'",
                "    android:layout_width='wrap_content'",
                "    android:layout_height='match_parent'>",
                "    <TextView",
                "        android:layout_width='fill_parent'",
                "        android:layout_height='wrap_content'",
                "        android:layout_marginTop=''",
                "        android:layout_marginLeft=''");
    String messageText = "String types not allowed (at 'layout_marginTop' with value '').";
    String err = sourceFilePath + ":5: error: Error: " + messageText;
    Collection<Message> messages = parser.parseGradleOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 8, 34);
  }

  @Test
  public void parseAaptOutputWithRange11() throws IOException {
    createTempXmlFile();
    writeToFile("<FrameLayout",
                "    xmlns:android='http://schemas.android.com/apk/res/android'",
                "    android:layout_width='wrap_content'",
                "    android:layout_height='match_parent'>",
                "    <TextView",
                "        android:layout_width='fill_parent'",
                "        android:layout_height='wrap_content'",
                "        android:layout_marginTop=''",
                "        android:layout_marginLeft=''");
    String messageText = "String types not allowed (at 'layout_marginLeft' with value '').";
    String err = sourceFilePath + ":5: error: Error: " + messageText;
    Collection<Message> messages = parser.parseGradleOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 9, 35);
  }

  @Test
  public void parseAaptOutputWithRange12() throws IOException {
    createTempXmlFile();
    writeToFile("<FrameLayout",
                "    xmlns:android='http://schemas.android.com/apk/res/android'",
                "    android:layout_width='wrap_content'",
                "    android:layout_height='match_parent'>",
                "    <TextView",
                "        android:id=''");
    String messageText = "String types not allowed (at 'id' with value '').";
    String err = sourceFilePath + ":5: error: Error: " + messageText;
    Collection<Message> messages = parser.parseGradleOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 6, 20);
  }

  private void createTempXmlFile() throws IOException {
    createTempFile(DOT_XML);
  }

  @Test
  public void parseJavaOutput1() throws IOException {
    createTempFile(".java");
    writeToFile("public class Test {",
                "  public static void main(String[] args) {",
                "    int v2 = v4");
    StringBuilder err = new StringBuilder();
    err.append(sourceFilePath).append(":3: error: ").append("cannot find symbol").append(NEWLINE)
       .append("symbol  : variable v4").append(NEWLINE)
       .append("location: Test").append(NEWLINE)
       .append("    int v2 = v4").append(NEWLINE)
       .append("             ^");
    Collection<Message> messages = parser.parseGradleOutput(err.toString());
    assertHasCorrectErrorMessage(messages, "error: cannot find symbol variable v4", 3, 14);
  }

  @Test
  public void parseJavaOutput2() throws IOException {
    createTempFile(".java");
    writeToFile("public class Test {",
                "  public static void main(String[] args) {",
                "    System.out.println();asd");
    StringBuilder err = new StringBuilder();
    err.append(sourceFilePath).append(":3: error: ").append("not a statement").append(NEWLINE)
       .append("    System.out.println();asd").append(NEWLINE)
       .append("                         ^").append(NEWLINE);
    Collection<Message> messages = parser.parseGradleOutput(err.toString());
    assertHasCorrectErrorMessage(messages, "error: not a statement", 3, 26);
  }

  @Test
  public void parseGradleBuildFileOutput() throws IOException {
    createTempFile(".gradle");
    writeToFile("buildscript {",
                "    repositories {",
                "        mavenCentral()",
                "    }",
                "    dependencies {",
                "        classpath 'com.android.tools.build:gradle:0.4'",
                "    }",
                "}",
                "ERROR plugin: 'android'");
    StringBuilder err = new StringBuilder();
    err.append("FAILURE: Build failed with an exception.").append(NEWLINE).append(NEWLINE);
    err.append("* Where:").append(NEWLINE);
    err.append("Build file '").append(sourceFilePath).append("' line: 9").append(NEWLINE).append(NEWLINE);
    err.append("* What went wrong:").append(NEWLINE);
    err.append("A problem occurred evaluating project ':project'.").append(NEWLINE);
    err.append("> Could not find method ERROR() for arguments [{plugin=android}] on project ':project'.").append(NEWLINE).append(NEWLINE);
    err.append("* Try:").append(NEWLINE);
    err.append("Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.").append(
      NEWLINE).append(NEWLINE);
    err.append("BUILD FAILED").append(NEWLINE).append(NEWLINE);
    err.append("Total time: 18.303 secs\n");
    List<Message> messages = parser.parseGradleOutput(err.toString());

    assertEquals("0: Error:A problem occurred evaluating project ':project'.\n" +
                 "> Could not find method ERROR() for arguments [{plugin=android}] on project ':project'.\n" +
                 "\t" + sourceFilePath + ":9:1\n" +
                 "1: Info:BUILD FAILED\n" +
                 "2: Info:Total time: 18.303 secs\n",
                 toString(messages));
  }

  @Test
  public void parseXmlValidationErrorOutput() {
    StringBuilder err = new StringBuilder();
    err.append("[Fatal Error] :5:7: The element type \"error\" must be terminated by the matching end-tag \"</error>\".").append(NEWLINE);
    err.append("FAILURE: Build failed with an exception.").append(NEWLINE);
    List<Message> messages = parser.parseGradleOutput(err.toString());

    assertEquals("0: Error:The element type \"error\" must be terminated by the matching end-tag \"</error>\".\n" +
                 "1: Simple:FAILURE: Build failed with an exception.\n",
                 toString(messages));
  }

  @Test
  public void parseIncubatingFeatureMessage() {
    String out = "Parallel execution with configuration on demand is an incubating feature.";
    List<Message> messages = parser.parseGradleOutput(out);
    assertEquals("", toString(messages));
  }

  private void createTempFile(@NotNull String fileExtension) throws IOException {
    sourceFile = File.createTempFile(BuildOutputParserTest.class.getName(), fileExtension);
    sourceFilePath = sourceFile.getAbsolutePath();
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private void writeToFile(@NotNull String... lines) throws IOException {
    BufferedWriter out = null;
    try {
      out = new BufferedWriter(new FileWriter(sourceFile));
      for (String line : lines) {
        out.write(line);
        out.newLine();
      }
    }
    finally {
      //noinspection deprecation
      Closeables.close(out, true /* swallowIOException */);
    }
  }

  private void assertHasCorrectErrorMessage(@NotNull Collection<Message> messages,
                                            @NotNull String expectedText,
                                            long expectedLine,
                                            long expectedColumn) {
    assertEquals("[message count]", 1, messages.size());
    Message message = ContainerUtil.getFirstItem(messages);
    assertNotNull(message);
    assertEquals("[file path]", sourceFilePath, message.getSourcePath());
    assertEquals("[message severity]", Message.Kind.ERROR, message.getKind());
    assertEquals("[message text]", expectedText, message.getText());
    assertEquals("[position line]", expectedLine, message.getLineNumber());
    assertEquals("[position column]", expectedColumn, message.getColumn());
  }

  @Test
  public void redirectValueLinksOutput() throws Exception {
    // Do not run tests on Windows (see http://b.android.com/222904)
    assumeFalse(SystemInfo.isWindows);

    TestResources.getFileInDirectory(
      getClass(), "/values.xml", "src/test/resources/testData/resources/baseSet/values/values.xml");

    // Need file to be named (exactly) values.xml
    File tempDir = Files.createTempDir();
    File valueDir = new File(tempDir, "values-en");
    valueDir.mkdirs();
    sourceFile = new File(valueDir, "values.xml"); // Keep in sync with MergedResourceWriter.FN_VALUES_XML
    sourceFilePath = sourceFile.getAbsolutePath();

    writeToFile(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<resources xmlns:ns1=\"urn:oasis:names:tc:xliff:document:1.2\"\n" +
      "           xmlns:tools=\"http://schemas.android.com/tools\"\n" +
      "           xmlns:alias_for_tools=\"http://schemas.android.com/tools\">\n" +
      "\n" +
      "    <!-- From: src/test/resources/testData/resources/baseSet/values/values.xml -->\n" +
      "    <string-array name=\"string_array\" translatable=\"false\">\n" +
      "        <item/> <!-- 0 -->\n" +
      "        <item/> <!-- 1 -->\n" +
      "        <item>ABC</item> <!-- 2 -->\n" +
      "        <item>DEF</item> <!-- 3 -->\n" +
      "        <item>GHI</item> <!-- 4 -->\n" +
      "        <item>JKL</item> <!-- 5 -->\n" +
      "        <item>MNO</item> <!-- 6 -->\n" +
      "        <item>PQRS</item> <!-- 7 -->\n" +
      "        <item>TUV</item> <!-- 8 -->\n" +
      "        <item>WXYZ</item> <!-- 9 -->\n" +
      "    </string-array>\n" +
      "\n" +
      "    <attr name=\"dimen_attr\" format=\"dimension\" />\n" +
      "    <attr name=\"enum_attr\">\n" +
      "        <enum name=\"normal\" value=\"0\" />\n" +
      "        <enum name=\"sans\" value=\"1\" />\n" +
      "        <enum name=\"serif\" value=\"2\" />\n" +
      "        <enum name=\"monospace\" value=\"3\" />\n" +
      "    </attr>\n" +
      "    <attr name=\"flag_attr\">\n" +
      "        <flag name=\"normal\" value=\"0\" />\n" +
      "        <flag name=\"bold\" value=\"1\" />\n" +
      "        <flag name=\"italic\" value=\"2\" />\n" +
      "    </attr>\n" +
      "    <attr name=\"string_attr\" format=\"string\" />\n" +
      "    <!-- From: src/test/resources/testData/resources/baseMerge/overlay/values/values.xml -->\n" +
      "    <color name=\"color\">#FFFFFFFF</color>\n" +
      "    <!-- From: src/test/resources/testData/resources/baseSet/values/values.xml -->\n" +
      "    <declare-styleable name=\"declare_styleable\">\n" +
      "\n" +
      "        <!-- ============== -->\n" +
      "        <!-- Generic styles -->\n" +
      "        <!-- ============== -->\n" +
      "        <eat-comment />\n" +
      "\n" +
      "        <!-- Default color of foreground imagery. -->\n" +
      "        <attr name=\"blah\" format=\"color\" />\n" +
      "        <!-- Default color of foreground imagery on an inverted background. -->\n" +
      "        <attr name=\"android:colorForegroundInverse\" />\n" +
      "    </declare-styleable>\n" +
      "\n" +
      "    <dimen name=\"dimen\">164dp</dimen>\n" +
      "\n" +
      "    <drawable name=\"color_drawable\">#ffffffff</drawable>\n" +
      "    <drawable name=\"drawable_ref\">@drawable/stat_notify_sync_anim0</drawable>\n" +
      "\n" +
      "    <item name=\"item_id\" type=\"id\"/>\n" +
      "\n" +
      "    <integer name=\"integer\">75</integer>\n" +
      "    <!-- From: src/test/resources/testData/resources/baseMerge/overlay/values/values.xml -->\n" +
      "    <item name=\"file_replaced_by_alias\" type=\"layout\">@layout/ref</item>\n" +
      "    <!-- From: src/test/resources/testData/resources/baseSet/values/values.xml -->\n" +
      "    <item name=\"layout_ref\" type=\"layout\">@layout/ref</item>\n" +
      "    <!-- From: src/test/resources/testData/resources/baseMerge/overlay/values/values.xml -->\n" +
      "    <string name=\"basic_string\">overlay_string</string>\n" +
      "    <!-- From: src/test/resources/testData/resources/baseSet/values/values.xml -->\n" +
      "    <string name=\"styled_string\">Forgot your username or password\\?\\nVisit <b>google.com/accounts/recovery</b>.</string>\n" +
      "    <string name=\"xliff_string\"><ns1:g id=\"number\" example=\"123\">%1$s</ns1:g><ns1:g id=\"unit\" example=\"KB\">%2$s</ns1:g></string>\n" +
      "\n" +
      "    <style name=\"style\" parent=\"@android:style/Holo.Light\">\n" +
      "        <item name=\"android:singleLine\">true</item>\n" +
      "        <item name=\"android:textAppearance\">@style/TextAppearance.WindowTitle</item>\n" +
      "        <item name=\"android:shadowColor\">#BB000000</item>\n" +
      "        <item name=\"android:shadowRadius\">2.75</item>\n" +
      "        <item name=\"foo\">foo</item>\n" +
      "    </style>\n" +
      "\n" +
      "</resources>\n");

    String messageText = "String types not allowed (at 'drawable_ref' with value '@drawable/stat_notify_sync_anim0').";
    String err = sourceFilePath + ":46: error: Error: " + messageText;
    Collection<Message> messages = parser.parseGradleOutput(err);
    assertEquals(1, messages.size());

    assertEquals("[message count]", 1, messages.size());
    Message message = ContainerUtil.getFirstItem(messages);
    assertNotNull(message);

    // NOT sourceFilePath; should be translated back from source comment
    assertEquals("[file path]", "src/test/resources/testData/resources/baseSet/values/values.xml", getSystemIndependentSourcePath(message));

    assertEquals("[message severity]", Message.Kind.ERROR, message.getKind());
    assertEquals("[message text]", messageText, message.getText());
    assertEquals("[position line]", 11, message.getLineNumber());
    assertEquals("[position column]", 35, message.getColumn());
  }

  @Nullable
  private static String getSystemIndependentSourcePath(@NotNull Message message) {
    String sourcePath = message.getSourcePath();
    if (sourcePath == null) {
      return null;
    }
    String name = FileUtil.toSystemIndependentName(sourcePath);
    if (name.startsWith(CWD_PATH)) {
      name = name.substring(CWD_PATH.length() + 1);
    };
    return name;
  }

  @Test
  public void redirectFileLinksOutput() throws Exception {
    // Do not run tests on Windows (see http://b.android.com/222904)
    assumeFalse(SystemInfo.isWindows);

    // Need file to be named (exactly) values.xml
    File tempDir = Files.createTempDir();
    File layoutDir = new File(tempDir, "layout-land");
    layoutDir.mkdirs();
    sourceFile = new File(layoutDir, "main.xml");
    sourceFilePath = sourceFile.getAbsolutePath();

    writeToFile(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    android:orientation=\"vertical\"\n" +
      "    android:layout_width=\"fill_parent\"\n" +
      "    android:layout_height=\"fill_parent\"\n" +
      "    >\n" +
      "<TextView\n" +
      "    android:layout_width=\"fill_parent\"\n" +
      "    android:layout_height=\"wrap_content\"\n" +
      "    android:text=\"Test App - Basic\"\n" +
      "    android:id=\"@+id/text\"\n" +
      "    />\n" +
      "</LinearLayout>\n" +
      "\n" +
      "<!-- From: file:src/test/resources/testData/resources/incMergeData/filesVsValues/main/layout/main.xml -->");

    String messageText = "Random error message here";
    String err = sourceFilePath + ":4: error: Error: " + messageText;
    Collection<Message> messages = parser.parseGradleOutput(err);
    assertEquals(1, messages.size());

    assertEquals("[message count]", 1, messages.size());
    Message message = ContainerUtil.getFirstItem(messages);
    assertNotNull(message);

    // NOT sourceFilePath; should be translated back from source comment
    String expected = "src/test/resources/testData/resources/incMergeData/filesVsValues/main/layout/main.xml";
    assertEquals("[file path]", expected, getSystemIndependentSourcePath(message));

    assertEquals("[message severity]", Message.Kind.ERROR, message.getKind());
    assertEquals("[message text]", messageText, message.getText());
    assertEquals("[position line]", 4, message.getLineNumber());
    //assertEquals("[position column]", 35, message.getColumn());

    // TODO: Test encoding issues (e.g. & in path where the XML source comment would be &amp; instead)
  }

  @Test
  public void gradleAaptErrorParser() throws IOException {
    createTempXmlFile();
    writeToFile("<resources xmlns:android='http://schemas.android.com/apk/res/android'>",
                "    <TextView\n" +
                "            android:layout_width=\"fill_parent\"\n" +
                "            android:layout_height=\"wrap_content\"\n" +
                "            android:text=\"@string/does_not_exist\"\n" +
                "            android:id=\"@+id/text\"\n" +
                "            />\n");
    final String messageText = "No resource found that matches the given name (at 'text' with value '@string/does_not_exist').";
    String err = "FAILURE: Build failed with an exception.\n" +
                 "\n" +
                 "* What went wrong:\n" +
                 "Execution failed for task ':five:processDebugResources'.\n" +
                 "> Failed to run command:\n" +
                 "  \t/Applications/Android Studio.app/sdk/build-tools/android-4.2.2/aapt package -f --no-crunch -I ...\n" +
                 "  Error Code:\n" +
                 "  \t1\n" +
                 "  Output:\n" +
                 "  \t" + sourceFilePath + ":5: error: Error: " + messageText + "\n" +
                 "\n" +
                 "\n" +
                 "* Try:\n" +
                 "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
                 "\n" +
                 "BUILD FAILED\n";

    assertEquals("0: Error:Error while executing aapt command\n" +
                 "1: Error:No resource found that matches the given name (at 'text' with value '@string/does_not_exist').\n" +
                 "\t" + sourceFilePath + ":5:27\n" +
                 "2: Error:Execution failed for task ':five:processDebugResources'.\n" +
                 "3: Info:BUILD FAILED\n",
                 toString(parser.parseGradleOutput(err)));
  }

  @Test
  public void lockOwner() throws Exception {
    // https://code.google.com/p/android/issues/detail?id=59444
    String output =
      "FAILURE: Build failed with an exception.\n" +
      "* What went wrong:\n" +
      " A problem occurred configuring project ':MyApplication1'.\n" +
      "> Failed to notify project evaluation listener.\n" +
      "   > Could not resolve all dependencies for configuration ':MyApplication1:_DebugCompile'.\n" +
      "      > Problems pinging owner of lock '-7513739537696464924' at port: 55416\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 24.154 secs";

    assertEquals("0: Error:Possibly unstable network connection: Failed to connect to lock owner. Try to rebuild.\n" +
                 "1: Error: A problem occurred configuring project ':MyApplication1'.\n" +
                 "> Failed to notify project evaluation listener.\n" +
                 "   > Could not resolve all dependencies for configuration ':MyApplication1:_DebugCompile'.\n" +
                 "      > Problems pinging owner of lock '-7513739537696464924' at port: 55416\n" +
                 "2: Info:BUILD FAILED\n" +
                 "3: Info:Total time: 24.154 secs\n",
                 toString(parser.parseGradleOutput(output)));
  }

  @Test
  public void duplicateResources() throws Exception {
    // To reproduce, create a source file with two duplicate string item definitions
    createTempXmlFile();
    String output =
      "Failed to parse " + sourceFilePath + "\n" +
      "java.io.IOException: Found item String/drawer_open more than one time\n" +
      "\tat com.android.ide.common.res2.ValueResourceParser2.checkDuplicate(ValueResourceParser2.java:249)\n" +
      "\tat com.android.ide.common.res2.ValueResourceParser2.parseFile(ValueResourceParser2.java:103)\n" +
      "\tat com.android.ide.common.res2.ResourceSet.createResourceFile(ResourceSet.java:273)\n" +
      "\tat com.android.ide.common.res2.ResourceSet.parseFolder(ResourceSet.java:248)\n" +
      // ...
      "\tat org.gradle.launcher.daemon.server.DefaultIncomingConnectionHandler$ConnectionWorker.run(DefaultIncomingConnectionHandler.java:116)\n" +
      "\tat org.gradle.internal.concurrent.DefaultExecutorFactory$StoppableExecutorImpl$1.run(DefaultExecutorFactory.java:66)\n" +
      "\tat java.util.concurrent.ThreadPoolExecutor$Worker.runTask(ThreadPoolExecutor.java:895)\n" +
      "\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:918)\n" +
      "\tat java.lang.Thread.run(Thread.java:680)\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':MyApp:mergeDebugResources'.\n" +
      "> Found item String/drawer_open more than one time\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n";

    assertEquals("0: Error:Found item String/drawer_open more than one time: Failed to parse " + sourceFilePath + "\n" +
                 "\t" + sourceFilePath + ":-1:-1\n" +
                 "1: Error:Execution failed for task ':MyApp:mergeDebugResources'.\n" +
                 "> Found item String/drawer_open more than one time\n",
                 toString(parser.parseGradleOutput(output)));

    // Also test CRLF handling:
    output = output.replace("\n", "\r\n");
    assertEquals("0: Error:Found item String/drawer_open more than one time: Failed to parse " + sourceFilePath + "\n" +
                 "\t" + sourceFilePath + ":-1:-1\n" +
                 "1: Error:Execution failed for task ':MyApp:mergeDebugResources'.\n" +
                 "> Found item String/drawer_open more than one time\n",
                 toString(parser.parseGradleOutput(output)));
  }

  @Test
  public void duplicateResources2() throws Exception {
    File file1 = File.createTempFile(BuildOutputParserTest.class.getName(), DOT_XML);
    File file2 = File.createTempFile(BuildOutputParserTest.class.getName(), DOT_XML);
    String path1 = file1.getPath();
    String path2 = file2.getPath();

    Files.write(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<resources xmlns:ns1=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
      "    <!-- This is just a comment: group2_string -->" +
      "    <item name=\"group1\" type=\"id\"/>\n" +
      "    <string name=\"group2_string\">Hello</string>\n" +
      "\n" +
      "</resources>\n", file1, Charsets.UTF_8);

    Files.write(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<resources xmlns:ns1=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
      "    <item type=\"string\" name=\"group2_string\">Hello</item>\n" +
      "</resources>\n", file2, Charsets.UTF_8);

    String output =
      ":preBuild UP-TO-DATE\n" +
      ":preF2FaDebugBuild UP-TO-DATE\n" +
      ":prepareF2FaDebugDependencies\n" +
      ":compileF2FaDebugRenderscript UP-TO-DATE\n" +
      ":mergeF2FaDebugResources FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':mergeF2FaDebugResources'.\n" +
      "> Duplicate resources: " + path1 + ":string/group2_string, " + path2 + ":string/group2_string\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 6.462 secs";

    assertEquals("0: Simple::preBuild UP-TO-DATE\n" +
                 "1: Simple::preF2FaDebugBuild UP-TO-DATE\n" +
                 "2: Simple::prepareF2FaDebugDependencies\n" +
                 "3: Simple::compileF2FaDebugRenderscript UP-TO-DATE\n" +
                 "4: Simple::mergeF2FaDebugResources FAILED\n" +
                 "5: Error:> Duplicate resources: " + path1 + ":string/group2_string, " + path2 + ":string/group2_string\n" +
                 "\t" + path1 + ":4:13\n" +
                 "\t" + path2 + ":3:31\n" +
                 "6: Error:Execution failed for task ':mergeF2FaDebugResources'.\n" +
                 "\t" + path1 + ":4:13\n" +
                 "7: Info:BUILD FAILED\n" +
                 "8: Info:Total time: 6.462 secs\n",
                 toString(parser.parseGradleOutput(output)));

    file1.delete();
    file2.delete();
  }

  @Test
  public void buildCommandFailed() {
    // Do not run tests on Windows (see http://b.android.com/222904)
    assumeFalse(SystemInfo.isWindows);

    String output =
      ":app:externalNativeBuildDebug\n" +
      "  building /foo/bar/libtest.so\n" +
      ":app:externalNativeBuildDebug FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':app:externalNativeBuildDebug'.\n" +
      "> Build command failed.\n" +
      "  Error while executing '/foo/bar/cmake' with arguments {--build /foo/bar --target test}\n" +
      "  [1/2] Building CXX object test.cpp.o\n" +
      "  FAILED: /foo/bar/clang++ /foo/bar/test.cpp\n" +
      "  /foo/bar/test.cpp:8:5: error: use of undeclared identifier 'foo'; did you mean 'for'?\n" +
      "      foo;\n" +
      "      ^~~\n" +
      "      for\n" +
      "  /foo/bar/test.cpp:8:8: error: expected '(' after 'for'\n" +
      "      foo;\n" +
      "         ^\n" +
      "  /foo/bar/test.cpp:9:15: error: expected expression\n" +
      "      int bar = ;\n" +
      "                ^\n" +
      "  /foo/bar/test.cpp:10:5: error: use of undeclared identifier 'baz'; did you mean 'bar'?\n" +
      "      baz\n" +
      "      ^~~\n" +
      "      bar\n" +
      "  /foo/bar/test.cpp:9:9: note: 'bar' declared here\n" +
      "      int bar = ;\n" +
      "          ^\n" +
      "  /foo/bar/test.cpp:10:8: error: expected ';' after expression\n" +
      "      baz\n" +
      "         ^\n" +
      "         ;\n" +
      "  /foo/bar/test.cpp:7:5: warning: expression result unused [-Wunused-value]\n" +
      "      0;\n" +
      "      ^\n" +
      "  /foo/bar/test.cpp:10:5: warning: expression result unused [-Wunused-value]\n" +
      "      baz\n" +
      "      ^~~\n" +
      "  2 warnings and 5 errors generated.\n" +
      "  ninja: build stopped: subcommand failed.\n" +
      "\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 0.504 secs\n";
    assertEquals("0: Simple::app:externalNativeBuildDebug\n" +
                 "1: Simple:  building /foo/bar/libtest.so\n" +
                 "2: Simple::app:externalNativeBuildDebug FAILED\n" +
                 "3: Error:error: use of undeclared identifier 'foo'; did you mean 'for'?\n" +
                 "\t/foo/bar/test.cpp:8:5\n" +
                 "4: Error:error: expected '(' after 'for'\n" +
                 "\t/foo/bar/test.cpp:8:8\n" +
                 "5: Error:error: expected expression\n" +
                 "\t/foo/bar/test.cpp:9:15\n" +
                 "6: Error:error: use of undeclared identifier 'baz'; did you mean 'bar'?\n" +
                 "\t/foo/bar/test.cpp:10:5\n" +
                 "7: Info:note: 'bar' declared here\n" +
                 "\t/foo/bar/test.cpp:9:9\n" +
                 "8: Error:error: expected ';' after expression\n" +
                 "\t/foo/bar/test.cpp:10:8\n" +
                 "9: Warning:warning: expression result unused [-Wunused-value]\n" +
                 "\t/foo/bar/test.cpp:7:5\n" +
                 "10: Warning:warning: expression result unused [-Wunused-value]\n" +
                 "\t/foo/bar/test.cpp:10:5\n" +
                 "11: Info:BUILD FAILED\n" +
                 "12: Info:Total time: 0.504 secs\n",
                 toString(parser.parseGradleOutput(output)));
    output =
      ":app:externalNativeBuildDebug\n" +
      "  building C:\\foo\\bar\\libtest.so\n" +
      ":app:externalNativeBuildDebug FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':app:externalNativeBuildDebug'.\n" +
      "> Build command failed.\n" +
      "  Error while executing 'C:\\foo\\bar\\cmake.exe' with arguments {--build C:\\foo\\bar --target test}\n" +
      "  [1/2] Building CXX object test.cpp.o\n" +
      "  FAILED: C:\\foo\\bar\\clang++.exe C:\\foo\\bar\\test.cpp\n" +
      "  C:\\foo\\bar\\test.cpp:2:10: fatal error: 'garbage' file not found\n" +
      "  #include \"garbage\"\n" +
      "           ^\n" +
      "  1 error generated.\n" +
      "  ninja: build stopped: subcommand failed.\n" +
      "\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 6.675 secs\n";
    assertEquals("0: Simple::app:externalNativeBuildDebug\n" +
                 "1: Simple:  building C:\\foo\\bar\\libtest.so\n" +
                 "2: Simple::app:externalNativeBuildDebug FAILED\n" +
                 "3: Error:fatal error: 'garbage' file not found\n" +
                 "\tC:\\foo\\bar\\test.cpp:2:10\n" +
                 "4: Info:BUILD FAILED\n" +
                 "5: Info:Total time: 6.675 secs\n",
                 toString(parser.parseGradleOutput(output)));
  }

  @Test
  public void unexpectedOutput() throws Exception {
    // To reproduce, create a source file with two duplicate string item definitions
    createTempXmlFile();
    String output =
      "This output is not expected.\n" +
      "Nor is this.\n" +
      "java.io.SurpriseSurpriseError: Bet you didn't expect to see this\n" +
      "\tat com.android.ide.common.res2.ValueResourceParser2.checkSurpriseSurprise(ValueResourceParser2.java:249)\n" +
      "\tat com.android.ide.common.res2.ValueResourceParser2.parseFile(ValueResourceParser2.java:103)\n" +
      "\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:918)\n" +
      "\tat java.lang.Thread.run(Thread.java:680)\n" +
      "More unexpected output.\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':MyApp:mergeDebugResources'.\n" +
      "> I was surprised\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n";

    assertEquals("0: Simple:This output is not expected.\n" +
                 "1: Simple:Nor is this.\n" +
                 "2: Simple:java.io.SurpriseSurpriseError: Bet you didn't expect to see this\n" +
                 "3: Simple:\tat com.android.ide.common.res2.ValueResourceParser2.checkSurpriseSurprise(ValueResourceParser2.java:249)\n" +
                 "4: Simple:\tat com.android.ide.common.res2.ValueResourceParser2.parseFile(ValueResourceParser2.java:103)\n" +
                 "5: Simple:\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:918)\n" +
                 "6: Simple:\tat java.lang.Thread.run(Thread.java:680)\n" +
                 "7: Simple:More unexpected output.\n" +
                 "8: Error:Execution failed for task ':MyApp:mergeDebugResources'.\n" +
                 "> I was surprised\n",
                 toString(parser.parseGradleOutput(output)));
  }

  @Test
  public void xmlError() throws Exception {
    createTempXmlFile();
    String output =
      "[Fatal Error] :7:18: Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
      "Failed to parse " + sourceFilePath + "\n" +
      "java.io.IOException: org.xml.sax.SAXParseException: Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
      "\tat com.android.ide.common.res2.ValueResourceParser2.parseDocument(ValueResourceParser2.java:193)\n" +
      "\tat com.android.ide.common.res2.ValueResourceParser2.parseFile(ValueResourceParser2.java:78)\n" +
      "\tat com.android.ide.common.res2.ResourceSet.createResourceFile(ResourceSet.java:273)\n" +
      "\tat com.android.ide.common.res2.ResourceSet.parseFolder(ResourceSet.java:248)\n" +
      // ...
      "\tat org.gradle.wrapper.WrapperExecutor.execute(WrapperExecutor.java:130)\n" +
      "\tat org.gradle.wrapper.GradleWrapperMain.main(GradleWrapperMain.java:61)\n" +
      "Caused by: org.xml.sax.SAXParseException: Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
      "\tat com.sun.org.apache.xerces.internal.parsers.DOMParser.parse(DOMParser.java:246)\n" +
      "\tat com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderImpl.parse(DocumentBuilderImpl.java:284)\n" +
      "\tat com.android.ide.common.res2.ValueResourceParser2.parseDocument(ValueResourceParser2.java:189)\n" +
      "\t... 109 more\n" +
      ":MyApp:mergeDebugResources FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':MyApp:mergeDebugResources'.\n" +
      "> org.xml.sax.SAXParseException: Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 7.245 secs\n";

    assertEquals("0: Error:Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
                 "\t" + sourceFilePath + ":7:18\n" +
                 "1: Simple:java.io.IOException: org.xml.sax.SAXParseException: Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
                 "2: Simple:\tat com.android.ide.common.res2.ValueResourceParser2.parseDocument(ValueResourceParser2.java:193)\n" +
                 "3: Simple:\tat com.android.ide.common.res2.ValueResourceParser2.parseFile(ValueResourceParser2.java:78)\n" +
                 "4: Simple:\tat com.android.ide.common.res2.ResourceSet.createResourceFile(ResourceSet.java:273)\n" +
                 "5: Simple:\tat com.android.ide.common.res2.ResourceSet.parseFolder(ResourceSet.java:248)\n" +
                 "6: Simple:\tat org.gradle.wrapper.WrapperExecutor.execute(WrapperExecutor.java:130)\n" +
                 "7: Simple:\tat org.gradle.wrapper.GradleWrapperMain.main(GradleWrapperMain.java:61)\n" +
                 "8: Error:org.xml.sax.SAXParseException: Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
                 "9: Simple:\tat com.sun.org.apache.xerces.internal.parsers.DOMParser.parse(DOMParser.java:246)\n" +
                 "10: Simple:\tat com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderImpl.parse(DocumentBuilderImpl.java:284)\n" +
                 "11: Simple:\tat com.android.ide.common.res2.ValueResourceParser2.parseDocument(ValueResourceParser2.java:189)\n" +
                 "12: Simple:\t... 109 more\n" +
                 "13: Simple::MyApp:mergeDebugResources FAILED\n" +
                 "14: Error:Execution failed for task ':MyApp:mergeDebugResources'.\n" +
                 "> org.xml.sax.SAXParseException: Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
                 "15: Info:BUILD FAILED\n" +
                 "16: Info:Total time: 7.245 secs\n",
                 toString(parser.parseGradleOutput(output)));
  }

  @Test
  public void javac() throws Exception {
    createTempFile(".java");
    String output =
      sourceFilePath + ":70: <identifier> expected\n" +
      "x\n" +
      " ^\n" +
      sourceFilePath + ":71: <identifier> expected\n" +
      "    @Override\n" +
      "             ^\n" +
      "2 errors\n" +
      ":MyApp:compileDebug FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':MyApp:compileDebug'.\n" +
      "> Compilation failed; see the compiler error output for details.\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 12.42 secs";

    assertEquals("0: Error:<identifier> expected\n" +
                 "\t" + sourceFilePath + ":70:2\n" +
                 "1: Error:<identifier> expected\n" +
                 "\t" + sourceFilePath + ":71:14\n" +
                 "2: Simple::MyApp:compileDebug FAILED\n" +
                 "3: Error:Execution failed for task ':MyApp:compileDebug'.\n" +
                 "> Compilation failed; see the compiler error output for details.\n" +
                 "4: Info:BUILD FAILED\n" +
                 "5: Info:Total time: 12.42 secs\n",
                 toString(parser.parseGradleOutput(output)));
  }

  @Test
  public void dom() throws Exception {
    createTempFile(".java");
    String output =
      // Came across this output on StackOverflow:
      "FAILURE: Build failed with an exception.\n" +
      "* What went wrong:\n" +
      "A problem occurred configuring project ':EpicMix'.\n" +
      "> Failed to notify project evaluation listener.\n" +
      "   > A problem occurred configuring project ':facebook'.\n" +
      "      > Failed to notify project evaluation listener.\n" +
      "         > java.lang.OutOfMemoryError: PermGen space" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 24.154 secs";

    assertEquals("0: Error:A problem occurred configuring project ':EpicMix'.\n" +
                 "> Failed to notify project evaluation listener.\n" +
                 "   > A problem occurred configuring project ':facebook'.\n" +
                 "      > Failed to notify project evaluation listener.\n" +
                 "         > java.lang.OutOfMemoryError: PermGen space\n" +
                 "1: Info:BUILD FAILED\n" +
                 "2: Info:Total time: 24.154 secs\n",
                 toString(parser.parseGradleOutput(output)));

    String output2 =
      "To honour the JVM settings for this build a new JVM will be forked. Please consider using the daemon: http://gradle.org/docs/1.7/userguide/gradle_daemon.html.\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "A problem occurred configuring project ':MyNewApp'.\n" +
      "> Failed to notify project evaluation listener.\n" +
      "   > java.lang.OutOfMemoryError: Java heap space\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 24.154 secs";

    assertEquals("0: Simple:To honour the JVM settings for this build a new JVM will be forked. Please consider using the daemon: http://gradle.org/docs/1.7/userguide/gradle_daemon.html.\n" +
                 "1: Error:A problem occurred configuring project ':MyNewApp'.\n" +
                 "> Failed to notify project evaluation listener.\n" +
                 "   > java.lang.OutOfMemoryError: Java heap space\n" +
                 "2: Info:BUILD FAILED\n" +
                 "3: Info:Total time: 24.154 secs\n",
                 toString(parser.parseGradleOutput(output2)));
  }

  @Test
  public void test() throws Exception {
    File tempDir = Files.createTempDir();
    sourceFile = new File(tempDir, "values.xml"); // Name matters for position search
    sourceFilePath = sourceFile.getAbsolutePath();
    File source = new File(tempDir, "dimens.xml");
    Files.write("<resources>\n" +
                "    <!-- Default screen margins, per the Android Design guidelines. -->\n" +
                "    <dimen name=\"activity_horizontal_margin\">16dp</dimen>\n" +
                "    <dimen name=\"activity_vertical_margin\">16dp</dimen>\n" +
                "    <dimen name=\"new_name\">50</dimen>\n" +
                "</resources>", source, Charsets.UTF_8);
    source.deleteOnExit();
    Files.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "    <!-- From: file:/Users/unittest/AndroidStudioProjects/BlankProject1Project/BlankProject1/build/exploded-bundles/ComAndroidSupportAppcompatV71800.aar/res/values/values.xml -->\n" +
                "    <dimen name=\"abc_action_bar_default_height\">48dip</dimen>\n" +
                "    <dimen name=\"abc_action_bar_icon_vertical_padding\">8dip</dimen>\n" +
                "    <!-- From: file:" + source.getPath() + " -->\n" +
                "    <dimen name=\"activity_horizontal_margin\">16dp</dimen>\n" +
                "    <dimen name=\"activity_vertical_margin\">16dp</dimen>\n" +
                "    <dimen name=\"ok\">50dp</dimen>\n" +
                "    <dimen name=\"new_name\">50</dimen>\n" +
                "    <!-- From: file:/Users/unittest/AndroidStudioProjects/BlankProject1Project/BlankProject1/build/exploded-bundles/ComAndroidSupportAppcompatV71800.aar/res/values/values.xml -->\n" +
                "    <item name=\"action_bar_activity_content\" type=\"id\"/>\n" +
                "    <item name=\"action_menu_divider\" type=\"id\"/>\n" +
                "    <item name=\"action_menu_presenter\" type=\"id\"/>\n" +
                "    <item name=\"home\" type=\"id\"/>\n" +
                "</resources>\n", sourceFile, Charsets.UTF_8);

    String output =
      "Relying on packaging to define the extension of the main artifact has been deprecated and is scheduled to be removed in Gradle 2.0\n" +
      ":BlankProject1:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
      ":BlankProject1:prepareDebugDependencies\n" +
      ":BlankProject1:mergeDebugAssets UP-TO-DATE\n" +
      ":BlankProject1:compileDebugRenderscript UP-TO-DATE\n" +
      ":BlankProject1:mergeDebugResources UP-TO-DATE\n" +
      ":BlankProject1:processDebugManifest UP-TO-DATE\n" +
      ":BlankProject1:processDebugResources\n" +
      sourceFilePath + ":10: error: Error: Integer types not allowed (at 'new_name' with value '50').\n" +
      ":BlankProject1:processDebugResources FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':BlankProject1:processDebugResources'.\n" +
      "> Failed to run command:\n" +
      "  \t/Users/tnorbye/dev/sdks/build-tools/18.0.1/aapt package -f --no-crunch -I ...\n" +
      "  Error Code:\n" +
      "  \t1\n" +
      "  Output:\n" +
      "  \t" + sourceFilePath + ":10: error: Error: Integer types not allowed (at 'new_name' with value '50').\n" +
      "\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 5.435 secs";

    assertEquals("0: Simple::BlankProject1:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
                 "1: Simple::BlankProject1:prepareDebugDependencies\n" +
                 "2: Simple::BlankProject1:mergeDebugAssets UP-TO-DATE\n" +
                 "3: Simple::BlankProject1:compileDebugRenderscript UP-TO-DATE\n" +
                 "4: Simple::BlankProject1:mergeDebugResources UP-TO-DATE\n" +
                 "5: Simple::BlankProject1:processDebugManifest UP-TO-DATE\n" +
                 "6: Simple::BlankProject1:processDebugResources\n" +
                 "7: Error:Integer types not allowed (at 'new_name' with value '50').\n" +
                 "\t" + source.getPath() + ":5:28\n" +
                 "8: Simple::BlankProject1:processDebugResources FAILED\n" +
                 "9: Error:Error while executing aapt command\n" +
                 "10: Error:Integer types not allowed (at 'new_name' with value '50').\n" +
                 "\t" + source.getPath() + ":5:28\n" +
                 "11: Error:Execution failed for task ':BlankProject1:processDebugResources'.\n" +
                 "12: Info:BUILD FAILED\n" +
                 "13: Info:Total time: 5.435 secs\n",
                 toString(parser.parseGradleOutput(output)));

    sourceFile.delete();
    source.delete();
    tempDir.delete();
  }

  @Test
  public void dashes() throws Exception {
    File tempDir = Files.createTempDir();
    String dirName = currentPlatform() == PLATFORM_WINDOWS ? "My -- Q&A Dir" : "My -- Q&A< Dir";
    File dir = new File(tempDir, dirName); // path which should force encoding of path chars, see for example issue 60050
    dir.mkdirs();
    sourceFile = new File(dir, "values.xml"); // Name matters for position search
    sourceFilePath = sourceFile.getAbsolutePath();
    File source = new File(dir, "dimens.xml");
    Files.write("<resources>\n" +
                "    <!-- Default screen margins, per the Android Design guidelines. -->\n" +
                "    <dimen name=\"activity_horizontal_margin\">16dp</dimen>\n" +
                "    <dimen name=\"activity_vertical_margin\">16dp</dimen>\n" +
                "    <dimen name=\"new_name\">50</dimen>\n" +
                "</resources>", source, Charsets.UTF_8);
    source.deleteOnExit();
    Files.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "    <!-- From: file:/Users/unittest/AndroidStudioProjects/BlankProject1Project/BlankProject1/build/exploded-bundles/ComAndroidSupportAppcompatV71800.aar/res/values/values.xml -->\n" +
                "    <dimen name=\"abc_action_bar_default_height\">48dip</dimen>\n" +
                "    <dimen name=\"abc_action_bar_icon_vertical_padding\">8dip</dimen>\n" +
                "    <!-- " + createPathComment(source, false) + " -->\n" +
                "    <dimen name=\"activity_horizontal_margin\">16dp</dimen>\n" +
                "    <dimen name=\"activity_vertical_margin\">16dp</dimen>\n" +
                "    <dimen name=\"ok\">50dp</dimen>\n" +
                "    <dimen name=\"new_name\">50</dimen>\n" +
                "    <!-- From: file:/Users/unittest/AndroidStudioProjects/BlankProject1Project/BlankProject1/build/exploded-bundles/ComAndroidSupportAppcompatV71800.aar/res/values/values.xml -->\n" +
                "    <item name=\"action_bar_activity_content\" type=\"id\"/>\n" +
                "    <item name=\"action_menu_divider\" type=\"id\"/>\n" +
                "    <item name=\"action_menu_presenter\" type=\"id\"/>\n" +
                "    <item name=\"home\" type=\"id\"/>\n" +
                "</resources>\n", sourceFile, Charsets.UTF_8);

    // TODO: Test layout too

    String output =
      "Relying on packaging to define the extension of the main artifact has been deprecated and is scheduled to be removed in Gradle 2.0\n" +
      ":BlankProject1:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
      ":BlankProject1:prepareDebugDependencies\n" +
      ":BlankProject1:mergeDebugAssets UP-TO-DATE\n" +
      ":BlankProject1:compileDebugRenderscript UP-TO-DATE\n" +
      ":BlankProject1:mergeDebugResources UP-TO-DATE\n" +
      ":BlankProject1:processDebugManifest UP-TO-DATE\n" +
      ":BlankProject1:processDebugResources\n" +
      sourceFilePath + ":10: error: Error: Integer types not allowed (at 'new_name' with value '50').\n" +
      ":BlankProject1:processDebugResources FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':BlankProject1:processDebugResources'.\n" +
      "> Failed to run command:\n" +
      "  \t/Users/tnorbye/dev/sdks/build-tools/18.0.1/aapt package -f --no-crunch -I ...\n" +
      "  Error Code:\n" +
      "  \t1\n" +
      "  Output:\n" +
      "  \t" + sourceFilePath + ":10: error: Error: Integer types not allowed (at 'new_name' with value '50').\n" +
      "\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 5.435 secs";

    assertEquals("0: Simple::BlankProject1:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
                 "1: Simple::BlankProject1:prepareDebugDependencies\n" +
                 "2: Simple::BlankProject1:mergeDebugAssets UP-TO-DATE\n" +
                 "3: Simple::BlankProject1:compileDebugRenderscript UP-TO-DATE\n" +
                 "4: Simple::BlankProject1:mergeDebugResources UP-TO-DATE\n" +
                 "5: Simple::BlankProject1:processDebugManifest UP-TO-DATE\n" +
                 "6: Simple::BlankProject1:processDebugResources\n" +
                 "7: Error:Integer types not allowed (at 'new_name' with value '50').\n" +
                 "\t" + source.getPath() + ":5:28\n" +
                 "8: Simple::BlankProject1:processDebugResources FAILED\n" +
                 "9: Error:Error while executing aapt command\n" +
                 "10: Error:Integer types not allowed (at 'new_name' with value '50').\n" +
                 "\t" + source.getPath() + ":5:28\n" +
                 "11: Error:Execution failed for task ':BlankProject1:processDebugResources'.\n" +
                 "12: Info:BUILD FAILED\n" +
                 "13: Info:Total time: 5.435 secs\n",
                 toString(parser.parseGradleOutput(output)));

    sourceFile.delete();
    source.delete();
    dir.delete();
    tempDir.delete();
  }

  @Test
  public void layoutFileSuffix() throws Exception {
    File tempDir = Files.createTempDir();
    sourceFile = new File(tempDir, "layout.xml");
    sourceFilePath = sourceFile.getAbsolutePath();
    File source = new File(tempDir, "real-layout.xml");
    Files.write("<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"match_parent\"\n" +
                "    android:paddingLeft=\"@dimen/activity_horizontal_margin\"\n" +
                "    android:paddingRight=\"@dimen/activity_horizontal_margin\"\n" +
                "    android:paddingTop=\"@dimen/activity_vertical_margin\"\n" +
                "    android:paddingBottom=\"@dimen/activity_vertical_margin\"\n" +
                "    tools:context=\".MainActivity\">\n" +
                "\n" +
                "\n" +
                "    <Button\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:hint=\"fy faen\"\n" +
                "        android:text=\"@string/hello_world\"\n" +
                "        android:slayout_alignParentTop=\"true\"\n" +
                "        android:layout_alignParentLeft=\"true\" />\n" +
                "\n" +
                "</RelativeLayout>\n", source, Charsets.UTF_8);
    source.deleteOnExit();
    Files.write("<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"match_parent\"\n" +
                "    android:paddingLeft=\"@dimen/activity_horizontal_margin\"\n" +
                "    android:paddingRight=\"@dimen/activity_horizontal_margin\"\n" +
                "    android:paddingTop=\"@dimen/activity_vertical_margin\"\n" +
                "    android:paddingBottom=\"@dimen/activity_vertical_margin\"\n" +
                "    tools:context=\".MainActivity\">\n" +
                "\n" +
                "    <!--style=\"@style/Buttons\"-->\n" +
                "    <Button\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:hint=\"fy faen\"\n" +
                "        android:text=\"@string/hello_world\"\n" +
                "        android:slayout_alignParentTop=\"true\"\n" +
                "        android:layout_alignParentLeft=\"true\" />\n" +
                "\n" +
                "</RelativeLayout>\n" +
                "<!-- " + createPathComment(source, false) + " -->", sourceFile, Charsets.UTF_8);

    String output =
      "Relying on packaging to define the extension of the main artifact has been deprecated and is scheduled to be removed in Gradle 2.0\n" +
      ":BlankProject1:preBuild UP-TO-DATE\n" +
      ":BlankProject1:preDebugBuild UP-TO-DATE\n" +
      ":BlankProject1:preReleaseBuild UP-TO-DATE\n" +
      ":BlankProject1:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
      ":BlankProject1:prepareDebugDependencies\n" +
      ":BlankProject1:compileDebugAidl UP-TO-DATE\n" +
      ":BlankProject1:compileDebugRenderscript UP-TO-DATE\n" +
      ":BlankProject1:generateDebugBuildConfig UP-TO-DATE\n" +
      ":BlankProject1:mergeDebugAssets UP-TO-DATE\n" +
      ":BlankProject1:mergeDebugResources UP-TO-DATE\n" +
      ":BlankProject1:processDebugManifest UP-TO-DATE\n" +
      ":BlankProject1:processDebugResources\n" +
      sourceFilePath + ":12: error: No resource identifier found for attribute 'slayout_alignParentTop' in package 'android'\n" +
      ":BlankProject1:processDebugResources FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':BlankProject1:processDebugResources'.\n" +
      "> Failed to run command:\n" +
      "  \t/Users/tnorbye/dev/sdks/build-tools/18.0.1/aapt package -f --no-crunch -I ... " +
      "  Error Code:\n" +
      "  \t1\n" +
      "  Output:\n" +
      "  \t" + sourceFilePath + ":12: error: No resource identifier found for attribute 'slayout_alignParentTop' in package 'android'\n" +
      "\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n";

    assertEquals("0: Simple::BlankProject1:preBuild UP-TO-DATE\n" +
                 "1: Simple::BlankProject1:preDebugBuild UP-TO-DATE\n" +
                 "2: Simple::BlankProject1:preReleaseBuild UP-TO-DATE\n" +
                 "3: Simple::BlankProject1:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
                 "4: Simple::BlankProject1:prepareDebugDependencies\n" +
                 "5: Simple::BlankProject1:compileDebugAidl UP-TO-DATE\n" +
                 "6: Simple::BlankProject1:compileDebugRenderscript UP-TO-DATE\n" +
                 "7: Simple::BlankProject1:generateDebugBuildConfig UP-TO-DATE\n" +
                 "8: Simple::BlankProject1:mergeDebugAssets UP-TO-DATE\n" +
                 "9: Simple::BlankProject1:mergeDebugResources UP-TO-DATE\n" +
                 "10: Simple::BlankProject1:processDebugManifest UP-TO-DATE\n" +
                 "11: Simple::BlankProject1:processDebugResources\n" +
                 "12: Error:No resource identifier found for attribute 'slayout_alignParentTop' in package 'android'\n" +
                 "\t" + source.getPath() + ":12:-1\n" +
                 "13: Simple::BlankProject1:processDebugResources FAILED\n" +
                 "14: Error:Error while executing aapt command\n" +
                 "15: Error:No resource identifier found for attribute 'slayout_alignParentTop' in package 'android'\n" +
                 "\t" + source.getPath() + ":12:-1\n" +
                 "16: Error:Execution failed for task ':BlankProject1:processDebugResources'.\n" +
                 "17: Info:BUILD FAILED\n",
                 toString(parser.parseGradleOutput(output)));

    sourceFile.delete();
    source.delete();
    tempDir.delete();
  }

  @Test
  public void changedFile() throws Exception {
    createTempXmlFile();
    String output =
      ":MyApp:compileReleaseRenderscript UP-TO-DATE\n" +
      ":MyApp:mergeReleaseResources FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':MyApp:mergeReleaseResources'.\n" +
      "> In DataSet 'main', no data file for changedFile '" + sourceFilePath + "'\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 15.612 secs";

    assertEquals("0: Simple::MyApp:compileReleaseRenderscript UP-TO-DATE\n" +
                 "1: Simple::MyApp:mergeReleaseResources FAILED\n" +
                 "2: Error:Execution failed for task ':MyApp:mergeReleaseResources'.\n" +
                 "> In DataSet 'main', no data file for changedFile '" + sourceFilePath + "'\n" +
                 "\t" + sourceFilePath + ":-1:-1\n" +
                 "3: Info:BUILD FAILED\n" +
                 "4: Info:Total time: 15.612 secs\n", toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }

  @Test
  public void changedFile2() throws Exception {
    createTempXmlFile();
    String output =
      ":MyApp:compileReleaseRenderscript UP-TO-DATE\n" +
      ":MyApp:mergeReleaseResources FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':MyApp:mergeReleaseResources'.\n" +
      "> In DataSet 'main', no data file for changedFile '" + sourceFilePath + "'. This is an internal error in the incremental builds code; to work around it, try doing a full clean build.\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 15.612 secs";

    assertEquals("0: Simple::MyApp:compileReleaseRenderscript UP-TO-DATE\n" +
                 "1: Simple::MyApp:mergeReleaseResources FAILED\n" +
                 "2: Error:Execution failed for task ':MyApp:mergeReleaseResources'.\n" +
                 "> In DataSet 'main', no data file for changedFile '" + sourceFilePath + "'. This is an internal error in the incremental builds code; to work around it, try doing a full clean build.\n" +
                 "\t" + sourceFilePath + ":-1:-1\n" +
                 "3: Info:BUILD FAILED\n" +
                 "4: Info:Total time: 15.612 secs\n", toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }

  @Test
  public void mismatchedTag() throws Exception {
    // https://code.google.com/p/android/issues/detail?id=59824
    createTempXmlFile();
    String output =
      ":AudioPlayer:prepareDebugDependencies\n" +
      ":AudioPlayer:compileDebugAidl UP-TO-DATE\n" +
      ":AudioPlayer:generateDebugBuildConfig UP-TO-DATE\n" +
      ":AudioPlayer:mergeDebugAssets UP-TO-DATE\n" +
      ":AudioPlayer:compileDebugRenderscript UP-TO-DATE\n" +
      ":AudioPlayer:mergeDebugResources UP-TO-DATE\n" +
      ":AudioPlayer:processDebugManifest UP-TO-DATE\n" +
      ":AudioPlayer:processDebugResources\n" +
      sourceFilePath + ":101: error: Error parsing XML: mismatched tag\n" +
      ":AudioPlayer:processDebugResources FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':AudioPlayer:processDebugResources'.\n" +
      "> Failed to run command:\n" +
      "  \t/Users/sbarta/sdk/build-tools/android-4.2.2/aapt package -f --no-crunch -I ...\n" +
      "  Error Code:\n" +
      "  \t1\n" +
      "  Output:\n" +
      "  \t" + sourceFilePath + ":101: error: Error parsing XML: mismatched tag\n" +
      "\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 3.836 secs";
    assertEquals("0: Simple::AudioPlayer:prepareDebugDependencies\n" +
                 "1: Simple::AudioPlayer:compileDebugAidl UP-TO-DATE\n" +
                 "2: Simple::AudioPlayer:generateDebugBuildConfig UP-TO-DATE\n" +
                 "3: Simple::AudioPlayer:mergeDebugAssets UP-TO-DATE\n" +
                 "4: Simple::AudioPlayer:compileDebugRenderscript UP-TO-DATE\n" +
                 "5: Simple::AudioPlayer:mergeDebugResources UP-TO-DATE\n" +
                 "6: Simple::AudioPlayer:processDebugManifest UP-TO-DATE\n" +
                 "7: Simple::AudioPlayer:processDebugResources\n" +
                 "8: Error:Error parsing XML: mismatched tag\n" +
                 "\t" + sourceFilePath + ":101:-1\n" +
                 "9: Simple::AudioPlayer:processDebugResources FAILED\n" +
                 "10: Error:Error while executing aapt command\n" +
                 "11: Error:Error parsing XML: mismatched tag\n" +
                 "\t" + sourceFilePath + ":101:-1\n" +
                 "12: Error:Execution failed for task ':AudioPlayer:processDebugResources'.\n" +
                 "13: Info:BUILD FAILED\n" +
                 "14: Info:Total time: 3.836 secs\n",
                 toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }

  @Test
  public void duplicateResources3() throws Exception {
    // Duplicate resource exception: New gradle output format (using MergingException)
    createTempXmlFile();
    String output =
      "Relying on packaging to define the extension of the main artifact has been deprecated and is scheduled to be removed in Gradle 2.0\n" +
      ":MyApplication589:preBuild UP-TO-DATE\n" +
      ":MyApplication589:preDebugBuild UP-TO-DATE\n" +
      ":MyApplication589:preReleaseBuild UP-TO-DATE\n" +
      ":MyApplication589:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
      ":MyApplication589:prepareDebugDependencies\n" +
      ":MyApplication589:compileDebugAidl UP-TO-DATE\n" +
      ":MyApplication589:compileDebugRenderscript UP-TO-DATE\n" +
      ":MyApplication589:generateDebugBuildConfig UP-TO-DATE\n" +
      ":MyApplication589:mergeDebugAssets UP-TO-DATE\n" +
      ":MyApplication589:mergeDebugResources\n" +
      sourceFilePath + ": Error: Duplicate resources: " + sourceFilePath + ", /some/other/path/src/main/res/values/strings.xml:string/action_settings\n" +
      ":MyApplication589:mergeDebugResources FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':MyApplication589:mergeDebugResources'.\n" +
      "> " + sourceFilePath + ": Error: Duplicate resources: " + sourceFilePath + ", /some/other/path/src/main/res/values/strings.xml:string/action_settings\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 4.861 secs";

    assertEquals("0: Simple::MyApplication589:preBuild UP-TO-DATE\n" +
                 "1: Simple::MyApplication589:preDebugBuild UP-TO-DATE\n" +
                 "2: Simple::MyApplication589:preReleaseBuild UP-TO-DATE\n" +
                 "3: Simple::MyApplication589:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
                 "4: Simple::MyApplication589:prepareDebugDependencies\n" +
                 "5: Simple::MyApplication589:compileDebugAidl UP-TO-DATE\n" +
                 "6: Simple::MyApplication589:compileDebugRenderscript UP-TO-DATE\n" +
                 "7: Simple::MyApplication589:generateDebugBuildConfig UP-TO-DATE\n" +
                 "8: Simple::MyApplication589:mergeDebugAssets UP-TO-DATE\n" +
                 "9: Simple::MyApplication589:mergeDebugResources\n" +
                 "10: Error:Error: Duplicate resources: " + sourceFilePath +", /some/other/path/src/main/res/values/strings.xml:string/action_settings\n" +
                 "\t" + sourceFilePath + ":-1:-1\n" +
                 "11: Simple::MyApplication589:mergeDebugResources FAILED\n" +
                 "12: Error:Execution failed for task ':MyApplication589:mergeDebugResources'.\n" +
                 "> " + sourceFilePath + ": Error: Duplicate resources: " + sourceFilePath + ", /some/other/path/src/main/res/values/strings.xml:string/action_settings\n" +
                 "13: Info:BUILD FAILED\n" +
                 "14: Info:Total time: 4.861 secs\n",
                 toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }

  @Test
  public void xmlError2() throws Exception {
    // XML error; Added "<" on separate line; new gradle output format (using MergingException)
    createTempXmlFile();
    String output =
      "Relying on packaging to define the extension of the main artifact has been deprecated and is scheduled to be removed in Gradle 2.0\n" +
      ":MyApplication:preBuild UP-TO-DATE\n" +
      ":MyApplication:preDebugBuild UP-TO-DATE\n" +
      ":MyApplication:preReleaseBuild UP-TO-DATE\n" +
      ":MyApplication:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
      ":MyApplication:prepareDebugDependencies\n" +
      ":MyApplication:compileDebugAidl UP-TO-DATE\n" +
      ":MyApplication:compileDebugRenderscript UP-TO-DATE\n" +
      ":MyApplication:generateDebugBuildConfig UP-TO-DATE\n" +
      ":MyApplication:mergeDebugAssets UP-TO-DATE\n" +
      ":MyApplication:mergeDebugResources\n" +
      "[Fatal Error] :5:2: The content of elements must consist of well-formed character data or markup.\n" +
      sourceFilePath + ":4:1: Error: The content of elements must consist of well-formed character data or markup.\n" +
      ":MyApplication:mergeDebugResources FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':MyApplication:mergeDebugResources'.\n" +
      "> " + sourceFilePath + ":4:1: Error: The content of elements must consist of well-formed character data or markup.\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 5.187 secs";
    assertEquals("0: Simple::MyApplication:preBuild UP-TO-DATE\n" +
                 "1: Simple::MyApplication:preDebugBuild UP-TO-DATE\n" +
                 "2: Simple::MyApplication:preReleaseBuild UP-TO-DATE\n" +
                 "3: Simple::MyApplication:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
                 "4: Simple::MyApplication:prepareDebugDependencies\n" +
                 "5: Simple::MyApplication:compileDebugAidl UP-TO-DATE\n" +
                 "6: Simple::MyApplication:compileDebugRenderscript UP-TO-DATE\n" +
                 "7: Simple::MyApplication:generateDebugBuildConfig UP-TO-DATE\n" +
                 "8: Simple::MyApplication:mergeDebugAssets UP-TO-DATE\n" +
                 "9: Simple::MyApplication:mergeDebugResources\n" +
                 "10: Error:The content of elements must consist of well-formed character data or markup.\n" +
                 "11: Error:Error: The content of elements must consist of well-formed character data or markup.\n" +
                 "\t" + sourceFilePath + ":4:1\n" +
                 "12: Simple::MyApplication:mergeDebugResources FAILED\n" +
                 "13: Error:Execution failed for task ':MyApplication:mergeDebugResources'.\n" +
                 "> " + sourceFilePath + ":4:1: Error: The content of elements must consist of well-formed character data or markup.\n" +
                 "14: Info:BUILD FAILED\n" +
                 "15: Info:Total time: 5.187 secs\n",
                 toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }

  @Test
  public void xmlErrorBadLineNumbers() throws Exception {
    // Like testXmlError2, but with tweaked line numbers to check the MergingExceptionParser parser
    createTempXmlFile();
    String output =
      "[Fatal Error] :5:2: The content of elements must consist of well-formed character data or markup.\n" +
      sourceFilePath + ":42: Error: The content of elements must consist of well-formed character data or markup.\n" +
      sourceFilePath + ":-1: Error: The content of elements must consist of well-formed character data or markup.\n" +
      sourceFilePath + ":-1:-1: Error: The content of elements must consist of well-formed character data or markup.";
    assertEquals("0: Error:The content of elements must consist of well-formed character data or markup.\n" +
                 "1: Error:Error: The content of elements must consist of well-formed character data or markup.\n" +
                 "\t" + sourceFilePath + ":42:-1\n" +
                 "2: Error:Error: The content of elements must consist of well-formed character data or markup.\n" +
                 "\t" + sourceFilePath + ":-1:-1\n" +
                 "3: Error:Error: The content of elements must consist of well-formed character data or markup.\n" +
                 "\t" + sourceFilePath + ":-1:-1\n",
                 toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }

  @Test
  public void xmlError3() throws Exception {
    // XML error; Added <dimen name=activity_horizontal_margin">16dp</dimen> (missing quote
    createTempXmlFile();
    String output =
      "Relying on packaging to define the extension of the main artifact has been deprecated and is scheduled to be removed in Gradle 2.0\n" +
      ":MyApplication:preBuild UP-TO-DATE\n" +
      ":MyApplication:preDebugBuild UP-TO-DATE\n" +
      ":MyApplication:preReleaseBuild UP-TO-DATE\n" +
      ":MyApplication:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
      ":MyApplication:prepareDebugDependencies\n" +
      ":MyApplication:compileDebugAidl UP-TO-DATE\n" +
      ":MyApplication:compileDebugRenderscript UP-TO-DATE\n" +
      ":MyApplication:generateDebugBuildConfig UP-TO-DATE\n" +
      ":MyApplication:mergeDebugAssets UP-TO-DATE\n" +
      ":MyApplication:mergeDebugResources\n" +
      "[Fatal Error] :3:17: Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
      sourceFilePath + ":2:16: Error: Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
      ":MyApplication:mergeDebugResources FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':MyApplication:mergeDebugResources'.\n" +
      "> " + sourceFilePath + ":2:16: Error: Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 4.951 secs";
    assertEquals("0: Simple::MyApplication:preBuild UP-TO-DATE\n" +
                 "1: Simple::MyApplication:preDebugBuild UP-TO-DATE\n" +
                 "2: Simple::MyApplication:preReleaseBuild UP-TO-DATE\n" +
                 "3: Simple::MyApplication:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
                 "4: Simple::MyApplication:prepareDebugDependencies\n" +
                 "5: Simple::MyApplication:compileDebugAidl UP-TO-DATE\n" +
                 "6: Simple::MyApplication:compileDebugRenderscript UP-TO-DATE\n" +
                 "7: Simple::MyApplication:generateDebugBuildConfig UP-TO-DATE\n" +
                 "8: Simple::MyApplication:mergeDebugAssets UP-TO-DATE\n" +
                 "9: Simple::MyApplication:mergeDebugResources\n" +
                 "10: Error:Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
                 "11: Error:Error: Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
                 "\t" + sourceFilePath + ":2:16\n" +
                 "12: Simple::MyApplication:mergeDebugResources FAILED\n" +
                 "13: Error:Execution failed for task ':MyApplication:mergeDebugResources'.\n" +
                 "> "+ sourceFilePath + ":2:16: Error: Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
                 "14: Info:BUILD FAILED\n" +
                 "15: Info:Total time: 4.951 secs\n",
                 toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }

  @Test
  public void javacError() throws Exception {
    // Javac error
    createTempXmlFile();
    String output =
      "Relying on packaging to define the extension of the main artifact has been deprecated and is scheduled to be removed in Gradle 2.0\n" +
      ":MyApplication:preBuild UP-TO-DATE\n" +
      ":MyApplication:preDebugBuild UP-TO-DATE\n" +
      ":MyApplication:preReleaseBuild UP-TO-DATE\n" +
      ":MyApplication:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
      ":MyApplication:prepareDebugDependencies\n" +
      ":MyApplication:compileDebugAidl UP-TO-DATE\n" +
      ":MyApplication:compileDebugRenderscript UP-TO-DATE\n" +
      ":MyApplication:generateDebugBuildConfig UP-TO-DATE\n" +
      ":MyApplication:mergeDebugAssets UP-TO-DATE\n" +
      ":MyApplication:mergeDebugResources UP-TO-DATE\n" +
      ":MyApplication:processDebugManifest UP-TO-DATE\n" +
      ":MyApplication:processDebugResources UP-TO-DATE\n" +
      ":MyApplication:generateDebugSources UP-TO-DATE\n" +
      ":MyApplication:compileDebug\n" +
      "Ignoring platform 'android-1': build.prop is missing.\n" +
      sourceFilePath + ":12: not a statement\n" +
      "x        super.onCreate(savedInstanceState);\n" +
      "^\n" +
      sourceFilePath + ":12: ';' expected\n" +
      "x        super.onCreate(savedInstanceState);\n" +
      " ^\n" +
      "2 errors\n" +
      ":MyApplication:compileDebug FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':MyApplication:compileDebug'.\n" +
      "> Compilation failed; see the compiler error output for details.\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 6.177 secs";
    assertEquals("0: Simple::MyApplication:preBuild UP-TO-DATE\n" +
                 "1: Simple::MyApplication:preDebugBuild UP-TO-DATE\n" +
                 "2: Simple::MyApplication:preReleaseBuild UP-TO-DATE\n" +
                 "3: Simple::MyApplication:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
                 "4: Simple::MyApplication:prepareDebugDependencies\n" +
                 "5: Simple::MyApplication:compileDebugAidl UP-TO-DATE\n" +
                 "6: Simple::MyApplication:compileDebugRenderscript UP-TO-DATE\n" +
                 "7: Simple::MyApplication:generateDebugBuildConfig UP-TO-DATE\n" +
                 "8: Simple::MyApplication:mergeDebugAssets UP-TO-DATE\n" +
                 "9: Simple::MyApplication:mergeDebugResources UP-TO-DATE\n" +
                 "10: Simple::MyApplication:processDebugManifest UP-TO-DATE\n" +
                 "11: Simple::MyApplication:processDebugResources UP-TO-DATE\n" +
                 "12: Simple::MyApplication:generateDebugSources UP-TO-DATE\n" +
                 "13: Simple::MyApplication:compileDebug\n" +
                 "14: Simple:Ignoring platform 'android-1': build.prop is missing.\n" +
                 "15: Error:not a statement\n" +
                 "\t" + sourceFilePath + ":12:-1\n" +
                 "16: Simple:x        super.onCreate(savedInstanceState);\n" +
                 "17: Simple:^\n" +
                 "18: Error:';' expected\n" +
                 "\t" + sourceFilePath + ":12:-1\n" +
                 "19: Simple:x        super.onCreate(savedInstanceState);\n" +
                 "20: Simple: ^\n" +
                 "21: Simple::MyApplication:compileDebug FAILED\n" +
                 "22: Error:Execution failed for task ':MyApplication:compileDebug'.\n" +
                 "> Compilation failed; see the compiler error output for details.\n" +
                 "23: Info:BUILD FAILED\n" +
                 "24: Info:Total time: 6.177 secs\n",
                 toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }

  @Test
  public void missingSourceCompat() throws Exception {
    // Wrong target (source, not target
    // Should rewrite to suggest checking JAVA_HOME
    createTempXmlFile();
    String output =
      ":MyApplication:preBuild UP-TO-DATE\n" +
      ":MyApplication:preFreeDebugBuild UP-TO-DATE\n" +
      ":MyApplication:preFreeReleaseBuild UP-TO-DATE\n" +
      ":MyApplication:preProDebugBuild UP-TO-DATE\n" +
      ":MyApplication:preProReleaseBuild UP-TO-DATE\n" +
      ":MyApplication:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
      ":MyApplication:prepareFreeDebugDependencies\n" +
      ":MyApplication:compileFreeDebugAidl UP-TO-DATE\n" +
      ":MyApplication:compileFreeDebugRenderscript UP-TO-DATE\n" +
      ":MyApplication:generateFreeDebugBuildConfig UP-TO-DATE\n" +
      ":MyApplication:mergeFreeDebugAssets UP-TO-DATE\n" +
      ":MyApplication:mergeFreeDebugResources UP-TO-DATE\n" +
      ":MyApplication:processFreeDebugManifest UP-TO-DATE\n" +
      ":MyApplication:processFreeDebugResources UP-TO-DATE\n" +
      ":MyApplication:generateFreeDebugSources UP-TO-DATE\n" +
      ":MyApplication:compileFreeDebug\n" +
      "Ignoring platform 'android-1': build.prop is missing.\n" +
      ":MyApplication:compileFreeDebug FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':MyApplication:compileFreeDebug'.\n" +
      "> invalid source release: 1.7\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 5.47 secs";
    assertEquals("0: Simple::MyApplication:preBuild UP-TO-DATE\n" +
                 "1: Simple::MyApplication:preFreeDebugBuild UP-TO-DATE\n" +
                 "2: Simple::MyApplication:preFreeReleaseBuild UP-TO-DATE\n" +
                 "3: Simple::MyApplication:preProDebugBuild UP-TO-DATE\n" +
                 "4: Simple::MyApplication:preProReleaseBuild UP-TO-DATE\n" +
                 "5: Simple::MyApplication:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
                 "6: Simple::MyApplication:prepareFreeDebugDependencies\n" +
                 "7: Simple::MyApplication:compileFreeDebugAidl UP-TO-DATE\n" +
                 "8: Simple::MyApplication:compileFreeDebugRenderscript UP-TO-DATE\n" +
                 "9: Simple::MyApplication:generateFreeDebugBuildConfig UP-TO-DATE\n" +
                 "10: Simple::MyApplication:mergeFreeDebugAssets UP-TO-DATE\n" +
                 "11: Simple::MyApplication:mergeFreeDebugResources UP-TO-DATE\n" +
                 "12: Simple::MyApplication:processFreeDebugManifest UP-TO-DATE\n" +
                 "13: Simple::MyApplication:processFreeDebugResources UP-TO-DATE\n" +
                 "14: Simple::MyApplication:generateFreeDebugSources UP-TO-DATE\n" +
                 "15: Simple::MyApplication:compileFreeDebug\n" +
                 "16: Simple:Ignoring platform 'android-1': build.prop is missing.\n" +
                 "17: Simple::MyApplication:compileFreeDebug FAILED\n" +
                 "18: Error:Execution failed for task ':MyApplication:compileFreeDebug'.\n" +
                 // TODO: This is all we currently get. We should find a way to trap this and point to the
                 // right source file where the invalid source flag is set
                 "> invalid source release: 1.7\n" +
                 "19: Info:BUILD FAILED\n" +
                 "20: Info:Total time: 5.47 secs\n",
                 toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }

  @Test
  public void invalidLayoutName() throws Exception {
    // Invalid layout name
    createTempXmlFile();
    String output =
      "Relying on packaging to define the extension of the main artifact has been deprecated and is scheduled to be removed in Gradle 2.0\n" +
      ":MyApplication585:preBuild UP-TO-DATE\n" +
      ":MyApplication585:preDebugBuild UP-TO-DATE\n" +
      ":MyApplication585:preReleaseBuild UP-TO-DATE\n" +
      ":MyApplication585:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
      ":MyApplication585:prepareDebugDependencies\n" +
      ":MyApplication585:compileDebugAidl UP-TO-DATE\n" +
      ":MyApplication585:compileDebugRenderscript UP-TO-DATE\n" +
      ":MyApplication585:generateDebugBuildConfig UP-TO-DATE\n" +
      ":MyApplication585:mergeDebugAssets UP-TO-DATE\n" +
      ":MyApplication585:mergeDebugResources\n" +
      sourceFilePath + ": Error: Invalid file name: must contain only lowercase letters and digits ([a-z0-9_.])\n" +
      ":MyApplication585:mergeDebugResources FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':MyApplication585:mergeDebugResources'.\n" +
      "> " + sourceFilePath + ": Error: Invalid file name: must contain only lowercase letters and digits ([a-z0-9_.])\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 8.91 secs";
    assertEquals("0: Simple::MyApplication585:preBuild UP-TO-DATE\n" +
                 "1: Simple::MyApplication585:preDebugBuild UP-TO-DATE\n" +
                 "2: Simple::MyApplication585:preReleaseBuild UP-TO-DATE\n" +
                 "3: Simple::MyApplication585:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
                 "4: Simple::MyApplication585:prepareDebugDependencies\n" +
                 "5: Simple::MyApplication585:compileDebugAidl UP-TO-DATE\n" +
                 "6: Simple::MyApplication585:compileDebugRenderscript UP-TO-DATE\n" +
                 "7: Simple::MyApplication585:generateDebugBuildConfig UP-TO-DATE\n" +
                 "8: Simple::MyApplication585:mergeDebugAssets UP-TO-DATE\n" +
                 "9: Simple::MyApplication585:mergeDebugResources\n" +
                 "10: Error:Error: Invalid file name: must contain only lowercase letters and digits ([a-z0-9_.])\n" +
                 "\t" + sourceFilePath + ":-1:-1\n" +
                 "11: Simple::MyApplication585:mergeDebugResources FAILED\n" +
                 "12: Error:Execution failed for task ':MyApplication585:mergeDebugResources'.\n" +
                 "> " + sourceFilePath + ": Error: Invalid file name: must contain only lowercase letters and digits ([a-z0-9_.])\n" +
                 "13: Info:BUILD FAILED\n" +
                 "14: Info:Total time: 8.91 secs\n",
                 toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }

  @Test
  public void invalidLayoutName2() throws Exception {
    // Like testInvalidLayoutName, but with line numbers in the error pattern
    createTempXmlFile();
    String output =
      "Relying on packaging to define the extension of the main artifact has been deprecated and is scheduled to be removed in Gradle 2.0\n" +
      ":MyApplication585:preBuild UP-TO-DATE\n" +
      ":MyApplication585:preDebugBuild UP-TO-DATE\n" +
      ":MyApplication585:preReleaseBuild UP-TO-DATE\n" +
      ":MyApplication585:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
      ":MyApplication585:prepareDebugDependencies\n" +
      ":MyApplication585:compileDebugAidl UP-TO-DATE\n" +
      ":MyApplication585:compileDebugRenderscript UP-TO-DATE\n" +
      ":MyApplication585:generateDebugBuildConfig UP-TO-DATE\n" +
      ":MyApplication585:mergeDebugAssets UP-TO-DATE\n" +
      ":MyApplication585:mergeDebugResources\n" +
      sourceFilePath + ":4: Error: Invalid file name: must contain only lowercase letters and digits ([a-z0-9_.])\n" +
      ":MyApplication585:mergeDebugResources FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':MyApplication585:mergeDebugResources'.\n" +
      "> " + sourceFilePath + ":4: Error: Invalid file name: must contain only lowercase letters and digits ([a-z0-9_.])\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 8.91 secs";
    assertEquals("0: Simple::MyApplication585:preBuild UP-TO-DATE\n" +
                 "1: Simple::MyApplication585:preDebugBuild UP-TO-DATE\n" +
                 "2: Simple::MyApplication585:preReleaseBuild UP-TO-DATE\n" +
                 "3: Simple::MyApplication585:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
                 "4: Simple::MyApplication585:prepareDebugDependencies\n" +
                 "5: Simple::MyApplication585:compileDebugAidl UP-TO-DATE\n" +
                 "6: Simple::MyApplication585:compileDebugRenderscript UP-TO-DATE\n" +
                 "7: Simple::MyApplication585:generateDebugBuildConfig UP-TO-DATE\n" +
                 "8: Simple::MyApplication585:mergeDebugAssets UP-TO-DATE\n" +
                 "9: Simple::MyApplication585:mergeDebugResources\n" +
                 "10: Error:Error: Invalid file name: must contain only lowercase letters and digits ([a-z0-9_.])\n" +
                 "\t" + sourceFilePath + ":4:-1\n" +
                 "11: Simple::MyApplication585:mergeDebugResources FAILED\n" +
                 "12: Error:Execution failed for task ':MyApplication585:mergeDebugResources'.\n" +
                 "> " + sourceFilePath + ":4: Error: Invalid file name: must contain only lowercase letters and digits ([a-z0-9_.])\n" +
                 "13: Info:BUILD FAILED\n" +
                 "14: Info:Total time: 8.91 secs\n",
                 toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }

  @Test
  public void multipleResourcesInSameFile() throws Exception {
    // Multiple items (repeated)
    createTempXmlFile();
    String output =
      "Relying on packaging to define the extension of the main artifact has been deprecated and is scheduled to be removed in Gradle 2.0\n" +
      ":MyApplication:preBuild UP-TO-DATE\n" +
      ":MyApplication:preDebugBuild UP-TO-DATE\n" +
      ":MyApplication:preReleaseBuild UP-TO-DATE\n" +
      ":MyApplication:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
      ":MyApplication:prepareDebugDependencies\n" +
      ":MyApplication:compileDebugAidl UP-TO-DATE\n" +
      ":MyApplication:compileDebugRenderscript UP-TO-DATE\n" +
      ":MyApplication:generateDebugBuildConfig UP-TO-DATE\n" +
      ":MyApplication:mergeDebugAssets UP-TO-DATE\n" +
      ":MyApplication:mergeDebugResources\n" +
      sourceFilePath + ": Error: Found item Dimension/activity_horizontal_margin more than one time\n" +
      ":MyApplication:mergeDebugResources FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':MyApplication:mergeDebugResources'.\n" +
      "> " + sourceFilePath + ": Error: Found item Dimension/activity_horizontal_margin more than one time\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 5.623 secs";
    assertEquals("0: Simple::MyApplication:preBuild UP-TO-DATE\n" +
                 "1: Simple::MyApplication:preDebugBuild UP-TO-DATE\n" +
                 "2: Simple::MyApplication:preReleaseBuild UP-TO-DATE\n" +
                 "3: Simple::MyApplication:prepareComAndroidSupportAppcompatV71800Library UP-TO-DATE\n" +
                 "4: Simple::MyApplication:prepareDebugDependencies\n" +
                 "5: Simple::MyApplication:compileDebugAidl UP-TO-DATE\n" +
                 "6: Simple::MyApplication:compileDebugRenderscript UP-TO-DATE\n" +
                 "7: Simple::MyApplication:generateDebugBuildConfig UP-TO-DATE\n" +
                 "8: Simple::MyApplication:mergeDebugAssets UP-TO-DATE\n" +
                 "9: Simple::MyApplication:mergeDebugResources\n" +
                 "10: Error:Error: Found item Dimension/activity_horizontal_margin more than one time\n" +
                 "\t" + sourceFilePath + ":-1:-1\n" +
                 "11: Simple::MyApplication:mergeDebugResources FAILED\n" +
                 "12: Error:Execution failed for task ':MyApplication:mergeDebugResources'.\n" +
                 "> " + sourceFilePath + ": Error: Found item Dimension/activity_horizontal_margin more than one time\n" +
                 "13: Info:BUILD FAILED\n" +
                 "14: Info:Total time: 5.623 secs\n",
                 toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }


  @Test
  public void ndkWarningOutputUnix() throws Exception {
    // Do not run tests on Windows (see http://b.android.com/222904)
    assumeFalse(SystemInfo.isWindows);

    createTempXmlFile();
    String output =
      ":foolib:compileLint\n" +
      ":foolib:copyDebugLint UP-TO-DATE\n" +
      ":foolib:mergeDebugProguardFiles UP-TO-DATE\n" +
      ":foolib:buildNative\n" +
      "Unexpected close tag: MANIFEST, expecting APPLICATION\n" +
      "jni/Android.mk:24: warning: overriding commands for target `dump'\n" +
      "jni/Android.mk:24: warning: ignoring old commands for target `dump'\n" +
      "jni/Android.mk:24: warning: overriding commands for target `dump'\n" +
      "jni/Android.mk:24: warning: ignoring old commands for target `dump'\n" +
      sourceFilePath + ":393: warning: overriding commands for target `obj/local/x86/objs/blah/src/blah3_a_16.o'\n" +
      sourceFilePath + ":393: warning: ignoring old commands for target `obj/local/x86/objs/blah/src/blah3_a_16.o'\n" +
      sourceFilePath + ":393: warning: overriding commands for target `obj/local/x86/objs/blah/src/blah3_a_9.o'\n" +
      sourceFilePath + ":393: warning: ignoring old commands for target `obj/local/x86/objs/blah/src/blah3_a_9.o'\n" +
      sourceFilePath + ":393: warning: overriding commands for target `obj/local/x86/objs/blah/src/blah3_b_18.o'\n" +
      sourceFilePath + ":393: warning: ignoring old commands for target `obj/local/x86/objs/blah/src/blah3_b_18.o'\n" +
      sourceFilePath + ":393: warning: overriding commands for target `obj/local/x86/objs/blah/src/blah3_c.o'\n" +
      sourceFilePath + ":393: warning: ignoring old commands for target `obj/local/x86/objs/blah/src/blah3_c.o'\n" +
      "jni/Android.mk:24: warning: overriding commands for target `dump'\n" +
      "jni/Android.mk:24: warning: ignoring old commands for target `dump'\n" +
      "[armeabi-v7a] Install        : libaacdecoder.so => libs/armeabi-v7a/libaacdecoder.so\n" +
      "[armeabi] Install        : libaacdecoder.so => libs/armeabi/libaacdecoder.so\n" +
      "[x86] Install        : libaacdecoder.so => libs/x86/libaacdecoder.so\n" +
      "[mips] Install        : libaacdecoder.so => libs/mips/libaacdecoder.so\n" +
      ":foolib:copyNativeLibs UP-TO-DATE" +
      ":app:assembleDebug UP-TO-DATE\n" +
      "\n" +
      "BUILD SUCCESSFUL\n" +
      "\n" +
      "Total time: 11.965 secs";

    assertEquals("0: Simple::foolib:compileLint\n" +
                 "1: Simple::foolib:copyDebugLint UP-TO-DATE\n" +
                 "2: Simple::foolib:mergeDebugProguardFiles UP-TO-DATE\n" +
                 "3: Simple::foolib:buildNative\n" +
                 "4: Simple:Unexpected close tag: MANIFEST, expecting APPLICATION\n" +
                 "5: Warning:warning: overriding commands for target `dump'\n" +
                 "\t" + CWD_PATH + "/jni/Android.mk:24:-1\n" +
                 "6: Warning:warning: ignoring old commands for target `dump'\n" +
                 "\t" + CWD_PATH + "/jni/Android.mk:24:-1\n" +
                 "7: Warning:warning: overriding commands for target `dump'\n" +
                 "\t" + CWD_PATH + "/jni/Android.mk:24:-1\n" +
                 "8: Warning:warning: ignoring old commands for target `dump'\n" +
                 "\t" + CWD_PATH + "/jni/Android.mk:24:-1\n" +
                 "9: Warning:warning: overriding commands for target `obj/local/x86/objs/blah/src/blah3_a_16.o'\n" +
                 "\t" + sourceFilePath + ":393:-1\n" +
                 "10: Warning:warning: ignoring old commands for target `obj/local/x86/objs/blah/src/blah3_a_16.o'\n" +
                 "\t" + sourceFilePath + ":393:-1\n" +
                 "11: Warning:warning: overriding commands for target `obj/local/x86/objs/blah/src/blah3_a_9.o'\n" +
                 "\t" + sourceFilePath + ":393:-1\n" +
                 "12: Warning:warning: ignoring old commands for target `obj/local/x86/objs/blah/src/blah3_a_9.o'\n" +
                 "\t" + sourceFilePath + ":393:-1\n" +
                 "13: Warning:warning: overriding commands for target `obj/local/x86/objs/blah/src/blah3_b_18.o'\n" +
                 "\t" + sourceFilePath + ":393:-1\n" +
                 "14: Warning:warning: ignoring old commands for target `obj/local/x86/objs/blah/src/blah3_b_18.o'\n" +
                 "\t" + sourceFilePath + ":393:-1\n" +
                 "15: Warning:warning: overriding commands for target `obj/local/x86/objs/blah/src/blah3_c.o'\n" +
                 "\t" + sourceFilePath + ":393:-1\n" +
                 "16: Warning:warning: ignoring old commands for target `obj/local/x86/objs/blah/src/blah3_c.o'\n" +
                 "\t" + sourceFilePath + ":393:-1\n" +
                 "17: Warning:warning: overriding commands for target `dump'\n" +
                 "\t" + CWD_PATH + "/jni/Android.mk:24:-1\n" +
                 "18: Warning:warning: ignoring old commands for target `dump'\n" +
                 "\t" + CWD_PATH + "/jni/Android.mk:24:-1\n" +
                 "19: Simple:[armeabi-v7a] Install        : libaacdecoder.so => libs/armeabi-v7a/libaacdecoder.so\n" +
                 "20: Simple:[armeabi] Install        : libaacdecoder.so => libs/armeabi/libaacdecoder.so\n" +
                 "21: Simple:[x86] Install        : libaacdecoder.so => libs/x86/libaacdecoder.so\n" +
                 "22: Simple:[mips] Install        : libaacdecoder.so => libs/mips/libaacdecoder.so\n" +
                 "23: Simple::foolib:copyNativeLibs UP-TO-DATE:app:assembleDebug UP-TO-DATE\n" +
                 "24: Info:BUILD SUCCESSFUL\n" +
                 "25: Info:Total time: 11.965 secs\n",
                 toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }

  @Test
  public void ndErrorOutputUnix() throws Exception {
    createTempFile(".cpp");
    String output =
      ":foolib:mergeDebugProguardFiles UP-TO-DATE\n" +
      ":foolib:buildNative\n" +
      sourceFilePath + ": In function 'void foo_bar(STRUCT_FOO_BAR*)':\n" +
      sourceFilePath + ":182:1: error: 'xyz' was not declared in this scope\n" +
      sourceFilePath + ": In function 'void foo_bar(STRUCT_FOO_BAR*)':\n" +
      sourceFilePath + ":190:1: warning: some random warning here\n" +
      "make: *** [obj/local/armeabi-v7a/objs/foo/src/bar.o] Error 1\n" +
      ":foolib:buildNative FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':foolib:buildNative'.\n" +
      "> Process 'command '/Users/tnorbye/dev/android-ndk-r9d/ndk-build'' finished with non-zero exit value 2\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 7.994 secs";

    assertEquals("0: Simple::foolib:mergeDebugProguardFiles UP-TO-DATE\n" +
                 "1: Simple::foolib:buildNative\n" +
                 "2: Simple:" + sourceFilePath + ": In function 'void foo_bar(STRUCT_FOO_BAR*)':\n" +
                 "3: Error:error: 'xyz' was not declared in this scope\n" +
                 "\t" + sourceFilePath + ":182:1\n" +
                 "4: Simple:" + sourceFilePath + ": In function 'void foo_bar(STRUCT_FOO_BAR*)':\n" +
                 "5: Warning:warning: some random warning here\n" +
                 "\t" + sourceFilePath + ":190:1\n" +
                 "6: Simple:make: *** [obj/local/armeabi-v7a/objs/foo/src/bar.o] Error 1\n" +
                 "7: Simple::foolib:buildNative FAILED\n" +
                 "8: Error:Execution failed for task ':foolib:buildNative'.\n" +
                 "> Process 'command '/Users/tnorbye/dev/android-ndk-r9d/ndk-build'' finished with non-zero exit value 2\n" +
                 "9: Info:BUILD FAILED\n" +
                 "10: Info:Total time: 7.994 secs\n",
                 toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }

  @Test
  public void wrongGradleVersion() throws Exception {
    // Wrong gradle
    createTempFile(DOT_GRADLE);
    String output =
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* Where:\n" +
      "Build file '" + sourceFilePath  +"' line: 24\n" +
      "\n" +
      "* What went wrong:\n" +
      "A problem occurred evaluating project ':MyApplication'.\n" +
      "> Gradle version 1.8 is required. Current version is 1.7. If using the gradle wrapper, try editing the distributionUrl in /some/path/gradle.properties to gradle-1.8-all.zip\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 4.467 secs";
    assertEquals("0: Error:A problem occurred evaluating project ':MyApplication'.\n" +
                 // TODO: Link to the gradle.properties file directly!
                 // However, we have an import hyperlink helper to do it automatically, so may not be necessary
                 "> Gradle version 1.8 is required. Current version is 1.7. If using the gradle wrapper, try editing the distributionUrl in /some/path/gradle.properties to gradle-1.8-all.zip\n" +
                 "\t" + sourceFilePath + ":24:1\n" +
                 "1: Info:BUILD FAILED\n" +
                 "2: Info:Total time: 4.467 secs\n",
                 toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }

  @Test
  public void androidPluginStructuredOutput() {
    String output =
      "WARNING|:project:app1|A minor warning\n" +
      "WARNING|:project:app2|A|minor|warning\n" +
      "ERROR|:project:libs:lib1|Serious error\n";
    assertEquals("0: Warning:A minor warning\n" +
                 ":project:app1\n" +
                 "1: Warning:A|minor|warning\n" +
                 ":project:app2\n" +
                 "2: Error:Serious error\n" +
                 ":project:libs:lib1\n",
                 toString(parser.parseGradleOutput(output)));
  }

  @Test
  public void newManifestMergeError() throws Exception {
    // Do not run tests on Windows (see http://b.android.com/222904)
    assumeFalse(SystemInfo.isWindows);

    createTempFile(DOT_XML);
    String output =
      sourceFilePath + ":50:4 Warning:\n" +
      "\tElement intent-filter#android.intent.action.VIEW+android.intent.category.DEFAULT at AndroidManifest.xml:50:4 duplicated with element declared at AndroidManifest.xml:45:4\n" +
      "C:\\foo:62:4 Error:\n" +
      "\tElement intent-filter#android.intent.action.VIEW+android.intent.category.DEFAULT at AndroidManifest.xml:62:4 duplicated with element declared at AndroidManifest.xml:50:4\n" +
      sourceFilePath + ":0:0 Error:\n" +
      "\tValidation failed, exiting\n";
    assertEquals("0: Warning:Element intent-filter#android.intent.action.VIEW+android.intent.category.DEFAULT at AndroidManifest.xml:50:4 duplicated with element declared at AndroidManifest.xml:45:4\n" +
                 "\t" + sourceFilePath + ":50:4\n" +
                 "1: Error:Element intent-filter#android.intent.action.VIEW+android.intent.category.DEFAULT at AndroidManifest.xml:62:4 duplicated with element declared at AndroidManifest.xml:50:4\n" +
                 "\tC:\\foo:62:4\n",
                 toString(parser.parseGradleOutput(output)));
  }

  @Test
  public void manifestMergeError() throws Exception {
    createTempFile(DOT_XML);
    String output =
      ":processFlavor1DebugManifest\n" +
      "[" + sourceFilePath + ":1] Could not find element /manifest/application.\n" +
      ":processFlavor1DebugManifest FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':processFlavor1DebugManifest'.\n" +
      "> Manifest merging failed. See console for more info.\n" +
      "\n" +
      "* Try:\n" +
      "Run with --info or --debug option to get more log output.\n";
    assertEquals("0: Simple::processFlavor1DebugManifest\n" +
                 "1: Error:Could not find element /manifest/application.\n" +
                 "\t" + sourceFilePath + ":1:-1\n" +
                 "2: Simple::processFlavor1DebugManifest FAILED\n" +
                 "3: Simple:FAILURE: Build failed with an exception.\n",
                 toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }

  @Test
  public void manifestMergeWindowsError() throws Exception {
    // Do not run tests on Windows (see http://b.android.com/222904)
    assumeFalse(SystemInfo.isWindows);

    createTempFile(DOT_XML);
    String output =
      ":processFlavor1DebugManifest\n" +
      "[C:\\Users\\Android\\AppData\\Local\\Temp\\com.android.tools.idea.gradle.output.parser.GradleErrorOutputParserTest4437574780178007978.xml:1] Could not find element /manifest/application.\n" +
      ":processFlavor1DebugManifest FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':processFlavor1DebugManifest'.\n" +
      "> Manifest merging failed. See console for more info.\n" +
      "\n" +
      "* Try:\n" +
      "Run with --info or --debug option to get more log output.\n";
    assertEquals("0: Simple::processFlavor1DebugManifest\n" +
                 "1: Error:Could not find element /manifest/application.\n" +
                 "\tC:\\Users\\Android\\AppData\\Local\\Temp\\com.android.tools.idea.gradle.output.parser.GradleErrorOutputParserTest4437574780178007978.xml:1:-1\n" +
                 "2: Simple::processFlavor1DebugManifest FAILED\n" +
                 "3: Simple:FAILURE: Build failed with an exception.\n",
                 toString(parser.parseGradleOutput(output)));
    sourceFile.delete();
  }

  @Test
  public void dexDuplicateClassException() throws Exception {
    String output =
      ":two:dexDebug\n" +
      "UNEXPECTED TOP-LEVEL EXCEPTION:\n" +
      "java.lang.IllegalArgumentException: already added: Lcom/example/two/MainActivity;\n" +
      "\tat com.android.dx.dex.file.ClassDefsSection.add(ClassDefsSection.java:122)\n" +
      "\tat com.android.dx.dex.file.DexFile.add(DexFile.java:161)\n" +
      "\tat com.android.dx.command.dexer.Main.processClass(Main.java:685)\n" +
      "\tat com.android.dx.command.dexer.Main.processFileBytes(Main.java:634)\n" +
      "\tat com.android.dx.command.dexer.Main.access$600(Main.java:78)\n" +
      "\tat com.android.dx.command.dexer.Main$1.processFileBytes(Main.java:572)\n" +
      "\tat com.android.dx.cf.direct.ClassPathOpener.processArchive(ClassPathOpener.java:284)\n" +
      "\tat com.android.dx.cf.direct.ClassPathOpener.processOne(ClassPathOpener.java:166)\n" +
      "\tat com.android.dx.cf.direct.ClassPathOpener.process(ClassPathOpener.java:144)\n" +
      "\tat com.android.dx.command.dexer.Main.processOne(Main.java:596)\n" +
      "\tat com.android.dx.command.dexer.Main.processAllFiles(Main.java:498)\n" +
      "\tat com.android.dx.command.dexer.Main.runMonoDex(Main.java:264)\n" +
      "\tat com.android.dx.command.dexer.Main.run(Main.java:230)\n" +
      "\tat com.android.dx.command.dexer.Main.main(Main.java:199)\n" +
      "\tat com.android.dx.command.Main.main(Main.java:103)\n" +
      "1 error; aborting\n" +
      " FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':two:dexDebug'.\n" +
      "> Could not call IncrementalTask.taskAction() on task ':two:dexDebug'\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 9.491 secs";
    assertEquals("0: Simple::two:dexDebug\n" +
                 "1: Error:Class com.example.two.MainActivity has already been added to output. Please remove duplicate copies.\n" +
                 "2: Simple:1 error; aborting\n" +
                 "3: Error:Execution failed for task ':two:dexDebug'.\n" +
                 "> Could not call IncrementalTask.taskAction() on task ':two:dexDebug'\n" +
                 "4: Info:BUILD FAILED\n" +
                 "5: Info:Total time: 9.491 secs\n",
                 toString(parser.parseGradleOutput(output)));
  }

  @Test
  public void multilineCompileError() throws Exception {
    createTempFile(DOT_JAVA);
    String output =
      ":two:compileDebug\n" +
      sourceFilePath + ":20: incompatible types\n" +
      "found   : java.util.ArrayList<java.lang.String>\n" +
      "required: java.util.Set<java.lang.String>\n" +
      "        Set<String> checkedList = new ArrayList<String>();\n" +
      "                                  ^\n" +
      "1 error\n" +
      ":two:compileDebug FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':two:compileDebug'.\n" +
      "> Compilation failed; see the compiler error output for details.\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 5.354 secs\n";
    assertEquals("0: Simple::two:compileDebug\n" +
                 "1: Error:incompatible types\n" +
                 "found   : java.util.ArrayList<java.lang.String>\n" +
                 "required: java.util.Set<java.lang.String>\n" +
                 "\t" + sourceFilePath + ":20:35\n" +
                 "2: Simple::two:compileDebug FAILED\n" +
                 "3: Error:Execution failed for task ':two:compileDebug'.\n" +
                 "> Compilation failed; see the compiler error output for details.\n" +
                 "4: Info:BUILD FAILED\n" +
                 "5: Info:Total time: 5.354 secs\n",
                 toString(parser.parseGradleOutput(output)).replaceAll("\r\n","\n"));
    sourceFile.delete();
  }

  @Test
  public void rewriteManifestPaths1() throws Exception {
    File tempDir = Files.createTempDir();
    sourceFile = new File(tempDir, ANDROID_MANIFEST_XML);
    sourceFilePath = sourceFile.getAbsolutePath();
    File source = new File(tempDir, "real-manifest.xml");
    Files.write("<<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    package=\"com.example.app\"\n" +
                "    android:versionCode=\"1\"\n" +
                "    android:versionName=\"1.0\" >\n" +
                "\n" +
                "    <uses-sdk\n" +
                "        android:minSdkVersion=\"7\"\n" +
                "        android:targetSdkVersion=\"19\" />\n" +
                "\n" +
                "    <application\n" +
                "        android:allowBackup=\"true\"\n" +
                "        android:icon=\"@drawable/ic_xlauncher\"\n" +
                "        android:label=\"@string/app_name\"\n" +
                "        android:theme=\"@style/AppTheme\" >\n" +
                "        <activity\n" +
                "            android:name=\"com.example.app.MainActivity\"\n" +
                "            android:label=\"@string/app_name\" >\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                "\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                "            </intent-filter>\n" +
                "        </activity>\n" +
                "    </application>\n" +
                "\n" +
                "</manifest>\n", source, Charsets.UTF_8);
    source.deleteOnExit();
    Files.write("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" android:versionCode=\"1\" android:versionName=\"1.0\" package=\"com.example.app\">\n" +
                "\n" +
                "    <!-- " + createPathComment(source, false) + " -->\n" +
                "\n" +
                "    <uses-sdk android:minSdkVersion=\"7\" android:targetSdkVersion=\"19\"/>\n" +
                "\n" +
                "    <application android:allowBackup=\"true\" android:icon=\"@drawable/ic_xlauncher\" android:label=\"@string/app_name\" android:theme=\"@style/AppTheme\">\n" +
                "        <activity android:label=\"@string/app_name\" android:name=\"com.example.app.MainActivity\">\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\"/>\n" +
                "\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\"/>\n" +
                "            </intent-filter>\n" +
                "        </activity>\n" +
                "    </application>\n" +
                "    <!-- From: /path/to/App/src/debug/AndroidManifest.xml -->\n" +
                "    <uses-permission android:name=\"android.permission.ACCESS_MOCK_LOCATION\"/>\n" +
                "\n" +
                "    <!-- " + createPathComment(source, false) + " -->\n" +
                "    </manifest>", sourceFile, Charsets.UTF_8);

    String output =
      "Relying on packaging to define the extension of the main artifact has been deprecated and is scheduled to be removed in Gradle 2.0\n" +
      ":App:compileDefaultFlavorDebugNdk UP-TO-DATE\n" +
      ":App:preBuild UP-TO-DATE\n" +
      ":App:preDefaultFlavorDebugBuild UP-TO-DATE\n" +
      ":App:preDefaultFlavorReleaseBuild UP-TO-DATE\n" +
      ":App:prepareComAndroidSupportAppcompatV71900Library UP-TO-DATE\n" +
      ":App:prepareDefaultFlavorDebugDependencies\n" +
      ":App:compileDefaultFlavorDebugAidl UP-TO-DATE\n" +
      ":App:compileDefaultFlavorDebugRenderscript UP-TO-DATE\n" +
      ":App:generateDefaultFlavorDebugBuildConfig UP-TO-DATE\n" +
      ":App:mergeDefaultFlavorDebugAssets UP-TO-DATE\n" +
      ":App:mergeDefaultFlavorDebugResources UP-TO-DATE\n" +
      ":App:processDefaultFlavorDebugManifest UP-TO-DATE\n" +
      ":App:processDefaultFlavorDebugResources\n" +
      sourceFilePath + ":7: error: Error: No resource found that matches the given name (at 'icon' with value '@drawable/ic_xlauncher').\n" +
      ":App:processDefaultFlavorDebugResources FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':App:processDefaultFlavorDebugResources'.\n" +
      "> com.android.ide.common.internal.LoggedErrorException: Failed to run command:\n" +
      "  \tSDK/build-tools/android-4.4/aapt package -f --no-crunch " +
      "-I SDK/platforms/android-19/android.jar " +
      "-M PROJECT/App/build/manifests/defaultFlavor/debug/AndroidManifest.xml " +
      "-S PROJECT/App/build/res/all/defaultFlavor/debug " +
      "-A PROJECT/App/build/assets/defaultFlavor/debug " +
      "-m -J PROJECT/App/build/source/r/defaultFlavor/debug " +
      "-F PROJECT/App/build/libs/App-defaultFlavor-debug.ap_ " +
      "--debug-mode --custom-package com.example.app " +
      "--output-text-symbols PROJECT/App/build/symbols/defaultFlavor/debug\n" +
      "  Error Code:\n" +
      "  \t1\n" +
      "  Output:\n" +
      "  \t" + sourceFilePath + ":7: error: Error: No resource found that matches the given name (at 'icon' with value '@drawable/ic_xlauncher').\n" +
      "\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 7.024 secs";

    assertEquals("0: Simple::App:compileDefaultFlavorDebugNdk UP-TO-DATE\n" +
                 "1: Simple::App:preBuild UP-TO-DATE\n" +
                 "2: Simple::App:preDefaultFlavorDebugBuild UP-TO-DATE\n" +
                 "3: Simple::App:preDefaultFlavorReleaseBuild UP-TO-DATE\n" +
                 "4: Simple::App:prepareComAndroidSupportAppcompatV71900Library UP-TO-DATE\n" +
                 "5: Simple::App:prepareDefaultFlavorDebugDependencies\n" +
                 "6: Simple::App:compileDefaultFlavorDebugAidl UP-TO-DATE\n" +
                 "7: Simple::App:compileDefaultFlavorDebugRenderscript UP-TO-DATE\n" +
                 "8: Simple::App:generateDefaultFlavorDebugBuildConfig UP-TO-DATE\n" +
                 "9: Simple::App:mergeDefaultFlavorDebugAssets UP-TO-DATE\n" +
                 "10: Simple::App:mergeDefaultFlavorDebugResources UP-TO-DATE\n" +
                 "11: Simple::App:processDefaultFlavorDebugManifest UP-TO-DATE\n" +
                 "12: Simple::App:processDefaultFlavorDebugResources\n" +
                 "13: Error:No resource found that matches the given name (at 'icon' with value '@drawable/ic_xlauncher').\n" +
                 "\t" + source.getPath() + ":13:23\n" +
                 "14: Simple::App:processDefaultFlavorDebugResources FAILED\n" +
                 "15: Error:Execution failed for task ':App:processDefaultFlavorDebugResources'.\n" +
                 "> com.android.ide.common.internal.LoggedErrorException: Failed to run command:\n" +
                 "  \tSDK/build-tools/android-4.4/aapt package -f --no-crunch -I SDK/platforms/android-19/android.jar" +
                 " -M PROJECT/App/build/manifests/defaultFlavor/debug/AndroidManifest.xml" +
                 " -S PROJECT/App/build/res/all/defaultFlavor/debug" +
                 " -A PROJECT/App/build/assets/defaultFlavor/debug" +
                 " -m -J PROJECT/App/build/source/r/defaultFlavor/debug" +
                 " -F PROJECT/App/build/libs/App-defaultFlavor-debug.ap_" +
                 " --debug-mode --custom-package com.example.app" +
                 " --output-text-symbols PROJECT/App/build/symbols/defaultFlavor/debug\n" +
                 "  Error Code:\n" +
                 "  \t1\n" +
                 "  Output:\n" +
                 "  \t" + sourceFilePath + ":7: error: Error: No resource found that matches the given name (at 'icon' with value '@drawable/ic_xlauncher').\n" +
                 "16: Info:BUILD FAILED\n" +
                 "17: Info:Total time: 7.024 secs\n",
                 toString(parser.parseGradleOutput(output)));

    sourceFile.delete();
    source.delete();
    tempDir.delete();
  }

  @Test
  public void rewriteManifestPaths2() throws Exception {
    // Like testRewriteManifestPaths1, but with a source comment at the end of the file
    // (which happens when there is only a single manifest, so no files are merged and it's a straight
    // file copy in the gradle plugin)
    File tempDir = Files.createTempDir();
    sourceFile = new File(tempDir, ANDROID_MANIFEST_XML);
    sourceFilePath = sourceFile.getAbsolutePath();
    File source = new File(tempDir, "real-manifest.xml");
    Files.write("<<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    package=\"com.example.app\"\n" +
                "    android:versionCode=\"1\"\n" +
                "    android:versionName=\"1.0\" >\n" +
                "\n" +
                "    <uses-sdk\n" +
                "        android:minSdkVersion=\"7\"\n" +
                "        android:targetSdkVersion=\"19\" />\n" +
                "\n" +
                "    <application\n" +
                "        android:allowBackup=\"true\"\n" +
                "        android:icon=\"@drawable/ic_xlauncher\"\n" +
                "        android:label=\"@string/app_name\"\n" +
                "        android:theme=\"@style/AppTheme\" >\n" +
                "        <activity\n" +
                "            android:name=\"com.example.app.MainActivity\"\n" +
                "            android:label=\"@string/app_name\" >\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                "\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                "            </intent-filter>\n" +
                "        </activity>\n" +
                "    </application>\n" +
                "\n" +
                "</manifest>\n", source, Charsets.UTF_8);
    source.deleteOnExit();
    Files.write("<<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    package=\"com.example.app\"\n" +
                "    android:versionCode=\"1\"\n" +
                "    android:versionName=\"1.0\" >\n" +
                "\n" +
                "    <uses-sdk\n" +
                "        android:minSdkVersion=\"7\"\n" +
                "        android:targetSdkVersion=\"19\" />\n" +
                "\n" +
                "    <application\n" +
                "        android:allowBackup=\"true\"\n" +
                "        android:icon=\"@drawable/ic_xlauncher\"\n" +
                "        android:label=\"@string/app_name\"\n" +
                "        android:theme=\"@style/AppTheme\" >\n" +
                "        <activity\n" +
                "            android:name=\"com.example.app.MainActivity\"\n" +
                "            android:label=\"@string/app_name\" >\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                "\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                "            </intent-filter>\n" +
                "        </activity>\n" +
                "    </application>\n" +
                "\n" +
                "</manifest><!-- " + createPathComment(source, false) + " -->", sourceFile, Charsets.UTF_8);

    String output =
      "Relying on packaging to define the extension of the main artifact has been deprecated and is scheduled to be removed in Gradle 2.0\n" +
      ":App:compileDefaultFlavorDebugNdk UP-TO-DATE\n" +
      ":App:preBuild UP-TO-DATE\n" +
      ":App:preDefaultFlavorDebugBuild UP-TO-DATE\n" +
      ":App:preDefaultFlavorReleaseBuild UP-TO-DATE\n" +
      ":App:prepareComAndroidSupportAppcompatV71900Library UP-TO-DATE\n" +
      ":App:prepareDefaultFlavorDebugDependencies\n" +
      ":App:compileDefaultFlavorDebugAidl UP-TO-DATE\n" +
      ":App:compileDefaultFlavorDebugRenderscript UP-TO-DATE\n" +
      ":App:generateDefaultFlavorDebugBuildConfig UP-TO-DATE\n" +
      ":App:mergeDefaultFlavorDebugAssets UP-TO-DATE\n" +
      ":App:mergeDefaultFlavorDebugResources UP-TO-DATE\n" +
      ":App:processDefaultFlavorDebugManifest UP-TO-DATE\n" +
      ":App:processDefaultFlavorDebugResources\n" +
      sourceFilePath + ":7: error: Error: No resource found that matches the given name (at 'icon' with value '@drawable/ic_xlauncher').\n" +
      ":App:processDefaultFlavorDebugResources FAILED\n" +
      "\n" +
      "FAILURE: Build failed with an exception.\n" +
      "\n" +
      "* What went wrong:\n" +
      "Execution failed for task ':App:processDefaultFlavorDebugResources'.\n" +
      "> com.android.ide.common.internal.LoggedErrorException: Failed to run command:\n" +
      "  \tSDK/build-tools/android-4.4/aapt package -f --no-crunch " +
      "-I SDK/platforms/android-19/android.jar " +
      "-M PROJECT/App/build/manifests/defaultFlavor/debug/AndroidManifest.xml " +
      "-S PROJECT/App/build/res/all/defaultFlavor/debug " +
      "-A PROJECT/App/build/assets/defaultFlavor/debug " +
      "-m -J PROJECT/App/build/source/r/defaultFlavor/debug " +
      "-F PROJECT/App/build/libs/App-defaultFlavor-debug.ap_ " +
      "--debug-mode --custom-package com.example.app " +
      "--output-text-symbols PROJECT/App/build/symbols/defaultFlavor/debug\n" +
      "  Error Code:\n" +
      "  \t1\n" +
      "  Output:\n" +
      "  \t" + sourceFilePath + ":7: error: Error: No resource found that matches the given name (at 'icon' with value '@drawable/ic_xlauncher').\n" +
      "\n" +
      "\n" +
      "* Try:\n" +
      "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.\n" +
      "\n" +
      "BUILD FAILED\n" +
      "\n" +
      "Total time: 7.024 secs";

    assertEquals("0: Simple::App:compileDefaultFlavorDebugNdk UP-TO-DATE\n" +
                 "1: Simple::App:preBuild UP-TO-DATE\n" +
                 "2: Simple::App:preDefaultFlavorDebugBuild UP-TO-DATE\n" +
                 "3: Simple::App:preDefaultFlavorReleaseBuild UP-TO-DATE\n" +
                 "4: Simple::App:prepareComAndroidSupportAppcompatV71900Library UP-TO-DATE\n" +
                 "5: Simple::App:prepareDefaultFlavorDebugDependencies\n" +
                 "6: Simple::App:compileDefaultFlavorDebugAidl UP-TO-DATE\n" +
                 "7: Simple::App:compileDefaultFlavorDebugRenderscript UP-TO-DATE\n" +
                 "8: Simple::App:generateDefaultFlavorDebugBuildConfig UP-TO-DATE\n" +
                 "9: Simple::App:mergeDefaultFlavorDebugAssets UP-TO-DATE\n" +
                 "10: Simple::App:mergeDefaultFlavorDebugResources UP-TO-DATE\n" +
                 "11: Simple::App:processDefaultFlavorDebugManifest UP-TO-DATE\n" +
                 "12: Simple::App:processDefaultFlavorDebugResources\n" +
                 "13: Error:No resource found that matches the given name (at 'icon' with value '@drawable/ic_xlauncher').\n" +
                 "\t" + source.getPath() + ":13:23\n" +
                 "14: Simple::App:processDefaultFlavorDebugResources FAILED\n" +
                 "15: Error:Execution failed for task ':App:processDefaultFlavorDebugResources'.\n" +
                 "> com.android.ide.common.internal.LoggedErrorException: Failed to run command:\n" +
                 "  \tSDK/build-tools/android-4.4/aapt package -f --no-crunch -I SDK/platforms/android-19/android.jar" +
                 " -M PROJECT/App/build/manifests/defaultFlavor/debug/AndroidManifest.xml" +
                 " -S PROJECT/App/build/res/all/defaultFlavor/debug" +
                 " -A PROJECT/App/build/assets/defaultFlavor/debug" +
                 " -m -J PROJECT/App/build/source/r/defaultFlavor/debug" +
                 " -F PROJECT/App/build/libs/App-defaultFlavor-debug.ap_" +
                 " --debug-mode --custom-package com.example.app" +
                 " --output-text-symbols PROJECT/App/build/symbols/defaultFlavor/debug\n" +
                 "  Error Code:\n" +
                 "  \t1\n" +
                 "  Output:\n" +
                 "  \t" + sourceFilePath + ":7: error: Error: No resource found that matches the given name (at 'icon' with value '@drawable/ic_xlauncher').\n" +
                 "16: Info:BUILD FAILED\n" +
                 "17: Info:Total time: 7.024 secs\n",
                 toString(parser.parseGradleOutput(output)));

    sourceFile.delete();
    source.delete();
    tempDir.delete();
  }

  @Test
  public void withInfoMessage() {
    String output = "21:12:17.771 [INFO] [org.gradle.api.internal.artifacts.ivyservice.IvyLoggingAdaper] setting 'http.nonProxyHosts' to 'local|*.local|169.254/16|*.169.254/16'";
    List<Message> Messages = parser.parseGradleOutput(output);
    assertEquals(1, Messages.size());
    Message message = Messages.get(0);
    assertEquals(output, message.getText());
    assertEquals(Message.Kind.SIMPLE, message.getKind());
  }

  @Test
  public void withDebugMessage() {
    String output = "21:12:17.771 [DEBUG] [org.gradle.api.internal.artifacts.ivyservice.IvyLoggingAdaper] setting 'http.nonProxyHosts' to 'local|*.local|169.254/16|*.169.254/16'";
    List<Message> Messages = parser.parseGradleOutput(output);
    assertEquals(1, Messages.size());
    Message message = Messages.get(0);
    assertEquals(output, message.getText());
    assertEquals(Message.Kind.SIMPLE, message.getKind());
  }

  @Test
  public void withAndroidConfigChanges() {
    String output = "128            android:configChanges=\"orientation|keyboardHidden|keyboard|screenSize\"";
    List<Message> Messages = parser.parseGradleOutput(output);
    assertEquals(1, Messages.size());
    Message message = Messages.get(0);
    assertEquals(output, message.getText());
    assertEquals(Message.Kind.SIMPLE, message.getKind());
  }

  @Test
  public void ignoringUnrecognizedText() {
    String output = " **--- HELLO WORLD ---**";
    List<Message> Messages = parser.parseGradleOutput(output, true);
    assertEquals(0, Messages.size());
  }

  private static final String WINDOWS_PATH_DRIVE_LETTER = "C:";
  private static final String WINDOWS_PATH_UNDER_UNIX = new File(WINDOWS_PATH_DRIVE_LETTER).getAbsolutePath();
  private static final int WINDOWS_PATH_UNIX_PREFIX_LENGTH = WINDOWS_PATH_UNDER_UNIX.length() - WINDOWS_PATH_DRIVE_LETTER.length();

  @NotNull
  private static String toString(@NotNull List<Message> messages) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0, n = messages.size(); i < n; i++) {
      Message message = messages.get(i);
      sb.append(Integer.toString(i)).append(':').append(' ');
      sb.append(StringUtil.capitalize(message.getKind().toString().toLowerCase(Locale.US))).append(':'); // INFO => Info
      sb.append(message.getText());
      if (message.getSourcePath() != null) {
        for (SourceFilePosition position: message.getSourceFilePositions()) {
          sb.append('\n');
          sb.append('\t');
          // Fudge for windows tests. As messages use a file object, which is filesystem aware, Windows paths come out prefaced with the
          // unix CWD.
          String path = position.getFile().toString();
          if (path.startsWith(WINDOWS_PATH_UNDER_UNIX)) {
            path = path.substring(WINDOWS_PATH_UNIX_PREFIX_LENGTH);
          }
          sb.append(path);
          int line = position.getPosition().getStartLine();
          sb.append(':').append(Integer.toString(line == -1 ? -1 : line + 1));
          int col = position.getPosition().getStartColumn();
          sb.append(':').append(Integer.toString(col == -1 ? -1 : col + 1));
        }
      } else {
        String gradlePath = message.getSourceFilePositions().get(0).getFile().getDescription();
        if (gradlePath != null) {
          sb.append('\n');
          sb.append(gradlePath);
        }
      }
      sb.append('\n');
    }

    return sb.toString();
  }
}
