/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.kotlin.run.debug;

import com.google.idea.blaze.java.run.BlazeJavaDebuggerRunner;
import com.google.idea.blaze.java.run.BlazeJavaRunProfileState;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.debugger.coroutine.DebuggerConnection;

/**
 * A runner that extends {@code BlazeJavaDebuggerRunner} to work with Kotlin projects run
 * configurations with Bazel.
 *
 * <p>This class is mainly needed to view coroutines debugging panel and enable coroutines plugin
 * for Kotlin targets that use coroutines and depend on the required versions of kotlinx-coroutines
 * library.
 */
public class KotlinDebuggerRunner extends BlazeJavaDebuggerRunner {

  @Override
  @Nullable
  public RunContentDescriptor createContentDescriptor(
      RunProfileState state, ExecutionEnvironment env) throws ExecutionException {

    if (state instanceof BlazeJavaRunProfileState) {
      AtomicReference<String> kotlinxCoroutinesJavaAgentPath =
          env.getCopyableUserData(BlazeKotlinDebuggingSetupHandler.COROUTINES_LIB_PATH);
      if (kotlinxCoroutinesJavaAgentPath != null && kotlinxCoroutinesJavaAgentPath.get() != null) {
        ((BlazeJavaRunProfileState) state)
            .addKotlinxCoroutinesJavaAgent(kotlinxCoroutinesJavaAgentPath.get());

        //noinspection unused go/checkreturnvalue
        DebuggerConnection unused =
            new DebuggerConnection(
                env.getProject(),
                /*configuration=*/ null,
                new JavaParameters(),
                /*modifyArgs=*/ false,
                /*alwaysShowPanel=*/ true);
      }
    }
    return super.createContentDescriptor(state, env);
  }
}
