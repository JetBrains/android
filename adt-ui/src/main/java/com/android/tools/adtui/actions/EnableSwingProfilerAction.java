/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.util.Key;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;

public class EnableSwingProfilerAction extends DumbAwareToggleAction {
  private static final Key<Object> SERVICE_KEY = Key.create("com.android.tools.swingp.server.StatsSerializer");
  private static boolean ourHasInstrumentedVm;

  public EnableSwingProfilerAction() {
    super("Enable Swing Profiler");
  }

  @Override
  @NotNull
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return SERVICE_KEY.isIn(ApplicationManager.getApplication());
  }

  @Override
  public void setSelected(@NotNull AnActionEvent event, boolean state) {
    Object serializer = SERVICE_KEY.get(ApplicationManager.getApplication());
    if (serializer == null && state) {
      if (!instrumentVm()) {
        return;
      }

      // Is off and should be turned on.
      try {
        Class<?> serializerClass = Class.forName("com.android.tools.swingp.server.StatsSerializer");
        Method start = serializerClass.getMethod("start");
        Object serializerInstance = serializerClass.getConstructor().newInstance();

        if ((Boolean)start.invoke(serializerInstance)) {
          SERVICE_KEY.set(ApplicationManager.getApplication(), serializerInstance);

          // Open up the HTML visualizer.
          URL visualizerUrl = getClass().getResource("/swingp/Visualizer.html");
          if (visualizerUrl != null) {
            BrowserUtil.browse(visualizerUrl);
          }
        }
      }
      catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
        Logger.getInstance(EnableSwingProfilerAction.class).warn("Stats serializer could not be started", e);
      }
    }
    else if (serializer != null && !state) {
      // Is on and should be turned off.
      try {
        Class<?> serializerClass = Class.forName("com.android.tools.swingp.server.StatsSerializer");
        Method stop = serializerClass.getMethod("stop");
        stop.invoke(serializer);
      }
      catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
        Logger.getInstance(EnableSwingProfilerAction.class).warn("Stats serializer could not stop or stop interrupted", e);
      }
      finally {
        SERVICE_KEY.set(ApplicationManager.getApplication(), null);
      }
    }
  }

  /**
   * Instruments the VM to load the Swing instrumenter jar so that the needed classes are loaded into the boot class loader.
   */
  private static boolean instrumentVm() {
    if (ourHasInstrumentedVm) {
      return true;
    }

    String vmName = ManagementFactory.getRuntimeMXBean().getName();
    String pid = vmName.substring(0, vmName.indexOf('@'));

    try {
      Path jarPath =
        Paths.get(PathManager.getHomePath(), "../../bazel-bin/tools/adt/idea/swingp/swing-instrumenter_deploy.jarjar.jar");
      VirtualMachine vm = VirtualMachine.attach(pid);
      // Only supports development mode, not release mode, therefore only look in the bazel-bin directory.
      vm.loadAgent(jarPath.toString(), null);
    }
    catch (AttachNotSupportedException | IOException | AgentLoadException | AgentInitializationException e) {
      Logger.getInstance(EnableSwingProfilerAction.class).warn("Could not connect to the VM and start instrumentation agent.", e);
      return false;
    }

    ourHasInstrumentedVm = true;
    return true;
  }
}
