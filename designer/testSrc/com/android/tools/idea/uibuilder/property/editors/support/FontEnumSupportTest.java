/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.editors.support;

import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import icons.StudioIcons;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import javax.swing.*;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class FontEnumSupportTest extends AndroidTestCase {
  @Mock
  private NlPropertyItem myProperty;
  @Mock
  private NlModel myModel;

  private FontEnumSupport mySupport;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject("fonts/customfont.ttf", "res/font/customfont.ttf");
    myFixture.copyFileToProject("fonts/my_circular_font_family_1.xml", "res/font/my_circular_font_family_1.xml");
    myFixture.copyFileToProject("fonts/my_circular_font_family_2.xml", "res/font/my_circular_font_family_2.xml");
    VirtualFile file = myFixture.copyFileToProject("fonts/roboto.xml", "res/font/roboto.xml");

    initMocks(this);
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myFacet).getConfiguration(file);
    when(myProperty.getResolver()).thenReturn(configuration.getResourceResolver());
    when(myProperty.getModel()).thenReturn(myModel);
    when(myModel.getFacet()).thenReturn(myFacet);
    doCallRealMethod().when(myProperty).resolveValue(anyString());

    mySupport = new FontEnumSupport(myProperty);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
      // tests through IJ)
      myProperty = null;
      myModel = null;
      mySupport = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testFindPossibleValues() {
    assertThat(mySupport.getAllValues()).containsExactly(
      new ValueWithDisplayString("customfont", "@font/customfont"),
      new ValueWithDisplayString("my_circular_font_family_1", "@font/my_circular_font_family_1"),
      new ValueWithDisplayString("my_circular_font_family_2", "@font/my_circular_font_family_2"),
      new ValueWithDisplayString("roboto", "@font/roboto"),
      ValueWithDisplayString.SEPARATOR,
      new ValueWithDisplayString("sans-serif", "sans-serif"),
      new ValueWithDisplayString("sans-serif-condensed", "sans-serif-condensed"),
      new ValueWithDisplayString("serif", "serif"),
      new ValueWithDisplayString("monospace", "monospace"),
      new ValueWithDisplayString("serif-monospace", "serif-monospace"),
      new ValueWithDisplayString("casual", "casual"),
      new ValueWithDisplayString("cursive", "cursive"),
      new ValueWithDisplayString("sans-serif-smallcaps", "sans-serif-smallcaps"),
      ValueWithDisplayString.SEPARATOR,
      new ValueWithDisplayString("More Fonts...", null)
    ).inOrder();
  }

  public void testCreateDefaultValue() {
    assertThat(mySupport.createValue(""))
      .isEqualTo(ValueWithDisplayString.UNSET);
  }

  public void testCreateValueWithPrefix() {
    assertThat(mySupport.createValue("@font/customfont"))
      .isEqualTo(new ValueWithDisplayString("customfont", "@font/customfont"));
  }

  public void testCreateNonSystemValueWithoutPrefix() {
    assertThat(mySupport.createValue("customfont"))
      .isEqualTo(new ValueWithDisplayString("customfont", "@font/customfont"));
  }

  public void testCreateValueWithoutPrefix() {
    assertThat(mySupport.createValue("serif"))
      .isEqualTo(new ValueWithDisplayString("serif", "serif"));
  }

  public void testCustomizeCellRendererWithSystemFont() {
    ColoredListCellRenderer<ValueWithDisplayString> renderer = new MyRenderer();
    mySupport.customizeCellRenderer(renderer, new ValueWithDisplayString("casual", "casual"), false);
    assertThat(renderer.getIcon()).isSameAs(StudioIcons.Shell.Filetree.ANDROID_PROJECT);
  }

  public void testCustomizeCellRendererWithEmbeddedFont() {
    ColoredListCellRenderer<ValueWithDisplayString> renderer = new MyRenderer();
    mySupport.customizeCellRenderer(renderer, new ValueWithDisplayString("customfont", "@font/customfont"), false);
    assertThat(renderer.getIcon()).isSameAs(StudioIcons.Shell.Filetree.FONT_FILE);
  }

  public void testCustomizeCellRendererWithDownloadableFont() {
    ColoredListCellRenderer<ValueWithDisplayString> renderer = new MyRenderer();
    mySupport.customizeCellRenderer(renderer, new ValueWithDisplayString("roboto", "@font/roboto"), false);
    assertThat(renderer.getIcon()).isSameAs(StudioIcons.Common.LINK);
  }

  public void testCustomizeCellRendererWithErrorFont() {
    ColoredListCellRenderer<ValueWithDisplayString> renderer = new MyRenderer();
    mySupport.customizeCellRenderer(renderer,
                                    new ValueWithDisplayString("my_circular_font_family_1", "@font/my_circular_font_family_1"), false);
    assertThat(renderer.getIcon()).isSameAs(AllIcons.General.BalloonError);
  }

  private static class MyRenderer extends ColoredListCellRenderer<ValueWithDisplayString> {
    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends ValueWithDisplayString> list,
                                         ValueWithDisplayString value, int index, boolean selected, boolean hasFocus) {
    }
  }
}
