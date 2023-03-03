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
package com.android.tools.idea.rendering.parsers;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_MIN_HEIGHT;
import static com.android.SdkConstants.ATTR_MIN_WIDTH;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_SRC;
import static com.android.SdkConstants.ATTR_TAG;
import static com.android.SdkConstants.ATTR_TEXT;
import static com.android.SdkConstants.ATTR_VISIBILITY;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.URI_PREFIX;
import static com.android.SdkConstants.VALUE_FILL_PARENT;
import static com.android.SdkConstants.VALUE_MATCH_PARENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.io.StringReader;
import java.util.SortedSet;
import java.util.TreeSet;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class LayoutPsiPullParserTest extends AndroidTestCase {
  @SuppressWarnings("SpellCheckingInspection")
  public static final String BASE_PATH = "xmlpull/";

  public LayoutPsiPullParserTest() {
  }

  public void testDesignAttributes() throws Exception {
    @SuppressWarnings("SpellCheckingInspection")
    VirtualFile virtualFile = myFixture.copyFileToProject("xmlpull/designtime.xml", "res/layout/designtime.xml");
    assertNotNull(virtualFile);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            StudioResourceRepositoryManager.getInstance(myModule));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("TextView", parser.getName());
    assertEquals("@+id/first", parser.getAttributeValue(ANDROID_URI, ATTR_ID));
    assertEquals(END_TAG, parser.nextTag());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("TextView", parser.getName());
    assertEquals("fill_parent", parser.getAttributeValue(ANDROID_URI, ATTR_LAYOUT_WIDTH)); // auto converted from match_parent
    assertEquals("wrap_content", parser.getAttributeValue(ANDROID_URI, ATTR_LAYOUT_HEIGHT));
    assertEquals("Designtime Text", parser.getAttributeValue(ANDROID_URI, ATTR_TEXT)); // overriding runtime text attribute
    assertEquals("@android:color/darker_gray", parser.getAttributeValue(ANDROID_URI, "textColor"));
    assertEquals(END_TAG, parser.nextTag());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("TextView", parser.getName());
    assertEquals("@+id/blank", parser.getAttributeValue(ANDROID_URI, ATTR_ID));
    assertEquals("", parser.getAttributeValue(ANDROID_URI, ATTR_TEXT)); // Don't unset when no framework attribute is defined
    assertEquals(END_TAG, parser.nextTag());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("ListView", parser.getName());
    assertEquals("@+id/listView", parser.getAttributeValue(ANDROID_URI, ATTR_ID));
    assertNull(parser.getAttributeValue(ANDROID_URI, "fastScrollAlwaysVisible")); // Cleared by overriding defined framework attribute
  }

  public void testRootFragment() throws Exception {
    @SuppressWarnings("SpellCheckingInspection")
    VirtualFile virtualFile = myFixture.copyFileToProject("xmlpull/root_fragment.xml", "res/layout/root_fragment.xml");
    assertNotNull(virtualFile);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            StudioResourceRepositoryManager.getInstance(myModule));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("FrameLayout", parser.getName()); // Automatically inserted surrounding the <include>
    assertEquals(7, parser.getAttributeCount());
    assertEquals("@+id/item_list", parser.getAttributeValue(ANDROID_URI, ATTR_ID));
    assertEquals("com.unit.test.app.ItemListFragment", parser.getAttributeValue(ANDROID_URI, ATTR_NAME));
    assertEquals("fill_parent", parser.getAttributeValue(ANDROID_URI, "layout_width"));
    assertEquals("fill_parent", parser.getAttributeValue(ANDROID_URI, "layout_height"));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("include", parser.getName());
    assertEquals(null, parser.getAttributeValue(ANDROID_URI, ATTR_ID));
    //noinspection ConstantConditions
    assertEquals("@android:layout/list_content", parser.getAttributeValue(null, ATTR_LAYOUT));
    assertEquals("fill_parent", parser.getAttributeValue(ANDROID_URI, "layout_width"));
    assertEquals("fill_parent", parser.getAttributeValue(ANDROID_URI, "layout_height"));
    assertEquals(END_TAG, parser.nextTag());
  }

  public void testFrameLayoutInclude() throws Exception {
    @SuppressWarnings("SpellCheckingInspection")
    VirtualFile virtualFile = myFixture.copyFileToProject("xmlpull/frame_tools_layout.xml", "res/layout/frame_tools_layout.xml");
    assertNotNull(virtualFile);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            StudioResourceRepositoryManager.getInstance(myModule));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("FrameLayout", parser.getName()); // Automatically inserted surrounding the <include>
    assertEquals(3, parser.getAttributeCount());
    assertEquals("fill_parent", parser.getAttributeValue(ANDROID_URI, "layout_width"));
    assertEquals("fill_parent", parser.getAttributeValue(ANDROID_URI, "layout_height"));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("include", parser.getName());
    assertEquals(null, parser.getAttributeValue(ANDROID_URI, ATTR_ID));
    //noinspection ConstantConditions
    assertEquals("@android:layout/list_content", parser.getAttributeValue(null, ATTR_LAYOUT));
    assertEquals("fill_parent", parser.getAttributeValue(ANDROID_URI, "layout_width"));
    assertEquals("fill_parent", parser.getAttributeValue(ANDROID_URI, "layout_height"));
    assertEquals(END_TAG, parser.nextTag());
  }

  public void testVisibleChild() throws Exception {
    @SuppressWarnings("SpellCheckingInspection")
    VirtualFile virtualFile = myFixture.copyFileToProject("xmlpull/visible_child.xml", "res/layout/visible_child.xml");
    assertNotNull(virtualFile);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            StudioResourceRepositoryManager.getInstance(myModule));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("FrameLayout", parser.getName());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("Button", parser.getName());
    assertEquals("New Button", parser.getAttributeValue(ANDROID_URI, ATTR_TEXT));
    assertEquals("gone", parser.getAttributeValue(ANDROID_URI, ATTR_VISIBILITY));
    assertEquals(END_TAG, parser.nextTag());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("CheckBox", parser.getName());
    assertEquals("New CheckBox", parser.getAttributeValue(ANDROID_URI, ATTR_TEXT));
    assertEquals("visible", parser.getAttributeValue(ANDROID_URI, ATTR_VISIBILITY));
    assertEquals(END_TAG, parser.nextTag());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("TextView", parser.getName());
    assertEquals("New TextView", parser.getAttributeValue(ANDROID_URI, ATTR_TEXT));
    assertEquals("gone", parser.getAttributeValue(ANDROID_URI, ATTR_VISIBILITY));
    assertEquals(END_TAG, parser.nextTag());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("Switch", parser.getName());
    assertEquals("New Switch", parser.getAttributeValue(ANDROID_URI, ATTR_TEXT));
    assertEquals("visible", parser.getAttributeValue(ANDROID_URI, ATTR_VISIBILITY));
    assertEquals(END_TAG, parser.nextTag());
  }

  public void test1() throws Exception {
    checkFile("layout.xml", ResourceFolderType.LAYOUT);
  }

  public void test2() throws Exception {
    checkFile("simple.xml",  ResourceFolderType.LAYOUT);
  }

  public void testSrcCompat() throws Exception {
    VirtualFile virtualFile = myFixture.copyFileToProject("xmlpull/srccompat.xml", "res/layout/srccompat.xml");
    assertNotNull(virtualFile);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            StudioResourceRepositoryManager.getInstance(myModule));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(START_TAG, parser.nextTag()); // ImageView
    assertEquals("ImageView", parser.getName());
    assertEquals("@drawable/normal_src", parser.getAttributeValue(ANDROID_URI, ATTR_SRC));
    assertEquals("@drawable/compat_src", parser.getAttributeValue(AUTO_URI, "srcCompat"));
    parser.setUseSrcCompat(true);
    assertEquals("@drawable/compat_src", parser.getAttributeValue(ANDROID_URI, ATTR_SRC));
    assertEquals("@drawable/compat_src", parser.getAttributeValue(AUTO_URI, "srcCompat"));
    parser.setUseSrcCompat(false);
    assertEquals(END_TAG, parser.nextTag()); // ImageView (@id/first)

    assertEquals(START_TAG, parser.nextTag()); // ImageView (@id/second)
    assertNull(parser.getAttributeValue(ANDROID_URI, ATTR_SRC));
    parser.setUseSrcCompat(true);
    assertEquals("@drawable/compat_src_2", parser.getAttributeValue(ANDROID_URI, ATTR_SRC));
    parser.setUseSrcCompat(false);
    assertEquals(END_TAG, parser.nextTag()); // ImageView (@id/second)

    assertEquals(START_TAG, parser.nextTag()); // NotAImageView (@id/third)
    assertEquals("@drawable/compat_src_3", parser.getAttributeValue(AUTO_URI, "srcCompat"));
    assertEquals("@drawable/normal_src_3", parser.getAttributeValue(ANDROID_URI, ATTR_SRC));
    parser.setUseSrcCompat(true);
    assertEquals("@drawable/normal_src_3", parser.getAttributeValue(ANDROID_URI, ATTR_SRC));
    parser.setUseSrcCompat(false);
    assertEquals(END_TAG, parser.nextTag()); // NotAImageView (@id/third)
  }

  public void testAdAndMapViews() throws Exception {
    VirtualFile virtualFile = myFixture.copyFileToProject("xmlpull/adandmapviews.xml", "res/layout/adandmapviews.xml");
    assertNotNull(virtualFile);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            StudioResourceRepositoryManager.getInstance(myModule));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(START_TAG, parser.nextTag()); // ImageView
    assertEquals("ImageView", parser.getName());
    assertEquals(4, parser.getAttributeCount()); // id + layout_width + layout_height + src
    assertEquals(END_TAG, parser.nextTag()); // ImageView (@id/first)

    assertEquals(START_TAG, parser.nextTag()); // com.google.android.gms.maps.MapView (@id/second)
    assertEquals("com.google.android.gms.maps.MapView", parser.getName());
    assertEquals(6, parser.getAttributeCount()); // id + layout_ * 2 + tools:background + tools:minHeight + tools:minWidth
    assertEquals("50dp", parser.getAttributeValue(TOOLS_URI, ATTR_MIN_WIDTH));
    assertEquals("50dp", parser.getAttributeValue(TOOLS_URI, ATTR_MIN_HEIGHT));
    assertEquals("#AAA", parser.getAttributeValue(TOOLS_URI, ATTR_BACKGROUND));
    assertEquals(END_TAG, parser.nextTag()); // com.google.android.gms.maps.MapView (@id/second) (@id/second)

    assertEquals(START_TAG, parser.nextTag()); // com.google.android.gms.ads.AdView (@id/third)
    assertEquals("com.google.android.gms.ads.AdView", parser.getName());
    assertEquals(6, parser.getAttributeCount()); // id + layout_ * 2 + tools:background + tools:minHeight + tools:minWidth
    assertEquals("50dp", parser.getAttributeValue(TOOLS_URI, ATTR_MIN_WIDTH));
    assertEquals("50dp", parser.getAttributeValue(TOOLS_URI, ATTR_MIN_HEIGHT));
    assertEquals("#AAA", parser.getAttributeValue(TOOLS_URI, ATTR_BACKGROUND));
    assertEquals(END_TAG, parser.nextTag()); // com.google.android.gms.ads.AdView (@id/third)
  }

  /**
   * Verifies that the passed parser contains an empty LinearLayout. That layout is returned by the LayoutPsiPullParser when the passed
   * file is invalid or empty.
   */
  private static void assertEmptyParser(LayoutPullParser parser) throws XmlPullParserException {
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(END_TAG, parser.nextTag());
  }

  public void testEmptyLayout() throws XmlPullParserException {
    XmlFile emptyFile = mock(XmlFile.class);
    when(emptyFile.getRootTag()).thenReturn(null);
    when(emptyFile.getProject()).thenReturn(getProject());
    RenderLogger logger = new RenderLogger("test", myModule);
    assertEmptyParser(LayoutPsiPullParser.create(emptyFile, logger, StudioResourceRepositoryManager.getInstance(myModule)));
    XmlTag emptyTag = mock(XmlTag.class);
    assertEmptyParser(new LayoutPsiPullParser(mock(XmlTag.class), logger, true,
                                              null, StudioResourceRepositoryManager.getInstance(myModule)));

    when(emptyTag.isValid()).thenReturn(true);
    assertEmptyParser(new LayoutPsiPullParser(mock(XmlTag.class), logger, true,
                                              null,  StudioResourceRepositoryManager.getInstance(myModule)));
  }

  public void testAaptAttr() throws Exception {
    VirtualFile virtualFile = myFixture.copyFileToProject("xmlpull/aaptattr.xml", "res/layout/aaptattr.xml");
    assertNotNull(virtualFile);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;
    long expectedId = AaptAttrAttributeSnapshot.ourUniqueId.get();
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            StudioResourceRepositoryManager.getInstance(myModule));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(START_TAG, parser.nextTag()); // ImageView
    assertEquals("ImageView", parser.getName());
    assertEquals("@aapt:_aapt/aapt" + expectedId, parser.getAttributeValue(ANDROID_URI, ATTR_SRC));
    assertEquals(END_TAG, parser.nextTag()); // ImageView (@id/first)

    assertEquals(START_TAG, parser.nextTag()); // ImageView (@id/second)
    assertEquals("@aapt:_aapt/aapt" + (expectedId + 1), parser.getAttributeValue(ANDROID_URI, ATTR_SRC));
    assertEquals(END_TAG, parser.nextTag()); // ImageView

    assertEquals("21dp", parser.getAaptDeclaredAttrs().get(Long.toString(expectedId)).getAttribute("width", ANDROID_URI));
    assertEquals("22dp", parser.getAaptDeclaredAttrs().get(Long.toString(expectedId + 1)).getAttribute("width", ANDROID_URI));
  }


  public void testMergeTag() {
    VirtualFile virtualFile = myFixture.copyFileToProject("xmlpull/merge.xml", "res/layout/merge.xml");
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;

    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            true, null, StudioResourceRepositoryManager.getInstance(myModule),
                                                            0);
    assertEquals("LinearLayout", parser.myRoot.tagName);
    assertEquals(VALUE_MATCH_PARENT, parser.myRoot.getAttribute(ATTR_LAYOUT_WIDTH, ANDROID_URI));
    assertEquals(VALUE_MATCH_PARENT, parser.myRoot.getAttribute(ATTR_LAYOUT_HEIGHT, ANDROID_URI));
    assertEquals("Button1", parser.myRoot.children.get(0).getAttribute("text"));

    // Now, do not honor the parentTag. We should get the <merge> tag as root.
    parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                        false, null, StudioResourceRepositoryManager.getInstance(myModule),
                                        0);
    assertEquals("merge", parser.myRoot.tagName);
    assertEquals("Button1", parser.myRoot.children.get(0).getAttribute("text"));
  }

  public void testToolsAttributes() throws Exception {
    @Language("XML")
    final String content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                           "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                           "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                           "    android:layout_width=\"match_parent\"\n" +
                           "    android:layout_height=\"match_parent\"\n" +
                           "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                           "    android:orientation=\"horizontal\">\n" +
                           "    <TextView\n" +
                           "        android:layout_width=\"wrap_content\"\n" +
                           "        android:layout_height=\"wrap_content\"\n" +
                           "        app:autoSizeText=\"none\"\n" +
                           "        tools:autoSizeText=\"uniform\"\n" +
                           "        android:text=\"Hello world\"\n" +
                           "        tools:text=\"Tools content\"/>\n" +
                           "</LinearLayout>";
    PsiFile psiFile = myFixture.addFileToProject("res/layout/layout.xml", content);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            StudioResourceRepositoryManager.getInstance(myModule));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(START_TAG, parser.nextTag()); // TextView
    assertEquals("TextView", parser.getName());
    assertEquals(6, parser.getAttributeCount()); // layout_width + layout_height + 2*autoSizeText + 2*text

    assertEquals("uniform", parser.getAttributeValue(TOOLS_URI, "autoSizeText"));
    assertEquals("uniform", parser.getAttributeValue(AUTO_URI, "autoSizeText"));
    assertEquals("Tools content", parser.getAttributeValue(ANDROID_URI, "text"));
  }

  public void testAppAttributes() throws XmlPullParserException {
    @Language("XML")
    final String content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                           "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                           "    android:layout_width=\"match_parent\"\n" +
                           "    android:layout_height=\"match_parent\"\n" +
                           "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                           "    android:orientation=\"horizontal\">\n" +
                           "    <TextView\n" +
                           "        android:layout_width=\"wrap_content\"\n" +
                           "        android:layout_height=\"wrap_content\"\n" +
                           "        android:text=\"Content\"\n" +
                           "        app:randomAttr=\"123\"/>\n" +
                           "</LinearLayout>";
    PsiFile psiFile = myFixture.addFileToProject("res/layout/layout.xml", content);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            StudioResourceRepositoryManager.getInstance(myModule));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(START_TAG, parser.nextTag()); // TextView
    assertEquals("TextView", parser.getName());
    // Make sure that library namespaces are converted into app
    assertEquals("123", parser.getAttributeValue("http://schemas.android.com/apk/res/foo.bar", "randomAttr"));
    // Check that we do not accidentally convert android namespace
    assertNull(parser.getAttributeValue("http://schemas.android.com/apk/res/foo.bar", "text"));
  }

  public void testSampleDataOffset() throws XmlPullParserException {
    @Language("XML")
    final String content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                           "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                           "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                           "    android:layout_width=\"match_parent\"\n" +
                           "    android:layout_height=\"match_parent\"\n" +
                           "    android:orientation=\"horizontal\">\n" +
                           "    <TextView\n" +
                           "        android:layout_width=\"wrap_content\"\n" +
                           "        android:layout_height=\"wrap_content\"\n" +
                           "        tools:text=\"@tools:sample/city\"/>\n" +
                           "    <TextView\n" +
                           "        android:layout_width=\"wrap_content\"\n" +
                           "        android:layout_height=\"wrap_content\"\n" +
                           "        tools:text=\"@tools:sample/city\"/>\n" +
                           "    <TextView\n" +
                           "        android:layout_width=\"wrap_content\"\n" +
                           "        android:layout_height=\"wrap_content\"\n" +
                           "        tools:text=\"@tools:sample/city\"/>\n" +
                           "</LinearLayout>";
    PsiFile psiFile = myFixture.addFileToProject("res/layout/layout.xml", content);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            false, null, StudioResourceRepositoryManager.getInstance(myModule),
                                                            3);
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(START_TAG, parser.nextTag()); // 1st TextView
    assertEquals("TextView", parser.getName());
    assertEquals("@tools:sample/city[3]", parser.getAttributeValue(TOOLS_URI, "text"));
    assertEquals(END_TAG, parser.nextTag());

    assertEquals(START_TAG, parser.nextTag()); // 2nd TextView
    assertEquals("TextView", parser.getName());
    assertEquals("@tools:sample/city[4]", parser.getAttributeValue(TOOLS_URI, "text"));
    assertEquals(END_TAG, parser.nextTag());

    assertEquals(START_TAG, parser.nextTag()); // 3rd TextView
    assertEquals("TextView", parser.getName());
    assertEquals("@tools:sample/city[5]", parser.getAttributeValue(TOOLS_URI, "text"));
    assertEquals(END_TAG, parser.nextTag());
  }

  public void testSampleDataInterval() throws XmlPullParserException {
    @Language("XML")
    final String content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                           "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                           "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                           "    android:layout_width=\"match_parent\"\n" +
                           "    android:layout_height=\"match_parent\"\n" +
                           "    android:orientation=\"horizontal\">\n" +
                           "    <TextView\n" +
                           "        android:layout_width=\"wrap_content\"\n" +
                           "        android:layout_height=\"wrap_content\"\n" +
                           "        tools:text=\"@tools:sample/city[5:6]\"/>\n" +
                           "    <TextView\n" +
                           "        android:layout_width=\"wrap_content\"\n" +
                           "        android:layout_height=\"wrap_content\"\n" +
                           "        tools:text=\"@tools:sample/city[5:6]\"/>\n" +
                           "    <TextView\n" +
                           "        android:layout_width=\"wrap_content\"\n" +
                           "        android:layout_height=\"wrap_content\"\n" +
                           "        tools:text=\"@tools:sample/city[5:6]\"/>\n" +
                           "</LinearLayout>";
    PsiFile psiFile = myFixture.addFileToProject("res/layout/layout.xml", content);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            StudioResourceRepositoryManager.getInstance(myModule));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(START_TAG, parser.nextTag()); // 1st TextView
    assertEquals("TextView", parser.getName());
    assertEquals("@tools:sample/city[5]", parser.getAttributeValue(TOOLS_URI, "text"));
    assertEquals(END_TAG, parser.nextTag());

    assertEquals(START_TAG, parser.nextTag()); // 2nd TextView
    assertEquals("TextView", parser.getName());
    assertEquals("@tools:sample/city[6]", parser.getAttributeValue(TOOLS_URI, "text"));
    assertEquals(END_TAG, parser.nextTag());

    assertEquals(START_TAG, parser.nextTag()); // 3rd TextView
    assertEquals("TextView", parser.getName());
    assertEquals("@tools:sample/city[5]", parser.getAttributeValue(TOOLS_URI, "text"));
    assertEquals(END_TAG, parser.nextTag());
  }

  public void testDatabinding() throws Exception {
    @Language("XML")
    String contents = "<merge xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "  xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                      "  android:layout_width=\"match_parent\"\n" +
                      "  android:layout_height=\"match_parent\"\n" +
                      "  android:orientation=\"vertical\"\n" +
                      "  tools:parentTag=\"LinearLayout\">\n" +
                      "\n" +
                      "  <TextView\n" +
                      "      android:id=\"@+id/test1\"\n" +
                      "      android:layout_width=\"wrap_content\"\n" +
                      "      android:layout_height=\"wrap_content\"\n" +
                      "      tools:text=\"Hello\"/>\n" +
                      "  \n" +
                      "  <FrameLayout\n" +
                      "      android:layout_width=\"wrap_content\"\n" +
                      "      android:layout_height=\"wrap_content\">\n" +
                      "    <TextView\n" +
                      "        android:id=\"@+id/test2\"\n" +
                      "        android:layout_width=\"wrap_content\"\n" +
                      "        android:layout_height=\"wrap_content\"\n" +
                      "        android:text=\"World\" />\n" +
                      "  </FrameLayout>\n" +
                      "\n" +
                      "</merge>\n";
    PsiFile noLayoutPsiFile = myFixture.addFileToProject("res/layout/no_data_binding.xml", contents);
    PsiFile layoutPsiFile = myFixture.addFileToProject("res/layout/data_binding.xml",
                                                       "<layout>" + contents + "</layout>");
    XmlFile xmlFile = (XmlFile)layoutPsiFile;

    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            StudioResourceRepositoryManager.getInstance(myModule));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("TextView", parser.getName());
    assertEquals("layout/data_binding_0", parser.getAttributeValue(ANDROID_URI, ATTR_TAG));
    assertEquals(END_TAG, parser.nextTag());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("FrameLayout", parser.getName());
    assertEquals("layout/data_binding_1", parser.getAttributeValue(ANDROID_URI, ATTR_TAG));
    // The children should not have tag
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("TextView", parser.getName());
    assertNull(parser.getAttributeValue(ANDROID_URI, ATTR_TAG));

    // Now check that if the file is not a databinding layout, the tags are not included
    xmlFile = (XmlFile)noLayoutPsiFile;

    parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                        StudioResourceRepositoryManager.getInstance(myModule));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("TextView", parser.getName());
    assertNull(parser.getAttributeValue(ANDROID_URI, ATTR_TAG));
    assertEquals(END_TAG, parser.nextTag());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("FrameLayout", parser.getName());
    assertNull(parser.getAttributeValue(ANDROID_URI, ATTR_TAG));
  }

  /**
   * When a ListView does not contain an id, we must dynamically generate one so we can bind the sample data to it.
   * http://b/68304427
   */
  public void testListViewDynamicId() throws Exception {
    @Language("XML")
    String contents = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "    android:layout_width=\"match_parent\"\n" +
                      "    android:layout_height=\"match_parent\">\n" +
                      "   <ListView\n" +
                      "       android:layout_width=\"match_parent\"\n" +
                      "       android:layout_height=\"match_parent\" />\n" +
                      "</LinearLayout>";
    PsiFile layoutPsiFile = myFixture.addFileToProject("res/layout/no_data_binding.xml", contents);
    XmlFile xmlFile = (XmlFile)layoutPsiFile;

    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            StudioResourceRepositoryManager.getInstance(myModule));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("ListView", parser.getName());
    assertEquals("@+id/_dynamic", parser.getAttributeValue(ANDROID_URI, ATTR_ID));
    assertEquals(END_TAG, parser.nextTag());
  }

  public void testNamespaces() throws XmlPullParserException {
    VirtualFile virtualFile = myFixture.copyFileToProject("xmlpull/namespaces.xml", "res/layout/my_layout.xml");
    assertNotNull(virtualFile);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            StudioResourceRepositoryManager.getInstance(myModule));

    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(ANDROID_URI, parser.getNamespace("a"));
    assertNull(parser.getNamespace("android"));
    assertNull(parser.getNamespace("a2"));


    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(ANDROID_URI, parser.getNamespace("a"));
    assertEquals(ANDROID_URI, parser.getNamespace("a2"));
    assertNull(parser.getNamespace("android"));

    assertEquals(START_TAG, parser.nextTag());
    assertEquals("ImageView", parser.getName());
    assertEquals(URI_PREFIX + "com.example.aaa", parser.getNamespace("a"));
    assertEquals(ANDROID_URI, parser.getNamespace("a2"));
    assertNull(parser.getNamespace("android"));
  }

  public void testSrcCompatTools() throws XmlPullParserException {
    @Language("XML")
    final String layout = "<ImageView xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                          "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                          "    android:layout_width=\"wrap_content\"\n" +
                          "    android:layout_height=\"wrap_content\"\n" +
                          "    android:src=\"value\"" +
                          "    tools:srcCompat=\"srcCompatValue\" />";

    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/my_layout.xml", layout);
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            StudioResourceRepositoryManager.getInstance(myModule));
    parser.setUseSrcCompat(true); // Enable srcCompat replacing src values
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("ImageView", parser.getName());
    // when using AppCompat, the srcCompat value takes precedence over the src. This is done to avoid the AppCompat vector drawable loader
    // from trying to load drawables since it does not support the layoutlib resources and it would fail.
    assertEquals("srcCompatValue", parser.getAttributeValue(ANDROID_URI, ATTR_SRC));
  }

  public void testNavigation() throws XmlPullParserException {
    @Language("XML")
    String layout = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "          xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                          "    android:layout_width=\"match_parent\"\n" +
                          "    android:layout_height=\"match_parent\">\n" +
                          "  <fragment\n" +
                          "      android:id=\"@+id/fragment\"\n" +
                          "      android:name=\"androidx.navigation.fragment.NavHostFragment\"\n" +
                          "      android:layout_width=\"match_parent\"\n" +
                          "      android:layout_height=\"match_parent\"\n" +
                          "      app:navGraph=\"@navigation/mobile_navigation\" />\n" +
                          "</LinearLayout>";

    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/my_layout.xml", layout);

    String nav = "<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                 "    app:startDestination=\"@+id/launcher_home\">\n" +
                 "    <fragment\n" +
                 "        android:id=\"@+id/launcher_home\"\n" +
                 "        tools:layout=\"@layout/main_fragment\">\n";
    myFixture.addFileToProject("res/navigation/mobile_navigation.xml", nav);

    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = manager.getConfiguration(xmlFile.getVirtualFile());
    ResourceResolver resourceResolver = configuration.getResourceResolver();

    LayoutPsiPullParser parser =
      LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                 null, Density.MEDIUM, resourceResolver,
                                 StudioResourceRepositoryManager.getInstance(myModule), true);
    assertEquals(START_TAG, parser.nextTag());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("include", parser.getName());
    assertEquals("@layout/main_fragment", parser.getAttributeValue(null, ATTR_LAYOUT));
  }

  public void testNavigationNestedStartDestination() throws XmlPullParserException {
    @Language("XML")
    final String layout = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "          xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\">\n" +
                    "  <fragment\n" +
                    "      android:id=\"@+id/fragment\"\n" +
                    "      android:name=\"androidx.navigation.fragment.NavHostFragment\"\n" +
                    "      android:layout_width=\"match_parent\"\n" +
                    "      android:layout_height=\"match_parent\"\n" +
                    "      app:navGraph=\"@navigation/mobile_navigation\" />\n" +
                    "</LinearLayout>";

    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/my_layout.xml", layout);

    @Language("XML") final String nav = "<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                        "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                                        "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                                        "    app:startDestination=\"@+id/nested_graph\">\n" +
                                        "    <navigation\n" +
                                        "        android:id=\"@+id/nested_graph\"\n" +
                                        "        app:startDestination=\"@+id/launcher_home\">\n" +
                                        "        <fragment\n" +
                                        "            android:id=\"@+id/launcher_home\"\n" +
                                        "            tools:layout=\"@layout/main_fragment\"/>\n" +
                                        "    </navigation>\n" +
                                        "</navigation>\n";
    myFixture.addFileToProject("res/navigation/mobile_navigation.xml", nav);

    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = manager.getConfiguration(xmlFile.getVirtualFile());
    ResourceResolver resourceResolver = configuration.getResourceResolver();

    LayoutPsiPullParser parser =
      LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                 null, Density.MEDIUM, resourceResolver,
                                 StudioResourceRepositoryManager.getInstance(myModule), true);
    assertEquals(START_TAG, parser.nextTag());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("include", parser.getName());
    assertEquals("@layout/main_fragment", parser.getAttributeValue(null, ATTR_LAYOUT));
  }

  public void testMultiLineText() throws XmlPullParserException {
    @Language("XML")
    final String content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                           "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                           "    android:layout_width=\"match_parent\"\n" +
                           "    android:layout_height=\"match_parent\"\n" +
                           "    android:orientation=\"vertical\">\n" +
                           "    <TextView\n" +
                           "        android:layout_width=\"wrap_content\"\n" +
                           "        android:layout_height=\"wrap_content\"\n" +
                           "        android:text=\"This should end up\n" +
                           "being on one line\"/>\n" +
                           "    <TextView\n" +
                           "        android:layout_width=\"wrap_content\"\n" +
                           "        android:layout_height=\"wrap_content\"\n" +
                           "        android:text=\"This should end up\\nbeing on two lines\"/>\n" +
                           "</LinearLayout>";
    PsiFile psiFile = myFixture.addFileToProject("res/layout/layout.xml", content);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            StudioResourceRepositoryManager.getInstance(myModule));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(START_TAG, parser.nextTag()); // First TextView
    assertEquals("TextView", parser.getName());
    assertEquals("This should end up being on one line", parser.getAttributeValue(ANDROID_URI, "text"));
    assertEquals(END_TAG, parser.nextTag());
    assertEquals(START_TAG, parser.nextTag()); // Second TextView
    assertEquals("TextView", parser.getName());
    assertEquals("This should end up\nbeing on two lines", parser.getAttributeValue(ANDROID_URI, "text"));
  }

  public void testDisableToolsVisibilityAndPosition() throws XmlPullParserException {
    @Language("XML")
    final String content = "<androidx.constraintlayout.widget.ConstraintLayout\n" +
                           "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                           "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                           "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                           "    android:layout_width=\"match_parent\"\n" +
                           "    android:layout_height=\"match_parent\">\n" +
                           "\n" +
                           "    <Button\n" +
                           "        android:id=\"@+id/button\"\n" +
                           "        android:layout_width=\"wrap_content\"\n" +
                           "        android:layout_height=\"wrap_content\"\n" +
                           "        android:text=\"Button\"\n" +
                           "        app:layout_constraintEnd_toEndOf=\"parent\"\n" +
                           "        app:layout_constraintStart_toStartOf=\"parent\"\n" +
                           "        tools:layout_editor_absoluteY=\"116dp\" />\n" +
                           "\n" +
                           "    <ImageView\n" +
                           "        android:id=\"@+id/imageView2\"\n" +
                           "        android:layout_width=\"wrap_content\"\n" +
                           "        android:layout_height=\"wrap_content\"\n" +
                           "        android:layout_marginTop=\"40dp\"\n" +
                           "        tools:layout_marginTop=\"0dp\"\n" +
                           "        tools:visibility=\"invisible\"\n" +
                           "        app:srcCompat=\"@drawable/abc\"\n" +
                           "        app:layout_constraintEnd_toEndOf=\"@+id/button\"\n" +
                           "        app:layout_constraintStart_toStartOf=\"@+id/button\"\n" +
                           "        app:layout_constraintTop_toBottomOf=\"@+id/button\"\n" +
                           "        tools:srcCompat=\"@tools:sample/avatars\" />\n" +
                           "\n" +
                           "</androidx.constraintlayout.widget.ConstraintLayout>";
    PsiFile psiFile = myFixture.addFileToProject("res/layout/layout.xml", content);
    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile) psiFile;
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            null, Density.MEDIUM, null,
                                                            StudioResourceRepositoryManager.getInstance(myModule), false);

    assertEquals(START_TAG, parser.nextTag());
    assertEquals("androidx.constraintlayout.widget.ConstraintLayout", parser.getName());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("Button", parser.getName());
    assertNull(parser.getAttributeValue(TOOLS_URI, "layout_editor_absoluteY"));
    assertEquals(END_TAG, parser.nextTag());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("ImageView", parser.getName());
    assertNull(parser.getAttributeValue(TOOLS_URI, "layout_marginTop"));
    assertEquals("0dp", parser.getAttributeValue(ANDROID_URI, "layout_marginTop"));
    assertNull(parser.getAttributeValue(TOOLS_URI, "visibility"));
    assertNull(parser.getAttributeValue(TOOLS_URI, "srcCompat"));
    assertEquals("@tools:sample/avatars[0]", parser.getAttributeValue(AUTO_URI, "srcCompat"));
  }

  // http://b/258051626
  // paddingHorizontal will take precedence and be used over "paddingLeft" or "paddingRight".
  // paddingVertical will take precedence and be used over "paddingTop" or "paddingBottom".
  public void testPaddingOverrides() throws XmlPullParserException {
    // Assert paddingHorizontal overrides existing paddingLeft and missing paddingRight
    {
      @Language("XML") final String content = "<TextView\n" +
                                              "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                              "    android:layout_width=\"match_parent\"\n" +
                                              "    android:layout_height=\"match_parent\"\n" +
                                              "    android:paddingLeft=\"1dp\"\n" +
                                              "    android:paddingHorizontal=\"12dp\" />\n";
      PsiFile psiFile = myFixture.addFileToProject("res/layout/layout.xml", content);
      XmlFile xmlFile = (XmlFile) psiFile;
      LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                              null, Density.MEDIUM, null,
                                                              StudioResourceRepositoryManager.getInstance(myModule), false);
      assertEquals(START_TAG, parser.nextTag());
      assertEquals("12dp", parser.getAttributeValue(ANDROID_URI, "paddingLeft"));
      assertEquals("12dp", parser.getAttributeValue(ANDROID_URI, "paddingRight"));
      assertEquals("12dp", parser.getAttributeValue(ANDROID_URI, "paddingHorizontal"));
      assertNull(parser.getAttributeValue(ANDROID_URI, "paddingVertical"));
    }

    // Assert tools paddingHorizontal overrides existing paddingLeft and missing paddingRight
    {
      @Language("XML") final String content = "<TextView\n" +
                                              "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                              "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                                              "    android:layout_width=\"match_parent\"\n" +
                                              "    android:layout_height=\"match_parent\"\n" +
                                              "    android:paddingLeft=\"1dp\"\n" +
                                              "    tools:paddingHorizontal=\"12dp\" />\n";
      PsiFile psiFile = myFixture.addFileToProject("res/layout/layout.xml", content);
      XmlFile xmlFile = (XmlFile) psiFile;
      LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                              null, Density.MEDIUM, null,
                                                              StudioResourceRepositoryManager.getInstance(myModule), false);
      assertEquals(START_TAG, parser.nextTag());
      assertEquals("12dp", parser.getAttributeValue(ANDROID_URI, "paddingLeft"));
      assertEquals("12dp", parser.getAttributeValue(ANDROID_URI, "paddingRight"));
      assertEquals("12dp", parser.getAttributeValue(ANDROID_URI, "paddingHorizontal"));
      assertNull(parser.getAttributeValue(ANDROID_URI, "paddingVertical"));
    }

    // Assert tools paddingVertical overrides existing paddingTop and missing paddingBottom
    {
      @Language("XML") final String content = "<TextView\n" +
                                              "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                              "    android:layout_width=\"match_parent\"\n" +
                                              "    android:layout_height=\"match_parent\"\n" +
                                              "    android:paddingTop=\"1dp\"\n" +
                                              "    android:paddingVertical=\"12dp\" />\n";
      PsiFile psiFile = myFixture.addFileToProject("res/layout/layout.xml", content);
      XmlFile xmlFile = (XmlFile) psiFile;
      LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                              null, Density.MEDIUM, null,
                                                              StudioResourceRepositoryManager.getInstance(myModule), false);
      assertEquals(START_TAG, parser.nextTag());
      assertEquals("12dp", parser.getAttributeValue(ANDROID_URI, "paddingTop"));
      assertEquals("12dp", parser.getAttributeValue(ANDROID_URI, "paddingBottom"));
      assertEquals("12dp", parser.getAttributeValue(ANDROID_URI, "paddingVertical"));
      assertNull(parser.getAttributeValue(ANDROID_URI, "paddingHorizontal"));
    }

    // Assert missing paddingHorizontal/paddingVertical do not break valid paddingLeft/Right/Top/Bottom
    {
      @Language("XML") final String content = "<TextView\n" +
                                              "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                              "    android:layout_width=\"match_parent\"\n" +
                                              "    android:layout_height=\"match_parent\"\n" +
                                              "    android:paddingRight=\"1dp\"\n" +
                                              "    android:paddingBottom=\"2dp\" />\n";
      PsiFile psiFile = myFixture.addFileToProject("res/layout/layout.xml", content);
      XmlFile xmlFile = (XmlFile) psiFile;
      LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                              null, Density.MEDIUM, null,
                                                              StudioResourceRepositoryManager.getInstance(myModule),  false);
      assertEquals(START_TAG, parser.nextTag());
      assertEquals("1dp", parser.getAttributeValue(ANDROID_URI, "paddingRight"));
      assertEquals("2dp", parser.getAttributeValue(ANDROID_URI, "paddingBottom"));
      assertNull(parser.getAttributeValue(ANDROID_URI, "paddingLeft"));
      assertNull(parser.getAttributeValue(ANDROID_URI, "paddingTop"));
      assertNull(parser.getAttributeValue(ANDROID_URI, "paddingHorizontal"));
      assertNull(parser.getAttributeValue(ANDROID_URI, "paddingVertical"));
    }

  }

  enum NextEventType { NEXT, NEXT_TOKEN, NEXT_TAG }

  private void compareParsers(PsiFile file, NextEventType nextEventType) throws Exception {
    assertTrue(file instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)file;
    KXmlParser referenceParser = createReferenceParser(file);
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule),
                                                            StudioResourceRepositoryManager.getInstance(myModule));

    assertEquals("Expected " + name(referenceParser.getEventType()) + " but was "
                 + name(parser.getEventType())
                 + " (at line:column " + describePosition(referenceParser) + ")",
                 referenceParser.getEventType(), parser.getEventType());

    while (true) {
      int expected, next;
      switch (nextEventType) {
        case NEXT:
          expected = referenceParser.next();
          next = parser.next();
          break;
        case NEXT_TOKEN:
          expected = referenceParser.nextToken();
          next = parser.nextToken();
          break;
        case NEXT_TAG: {
          try {
            expected = referenceParser.nextTag();
          } catch (Exception e) {
            expected = referenceParser.getEventType();
          }
          try {
            next = parser.nextTag();
          } catch (Exception e) {
            next = parser.getEventType();
          }
          break;
        }
        default:
          throw new AssertionError("Unexpected type");
      }

      PsiElement element = null;
      if (expected == START_TAG) {
        assertNotNull(parser.getViewCookie());
        assertTrue(parser.getViewCookie() instanceof TagSnapshot);
        element = ((TagSnapshot)parser.getViewCookie()).tag;
      }

      if (expected == START_TAG) {
        assertEquals(referenceParser.getName(), parser.getName());
        if (element != xmlFile.getRootTag()) { // KXmlParser seems to not include xmlns: attributes on the root tag!{
          SortedSet<String> referenceAttributes = new TreeSet<>();
          SortedSet<String> attributes = new TreeSet<>();
          for (int i = 0; i < referenceParser.getAttributeCount(); i++) {
            String s = referenceParser.getAttributePrefix(i) + ':' + referenceParser.getAttributeName(i) + '='
                       + referenceParser.getAttributeValue(i);
            referenceAttributes.add(s);
          }
          for (int i = 0; i < parser.getAttributeCount(); i++) {
            String s = parser.getAttributePrefix(i) + ':' + parser.getAttributeName(i) + '=' + parser.getAttributeValue(i);
            attributes.add(s);
            if (parser.getAttributeNamespace(i) != null) {
              //noinspection ConstantConditions
              assertEquals(normalizeValue(parser.getAttributeValue(i)),
                           normalizeValue(parser.getAttributeValue(parser.getAttributeNamespace(i), parser.getAttributeName(i))));
            }
          }

          assertEquals(referenceAttributes, attributes);
        }

        // We're not correctly implementing this; it turns out Android doesn't need it, so we haven't bothered
        // pulling out the state correctly to do it
        //assertEquals(referenceParser.isEmptyElementTag(), parser.isEmptyElementTag());

        if (element instanceof XmlTag) {
          XmlTag tag = (XmlTag)element;
          for (XmlAttribute attribute : tag.getAttributes()) {
            String namespace = attribute.getNamespace();
            String name = attribute.getLocalName();
            if (namespace.isEmpty()) {
              String prefix = attribute.getNamespacePrefix();
              if (!prefix.isEmpty()) {
                name = prefix + ":" + prefix;
              }
            }
            //noinspection ConstantConditions
            assertEquals(namespace + ':' + name + " in element " + parser.getName(),
                         normalizeValue(referenceParser.getAttributeValue(namespace, name)),
                         normalizeValue(parser.getAttributeValue(namespace, name)));
          }
        }
      } else if (expected == XmlPullParser.TEXT || expected == XmlPullParser.COMMENT) {
        assertEquals(StringUtil.notNullize(referenceParser.getText()).trim(), StringUtil.notNullize(parser.getText()).trim());
      }

      if (expected != next) {
        assertEquals("Expected " + name(expected) + " but was " + name(next)
                     + "(At " + describePosition(referenceParser) + ")",
                     expected, next);
      }
      if (expected == XmlPullParser.END_DOCUMENT) {
        break;
      }
    }
  }

  @Nullable
  private static String normalizeValue(String value) {
    // Some parser translate values; ensure that these are identical
    if (value != null && value.equals(VALUE_MATCH_PARENT)) {
      return VALUE_FILL_PARENT;
    }
    return value;
  }

  private static String name(int event) {
    return XmlPullParser.TYPES[event];
  }

  private void checkFile(String filename, ResourceFolderType folder) throws Exception {
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + filename, "res/" + folder.getName() + "/" + filename);
    assertNotNull(file);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    compareParsers(psiFile, NextEventType.NEXT_TAG);
    // The LayoutPsiPullParser only supports tags, not text (no text is used in layouts)
    //compareParsers(psiFile, NextEventType.NEXT);
    //compareParsers(psiFile, NextEventType.NEXT_TOKEN);
  }

  private static KXmlParser createReferenceParser(PsiFile file) throws XmlPullParserException {
    KXmlParser referenceParser = new KXmlParser();
    referenceParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
    referenceParser.setInput(new StringReader(file.getText()));
    return referenceParser;
  }

  private static String describePosition(KXmlParser referenceParser) {
    return referenceParser.getLineNumber() + ":" + referenceParser.getColumnNumber();
  }
}
