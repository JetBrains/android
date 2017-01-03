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

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

public class NewStyleDialogTest extends AndroidTestCase {
  public void testNewStyleNameSuggestion() {
    assertEquals("", NewStyleDialog.getNewStyleNameSuggestion(null, null));
    assertEquals("", NewStyleDialog.getNewStyleNameSuggestion(null, "MyNewTheme"));
    assertEquals("", NewStyleDialog.getNewStyleNameSuggestion("", "MyNewTheme"));

    assertEquals("Widget.MyNewTheme.EditStyle",
                 NewStyleDialog.getNewStyleNameSuggestion("Widget.AppCompat.EditStyle", "MyNewTheme"));
    assertEquals("Widget.MyNewTheme.EditStyle",
                 NewStyleDialog.getNewStyleNameSuggestion("Widget.AppCompat.EditStyle", "Theme.MyNewTheme"));
    assertEquals("Widget.MyNewTheme.EditStyle",
                 NewStyleDialog.getNewStyleNameSuggestion("Widget.AppCompat.EditStyle", "MyNewTheme"));
    assertEquals("Widget.MyNewTheme.EditStyle",
                 NewStyleDialog.getNewStyleNameSuggestion("Widget.AppCompat.EditStyle", "Theme.MyNewTheme"));

    assertEquals("Widget.AppCompat2.EditStyle.MyNewTheme",
                 NewStyleDialog.getNewStyleNameSuggestion("Widget.AppCompat2.EditStyle", "Theme.MyNewTheme"));
  }

  public void testGetStyleParentName() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attrs.xml", "res/values/attrs.xml");

    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(myFile);
    ThemeEditorContext context = new ThemeEditorContext(configuration);
    String styleName = "android:TextAppearance.Medium";

    NewStyleDialog dialog = new NewStyleDialog(false, context, styleName, "textAppearance", null);
    assertEquals(styleName, dialog.getStyleParentName());
    assertEquals("TextAppearance.Medium.textAppearance", dialog.getStyleName());

    // Calling .doCancelAction to dispose dialog object and avoid memory leaks
    dialog.doCancelAction();
  }
}
