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
package com.android.tools.idea.rendering;

import com.android.resources.ResourceFolderType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.StringReader;
import java.util.SortedSet;
import java.util.TreeSet;

import static com.android.SdkConstants.*;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

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
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule));
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
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule));
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
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule));
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("FrameLayout", parser.getName()); // Automatically inserted surrounding the <include>
    assertEquals(5, parser.getAttributeCount());
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
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule));
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
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule));
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

  enum NextEventType { NEXT, NEXT_TOKEN, NEXT_TAG }

  private void compareParsers(PsiFile file, NextEventType nextEventType) throws Exception {
    assertTrue(file instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)file;
    KXmlParser referenceParser = createReferenceParser(file);
    LayoutPsiPullParser parser = LayoutPsiPullParser.create(xmlFile, new RenderLogger("test", myModule));

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
      if (expected == XmlPullParser.START_TAG) {
        assertNotNull(parser.getViewKey());
        assertNotNull(parser.getViewCookie());
        assertTrue(parser.getViewCookie() instanceof PsiElement);
        element = (PsiElement)parser.getViewCookie();
      }

      if (expected == XmlPullParser.START_TAG) {
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
