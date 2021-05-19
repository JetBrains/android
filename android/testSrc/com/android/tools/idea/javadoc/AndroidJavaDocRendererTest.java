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
package com.android.tools.idea.javadoc;

import com.android.tools.lint.client.api.LintClient;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Consumer;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.Nullable;

public class AndroidJavaDocRendererTest extends AndroidTestCase {
  static {
    LintClient.setClientName(LintClient.CLIENT_STUDIO);
  }

  private static final String VERTICAL_ALIGN = "valign=\"top\"";

  public void checkStrings(String fileName, String targetPath, @Nullable String expectedDoc) {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-ta.xml", "res/values-ta/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-zh-rTW.xml",
                                "res/values-zh-rTW/strings.xml");
    checkDoc(fileName, targetPath, expectedDoc);
  }

  private void checkDoc(String fileName, String targetName, @Nullable String expectedDoc) {
    checkDoc(fileName, targetName, actualDoc -> assertEquals(expectedDoc, actualDoc));
  }

  /**
   * Test that the project can fetch documentation at the caret point (which is expected to be set
   * explicitly in the contents of {@code fileName}). {@code javadocConsumer} will be triggered with
   * the actual documentation returned and will be responsible for asserting expected values.
   */
  private void checkDoc(String fileName, String targetName, Consumer<String> javadocConsumer) {
    VirtualFile f = myFixture.copyFileToProject(getTestDataPath() + fileName, targetName);
    myFixture.configureFromExistingVirtualFile(f);
    PsiElement originalElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    assert originalElement != null;
    PsiElement docTargetElement =
        DocumentationManager.getInstance(getProject()).findTargetElement(myFixture.getEditor(), myFixture.getFile(), originalElement);
    assert docTargetElement != null;
    DocumentationProvider provider = DocumentationManager.getProviderFromElement(docTargetElement);
    javadocConsumer.consume(provider.generateDoc(docTargetElement, originalElement));
  }

  public void testString1Java() {
    checkString1("/javadoc/strings/Activity1.java", "src/p1/p2/Activity.java");
  }

  public void testString1Kotlin() {
    checkString1("/javadoc/strings/Activity1.kt", "src/p1/p2/Activity.kt");
  }

  public void checkString1(String sourcePath, String targetPath) {
    checkStrings(sourcePath, targetPath, "<html><body>Application Name</body></html>");
  }

  public void testString2Java() {
    checkString2("/javadoc/strings/Activity2.java", "src/p1/p2/Activity.java");
  }

  public void testString2Kotlin() {
    checkString2("/javadoc/strings/Activity2.kt", "src/p1/p2/Activity.kt");
  }

  public void checkString2(String sourcePath, String targetPath) {
    // Use FlagManagerTest#checkEncoding to get Unicode encoding
    checkStrings(sourcePath,
                 targetPath,
                 String.format("<html><body><table>" +
                               "<tr><th %1$s>Configuration</th><th %1$s>Value</th></tr>" +
                               "<tr><td %1$s>Default</td><td %1$s>Cancel</td></tr>" +
                               "<tr><td %1$s>ta</td><td %1$s>\u0bb0\u0ba4\u0bcd\u0ba4\u0bc1</td></tr>" +
                               "<tr><td %1$s>zh-rTW</td><td %1$s>\u53d6\u6d88</td></tr>" +
                               "</table></body></html>", VERTICAL_ALIGN));
  }

  public void testString3Java() {
    checkString3("/javadoc/strings/Activity3.java", "src/p1/p2/Activity.java");
  }

  public void testString3Kotlin() {
    checkString3("/javadoc/strings/Activity3.kt", "src/p1/p2/Activity.kt");
  }

  public void checkString3(String sourcePath, String targetPath) {
    checkStrings(sourcePath,
                 targetPath,
                 "<div class='definition'><pre>p1.p2<br>public static final class <b>string</b>\n" +
                 "extends <a href=\"psi_element://java.lang.Object\"><code>Object</code></a></pre></div><table class='sections'></table>");
  }

  public void testDimensionsJava() {
    checkDimensions("/javadoc/dimens/Activity1.java", "src/p1/p2/Activity.java");
  }

  public void testDimensionsKotlin() {
    checkDimensions("/javadoc/dimens/Activity1.kt", "src/p1/p2/Activity.kt");
  }

  public void checkDimensions(String sourcePath, String targetPath) {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/dimens/dimens.xml", "res/values/dimens.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/dimens/dimens-sw720dp.xml", "res/values-sw720dp/dimens.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/dimens/dimens-land.xml", "res/values-land/dimens.xml");
    checkDoc(sourcePath,
             targetPath,
             String.format("<html><body><table>" +
                               "<tr><th %1$s>Configuration</th><th %1$s>Value</th></tr>" +
                               "<tr><td %1$s>Default</td><td %1$s>200dp</td></tr>" +
                               "<tr><td %1$s>land</td><td %1$s>200px</td></tr>" +
                               "<tr><td %1$s>sw720dp</td><td %1$s>300dip</td></tr>" +
                               "</table></body></html>", VERTICAL_ALIGN));
  }

  public void testDrawablesJava() {
    checkDrawables("/javadoc/drawables/Activity1.java", "src/p1/p2/Activity.java");
  }

  public void testDrawablesKotlin() {
    checkDrawables("/javadoc/drawables/Activity1.kt", "src/p1/p2/Activity.kt");
  }

  public void checkDrawables(String sourcePath, String targetPath) {
    String p1 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable/ic_launcher.png").getPath();
    String p2 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable-hdpi/ic_launcher.png").getPath();

    String divTag = "<div style=\"background-color:gray;padding:10px\">";
    String imgTag1 = String.format("<img src='file:%1$s' alt=\"%2$s\" />", (p1.startsWith("/") ? p1 : '/' + p1), p1);
    String imgTag2 = String.format("<img src='file:%1$s' alt=\"%2$s\" />", (p2.startsWith("/") ? p2 : '/' + p2), p2);
    checkDoc(sourcePath,
             targetPath,
             String.format("<html><body><table>" +
                               "<tr><th %1$s>Configuration</th><th %1$s>Value</th></tr>" +
                               "<tr><td %1$s>drawable</td><td %1$s>%2$s%3$s</div>12&#xd7;12 px (12&#xd7;12 dp @ mdpi)<BR/>" +
                               "@drawable/ic_launcher => ic_launcher.png<BR/>" +
                               "</td></tr>" +
                               "<tr><td %1$s>drawable-hdpi</td><td %1$s>%2$s%4$s</div>12&#xd7;12 px (8&#xd7;8 dp @ hdpi)" +
                               "</td></tr>" +
                               "</table></body></html>", VERTICAL_ALIGN, divTag, imgTag1, imgTag2));
  }

  public void testStateListDrawablesJava() {
    checkStateListDrawables("/javadoc/drawables/Activity2.java", "src/p1/p2/Activity.java");
  }

  public void testStateListDrawablesKotlin() {
    checkStateListDrawables("/javadoc/drawables/Activity2.kt", "src/p1/p2/Activity.kt");
  }

  public void checkStateListDrawables(String sourcePath, String targetPath) {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/selector.xml", "res/drawable/selector.xml");
    String p1 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable/button.png").getPath();
    String p2 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable/button_active.png").getPath();
    String p3 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable/button_disabled.png").getPath();

    String imgTag1 = String.format("<img src='file:%1$s' alt=\"%2$s\" />", (p1.startsWith("/") ? p1 : '/' + p1), p1);
    String imgTag2 = String.format("<img src='file:%1$s' alt=\"%2$s\" />", (p2.startsWith("/") ? p2 : '/' + p2), p2);
    String imgTag3 = String.format("<img src='file:%1$s' alt=\"%2$s\" />", (p3.startsWith("/") ? p3 : '/' + p3), p3);
    checkDoc(sourcePath,
             targetPath,
             String.format("<html><body><table><tr><td><div style=\"background-color:gray;padding:10px\">" +
                               "%3$s" +
                               "</div>12&#xd7;12 px (12&#xd7;12 dp @ mdpi)</td><td>Not enabled</td><td><BR/>@drawable/button_disabled => button_disabled.png<BR/>" +
                               "</td></tr><tr><td><div style=\"background-color:gray;padding:10px\">" +
                               "%2$s" +
                               "</div>12&#xd7;12 px (12&#xd7;12 dp @ mdpi)</td><td>Active</td><td><BR/>@drawable/button_active => button_active.png<BR/>" +
                               "</td></tr><tr><td><div style=\"background-color:gray;padding:10px\">" +
                               "%1$s" +
                               "</div>12&#xd7;12 px (12&#xd7;12 dp @ mdpi)</td><td>Default</td><td><BR/>@drawable/button => button.png<BR/>" +
                               "</td></tr></table><BR/>@drawable/selector => selector.xml<BR/></body></html>",
                               imgTag1, imgTag2, imgTag3));
  }

  public void testMipmapJava() {
    checkMipmaps("/javadoc/mipmaps/Activity1.java", "src/p1/p2/Activity.java");
  }

  public void testMipmapKotlin() {
    checkMipmaps("/javadoc/mipmaps/Activity1.kt", "src/p1/p2/Activity.kt");
  }

  public void checkMipmaps(String sourcePath, String targetPath) {
    String p1 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/mipmaps/ic_launcher.png",
                                            "res/mipmap/ic_launcher.png").getPath();
    String p2 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/mipmaps/ic_launcher.png",
                                            "res/mipmap-hdpi/ic_launcher.png").getPath();

    String divTag = "<div style=\"background-color:gray;padding:10px\">";
    String imgTag1 = String.format("<img src='file:%1$s' alt=\"%2$s\" />", (p1.startsWith("/") ? p1 : '/' + p1), p1);
    String imgTag2 = String.format("<img src='file:%1$s' alt=\"%2$s\" />", (p2.startsWith("/") ? p2 : '/' + p2), p2);
    checkDoc(sourcePath,
             targetPath,
             String.format("<html><body><table>" +
                               "<tr><th %1$s>Configuration</th><th %1$s>Value</th></tr>" +
                               "<tr><td %1$s>mipmap</td><td %1$s>%2$s%3$s</div>12&#xd7;12 px (12&#xd7;12 dp @ mdpi)<BR/>" +
                               "@mipmap/ic_launcher => ic_launcher.png<BR/>" +
                               "</td></tr>" +
                               "<tr><td %1$s>mipmap-hdpi</td><td %1$s>%2$s%4$s</div>12&#xd7;12 px (8&#xd7;8 dp @ hdpi)" +
                               "</td></tr>" +
                               "</table></body></html>", VERTICAL_ALIGN, divTag, imgTag1, imgTag2));
  }

  public void testArraysJava() {
    checkArrays("/javadoc/arrays/Activity1.java", "src/p1/p2/Activity.java");
  }

  public void testArraysKotlin() {
    checkArrays("/javadoc/arrays/Activity1.kt", "src/p1/p2/Activity.kt");
  }

  public void checkArrays(String sourcePath, String targetPath) {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/arrays/arrays.xml", "res/values/arrays.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/arrays/arrays-no.xml", "res/values-no/arrays.xml");
    checkDoc(sourcePath,
             targetPath,
                 "<html><body>" +
                 "<table>" +
                 "<tr><th valign=\"top\">Configuration</th><th valign=\"top\">Value</th></tr>" +
                 "<tr><td valign=\"top\">Default</td><td valign=\"top\">red, orange, yellow, green</td></tr>" +
                 "<tr><td valign=\"top\">no</td><td valign=\"top\">r\u00F8d, oransj, gul, gr\u00F8nn</td></tr>" +
                 "</table>" +
                 "</body></html>");
  }

  public void testXmlString1() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-ta.xml", "res/values-ta/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-zh-rTW.xml", "res/values-zh-rTW/strings.xml");
    checkDoc("/javadoc/strings/layout1.xml", "res/layout/layout1.xml", "<html><body>Application Name</body></html>");
  }

  public void testAttributeValueDoc() {
    checkDoc("/javadoc/layout/layout.xml", "res/layout/layout.xml",
             "The view should be only big enough to enclose its content (plus padding).");
  }

  public void testAttributeEnumDoc() {
    checkDoc("/javadoc/styles/styles2.xml", "res/styles.xml", "The type of navigation to use.");
  }

  public void testAttributeEnumValueDoc() {
    checkDoc("/javadoc/styles/styles3.xml", "res/styles.xml", "The action bar will use a series of horizontal tabs for navigation.");
  }

  public void testXmlString2() {
    // Like testXmlString1, but the caret is at the right edge of an attribute value so the document provider has
    // to go to the previous XML token to obtain the resource url
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-ta.xml", "res/values-ta/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-zh-rTW.xml", "res/values-zh-rTW/strings.xml");
    checkDoc("/javadoc/strings/layout2.xml", "res/layout/layout2.xml", "<html><body>Application Name</body></html>");
  }

  public void testSystemAttributes() {
    checkDoc("/javadoc/attrs/layout1.xml", "res/layout/layout.xml",
                 "<html><body>Formats: enum<br>Values: horizontal, vertical<br><br>Should the layout be a column or a row?  Use \"horizontal\"\n" +
                 "             for a row, \"vertical\" for a column.  The default is\n" +
                 "             horizontal.</body></html>");
  }

  public void testLocalAttributes1() {
    doTestLocalAttributes("/javadoc/attrs/layout2.xml",
                          "<html><body>Formats: boolean, integer<br><br>my attr 1 docs for MyView1</body></html>");
  }

  public void testLocalAttributes2() {
    doTestLocalAttributes("/javadoc/attrs/layout3.xml",
                          "<html><body>Formats: boolean, reference<br><br>my attr 2 docs for MyView1</body></html>");
  }

  public void testLocalAttributes3() {
    doTestLocalAttributes("/javadoc/attrs/layout4.xml",
                          "<html><body>Formats: boolean, integer<br><br>my attr 1 docs for MyView2</body></html>");
  }

  public void testLocalAttributes4() {
    doTestLocalAttributes("/javadoc/attrs/layout5.xml",
                          "<html><body>Formats: boolean, reference<br><br>my attr 2 docs for MyView2</body></html>");
  }

  public void testLocalAttributes5() {
    doTestLocalAttributes("/javadoc/attrs/layout6.xml",
                          "<html><body>Formats: boolean, integer<br><br>my attr 1 global docs</body></html>");
  }

  public void testLocalEnumAttributes1() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/attrs.xml", "res/values/attrs.xml");
    checkDoc("/javadoc/styles/styles4.xml", "res/styles.xml", "The type of scrolling to use.");
  }

  public void testLocalEnumAttributes2() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/attrs.xml", "res/values/attrs.xml");
    checkDoc("/javadoc/styles/styles5.xml", "res/styles.xml", "The widget will scroll horizontally.");
  }

  private void doTestLocalAttributes(String file, String exp) {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/attrs.xml", "res/values/attrs.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/MyView1.java", "src/p1/p2/MyView1.java");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/MyView2.java", "src/p1/p2/MyView2.java");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/MyView3.java", "src/p1/p2/MyView3.java");
    checkDoc(file, "res/layout/layout.xml", exp);
  }

  public void testManifestAttributes() throws Exception {
    deleteManifest();
    checkDoc("/javadoc/attrs/manifest.xml", "AndroidManifest.xml",
                 "<html><body>Formats: string<br><br>Required name of the class implementing the activity, deriving from\n" +
                 "            {@link android.app.Activity}.  This is a fully\n" +
                 "            qualified class name (for example, com.mycompany.myapp.MyActivity); as a\n" +
                 "            short-hand if the first character of the class\n" +
                 "            is a period then it is appended to your package name.</body></html>");
  }

  public void testFrameworkColors2() {
    checkDoc("/javadoc/colors/layout2.xml", "res/layout/layout.xml",
                 "<html><body>" +
                 "<table style=\"background-color:rgb(255,255,255);width:200px;text-align:center;vertical-align:middle;\" border=\"0\">" +
                 "<tr height=\"100\">" + "" +
                 "<td align=\"center\" valign=\"middle\" height=\"100\" style=\"color:black\">#FFFFFF</td>" +
                 "</tr></table><BR/>" +
                 "@android:color/white => #ffffffff<BR/>" +
                 "</body></html>");
  }

  public void testAlphaColor() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/colors/values2.xml", "res/values/values2.xml");
    checkDoc("/javadoc/colors/layout3.xml", "res/layout/layout.xml",
                 "<html><body>" +
                 "<table style=\"background-color:rgb(123,123,123);width:200px;text-align:center;vertical-align:middle;\" " +
                 "border=\"0\">" +
                 "<tr height=\"100\">" +
                 "<td align=\"center\" valign=\"middle\" height=\"100\" style=\"color:black\">#80000000" +
                 "</td>" +
                 "</tr>" +
                 "</table><BR/>" +
                 "@color/my_color => #80000000<BR/>" +
                 "</body></html>");
  }

  public void testColorsAndResolution() {
    // This test checks
    //  - invoking XML documentation from an XML text node
    //  - a long chain of resource resolutions
    //  - evaluating colors, including XML color state lists
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/colors/third.xml", "res/color/third.xml");
    checkDoc("/javadoc/colors/values.xml", "res/values/values.xml",
                 "<html><body>" +
                 "<table><tr><td><table style=\"background-color:rgb(251,251,251);width:66px;text-align:center;vertical-align:middle;\" border=\"0\"><tr height=\"33\"><td align=\"center\" valign=\"middle\" height=\"33\" style=\"color:black\">#80FFFFFF</td></tr></table></td><td>Not enabled</td><td><BR/>" +
                 "@android:color/bright_foreground_dark_disabled => #80ffffff<BR/>" +
                 "</td></tr><tr><td><table style=\"background-color:rgb(255,255,255);width:66px;text-align:center;vertical-align:middle;\" border=\"0\"><tr height=\"33\"><td align=\"center\" valign=\"middle\" height=\"33\" style=\"color:black\">#FFFFFF</td></tr></table></td><td>Not window_focused</td><td><BR/>" +
                 "@android:color/bright_foreground_dark => @android:color/background_light => #ffffffff<BR/>" +
                 "</td></tr><tr><td><table style=\"background-color:rgb(0,0,0);width:66px;text-align:center;vertical-align:middle;\" border=\"0\"><tr height=\"33\"><td align=\"center\" valign=\"middle\" height=\"33\" style=\"color:white\">#000000</td></tr></table></td><td>Pressed</td><td><BR/>" +
                 "@android:color/bright_foreground_dark_inverse => @android:color/bright_foreground_light => @android:color/background_dark => #ff000000<BR/>" +
                 "</td></tr><tr><td><FONT color=\"#ff0000\"><B>@android:color/my_white</B></FONT></td><td>Selected</td></tr><tr><td><table><tr><td><table style=\"background-color:rgb(251,251,251);width:66px;text-align:center;vertical-align:middle;\" border=\"0\"><tr height=\"33\"><td align=\"center\" valign=\"middle\" height=\"33\" style=\"color:black\">#80FFFFFF</td></tr></table></td><td>Not enabled</td><td><BR/>" +
                 "@android:color/bright_foreground_dark_disabled => #80ffffff<BR/>" +
                 "</td></tr><tr><td><table style=\"background-color:rgb(255,255,255);width:66px;text-align:center;vertical-align:middle;\" border=\"0\"><tr height=\"33\"><td align=\"center\" valign=\"middle\" height=\"33\" style=\"color:black\">#FFFFFF</td></tr></table></td><td>Not window_focused</td><td><BR/>" +
                 "@android:color/bright_foreground_dark => @android:color/background_light => #ffffffff<BR/>" +
                 "</td></tr><tr><td><table style=\"background-color:rgb(0,0,0);width:66px;text-align:center;vertical-align:middle;\" border=\"0\"><tr height=\"33\"><td align=\"center\" valign=\"middle\" height=\"33\" style=\"color:white\">#000000</td></tr></table></td><td>Pressed</td><td><BR/>" +
                 "@android:color/bright_foreground_dark_inverse => @android:color/bright_foreground_light => @android:color/background_dark => #ff000000<BR/>" +
                 "</td></tr><tr><td><table style=\"background-color:rgb(0,0,0);width:66px;text-align:center;vertical-align:middle;\" border=\"0\"><tr height=\"33\"><td align=\"center\" valign=\"middle\" height=\"33\" style=\"color:white\">#000000</td></tr></table></td><td>Selected</td><td><BR/>" +
                 "@android:color/bright_foreground_dark_inverse => @android:color/bright_foreground_light => @android:color/background_dark => #ff000000<BR/>" +
                 "</td></tr><tr><td><table style=\"background-color:rgb(0,0,0);width:66px;text-align:center;vertical-align:middle;\" border=\"0\"><tr height=\"33\"><td align=\"center\" valign=\"middle\" height=\"33\" style=\"color:white\">#000000</td></tr></table></td><td>Activated</td><td><BR/>" +
                 "@android:color/bright_foreground_dark_inverse => @android:color/bright_foreground_light => @android:color/background_dark => #ff000000<BR/>" +
                 "</td></tr><tr><td><table style=\"background-color:rgb(255,255,255);width:66px;text-align:center;vertical-align:middle;\" border=\"0\"><tr height=\"33\"><td align=\"center\" valign=\"middle\" height=\"33\" style=\"color:black\">#FFFFFF</td></tr></table></td><td>Default</td><td><BR/>" +
                 "@android:color/bright_foreground_dark => @android:color/background_light => #ffffffff<BR/>" +
                 "</td></tr></table></td><td>Activated</td><td><BR/>" +
                 "@android:color/primary_text_dark => primary_text_dark.xml<BR/>" +
                 "</td></tr><tr><td><table style=\"background-color:rgb(170,68,170);width:66px;text-align:center;vertical-align:middle;\" border=\"0\"><tr height=\"33\"><td align=\"center\" valign=\"middle\" height=\"33\" style=\"color:white\">#AA44AA</td></tr></table></td><td>Default</td><td><BR/>" +
                 "@color/fourth => #aa44aa<BR/>" +
                 "</td></tr></table><BR/>" +
                 "@color/first => @color/second => @color/third => third.xml<BR/>" +
                 "</body></html>");
  }

  public void testStyle() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/styles/AndroidManifest.xml", "AndroidManifest.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/styles/styles.xml", "res/values/styles.xml");
    checkDoc("/javadoc/styles/layout.xml", "res/layout/layout.xml",
                 "<html><body><B>android:textAppearanceMedium</B><br/>Text color, typeface, size, and style for \"medium\" text. Defaults to primary text color.<br/><hr/><BR/>" +
                 "?android:attr/textAppearanceMedium => @android:style/TextAppearance.Medium<BR/>" +
                 "<BR/>" +
                 "<hr><B>TextAppearance.Medium</B>:<BR/>" +
                 "&nbsp;&nbsp;&nbsp;&nbsp;android:<B>textSize</B> = 18sp<BR/>" +
                 "<BR/>" +
                 "Inherits from: @android:style/TextAppearance:<BR/>" +
                 "&nbsp;&nbsp;&nbsp;&nbsp;android:<B>textColor</B> = ?textColorPrimary => #ff000000<BR/>" +
                 "&nbsp;&nbsp;&nbsp;&nbsp;android:<B>textColorHighlight</B> = ?textColorHighlight => 6633b5e5<BR/>" +
                 "&nbsp;&nbsp;&nbsp;&nbsp;android:<B>textColorHint</B> = ?textColorHint => #808080<BR/>" +
                 "&nbsp;&nbsp;&nbsp;&nbsp;android:<B>textColorLink</B> = ?textColorLink => #ff33b5e5<BR/>" +
                 "&nbsp;&nbsp;&nbsp;&nbsp;android:<B>textStyle</B> = normal<BR/>" +
                 "</body></html>");
  }

  public void testFrameworkStyleResolution() {
    // Checks that references in framework styles are always understood to point to framework resources,
    // even if the android: prefix is not explicitly written.
    checkDoc("/javadoc/styles/styles.xml", "res/values/styles.xml", actualDoc -> {
      boolean isContained = actualDoc.contains("@android:style/Theme.Holo<BR/>");
      assertTrue("\nExpected: " + "@android:style/Theme.Holo<BR/>" + "\nContained By: " + actualDoc, isContained);
    });
  }

  public void testStyleName() {
    checkDoc("/javadoc/styles/styles_attribute_documentation.xml", "res/values/styles.xml",
             "<html><body><B>android:textStyle</B><br/>Default text typeface style.<br/><hr/><BR/>@android:attr/textStyle<BR/><BR/></body></html>");
  }

  public void testInjectExternalDocumentation() {
    assertEquals("firstsecond", AndroidJavaDocRenderer.injectExternalDocumentation("first","second"));
    assertEquals("<html a=\"b\"><body b=\"c\">firstsecond</body></html>", AndroidJavaDocRenderer.injectExternalDocumentation("<html a=\"b\"><body b=\"c\">first</body></html>", "second"));
    assertEquals("<HTML a=\"b\"><BODY b=\"c\">firstsecond</BODY></HTML>", AndroidJavaDocRenderer.injectExternalDocumentation("<HTML a=\"b\"><BODY b=\"c\">first</BODY></HTML>", "second"));
    assertEquals("firstsecond", AndroidJavaDocRenderer.injectExternalDocumentation("first","<html a=\"b\"><body b=\"c\">second</body></html>"));
    assertEquals("firstsecond", AndroidJavaDocRenderer.injectExternalDocumentation("first","<HTML a=\"b\"><BODY b=\"c\">second</BODY></HTML>"));
    assertEquals("<html a=\"b\">firstsecond</html>", AndroidJavaDocRenderer.injectExternalDocumentation("<html a=\"b\">first</html>","<html b=\"c\">second</html>"));
    assertEquals("<BODY a=\"b\">firstsecond</BODY>", AndroidJavaDocRenderer.injectExternalDocumentation("<BODY a=\"b\">first</BODY>","<BODY b=\"c\">second</BODY>"));

    // insert style with head
    assertEquals("<head>head<style>s2</style></head><body>firstsecond</body>", AndroidJavaDocRenderer.injectExternalDocumentation("<head>head</head><body>first</body>","<style>s2</style>second"));
    // insert style without head
    assertEquals("<head><style>s2</style></head><body>firstsecond</body>", AndroidJavaDocRenderer.injectExternalDocumentation("<body>first</body>","<style>s2</style>second"));
  }

  public void testLintIssueId() {
    checkDoc("/javadoc/lint/lint_issue_id.xml", "lint.xml",
                 "A layout that has no children or no background can often be removed (since it is invisible) " +
                 "for a flatter and more efficient layout hierarchy.");
  }

  /**
   * Regression test for http://b/151964515
   */
  public void testInheritanceLoop() {
    // A layout is needed for the ResourceResolver to be able to automatically pick a default configuration (it will find a layout at
    // random). The layout is not related to the test.
    myFixture.copyFileToProject("/javadoc/layout/layout.xml", "res/layout/layout.xml");
    checkDoc("/javadoc/styles/styles_loop.xml", "res/values/styles.xml",
             doc -> assertTrue(doc.startsWith("<html><body><BR/>@style/TextAppearance<BR/><BR/><hr><B>TextAppearance</B>")));
  }

  // TODO: Test flavor docs
}
