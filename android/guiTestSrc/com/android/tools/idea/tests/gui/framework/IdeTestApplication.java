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

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.BootstrapClassLoaderUtil;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.WindowsCommandLineProcessor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.idea.IdeaApplication;
import com.intellij.idea.Main;
import com.intellij.idea.StartupUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.SystemDock;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static org.fest.reflect.core.Reflection.staticMethod;

public class IdeTestApplication implements Disposable {
  private static final Logger LOG = Logger.getInstance(IdeTestApplication.class);

  private static IdeTestApplication ourInstance;

  public static synchronized IdeTestApplication getInstance() throws Exception {
    return getInstance(null);
  }

  public static synchronized IdeTestApplication getInstance(@Nullable String configPath) throws Exception {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, "Idea");
    final String configPathToUse = configPath != null ? configPath : PathManager.getOptionsPath();

    if (ourInstance == null) {
      new IdeTestApplication();
      PluginManagerCore.getPlugins();
      final ApplicationEx app = ApplicationManagerEx.getApplicationEx();
      new WriteAction() {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          app.load(configPathToUse);
          ideaApplicationMain();
        }
      }.execute();
    }
    return ourInstance;
  }

  private IdeTestApplication() throws Exception {
    String[] args = new String[0];

    LOG.assertTrue(ourInstance == null, "Only one instance allowed.");
    ourInstance = this;

    pluginManagerStart(args);
    mainMain();
    bootstrapMain();
    startupUtilPrepareAndStart(args);
    mainImplStart();

    // duplicates what IdeaApplication#IdeaApplication does (this whole block.)
    staticMethod("patchSystem").in(IdeaApplication.class).invoke();
    ApplicationManagerEx.createApplication(true, false, false, true, ApplicationManagerEx.IDEA_APPLICATION, null);
    staticMethod("initLAF").in(IdeaApplication.class).invoke();

    ideaApplicationMain(args);
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

  private static void bootstrapMain() throws Exception {
    // Duplicates what Bootstrap#main does.
    UrlClassLoader newClassLoader = BootstrapClassLoaderUtil.initClassLoader(true);
    WindowsCommandLineProcessor.ourMirrorClass = Class.forName(WindowsCommandLineProcessor.class.getName(), true, newClassLoader);
  }

  private static void startupUtilPrepareAndStart(@NotNull String[] args) {
    // Duplicates what StartupUtil#prepareAndStart does.
    staticMethod("checkSystemFolders").in(StartupUtil.class).invoke();
    staticMethod("lockSystemFolders").withParameterTypes(String[].class).in(StartupUtil.class).invoke(new Object[] {args});
    Logger log = Logger.getInstance(IdeTestApplication.class);
    staticMethod("fixProcessEnvironment").withParameterTypes(Logger.class).in(StartupUtil.class).invoke(log);
    AppUIUtil.updateFrameClass();
    AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame());
    AppUIUtil.registerBundledFonts();
  }

  private static void mainImplStart() {
    // Duplicates what MainImpl#start does.
    PluginManager.installExceptionHandler();
  }

  private static void ideaApplicationMain(@NotNull String[] args) {
    // Duplicates what IdeaApplication#main does
    // SystemDock.updateMenu();

    WindowManagerImpl windowManager = (WindowManagerImpl)WindowManager.getInstance();
    IdeEventQueue.getInstance().setWindowManager(windowManager);

    ApplicationEx app = ApplicationManagerEx.getApplicationEx();

    Ref<Boolean> willOpenProject = new Ref<Boolean>(Boolean.FALSE);
    AppLifecycleListener lifecyclePublisher = app.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC);
    lifecyclePublisher.appFrameCreated(args, willOpenProject);
  }

  private static void ideaApplicationMain() {
    // duplicates what IdeaApplication#main does.
    WindowManagerImpl windowManager = (WindowManagerImpl)WindowManager.getInstance();
    windowManager.showFrame();
  }

  @Override
  public void dispose() {
    disposeInstance();
  }

  private static synchronized void disposeInstance() {
    if (ourInstance == null) {
      return;
    }
    final Application applicationEx = ApplicationManager.getApplication();
    if (applicationEx != null) {
      new WriteAction() {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          Disposer.dispose(applicationEx);
        }
      }.execute();
    }
    ourInstance = null;
  }
}
