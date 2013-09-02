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
package com.android.tools.idea.jps.output.parser;

import com.android.tools.idea.jps.output.parser.aapt.AbstractAaptOutputParser;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Tests for {@link GradleErrorOutputParser}.
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "StringBufferReplaceableByString"})
public class GradleErrorOutputParserTest extends TestCase {
  private static final String NEWLINE = SystemProperties.getLineSeparator();

  private File sourceFile;
  private String sourceFilePath;
  private GradleErrorOutputParser parser;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    parser = new GradleErrorOutputParser();
  }

  @Override
  public void tearDown() throws Exception {
    if (sourceFile != null) {
      FileUtil.delete(sourceFile);
    }
    super.tearDown();
  }

  public void testParseAaptOutputWithRange1() throws IOException {
    createTempXmlFile();
    writeToFile("<manifest xmlns:android='http://schemas.android.com/apk/res/android'",
                "    android:versionCode='12' android:versionName='2.0' package='com.android.tests.basic'>",
                "  <uses-sdk android:minSdkVersion='16' android:targetSdkVersion='16'/>",
                "  <application android:icon='@drawable/icon' android:label='@string/app_name2'>");
    String messageText = "No resource found that matches the given name (at 'label' with value " + "'@string/app_name2').";
    String err = sourceFilePath + ":4: error: Error: " + messageText;
    Collection<CompilerMessage> messages = parser.parseErrorOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 4, 61);
  }

  public void testParseAaptOutputWithRange2() throws IOException {
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
    Collection<CompilerMessage> messages = parser.parseErrorOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 5, 8);
  }

  public void testParseAaptOutputWithRange3() throws IOException {
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
    Collection<CompilerMessage> messages = parser.parseErrorOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 6, 17);
  }

  public void testParseAaptOutputWithRange4() throws IOException {
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
    Collection<CompilerMessage> messages = parser.parseErrorOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 3, 5);
  }

  public void testParseAaptOutputWithRange5() throws IOException {
    // Check for aapt error which occurs when the attribute name in an item style declaration is
    // non-existent.
    createTempXmlFile();
    writeToFile("<resources xmlns:android='http://schemas.android.com/apk/res/android'>",
                "  <style name='wrongAttribute'>",
                "    <item name='nonexistent'>left</item>");
    String messageText = "No resource found that matches the given name: attr 'nonexistent'.";
    String err = sourceFilePath + ":3: error: Error: " + messageText;
    Collection<CompilerMessage> messages = parser.parseErrorOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 3, 17);
  }

  public void testParseAaptOutputWithRange6() throws IOException {
    // Test missing resource name.
    createTempXmlFile();
    writeToFile("<resources xmlns:android='http://schemas.android.com/apk/res/android'>",
                "  <style>");
    String messageText = "A 'name' attribute is required for <style>";
    String err = sourceFilePath + ":2: error: " + messageText;
    Collection<CompilerMessage> messages = parser.parseErrorOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 2, 3);
  }

  public void testParseAaptOutputWithRange7() throws IOException {
    createTempXmlFile();
    writeToFile("<resources xmlns:android='http://schemas.android.com/apk/res/android'>",
                "  <item>");
    String messageText = "A 'type' attribute is required for <item>";
    String err = sourceFilePath + ":2: error: " + messageText;
    Collection<CompilerMessage> messages = parser.parseErrorOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 2, 3);
  }

  public void testParseAaptOutputWithRange8() throws IOException {
    createTempXmlFile();
    writeToFile("<resources xmlns:android='http://schemas.android.com/apk/res/android'>",
                "  <item>");
    String messageText = "A 'name' attribute is required for <item>";
    String err = sourceFilePath + ":2: error: " + messageText;
    Collection<CompilerMessage> messages = parser.parseErrorOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 2, 3);
  }

  public void testParseAaptOutputWithRange9() throws IOException {
    createTempXmlFile();
    writeToFile("<resources xmlns:android='http://schemas.android.com/apk/res/android'>",
                "  <style name='test'>",
                "        <item name='android:layout_width'></item>");
    String messageText = "String types not allowed (at 'android:layout_width' with value '').";
    String err = sourceFilePath + ":3: error: " + messageText;
    Collection<CompilerMessage> messages = parser.parseErrorOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 3, 21);
  }

  public void testParseAaptOutputWithRange10() throws IOException {
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
    Collection<CompilerMessage> messages = parser.parseErrorOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 8, 34);
  }

  public void testParseAaptOutputWithRange11() throws IOException {
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
    Collection<CompilerMessage> messages = parser.parseErrorOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 9, 35);
  }

  public void testParseAaptOutputWithRange12() throws IOException {
    createTempXmlFile();
    writeToFile("<FrameLayout",
                "    xmlns:android='http://schemas.android.com/apk/res/android'",
                "    android:layout_width='wrap_content'",
                "    android:layout_height='match_parent'>",
                "    <TextView",
                "        android:id=''");
    String messageText = "String types not allowed (at 'id' with value '').";
    String err = sourceFilePath + ":5: error: Error: " + messageText;
    Collection<CompilerMessage> messages = parser.parseErrorOutput(err);
    assertHasCorrectErrorMessage(messages, messageText, 6, 20);
  }

  private void createTempXmlFile() throws IOException {
    createTempFile(".xml");
  }

  public void testParseJavaOutput1() throws IOException {
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
    Collection<CompilerMessage> messages = parser.parseErrorOutput(err.toString());
    assertHasCorrectErrorMessage(messages, "error: cannot find symbol variable v4", 3, 14);
  }

  public void testParseJavaOutput2() throws IOException {
    createTempFile(".java");
    writeToFile("public class Test {",
                "  public static void main(String[] args) {",
                "    System.out.println();asd");
    StringBuilder err = new StringBuilder();
    err.append(sourceFilePath).append(":3: error: ").append("not a statement").append(NEWLINE)
       .append("    System.out.println();asd").append(NEWLINE)
       .append("                         ^").append(NEWLINE);
    Collection<CompilerMessage> messages = parser.parseErrorOutput(err.toString());
    assertHasCorrectErrorMessage(messages, "error: not a statement", 3, 26);
  }

  public void testParseGradleBuildFileOutput() throws IOException {
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
    List<CompilerMessage> messages = parser.parseErrorOutput(err.toString());

    assertEquals("0: Gradle:Error:A problem occurred evaluating project ':project'.\n" +
                 "> Could not find method ERROR() for arguments [{plugin=android}] on project ':project'.\n" +
                 "\t" + sourceFilePath + ":9:0\n" +
                 "1: Info:BUILD FAILED\n" +
                 "2: Info:Total time: 18.303 secs\n",
                 toString(messages));
  }

  public void testParseXmlValidationErrorOutput() {
    StringBuilder err = new StringBuilder();
    err.append("[Fatal Error] :5:7: The element type \"error\" must be terminated by the matching end-tag \"</error>\".").append(NEWLINE);
    err.append("FAILURE: Build failed with an exception.").append(NEWLINE);
    List<CompilerMessage> messages = parser.parseErrorOutput(err.toString());

    assertEquals("0: Gradle:Error:The element type \"error\" must be terminated by the matching end-tag \"</error>\".\n" +
                 "1: Info:FAILURE: Build failed with an exception.\n",
                 toString(messages));
  }

  private void createTempFile(String fileExtension) throws IOException {
    sourceFile = File.createTempFile(GradleErrorOutputParserTest.class.getName(), fileExtension);
    sourceFilePath = sourceFile.getAbsolutePath();
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private void writeToFile(String... lines) throws IOException {
    BufferedWriter out = null;
    try {
      out = new BufferedWriter(new FileWriter(sourceFile));
      for (String line : lines) {
        out.write(line);
        out.newLine();
      }
    }
    finally {
      Closeables.closeQuietly(out);
    }
  }

  private void assertHasCorrectErrorMessage(Collection<CompilerMessage> messages,
                                            String expectedText,
                                            long expectedLine,
                                            long expectedColumn) {
    assertEquals("[message count]", 1, messages.size());
    CompilerMessage message = ContainerUtil.getFirstItem(messages);
    assertNotNull(message);
    assertEquals("[file path]", sourceFilePath, message.getSourcePath());
    assertEquals("[message severity]", BuildMessage.Kind.ERROR, message.getKind());
    assertEquals("[message text]", expectedText, message.getMessageText());
    assertEquals("[position line]", expectedLine, message.getLine());
    assertEquals("[position column]", expectedColumn, message.getColumn());
  }

  private static String toString(List<CompilerMessage> messages) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0, n = messages.size(); i < n; i++) {
      CompilerMessage message = messages.get(i);
      sb.append(Integer.toString(i)).append(':').append(' ');
      if (!message.getCompilerName().isEmpty()) {
        sb.append(message.getCompilerName()).append(':');
      }
      sb.append(StringUtil.capitalize(message.getKind().toString().toLowerCase(Locale.US))).append(':'); // INFO => Info
      sb.append(message.getMessageText());
      if (message.getSourcePath() != null) {
        sb.append('\n');
        sb.append('\t');
        sb.append(message.getSourcePath());
        sb.append(':').append(Long.toString(message.getLine()));
        sb.append(':').append(Long.toString(message.getColumn()));
      }
      sb.append('\n');
    }

    return sb.toString();
  }

  public void testRedirectValueLinksOutput() throws Exception {
    String homePath = PathManager.getHomePath();
    assertNotNull(homePath);
    // The relative paths in the output file below is relative to the sdk-common directory in tools/base
    // (it's from one of the unit tests there)
    AbstractAaptOutputParser.ourRootDir = new File(homePath, ".." + File.separator + "base" + File.separator + "sdk-common");

    // Need file to be named (exactly) values.xml
    File tempDir = Files.createTempDir();
    File valueDir = new File(tempDir, "values-en");
    valueDir.mkdirs();
    sourceFile = new File(valueDir, "values.xml"); // Keep in sync with MergedResourceWriter.FN_VALUES_XML
    sourceFilePath = sourceFile.getAbsolutePath();

    writeToFile(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<resources xmlns:ns1=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
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
    Collection<CompilerMessage> messages = parser.parseErrorOutput(err);
    assertEquals(1, messages.size());

    assertEquals("[message count]", 1, messages.size());
    CompilerMessage message = ContainerUtil.getFirstItem(messages);
    assertNotNull(message);

    // NOT sourceFilePath; should be translated back from source comment
    assertEquals("[file path]", "src/test/resources/testData/resources/baseSet/values/values.xml", message.getSourcePath());

    assertEquals("[message severity]", BuildMessage.Kind.ERROR, message.getKind());
    assertEquals("[message text]", messageText, message.getMessageText());
    assertEquals("[position line]", 9, message.getLine());
    assertEquals("[position column]", 35, message.getColumn());
  }

  public void testRedirectFileLinksOutput() throws Exception {
    String homePath = PathManager.getHomePath();
    assertNotNull(homePath);
    // The relative paths in the output file below is relative to the sdk-common directory in tools/base
    // (it's from one of the unit tests there)
    AbstractAaptOutputParser.ourRootDir = new File(homePath, ".." + File.separator + "base" + File.separator + "sdk-common");

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
      "<!-- From: src/test/resources/testData/resources/incMergeData/filesVsValues/main/layout/main.xml -->");

    String messageText = "Random error message here";
    String err = sourceFilePath + ":4: error: Error: " + messageText;
    Collection<CompilerMessage> messages = parser.parseErrorOutput(err);
    assertEquals(1, messages.size());

    assertEquals("[message count]", 1, messages.size());
    CompilerMessage message = ContainerUtil.getFirstItem(messages);
    assertNotNull(message);

    // NOT sourceFilePath; should be translated back from source comment
    assertEquals("[file path]", "src/test/resources/testData/resources/incMergeData/filesVsValues/main/layout/main.xml", message.getSourcePath());

    assertEquals("[message severity]", BuildMessage.Kind.ERROR, message.getKind());
    assertEquals("[message text]", messageText, message.getMessageText());
    assertEquals("[position line]", 4, message.getLine());
    //assertEquals("[position column]", 35, message.getColumn());

    // TODO: Test encoding issues (e.g. & in path where the XML source comment would be &amp; instead)
  }

  public void testGradleAaptErrorParser() throws IOException {
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
                 "  \t/Applications/Android Studio.app/sdk/build-tools/android-4.2.2/aapt package -f --no-crunch -I /Applications/Android Studio.app/sdk/platforms/android-17/android.jar -M /Users/sbarta/AndroidStudioProjects/fiveProject/five/build/manifests/debug/AndroidManifest.xml -S /Users/sbarta/AndroidStudioProjects/fiveProject/five/build/res/all/debug -A /Users/sbarta/AndroidStudioProjects/fiveProject/five/build/assets/debug -m -J /Users/sbarta/AndroidStudioProjects/fiveProject/five/build/source/r/debug -F /Users/sbarta/AndroidStudioProjects/fiveProject/five/build/libs/five-debug.ap_ --debug-mode --custom-package com.example.five\n" +
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

    assertEquals("0: Gradle:Error:Error while executing aapt command\n" +
                 "1: Gradle:Error:No resource found that matches the given name (at 'text' with value '@string/does_not_exist').\n" +
                 "\t" + sourceFilePath + ":5:27\n" +
                 "2: Gradle:Error:Execution failed for task ':five:processDebugResources'.\n" +
                 "3: Info:BUILD FAILED\n",
                 toString(parser.parseErrorOutput(err)));
  }

  public void testDuplicateResources() throws Exception {
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

    assertEquals("0: Gradle:Error:Found item String/drawer_open more than one time: Failed to parse " + sourceFilePath + "\n" +
                 "\t" + sourceFilePath + ":-1:-1\n" +
                 "1: Gradle:Error:Execution failed for task ':MyApp:mergeDebugResources'.\n" +
                 "> Found item String/drawer_open more than one time\n" +
                 "\t" + sourceFilePath + ":-1:-1\n",
                 toString(parser.parseErrorOutput(output)));

    // Also test CRLF handling:
    output = output.replace("\n", "\r\n");
    assertEquals("0: Gradle:Error:Found item String/drawer_open more than one time: Failed to parse " + sourceFilePath + "\n" +
                 "\t" + sourceFilePath + ":-1:-1\n" +
                 "1: Gradle:Error:Execution failed for task ':MyApp:mergeDebugResources'.\n" +
                 "> Found item String/drawer_open more than one time\n" +
                 "\t" + sourceFilePath + ":-1:-1\n",
                 toString(parser.parseErrorOutput(output)));
  }

  public void testUnexpectedOutput() throws Exception {
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

    assertEquals("0: Info:This output is not expected.\n" +
                 "1: Info:Nor is this.\n" +
                 "2: Info:java.io.SurpriseSurpriseError: Bet you didn't expect to see this\n" +
                 "3: Info:\tat com.android.ide.common.res2.ValueResourceParser2.checkSurpriseSurprise(ValueResourceParser2.java:249)\n" +
                 "4: Info:\tat com.android.ide.common.res2.ValueResourceParser2.parseFile(ValueResourceParser2.java:103)\n" +
                 "5: Info:\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:918)\n" +
                 "6: Info:\tat java.lang.Thread.run(Thread.java:680)\n" +
                 "7: Info:More unexpected output.\n" +
                 "8: Gradle:Error:Execution failed for task ':MyApp:mergeDebugResources'.\n" +
                 "> I was surprised\n",
                 toString(parser.parseErrorOutput(output)));
  }

  public void testXmlError() throws Exception {
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

    assertEquals("0: Gradle:Error:Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
                 "\t" + sourceFilePath + ":7:18\n" +
                 "1: Info:java.io.IOException: org.xml.sax.SAXParseException: Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
                 "2: Info:\tat com.android.ide.common.res2.ValueResourceParser2.parseDocument(ValueResourceParser2.java:193)\n" +
                 "3: Info:\tat com.android.ide.common.res2.ValueResourceParser2.parseFile(ValueResourceParser2.java:78)\n" +
                 "4: Info:\tat com.android.ide.common.res2.ResourceSet.createResourceFile(ResourceSet.java:273)\n" +
                 "5: Info:\tat com.android.ide.common.res2.ResourceSet.parseFolder(ResourceSet.java:248)\n" +
                 "6: Info:\tat org.gradle.wrapper.WrapperExecutor.execute(WrapperExecutor.java:130)\n" +
                 "7: Info:\tat org.gradle.wrapper.GradleWrapperMain.main(GradleWrapperMain.java:61)\n" +
                 "8: Gradle:Error:: org.xml.sax.SAXParseException: Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
                 "9: Info:\tat com.sun.org.apache.xerces.internal.parsers.DOMParser.parse(DOMParser.java:246)\n" +
                 "10: Info:\tat com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderImpl.parse(DocumentBuilderImpl.java:284)\n" +
                 "11: Info:\tat com.android.ide.common.res2.ValueResourceParser2.parseDocument(ValueResourceParser2.java:189)\n" +
                 "12: Info:\t... 109 more\n" +
                 "13: Info::MyApp:mergeDebugResources FAILED\n" +
                 "14: Gradle:Error:Execution failed for task ':MyApp:mergeDebugResources'.\n" +
                 "> org.xml.sax.SAXParseException: Open quote is expected for attribute \"{1}\" associated with an  element type  \"name\".\n" +
                 "\t" + sourceFilePath + ":7:18\n" +
                 "15: Info:BUILD FAILED\n" +
                 "16: Info:Total time: 7.245 secs\n",
                 toString(parser.parseErrorOutput(output)));
  }

  public void testJavac() throws Exception {
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

    assertEquals("0: Gradle:Error:<identifier> expected\n" +
                 "\t" + sourceFilePath + ":70:2\n" +
                 "1: Gradle:Error:<identifier> expected\n" +
                 "\t" + sourceFilePath + ":71:14\n" +
                 "2: Info:2 errors\n" +
                 "3: Info::MyApp:compileDebug FAILED\n" +
                 "4: Gradle:Error:Execution failed for task ':MyApp:compileDebug'.\n" +
                 "> Compilation failed; see the compiler error output for details.\n" +
                 "5: Info:BUILD FAILED\n" +
                 "6: Info:Total time: 12.42 secs\n",
                 toString(parser.parseErrorOutput(output)));
  }

  public void testOom() throws Exception {
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

    assertEquals("0: Gradle:Error:A problem occurred configuring project ':EpicMix'.\n" +
                 "> Failed to notify project evaluation listener.\n" +
                 "   > A problem occurred configuring project ':facebook'.\n" +
                 "      > Failed to notify project evaluation listener.\n" +
                 "         > java.lang.OutOfMemoryError: PermGen space\n" +
                 "1: Info:BUILD FAILED\n" +
                 "2: Info:Total time: 24.154 secs\n",
                 toString(parser.parseErrorOutput(output)));

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

    assertEquals("0: Info:To honour the JVM settings for this build a new JVM will be forked. Please consider using the daemon: http://gradle.org/docs/1.7/userguide/gradle_daemon.html.\n" +
                 "1: Gradle:Error:A problem occurred configuring project ':MyNewApp'.\n" +
                 "> Failed to notify project evaluation listener.\n" +
                 "   > java.lang.OutOfMemoryError: Java heap space\n" +
                 "2: Info:BUILD FAILED\n" +
                 "3: Info:Total time: 24.154 secs\n",
                 toString(parser.parseErrorOutput(output2)));
  }
}
