/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework;

import com.intellij.ide.BootstrapClassLoaderUtil;
import com.intellij.ide.WindowsCommandLineProcessor;
import com.intellij.idea.Main;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.GuiTests.getProjectCreationLocationPath;
import static com.intellij.openapi.util.io.FileUtil.*;
import static org.fest.reflect.core.Reflection.staticMethod;

public class IdeTestApplication implements Disposable {
  private static final Logger LOG = Logger.getInstance(IdeTestApplication.class);

  private static IdeTestApplication ourInstance;

  @NotNull private final UrlClassLoader myIdeClassLoader;

  @NotNull
  public static synchronized IdeTestApplication getInstance() throws Exception {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, "AndroidStudio");
    File configDirPath = getConfigDirPath();
    System.setProperty(PathManager.PROPERTY_CONFIG_PATH, configDirPath.getPath());

    // Force Swing FileChooser on Mac (instead of native one) to be able to use FEST to drive it.
    System.setProperty("native.mac.file.chooser.enabled", "false");

    if (!isLoaded()) {
      ourInstance = new IdeTestApplication();
      recreateDirectory(configDirPath);

      File newProjectsRootDirPath = getProjectCreationLocationPath();
      recreateDirectory(newProjectsRootDirPath);

      UrlClassLoader ideClassLoader = ourInstance.getIdeClassLoader();
      Class<?> clazz = ideClassLoader.loadClass(GuiTests.class.getCanonicalName());
      staticMethod("waitForIdeToStart").in(clazz).invoke();
      staticMethod("setUpDefaultGeneralSettings").in(clazz).invoke();
    }

    return ourInstance;
  }

  private static File getConfigDirPath() throws IOException {
    String homeDirPath = toSystemDependentName(PathManager.getHomePath());
    assert !homeDirPath.isEmpty();
    File configDirPath = new File(homeDirPath, FileUtil.join("androidStudio", "gui-tests", "config"));
    ensureExists(configDirPath);
    return configDirPath;
  }

  private static void recreateDirectory(@NotNull File path) throws IOException {
    delete(path);
    ensureExists(path);
  }

  private IdeTestApplication() throws Exception {
    String[] args = new String[0];

    LOG.assertTrue(ourInstance == null, "Only one instance allowed.");
    ourInstance = this;

    pluginManagerStart(args);
    mainMain();

    myIdeClassLoader = BootstrapClassLoaderUtil.initClassLoader(true);
    WindowsCommandLineProcessor.ourMirrorClass = Class.forName(WindowsCommandLineProcessor.class.getName(), true, myIdeClassLoader);

    // We set "GUI Testing Mode" on right away, even before loading the IDE.
    Class<?> androidPluginClass = Class.forName("org.jetbrains.android.AndroidPlugin", true, myIdeClassLoader);
    staticMethod("setGuiTestingMode").withParameterTypes(boolean.class)
                                     .in(androidPluginClass)
                                     .invoke(true);

    Class<?> pluginManagerClass = Class.forName("com.intellij.ide.plugins.PluginManager", true, myIdeClassLoader);
    staticMethod("start").withParameterTypes(String.class, String.class, String[].class)
                         .in(pluginManagerClass)
                         .invoke("com.intellij.idea.MainImpl", "start", args);
  }

  private static void pluginManagerStart(@NotNull String[] args) {
    // Duplicates what PluginManager#start does.
    Main.setFlags(args);
    UIUtil.initDefaultLAF();
  }

  private static void mainMain() {
    // Duplicates what Main#main does.
    staticMethod("installPatch").in(Main.class).invoke();
  }

  @NotNull
  public UrlClassLoader getIdeClassLoader() {
    return myIdeClassLoader;
  }

  @Override
  public void dispose() {
    disposeInstance();
  }

  public static synchronized void disposeInstance() {
    if (!isLoaded()) {
      return;
    }
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      if (application instanceof ApplicationEx) {
        ((ApplicationEx)application).exit(true, true);
      }
      else {
        application.exit();
      }
    }
    ourInstance = null;
  }

  public static synchronized boolean isLoaded() {
    return ourInstance != null;
  }

}

