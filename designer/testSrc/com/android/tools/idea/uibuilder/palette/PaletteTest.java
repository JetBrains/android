/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.handlers.TextViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.palette.Palette.Group;
import com.google.common.base.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import icons.StudioIcons;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.StringReader;
import java.util.Iterator;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.tools.idea.uibuilder.api.PaletteComponentHandler.IN_PLATFORM;
import static com.android.tools.idea.uibuilder.api.PaletteComponentHandler.NO_PREVIEW;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

public class PaletteTest extends AndroidTestCase {
  private static final ViewHandler STANDARD_VIEW = new ViewHandler();
  private static final ViewHandler STANDARD_TEXT = new TextViewHandler();
  private static final ViewHandler STANDARD_LAYOUT = new ViewGroupHandler();
  private static final Splitter SPLITTER = Splitter.on("\n").trimResults();
  private static final boolean SUGGESTED = true;

  public void testPaletteStructure() throws Exception {
    Palette palette = loadPalette();
    Iterator<Palette.BaseItem> iterator = palette.getItems().iterator();
    Group group1 = assertIsGroup(iterator.next(), "Widgets");
    Group group2 = assertIsGroup(iterator.next(), "Advanced");
    assertFalse(iterator.hasNext());

    iterator = group1.getItems().iterator();
    assertTextViewItem(iterator.next());
    assertLinearLayoutItem(iterator.next());
    assertNormalProgressBarItem(iterator.next());
    assertFalse(iterator.hasNext());

    iterator = group2.getItems().iterator();
    Group group3 = assertIsGroup(iterator.next(), "Distinct");
    assertFalse(iterator.hasNext());

    iterator = group3.getItems().iterator();
    assertIncludeItem(iterator.next());
    assertCoordinatorLayoutItem(iterator.next());
    assertFalse(iterator.hasNext());
  }

  public void testParent() throws Exception {
    checkParents(null, loadPalette().getItems());
  }

  public void testGetById() throws Exception {
    Palette palette = loadPalette();
    assertTextViewItem(palette.getItemById("TextView"));
    assertNormalProgressBarItem(palette.getItemById("NormalProgressBar"));
  }

  private static Palette.Group assertIsGroup(@NotNull Palette.BaseItem item, @NotNull String name) {
    assertTrue(item instanceof Palette.Group);
    Palette.Group group = (Palette.Group)item;
    assertEquals(name, group.getName());
    return group;
  }

  private void assertTextViewItem(@NotNull Palette.BaseItem item) {
    assertStandardTextView(item, TEXT_VIEW, IN_PLATFORM, SUGGESTED);
  }

  @Language("XML")
  private static final String HORIZONTAL_LINEAR_LAYOUT_XML =
    "<LinearLayout\n" +
    "  android:orientation=\"horizontal\"\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"match_parent\">\n" +
    "</LinearLayout>\n";

  private void assertLinearLayoutItem(@NotNull Palette.BaseItem item) {
    checkItem(item, LINEAR_LAYOUT, "LinearLayout (horizontal)", StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_HORZ,
              HORIZONTAL_LINEAR_LAYOUT_XML, NO_PREVIEW, IN_PLATFORM, SUGGESTED);
    checkComponent(createMockComponent(LINEAR_LAYOUT), "LinearLayout (horizontal)", StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_HORZ);
  }

  @Language("XML")
  private static final String NORMAL_PROGRESS_XML =
    "<ProgressBar\n" +
    "  style=\"?android:attr/progressBarStyle\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "/>\n";

  private void assertNormalProgressBarItem(@NotNull Palette.BaseItem item) {
    checkItem(item, "ProgressBar", "ProgressBar", StudioIcons.LayoutEditor.Palette.PROGRESS_BAR, NORMAL_PROGRESS_XML, NORMAL_PROGRESS_XML,
              IN_PLATFORM, SUGGESTED);
    checkComponent(createMockComponent("ProgressBar"), "ProgressBar", StudioIcons.LayoutEditor.Palette.PROGRESS_BAR);
  }

  private void assertCoordinatorLayoutItem(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, COORDINATOR_LAYOUT.defaultName(), DESIGN_LIB_ARTIFACT, SUGGESTED);
  }

  private void assertIncludeItem(@NotNull Palette.BaseItem item) {
    checkItem(item, VIEW_INCLUDE, "<include>", StudioIcons.LayoutEditor.Palette.INCLUDE, "<include/>\n", NO_PREVIEW, IN_PLATFORM,
              !SUGGESTED);
    checkComponent(createMockComponent(VIEW_INCLUDE), "<include>", StudioIcons.LayoutEditor.Palette.INCLUDE);
  }

  private void assertStandardLayout(@NotNull Palette.BaseItem item, @NotNull String tag, @NotNull String expectedGradleCoordinate,
                                    boolean expectedInSuggested) {
    checkItem(item, tag, STANDARD_VIEW.getTitle(tag), STANDARD_LAYOUT.getIcon(tag), STANDARD_LAYOUT.getXml(tag, XmlType.COMPONENT_CREATION),
              NO_PREVIEW, expectedGradleCoordinate, expectedInSuggested);
    checkComponent(createMockComponent(tag), STANDARD_VIEW.getTitle(tag), STANDARD_LAYOUT.getIcon(tag));
  }

  private void assertStandardTextView(@NotNull Palette.BaseItem item, @NotNull String tag, @NotNull String expectedGradleCoordinate,
                                      boolean expectedInSuggested) {
    @Language("XML")
    String xml = STANDARD_TEXT.getXml(tag, XmlType.COMPONENT_CREATION);
    checkItem(item, tag, STANDARD_TEXT.getTitle(tag), STANDARD_TEXT.getIcon(tag), xml, xml, expectedGradleCoordinate, expectedInSuggested);
    NlComponent component = createMockComponent(tag);
    checkComponent(component, String.format("%1$s", tag), STANDARD_TEXT.getIcon(tag));

    when(component.getAttribute(ANDROID_URI, ATTR_TEXT)).thenReturn("My value for " + tag);
    checkComponent(component, String.format("%1$s - \"My value for %1$s\"", tag), STANDARD_TEXT.getIcon(tag));
  }

  private static void checkParents(@Nullable Palette.Group parent, @NotNull List<Palette.BaseItem> items) {
    for (Palette.BaseItem item : items) {
      assertThat(item.getParent()).isSameAs(parent);
      if (item instanceof Group) {
        Group group = (Group)item;
        checkParents(group, group.getItems());
      }
    }
  }

  private static NlComponent createMockComponent(@NotNull String tag) {
    NlComponent component = LayoutTestUtilities.createMockComponent();
    when(component.getTagName()).thenReturn(tag);
    return component;
  }

  private static void checkItem(@NotNull Palette.BaseItem base,
                                @NotNull String expectedTag,
                                @NotNull String expectedTitle,
                                @NotNull Icon expectedIcon,
                                @NotNull @Language("XML") String expectedXml,
                                @NotNull @Language("XML") String expectedDragPreviewXml,
                                @NotNull String expectedGradleCoordinateId,
                                boolean expectedInSuggested) {
    assertTrue(base instanceof Palette.Item);
    Palette.Item item = (Palette.Item)base;

    assertEquals(expectedTag + ".Tag", expectedTag, item.getTagName());
    assertEquals(expectedTag + ".Title", expectedTitle, item.getTitle());
    assertEquals(expectedTag + ".Icon", expectedIcon, item.getIcon());
    assertEquals(expectedTag + ".XML", formatXml(expectedXml), formatXml(item.getXml()));
    assertEquals(expectedTag + ".DragPreviewXML", formatXml(expectedDragPreviewXml), formatXml(item.getDragPreviewXml()));
    assertEquals(expectedTag + ".GradleCoordinateId", expectedGradleCoordinateId, item.getGradleCoordinateId());
    assertEquals(expectedInSuggested, item.isSuggested());
  }

  private void checkComponent(@NotNull NlComponent component, @NotNull String expectedTitle, @NotNull Icon expectedIcon) {
    ViewHandlerManager handlerManager = ViewHandlerManager.get(getProject());
    ViewHandler handler = handlerManager.getHandlerOrDefault(component);
    String title = handler.getTitle(component);
    String attrs = handler.getTitleAttributes(component);
    if (!StringUtil.isEmpty(attrs)) {
      title += " " + attrs;
    }
    assertEquals(component.getTagName() + ".Component.Title", expectedTitle, title);
    assertEquals(component.getTagName() + ".Component.Icon", expectedIcon, handler.getIcon(component));
  }

  @NotNull
  @Language("XML")
  private static String formatXml(@NotNull @Language("XML") String xml) {
    if (xml.equals(NO_PREVIEW)) {
      return xml;
    }
    StringBuilder text = new StringBuilder();
    int indent = 0;
    for (String line : SPLITTER.split(xml)) {
      if (!line.isEmpty()) {
        boolean decrementIndent = line.startsWith("</") || line.startsWith("/>");
        if (decrementIndent && indent > 0) {
          indent--;
        }
        for (int index = 0; index < indent; index++) {
          text.append("  ");
        }
        text.append(line).append("\n");
        if (!decrementIndent && line.startsWith("<")) {
          indent++;
        }
      }
    }
    return text.toString();
  }

  @Language("XML")
  private static final String PALETTE =
    "<palette>\n" +
    "  <group name=\"Widgets\">\n" +
    "    <item tag=\"TextView\" suggested=\"true\"/>\n" +
    "    <item tag=\"LinearLayout\" suggested=\"true\"" +
    "          title=\"LinearLayout (horizontal)\">\n" +
    "      <xml>\n" +
    "        <![CDATA[\n" +
    "            <LinearLayout\n" +
    "              android:orientation=\"horizontal\"\n" +
    "              android:layout_width=\"match_parent\"\n" +
    "              android:layout_height=\"match_parent\">\n" +
    "            </LinearLayout>\n" +
    "          ]]>\n" +
    "      </xml>\n" +
    "    </item>\n" +
    "    <item tag=\"ProgressBar\"\n" +
    "          suggested=\"true\"" +
    "          id=\"NormalProgressBar\"" +
    "          title=\"ProgressBar\">\n" +
    "      <xml reuse=\"preview,drag-preview\">\n" +
    "        <![CDATA[\n" +
    "            <ProgressBar\n" +
    "              style=\"?android:attr/progressBarStyle\"\n" +
    "              android:layout_width=\"wrap_content\"\n" +
    "              android:layout_height=\"wrap_content\"\n" +
    "            />\n" +
    "          ]]>\n" +
    "      </xml>\n" +
    "    </item>\n" +
    "  </group>\n" +
    "  <group name=\"Advanced\">\n" +
    "    <group name=\"Distinct\">\n" +
    "      <item tag=\"include\"/>\n" +
    "      <item tag=\"android.support.design.widget.CoordinatorLayout\" suggested=\"true\"/>\n" +
    "    </group>\n" +
    "  </group>\n" +
    "</palette>\n";

  private Palette loadPalette() throws Exception {
    ViewHandlerManager manager = new ViewHandlerManager(getProject());
    return Palette.parse(new StringReader(PALETTE), manager);
  }
}
