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

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.Nullable;

public class AndroidJavaDocRendererTest extends AndroidTestCase {
  private static final String VERTICAL_ALIGN = "valign=\"top\"";

  public void checkStrings(String fileName, @Nullable String expectedDoc) {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-ta.xml", "res/values-ta/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-zh-rTW.xml",
                                "res/values-zh-rTW/strings.xml");
    checkJavadoc(fileName, "src/com/foo/Activity.java", expectedDoc);
  }

  private void checkJavadoc(String fileName, @Nullable String expectedDoc) {
    checkJavadoc(fileName, "src/com/foo/Activity.java", expectedDoc);
  }

  private void checkJavadoc(String fileName, String targetName, @Nullable String expectedDoc) {
    final VirtualFile f = myFixture.copyFileToProject(getTestDataPath() + fileName, targetName);
    myFixture.configureFromExistingVirtualFile(f);
    PsiElement originalElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    assert originalElement != null;
    final PsiElement docTargetElement = DocumentationManager.getInstance(getProject()).findTargetElement(
      myFixture.getEditor(), myFixture.getFile(), originalElement);
    assert docTargetElement != null;
    DocumentationProvider provider = DocumentationManager.getProviderFromElement(docTargetElement);
    assertEquals(expectedDoc, provider.generateDoc(docTargetElement, originalElement));
  }

  public void testString1() {
    checkStrings("/javadoc/strings/Activity1.java", "<html><body>Application Name</body></html>");
  }

  public void testString2() {
    // Use FlagManagerTest#checkEncoding to get Unicode encoding
    checkStrings("/javadoc/strings/Activity2.java",
                 String.format("<html><body><table>" +
                               "<tr><th %1$s>Configuration</th><th %1$s>Value</th></tr>" +
                               "<tr><td %1$s>Default</td><td %1$s>Cancel</td></tr>" +
                               "<tr><td %1$s>ta</td><td %1$s>\u0bb0\u0ba4\u0bcd\u0ba4\u0bc1</td></tr>" +
                               "<tr><td %1$s>zh-rTW</td><td %1$s>\u53d6\u6d88</td></tr>" +
                               "</table></body></html>", VERTICAL_ALIGN));
  }

  public void testString3() {
    checkStrings("/javadoc/strings/Activity3.java", null);
  }

  public void testDimensions1() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/dimens/dimens.xml", "res/values/dimens.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/dimens/dimens-sw720dp.xml", "res/values-sw720dp/dimens.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/dimens/dimens-land.xml", "res/values-land/dimens.xml");
    checkJavadoc("/javadoc/dimens/Activity1.java", String.format("<html><body><table>" +
                                                                 "<tr><th %1$s>Configuration</th><th %1$s>Value</th></tr>" +
                                                                 "<tr><td %1$s>Default</td><td %1$s>200dp</td></tr>" +
                                                                 "<tr><td %1$s>land</td><td %1$s>200px</td></tr>" +
                                                                 "<tr><td %1$s>sw720dp</td><td %1$s>300dip</td></tr>" +
                                                                 "</table></body></html>", VERTICAL_ALIGN));
  }

  public void testDrawables() {
    String p1 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable/ic_launcher.png").getPath();
    String p2 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable-hdpi/ic_launcher.png").getPath();

    String divTag = "<div style=\"background-color:gray;padding:10px\">";
    String imgTag1 = String.format("<img src='file:%1$s' alt=\"%2$s\" />", (p1.startsWith("/") ? p1 : '/' + p1),
                                   FileUtil.toSystemDependentName(p1));
    String imgTag2 = String.format("<img src='file:%1$s' alt=\"%2$s\" />", (p2.startsWith("/") ? p2 : '/' + p2),
                                   FileUtil.toSystemDependentName(p2));
    checkJavadoc("/javadoc/drawables/Activity1.java",
                 String.format("<html><body><table>" +
                               "<tr><th %1$s>Configuration</th><th %1$s>Value</th></tr>" +
                               "<tr><td %1$s>drawable</td><td %1$s>%2$s%3$s</div>12&#xd7;12 px (12&#xd7;12 dp @ mdpi)<BR/>" +
                               "@drawable/ic_launcher => ic_launcher.png<BR/>" +
                               "</td></tr>" +
                               "<tr><td %1$s>drawable-hdpi</td><td %1$s>%2$s%4$s</div>12&#xd7;12 px (8&#xd7;8 dp @ hdpi)" +
                               "</td></tr>" +
                               "</table></body></html>", VERTICAL_ALIGN, divTag, imgTag1, imgTag2));
  }

  public void testStateListDrawables() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/selector.xml", "res/drawable/selector.xml");
    String p1 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable/button.png").getPath();
    String p2 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable/button_active.png").getPath();
    String p3 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/drawables/ic_launcher.png",
                                            "res/drawable/button_disabled.png").getPath();

    String imgTag1 = String.format("<img src='file:%1$s' alt=\"%2$s\" />", (p1.startsWith("/") ? p1 : '/' + p1),
                                   FileUtil.toSystemDependentName(p1));
    String imgTag2 = String.format("<img src='file:%1$s' alt=\"%2$s\" />", (p2.startsWith("/") ? p2 : '/' + p2),
                                   FileUtil.toSystemDependentName(p2));
    String imgTag3 = String.format("<img src='file:%1$s' alt=\"%2$s\" />", (p3.startsWith("/") ? p3 : '/' + p3),
                                   FileUtil.toSystemDependentName(p3));
    checkJavadoc("/javadoc/drawables/Activity2.java",
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

  public void testMipmaps() {
    String p1 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/mipmaps/ic_launcher.png",
                                            "res/mipmap/ic_launcher.png").getPath();
    String p2 = myFixture.copyFileToProject(getTestDataPath() + "/javadoc/mipmaps/ic_launcher.png",
                                            "res/mipmap-hdpi/ic_launcher.png").getPath();

    String divTag = "<div style=\"background-color:gray;padding:10px\">";
    String imgTag1 = String.format("<img src='file:%1$s' alt=\"%2$s\" />", (p1.startsWith("/") ? p1 : '/' + p1),
                                   FileUtil.toSystemDependentName(p1));
    String imgTag2 = String.format("<img src='file:%1$s' alt=\"%2$s\" />", (p2.startsWith("/") ? p2 : '/' + p2),
                                   FileUtil.toSystemDependentName(p2));
    checkJavadoc("/javadoc/mipmaps/Activity1.java",
                 String.format("<html><body><table>" +
                               "<tr><th %1$s>Configuration</th><th %1$s>Value</th></tr>" +
                               "<tr><td %1$s>mipmap</td><td %1$s>%2$s%3$s</div>12&#xd7;12 px (12&#xd7;12 dp @ mdpi)<BR/>" +
                               "@mipmap/ic_launcher => ic_launcher.png<BR/>" +
                               "</td></tr>" +
                               "<tr><td %1$s>mipmap-hdpi</td><td %1$s>%2$s%4$s</div>12&#xd7;12 px (8&#xd7;8 dp @ hdpi)" +
                               "</td></tr>" +
                               "</table></body></html>", VERTICAL_ALIGN, divTag, imgTag1, imgTag2));
  }

  public void testArrays() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/arrays/arrays.xml", "res/values/arrays.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/arrays/arrays-no.xml", "res/values-no/arrays.xml");
    checkJavadoc("/javadoc/arrays/Activity1.java",
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
    checkJavadoc("/javadoc/strings/layout1.xml", "res/layout/layout1.xml", "<html><body>Application Name</body></html>");
  }

  public void testAttributeValueDoc() {
    checkJavadoc("/javadoc/layout/layout.xml", "res/layout/layout.xml",
                 "The view should be only big enough to enclose its content (plus padding).");
  }

  public void testAttributeEnumDoc() {
    checkJavadoc("/javadoc/styles/styles2.xml", "res/styles.xml",  "The type of navigation to use.");
  }

  public void testAttributeEnumValueDoc() {
    checkJavadoc("/javadoc/styles/styles3.xml", "res/styles.xml",  "The action bar will use a series of horizontal tabs for navigation.");
  }

  public void testXmlString2() {
    // Like testXmlString1, but the caret is at the right edge of an attribute value so the document provider has
    // to go to the previous XML token to obtain the resource url
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-ta.xml", "res/values-ta/strings.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/strings/strings-zh-rTW.xml", "res/values-zh-rTW/strings.xml");
    checkJavadoc("/javadoc/strings/layout2.xml", "res/layout/layout2.xml", "<html><body>Application Name</body></html>");
  }

  public void testSystemAttributes() {
    checkJavadoc("/javadoc/attrs/layout1.xml", "res/layout/layout.xml",
                 "<html><body>Formats: enum<br>Values: horizontal, vertical<br><br> Should the layout be a column or a row?  Use \"horizontal\"\n" +
                 "             for a row, \"vertical\" for a column.  The default is\n" +
                 "             horizontal. </body></html>");
  }

  public void testLocalAttributes1() {
    doTestLocalAttributes("/javadoc/attrs/layout2.xml",
                          "<html><body>Formats: boolean, integer<br><br> my attr 1 docs for MyView1 </body></html>");
  }

  public void testLocalAttributes2() {
    doTestLocalAttributes("/javadoc/attrs/layout3.xml",
                          "<html><body>Formats: boolean, reference<br><br> my attr 2 docs for MyView1 </body></html>");
  }

  public void testLocalAttributes3() {
    doTestLocalAttributes("/javadoc/attrs/layout4.xml",
                          "<html><body>Formats: boolean, integer<br><br> my attr 1 docs for MyView2 </body></html>");
  }

  public void testLocalAttributes4() {
    doTestLocalAttributes("/javadoc/attrs/layout5.xml",
                          "<html><body>Formats: boolean, reference<br><br> my attr 2 docs for MyView2 </body></html>");
  }

  public void testLocalAttributes5() {
    doTestLocalAttributes("/javadoc/attrs/layout6.xml",
                          "<html><body>Formats: boolean, integer<br><br> my attr 1 global docs </body></html>");
  }

  public void testLocalEnumAttributes1() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/attrs.xml", "res/values/attrs.xml");
    checkJavadoc("/javadoc/styles/styles4.xml", "res/styles.xml",  "The type of scrolling to use.");
  }

  public void testLocalEnumAttributes2() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/attrs.xml", "res/values/attrs.xml");
    checkJavadoc("/javadoc/styles/styles5.xml", "res/styles.xml",  "The widget will scroll horizontally.");
  }

  private void doTestLocalAttributes(String file, String exp) {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/attrs.xml", "res/values/attrs.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/MyView1.java", "src/p1/p2/MyView1.java");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/MyView2.java", "src/p1/p2/MyView2.java");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/attrs/MyView3.java", "src/p1/p2/MyView3.java");
    checkJavadoc(file, "res/layout/layout.xml", exp);
  }

  public void testManifestAttributes() throws Exception {
    deleteManifest();
    checkJavadoc("/javadoc/attrs/manifest.xml", "AndroidManifest.xml",
                 "<html><body>Formats: string<br><br> Required name of the class implementing the activity, deriving from\n" +
                 "            {@link android.app.Activity}.  This is a fully\n" +
                 "            qualified class name (for example, com.mycompany.myapp.MyActivity); as a\n" +
                 "            short-hand if the first character of the class\n" +
                 "            is a period then it is appended to your package name. </body></html>");
  }

  // @formatter:off
  /**
   * This test requires {@link #requireRecentSdk} to return true (since we need a real SDK to resolve this
   * framework color, but unfortunately that doesn't work for other tests; the testLocalAttributes tests don't
   * pass on a recent SDK
   * <pre>
  public void testFrameworkColors1() {
    checkJavadoc("/javadoc/colors/layout.xml", "res/layout/layout.xml",
                 "<html><body>" +
                 "<table style=\"background-color:rgb(255,255,255);color:black;width:200px;text-align:center;" +
                 "vertical-align:middle;\" border=\"0\">" +
                 "<tr height=\"100\">" +
                 "<td align=\"center\" valign=\"middle\" height=\"100\">" +
                 "#ffffff" +
                 "</td>" +
                 "</tr><" +
                 "/table><BR/>\n" +
                 "@color/primary_text_dark => primary_text_dark.xml => @android:color/background_light => #ffffffff<BR/>\n" +
                 "</body></html>");
  }
  </pre>
  */ // @formatter:on
  public void testFrameworkColors2() {
    // @android:color/my_white is defined in
    // testData/sdk1.5/platforms/android-1.5/data/res/values
    // We're testing this because the bundled test platform isn't a complete framework so it doesn't
    // have the real framework attributes (and the one it does, @android:string/cancel, has a different
    // value than in the real platform. If we ever update the unit tests to use a more modern
    // platform, the below should be changed to a real framework color.
    checkJavadoc("/javadoc/colors/layout2.xml", "res/layout/layout.xml",
                 "<html><body>" +
                 "<table style=\"background-color:rgb(255,255,255);width:200px;text-align:center;vertical-align:middle;\" " +
                 "border=\"0\">" +
                 "<tr height=\"100\">" +
                 "<td align=\"center\" valign=\"middle\" height=\"100\" style=\"color:black\">" +
                 "#ffffff" +
                 "</td>" +
                 "</tr>" +
                 "</table><BR/>" +
                 "@android:color/my_white => #ffffff<BR/>" +
                 "</body></html>");
  }

  public void testAlphaColor() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/colors/values2.xml", "res/values/values2.xml");
    checkJavadoc("/javadoc/colors/layout3.xml", "res/layout/layout.xml",
                 "<html><body>" +
                 "<table style=\"background-color:rgb(0,0,0);width:200px;text-align:center;vertical-align:middle;\" " +
                 "border=\"0\">" +
                 "<tr height=\"100\">" +
                 "<td align=\"center\" valign=\"middle\" height=\"100\" style=\"color:white\">#80000000" +
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
    checkJavadoc("/javadoc/colors/values.xml", "res/values/values.xml",
                 "<html><body>" +
                 "<table><tr><td><font color=\"#ff0000\"><B>@android:color/bright_foreground_dark_disabled</B></font></td><td>Not enabled</td></tr>" +
                 "<tr><td><font color=\"#ff0000\"><B>@android:color/bright_foreground_dark</B></font></td><td>Not window_focused</td></tr>" +
                 "<tr><td><font color=\"#ff0000\"><B>@android:color/bright_foreground_dark_inverse</B></font></td><td>Pressed</td></tr>" +
                 "<tr><td><table style=\"background-color:rgb(255,255,255);width:66px;text-align:center;vertical-align:middle;\" border=\"0\"><tr height=\"33\"><td align=\"center\" valign=\"middle\" height=\"33\" style=\"color:black\">#ffffff</td></tr></table></td><td>Selected</td><td><BR/>@android:color/my_white => #ffffff<BR/></td></tr>" +
                 "<tr><td><table style=\"background-color:rgb(0,0,0);width:66px;text-align:center;vertical-align:middle;\" border=\"0\"><tr height=\"33\"><td align=\"center\" valign=\"middle\" height=\"33\" style=\"color:white\">#000000</td></tr></table></td><td>Activated</td><td><BR/>@android:color/primary_text_dark => #000000<BR/></td></tr>" +
                 "<tr><td><table style=\"background-color:rgb(170,68,170);width:66px;text-align:center;" +
                 "vertical-align:middle;\" border=\"0\">" +
                 "<tr height=\"33\">" +
                 "<td align=\"center\" valign=\"middle\" height=\"33\" style=\"color:white\">" +
                 "#aa44aa" +
                 "</td>" +
                 "</tr>" +
                 "</table>" +
                 "</td><td>Default</td><td><BR/>@color/fourth => #aa44aa<BR/></td></tr></table>" +
                 "<BR/>@color/first => @color/second => @color/third => third.xml<BR/>" +
                 "</body></html>");
  }

  public void testStyle() {
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/styles/AndroidManifest.xml", "AndroidManifest.xml");
    myFixture.copyFileToProject(getTestDataPath() + "/javadoc/styles/styles.xml", "res/values/styles.xml");
    checkJavadoc("/javadoc/styles/layout.xml", "res/layout/layout.xml",
                 "<html><body><B>android:textAppearanceMedium</B><br/> Text color, typeface, size, and style for \"medium\" text. Defaults to primary text color. <br/><hr/><BR/>" +
                 "?android:attr/textAppearanceMedium => @android:style/TextAppearance.Medium<BR/>" +
                 "<BR/>" +
                 "<hr><B>TextAppearance.Medium</B>:<BR/>" +
                 "&nbsp;&nbsp;&nbsp;&nbsp;android:<B>textSize</B> = 18sp<BR/>" +
                 "&nbsp;&nbsp;&nbsp;&nbsp;android:<B>textStyle</B> = normal<BR/>" +
                 "&nbsp;&nbsp;&nbsp;&nbsp;android:<B>textColor</B> = ?textColorPrimary<BR/>" +
                 "<table style=\"background-color:rgb(0,0,0);width:66px;text-align:center;vertical-align:middle;\" border=\"0\"><tr height=\"33\"><td align=\"center\" valign=\"middle\" height=\"33\" style=\"color:white\">#000000</td></tr></table>" +
                 "<BR/>" +
                 "Inherits from: @android:style/TextAppearance:<BR/>" +
                 "&nbsp;&nbsp;&nbsp;&nbsp;android:<B>textColorLink</B> = #5C5CFF<BR/>" +
                 "&nbsp;&nbsp;&nbsp;&nbsp;android:<B>textColorHighlight</B> = #FFFF9200<BR/>" +
                 "&nbsp;&nbsp;&nbsp;&nbsp;android:<B>textColorHint</B> = ?textColorHint => ?textColorHint<BR/>" +
                 "</body></html>");
  }

  public void testFrameworkStyleResolution() {
    // Checks that references in framework styles are always understood to point to framework resources,
    // even if the android: prefix is not explicitly written.
    checkJavadoc("/javadoc/styles/styles.xml", "res/values/styles.xml",
                 "<html><body><BR/>" +
                 "@android:style/Theme.FrameworkTheme<BR/>" +
                 "<BR/>" +
                 "<hr><B>Theme.FrameworkTheme</B>:<BR/>" +
                 "&nbsp;&nbsp;&nbsp;&nbsp;android:<B>textColorPrimary</B> = @color/state_list<BR/>" +
                 "<table><tr><td>" +
                 "<table style=\"background-color:rgb(255,255,255);width:66px;text-align:center;vertical-align:middle;\" border=\"0\"><tr height=\"33\"><td align=\"center\" valign=\"middle\" height=\"33\" style=\"color:black\">#ffffff</td></tr></table>" +
                 "</td><td>Default</td></tr></table>" +
                 "</body></html>");
  }

  public void testStyleName() {
    checkJavadoc("/javadoc/styles/styles_attribute_documentation.xml", "res/values/styles.xml",
                 "Default text typeface style.");
  }

  public void testInjectExternalDocumentation() throws Exception {
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
    checkJavadoc("/javadoc/lint/lint_issue_id.xml", "lint.xml",
                 "A layout that has no children or no background can often be removed (since it is invisible) " +
                 "for a flatter and more efficient layout hierarchy.");
  }

  // TODO: Test flavor docs
}
