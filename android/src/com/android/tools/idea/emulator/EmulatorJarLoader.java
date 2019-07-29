/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.emulator;


import com.android.tools.idea.sdk.IdeSdks;
import javax.swing.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.File;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.logging.Logger;

public class EmulatorJarLoader {

  private static final Logger LOGGER = Logger.getLogger(EmulatorJarLoader.class.getName());
  /* The name of the jar file that provides the emulator view */
  private static final String EMULATOR_VIEW_JAR = "emulator-studio-view.jar";
  /* The name of the class that provides the actual view. This class should be a JPanel */
  private static final String EMULATOR_VIEW_CLASS = "android.emulation.studio.InteractiveEmulatorView";

  private static Path getAndroidSdkRoot() {
    Path sdkRoot = IdeSdks.getInstance().getAndroidSdkPath().toPath();
    return Paths.get(sdkRoot.toString(), "emulator", "lib64");
  }

  /**
   * Creates the emulator view.
   * <p>
   * The emulator view is a simple interactive view that is shipped with
   * the emulator binary. It should be able to do the following things:
   *
   * - Mouse clicks
   * - Keyboard events
   * - Display the current state of the emulator
   *
   * @port The telnet port of the emulator we are connecting to.
   */
  public static JPanel createView(int port)
    throws MalformedURLException, ClassNotFoundException, NoSuchMethodException,
           IllegalAccessException, InstantiationException, InvocationTargetException {
    Path paths[] = new Path[]{Paths.get("."), getAndroidSdkRoot()};
    for (Path option : paths) {
      if (Files.exists(option.resolve(EMULATOR_VIEW_JAR))) {
        URLClassLoader child =
          new URLClassLoader(
            new URL[]{option.resolve(EMULATOR_VIEW_JAR).toUri().toURL()},
            EmulatorJarLoader.class.getClassLoader());
        Class classToLoad =
          Class.forName(EMULATOR_VIEW_CLASS, true, child);
        for (Constructor c : classToLoad.getConstructors()) {
          LOGGER.info("Const: " + c);
        }
        Constructor constructor = classToLoad.getDeclaredConstructor(new Class[]{Integer.TYPE});
        return (JPanel)constructor.newInstance(port);
      }
    }
    throw new FileSystemNotFoundException(
      "Cannot find " + EMULATOR_VIEW_JAR + " in any of " + Arrays.toString(paths));
  }
}
