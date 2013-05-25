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

package com.android.tools.idea;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/** Customize Android IDE specific experience. */
public class AndroidInitialConfigurator {
  @NonNls
  private static final ExtensionPointName<Runnable> EP_NAME =
    ExtensionPointName.create("com.intellij.androidStudioInitializer");

  @NonNls private static final String CONFIG_V1 = "AndroidInitConfigurator.V1";
  @NonNls private static final String TODO_TOOLWINDOW_ACTION_ID = "ActivateTODOToolWindow";
  @NonNls private static final String ANDROID_TOOLWINDOW_ACTION_ID = "ActivateAndroidToolWindow";

  public AndroidInitialConfigurator(MessageBus bus,
                                    final PropertiesComponent propertiesComponent,
                                    final FileTypeManager fileTypeManager) {
    setupSystemProperties();
    customizeSettings(propertiesComponent);

    // change default key maps to add a activate Android ToolWindow shortcut
    setActivateAndroidToolWindowShortcut();

    activateAndroidStudioInitializerExtensions();
  }

  /**
   * Setup some Java system properties based on environment variables.
   * This makes it easier to do local testing.
   */
  private static void setupSystemProperties() {
    // If defined, AS_UPDATE_URL should point to the *root* of the updates.xml file to use
    // and patches are expected to be in the same folder.
    String updateUrl = System.getenv("AS_UPDATE_URL");
    if (updateUrl != null) {
      if (!updateUrl.endsWith("/")) {
        updateUrl += "/";
      }
      // Set the Java system properties expected by UpdateChecker.
      System.setProperty("idea.updates.url", updateUrl + "updates.xml");
      System.setProperty("idea.patches.url", updateUrl);
    }

  }

  private static void customizeSettings(PropertiesComponent propertiesComponent) {
    if (!propertiesComponent.getBoolean(CONFIG_V1, false)) {
      propertiesComponent.setValue(CONFIG_V1, "true");
      CodeInsightSettings.getInstance().AUTO_POPUP_JAVADOC_INFO = true;
      UISettings.getInstance().SCROLL_TAB_LAYOUT_IN_EDITOR = true;
      EditorSettingsExternalizable.getInstance().setVirtualSpace(false);

      // For Macs, use 10.5+ keymap as the default
      if (SystemInfo.isMac) {
        setDefaultMacKeymap();
      }
    }
  }

  private static void setDefaultMacKeymap() {
    KeymapManagerImpl instance = (KeymapManagerImpl)KeymapManager.getInstance();
    Keymap mac105Keymap = getMac105Keymap();
    if (mac105Keymap != null) {
      instance.setActiveKeymap(mac105Keymap);
    }
  }

  @Nullable
  private static Keymap getMac105Keymap() {
    for (Keymap keymap: KeymapManagerEx.getInstanceEx().getAllKeymaps()) {
      if (keymap.getName().contains("10.5")) {
        return keymap;
      }
    }

    return null;
  }

  private static void setActivateAndroidToolWindowShortcut() {
    for (Keymap keymap: KeymapManagerEx.getInstanceEx().getAllKeymaps()) {
      KeyboardShortcut shortcut = removeFirstKeyboardShortcut(keymap, TODO_TOOLWINDOW_ACTION_ID);
      if (shortcut != null) {
        keymap.addShortcut(ANDROID_TOOLWINDOW_ACTION_ID, shortcut);
      }
    }
  }

  @Nullable
  private static KeyboardShortcut removeFirstKeyboardShortcut(Keymap keymap, String actionId) {
    Shortcut[] shortcuts = keymap.getShortcuts(actionId);
    for (Shortcut each : shortcuts) {
      if (each instanceof KeyboardShortcut) {
        keymap.removeShortcut(actionId, each);
        return (KeyboardShortcut)each;
      }
    }

    return null;
  }

  private static void activateAndroidStudioInitializerExtensions() {
    Runnable[] extensions = EP_NAME.getExtensions();
    for (Runnable r : extensions) {
      r.run();
    }
  }
}
