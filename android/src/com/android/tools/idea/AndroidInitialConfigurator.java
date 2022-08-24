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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/** Customize Android IDE specific experience. */
public class AndroidInitialConfigurator {
  @NonNls
  private static final ExtensionPointName<Runnable> EP_NAME =
    ExtensionPointName.create("com.intellij.androidStudioInitializer");

  @SuppressWarnings("SpellCheckingInspection")
  @NonNls private static final String TODO_TOOLWINDOW_ACTION_ID = "ActivateTODOToolWindow";
  @SuppressWarnings("SpellCheckingInspection")
  @NonNls private static final String ANDROIDMONITOR_TOOLWINDOW_ACTION_ID = "ActivateAndroidMonitorToolWindow";
  @SuppressWarnings("SpellCheckingInspection")
  @NonNls private static final String LOGCAT_TOOLWINDOW_ACTION_ID = "ActivateLogcatToolWindow";

  @NonNls private static final boolean EXPERIMENTAL_PROFILING_FLAG_ENABLED =
    !"false".equals(System.getProperty("enable.experimental.profiling"));


  public AndroidInitialConfigurator() {
    setupSystemProperties();

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


  private static void setActivateAndroidToolWindowShortcut() {
    // The IntelliJ keymap implementation behaves as follows:
    //  - getting a shortcut from a keymap gets the shortcut only from that keymap, and not from its parent, unless no shortcuts
    //    are defined in the keymap itself
    //  - however, adding a shortcut to a keymap explicitly copies all the shortcuts for that action from the parent keymap to this keymap
    // The upshot of all this is that to add a shortcut, we should do so in all the child keymaps first, then the parent keymap.
    // The following code does a simplified implementation of this behavior by changing the default keymap last after all the other
    // keymaps have been changed.
    Keymap defaultKeymap = null;
    for (Keymap keymap: KeymapManagerEx.getInstanceEx().getAllKeymaps()) {
      if (KeymapManager.DEFAULT_IDEA_KEYMAP.equals(keymap.getName())) {
        defaultKeymap = keymap;
        continue;
      }
      setActivateAndroidToolWindowShortcut(keymap);
    }

    if (defaultKeymap != null) {
      setActivateAndroidToolWindowShortcut(defaultKeymap);
    }
  }

  private static void setActivateAndroidToolWindowShortcut(Keymap keymap) {
    KeyboardShortcut shortcut = removeFirstKeyboardShortcut(keymap, TODO_TOOLWINDOW_ACTION_ID);
    if (shortcut != null) {
      if (EXPERIMENTAL_PROFILING_FLAG_ENABLED) {
        keymap.addShortcut(LOGCAT_TOOLWINDOW_ACTION_ID, shortcut);
      } else {
        keymap.addShortcut(ANDROIDMONITOR_TOOLWINDOW_ACTION_ID, shortcut);
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
    for (Runnable r : EP_NAME.getExtensionList()) {
      r.run();
    }
  }
}
