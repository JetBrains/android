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
package com.android.tools.swingp.instrumenter;

import java.awt.Window;
import java.lang.instrument.Instrumentation;
import javax.swing.JComponent;
import javax.swing.RepaintManager;

public final class Agent {
  public static void premain(String agentArgs, Instrumentation instrumentation) {
    System.out.println("Starting instrumentation agent.");
    agentmain(agentArgs, instrumentation);
    System.out.println("Exiting agent.");
  }

  public static void agentmain(String agentArgs, Instrumentation instrumentation) {
    try {
      instrumentation.addTransformer(new JComponentClassTransformer(), true);
      instrumentation.addTransformer(new RepaintManagerClassTransformer(), true);
      instrumentation.addTransformer(new WindowClassTransformer(), true);
      instrumentation.addTransformer(new BufferStrategyPaintManagerClassTransform(), true);
      instrumentation.retransformClasses(JComponent.class);
      instrumentation.retransformClasses(RepaintManager.class);
      instrumentation.retransformClasses(Window.class);
      instrumentation.retransformClasses(Class.forName("javax.swing.BufferStrategyPaintManager"));
    }
    catch (Exception e) {
      System.out.println(e);
    }
  }
}
