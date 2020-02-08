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
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EmulatorJarLoader {
  private static final Logger LOGGER = Logger.getLogger(EmulatorJarLoader.class.getName());
  /* The name of the jar file that provides the emulator view */
  private static final String EMULATOR_VIEW_JAR = "emulator-studio-view.jar";
  /* The name of the class that provides the actual view. This class should be a JPanel */
  private static final String EMULATOR_VIEW_CLASS = "android.emulation.studio.InteractiveEmulatorView";

  private static Path getAndroidSdkRoot() {
    File sdk = IdeSdks.getInstance().getAndroidSdkPath();
    if (sdk != null) {
      File root = new File(sdk, "emulator" + File.separator + "lib64");
      if (root.isDirectory()) {
        return root.toPath();
      }
    }

    return Paths.get("");
  }

  @Nullable
  private static Constructor<?> constructor = null;

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
  @NotNull
  public static JPanel createView(int port)
    throws MalformedURLException, ClassNotFoundException, NoSuchMethodException,
           IllegalAccessException, InstantiationException, InvocationTargetException {
    if (constructor == null) {
      Path[] paths = new Path[]{Paths.get("."), getAndroidSdkRoot()};
      for (Path option : paths) {
        if (Files.exists(option.resolve(EMULATOR_VIEW_JAR))) {
          URLClassLoader child =
            new URLClassLoader(
              new URL[]{option.resolve(EMULATOR_VIEW_JAR).toUri().toURL()},
              EmulatorJarLoader.class.getClassLoader());
          Class<?> classToLoad =
            Class.forName(EMULATOR_VIEW_CLASS, true, child);
          for (Constructor<?> c : classToLoad.getConstructors()) {
            LOGGER.info("Const: " + c);
          }
          constructor = classToLoad.getDeclaredConstructor(Integer.TYPE);
          break;
        }
      }
    }
    if (constructor != null) {
      return (JPanel)constructor.newInstance(port);
    }
    else {
      throw new IllegalStateException("Couldn't initialize embedded emulator");
    }
  }
}
