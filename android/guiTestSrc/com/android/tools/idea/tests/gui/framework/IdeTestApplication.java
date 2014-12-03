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
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.lang.ClassPath;
import com.intellij.util.lang.ClasspathCache;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.GuiTests.getProjectCreationDirPath;
import static com.intellij.openapi.util.io.FileUtil.*;
import static org.fest.reflect.core.Reflection.staticMethod;

public class IdeTestApplication implements Disposable {
  private static final Logger LOG = Logger.getInstance(IdeTestApplication.class);

  private static IdeTestApplication ourInstance;

  @NotNull private final ClassLoader myIdeClassLoader;

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

      File newProjectsRootDirPath = getProjectCreationDirPath();
      recreateDirectory(newProjectsRootDirPath);

      ClassLoader ideClassLoader = ourInstance.getIdeClassLoader();
      Class<?> clazz = ideClassLoader.loadClass(GuiTests.class.getCanonicalName());
      staticMethod("waitForIdeToStart").in(clazz).invoke();
      staticMethod("setUpDefaultGeneralSettings").in(clazz).invoke();
    }

    return ourInstance;
  }

  private static File getConfigDirPath() throws IOException {
    String homeDirPath = toSystemDependentName(PathManager.getHomePath());
    assert !homeDirPath.isEmpty();
    File configDirPath = new File(homeDirPath, join("androidStudio", "gui-tests", "config"));
    ensureExists(configDirPath);
    return configDirPath;
  }

  private static void recreateDirectory(@NotNull File path) throws IOException {
    delete(path);
    ensureExists(path);
  }

  private IdeTestApplication() throws Exception {
    String[] args = ArrayUtil.EMPTY_STRING_ARRAY;

    LOG.assertTrue(ourInstance == null, "Only one instance allowed.");
    ourInstance = this;

    pluginManagerStart(args);
    mainMain();

    myIdeClassLoader = BootstrapClassLoaderUtil.initClassLoader(true);
    forceEagerClassPathLoading();

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

  /**
   * We encountered a problem with {@link UrlClassLoader default IJ class loader} - it uses {@link ClassPath} which, in turn,
   * uses {@link ClasspathCache caching} by default and there is a race condition. Here are some facts about class loading implementation
   * details used by it:
   * <ol>
   *   <li>
   *     {@link ClassPath} lazily evaluates {@link ClassPath#push(List) configured classpath roots} when there is
   *     {@link ClassPath#getResource(String, boolean) a request} for a resource and target resource hasn't been cached yet;
   *   </li
   *   <li>
   *     {@link ClassPath} {@link ClasspathCache#nameSymbolsLoaded() seals loaded data} (reorganize it in a way to consume less memory)
   *     when all {@link ClassPath#push(List) configured classpath roots} are processed;
   *   </li>
   *   <li>
   *     Here is the problem - {@link ClasspathCache} state update from {@link ClasspathCache#myTempMapMode 'use temp map'} mode to
   *     <code>'not use temp map'</code> mode is performed in not thread-safe manner;
   *   </li>
   *   <li>
   *     Class loading is performed in a thread-safe manner (guaranteed by {@link ClassLoader} from the standard library. However,
   *     resource loading doesn't imply any locks. E.g. we encountered a situation below:
   *     <table>
   *       <thead>
   *         <tr>
   *           <th>Thread1</th>
   *           <th>Thread2</th>
   *         </tr>
   *       </thead>
   *       <tbody>
   *         <tr>
   *           <td>{@link ClassPath#getResource(String, boolean)} is called for a particular class</td>
   *           <td></td>
   *         </tr>
   *         <tr>
   *           <td>{@link ClasspathCache#iterateLoaders(String, ClasspathCache.LoaderIterator, Object, Object)} is called as a result</td>
   *           <td></td>
   *         </tr>
   *         <tr>
   *           <td></td>
   *           <td>
   *             {@link ClassPath#getResource(String, boolean)} is called for a particular resource (not synced with the active
   *             <code>'load class'</code> request
   *           </td>
   *         </tr>
   *         <tr>
   *           <td></td>
   *           <td>
   *             This request is a general purpose one (e.g.
   *             <a href="http://docs.oracle.com/javase/tutorial/sound/SPI-intro.html">a call to custom service implementation</a>)
   *             and target resource is not found in any of the configured classpath roots, effectively forcing {@link ClassPath}
   *             to iterate (load) all of them;
   *           </td>
   *         </tr>
   *         <tr>
   *           <td></td>
   *           <td>
   *             {@link ClasspathCache#nameSymbolsLoaded()} is called when all configured classpath roots are processed during
   *             an attempt to find target resource;
   *           </td>
   *         </tr>
   *         <tr>
   *           <td></td>
   *           <td>
   *             {@link ClasspathCache#myTempMapMode} is set to <code>false</code> as the very first thing during
   *             {@link ClasspathCache#nameSymbolsLoaded()} processing, {@link ClasspathCache#myNameFilter} object is created and
   *             its state population begins;
   *           </td>
   *         </tr>
   *         <tr>
   *           <td>
   *             {@link ClasspathCache#iterateLoaders(String, ClasspathCache.LoaderIterator, Object, Object)} continues the processing
   *             and calls {@link ClassPath.ResourceStringLoaderIterator#process(Loader, Object, Object)} which, in turn,
   *             calls {@link ClasspathCache#loaderHasName(String, Loader)};
   *           </td>
   *           <td></td>
   *         </tr>
   *         <tr>
   *           <td>
   *             And here lays the problem: {@link ClasspathCache#loaderHasName(String, Loader)} sees that
   *             {@link ClasspathCache#myTempMapMode} is set to <code>false</code> and forwards the processing to the
   *             {@link ClasspathCache#myNameFilter}. But it's state is still being updated, so, there is a possible case that it
   *             returns <code>null</code> for a resource {@link ClasspathCache#addNameEntry(String, Loader) added previously};
   *           </td>
   *           <td></td>
   *         </tr>
   *       </tbody>
   *     </table>
   *   </li>
   * </ol>
   * So, proper way to fix the problem is to address that race condition at the {@link ClassPath}/{@link ClasspathCache} level.
   * However, it's rather dangerous to just explicitly add synchronization there because JetBrains worked a lot on class loading
   * performance optimization and any change there requires thorough testing.
   * <p/>
   * That's why we did the following:
   * <ul>
   *   <li>told JetBrains about the problem (effectively putting the burden of a proper fix on them);</li
   *   <li>
   *     do a kind of a hack here as a temporary solution - force eager {@link ClasspathCache#nameSymbolsLoaded() classpath cache sealing}
   *     by requesting to load un-existing resource;
   *   </li>
   * </ul>
   */
  private void forceEagerClassPathLoading() {
    if (myIdeClassLoader instanceof UrlClassLoader) {
      ((UrlClassLoader)myIdeClassLoader).findResource("Really hope there is no resource with such name");
    }
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
  public ClassLoader getIdeClassLoader() {
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
