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

import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.dom.resources.StyleItem;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ThemeAttributeResolverTest extends AndroidTestCase {


  public boolean createNewStyle(@NotNull final String newStyleName,
                                @NotNull final String parentStyleName,
                                @Nullable final String colorPrimaryValue,
                                @NotNull final List<String> folders) {

    return new WriteCommandAction<Boolean>(myModule.getProject(), "Create new style " + newStyleName) {
      @Override
      protected void run(@NotNull Result<Boolean> result) {
        result.setResult(AndroidResourceUtil.
          createValueResource(myModule, newStyleName, null,
                              ResourceType.STYLE, "styles.xml", folders, new Processor<ResourceElement>() {
              @Override
              public boolean process(ResourceElement element) {
                assert element instanceof Style;
                final Style style = (Style)element;

                if (parentStyleName != null) {
                  style.getParentStyle().setStringValue(parentStyleName);
                  if (colorPrimaryValue != null) {
                    StyleItem styleItem = style.addItem();
                    styleItem.getName().setStringValue("colorPrimary");
                    styleItem.setStringValue(colorPrimaryValue);
                  }
                }

                return true;
              }
            }));
      }
    }.execute().getResultObject();
  }
  /**
   * Tests {@link ThemeAttributeResolver#resolveAll(ThemeEditorStyle, ThemeResolver)}
   */
  public void testResolveAll() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles.xml", "res/values/styles.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);

    createNewStyle("ThemeA", "android:Theme", "red", Lists.newArrayList("values-v13", "values-v16"));
    createNewStyle("ThemeB", "ThemeA", "blue", Lists.newArrayList("values-v12"));
    createNewStyle("ThemeB", "ThemeA", null, Lists.newArrayList("values-v15"));

    myFacet.refreshResources();
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ThemeEditorStyle style = themeResolver.getTheme("ThemeB");
    assertNotNull(style);
    List<EditedStyleItem> items = ThemeAttributeResolver.resolveAll(style, themeResolver);
    for (EditedStyleItem item : items) {
      if (item.getName().equals("colorPrimary")) {
        assertEquals(3, item.getAllConfiguredItems().size());
      }
    }
  }
}