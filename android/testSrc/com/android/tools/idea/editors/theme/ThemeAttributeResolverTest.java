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
package com.android.tools.idea.editors.theme;

import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredElement;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.dom.resources.StyleItem;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class ThemeAttributeResolverTest extends AndroidTestCase {


  public boolean createNewStyle(@NotNull final VirtualFile resourceDir,
                                @NotNull final String newStyleName,
                                @NotNull final String parentStyleName,
                                @Nullable final String colorPrimaryValue,
                                @NotNull final List<String> folders) {
    return new WriteCommandAction<Boolean>(getProject(), "Create new style " + newStyleName) {
      @Override
      protected void run(@NotNull Result<Boolean> result) {
        result.setResult(AndroidResourceUtil.
          createValueResource(getProject(), resourceDir, newStyleName, null, ResourceType.STYLE, "styles.xml", folders,
                              new Processor<ResourceElement>() {
            @Override
            public boolean process(ResourceElement element) {
              assert element instanceof Style;
              final Style style = (Style)element;

              style.getParentStyle().setStringValue(parentStyleName);
              if (colorPrimaryValue != null) {
                StyleItem styleItem = style.addItem();
                styleItem.getName().setStringValue("colorPrimary");
                styleItem.setStringValue(colorPrimaryValue);
              }

              return true;
            }
          }));
      }
    }.execute().getResultObject();
  }

  /**
   * Tests {@link ThemeAttributeResolver#resolveAll(ConfiguredThemeEditorStyle, ThemeResolver)}
   */
  public void testResolveAllVersion() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles.xml", "res/values/styles.xml");
    VirtualFile resourceDir = myFile.getParent().getParent();

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);

    createNewStyle(resourceDir, "ThemeA", "android:Theme", "red", Lists.newArrayList("values-v13", "values-v16"));
    createNewStyle(resourceDir, "ThemeB", "ThemeA", "blue", Lists.newArrayList("values-v12"));
    createNewStyle(resourceDir, "ThemeB", "ThemeA", null, Lists.newArrayList("values-v15"));

    // ResourceFolderRepository needs to rescan the files to pick up the changes.
    UIUtil.dispatchAllInvocationEvents();

    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle style = themeResolver.getTheme("ThemeB");
    assertNotNull(style);

    Set<String> answer = Sets.newHashSet("-v16:red", "-v15:red", "-v14:blue");

    List<EditedStyleItem> items = ThemeAttributeResolver.resolveAll(style, configuration.getConfigurationManager());
    boolean foundColorPrimary = false;
    for (EditedStyleItem item : items) {
      if (item.getName().equals("colorPrimary")) {
        foundColorPrimary = true;
        assertEquals(answer.size(), item.getAllConfiguredItems().size());
        for (ConfiguredElement<ItemResourceValue> value : item.getAllConfiguredItems()) {
          assertTrue(answer.contains(value.getConfiguration().getUniqueKey() + ":" + value.getElement().getValue()));
        }
      }
    }
    assertTrue(foundColorPrimary);
  }

  /**
   * Tests {@link ThemeAttributeResolver#resolveAll(ConfiguredThemeEditorStyle, ThemeResolver)}
   */
  public void testResolveAllEnum() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles.xml", "res/values/styles.xml");
    VirtualFile resourceDir = myFile.getParent().getParent();
    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);

    createNewStyle(resourceDir, "ThemeA", "android:Theme", "red", Lists.newArrayList("values-port", "values-square", "values-land"));
    createNewStyle(resourceDir, "ThemeB", "ThemeA", null, Lists.newArrayList("values", "values-port"));

    // ResourceFolderRepository needs to rescan the files to pick up the changes.
    UIUtil.dispatchAllInvocationEvents();

    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle style = themeResolver.getTheme("ThemeB");
    assertNotNull(style);
    Set<String> answer = Sets.newHashSet("-port:red", "-land:red", "-square:red");

    List<EditedStyleItem> items = ThemeAttributeResolver.resolveAll(style, configuration.getConfigurationManager());
    boolean foundColorPrimary = false;
    for (EditedStyleItem item : items) {
      if (item.getName().equals("colorPrimary")) {
        foundColorPrimary = true;
        assertEquals(answer.size(), item.getAllConfiguredItems().size());
        for (ConfiguredElement<ItemResourceValue> value : item.getAllConfiguredItems()) {
          assertTrue(answer.contains(value.getConfiguration().getUniqueKey() + ":" + value.getElement().getValue()));
        }
      }
    }
    assertTrue(foundColorPrimary);
  }
}
