/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.idea.blaze.android.run;

import com.android.sdklib.devices.Abi;
import com.android.tools.idea.projectsystem.ApplicationProjectContext;
import com.android.tools.ndk.run.NativeDebuggerAppContext;
import com.android.tools.ndk.run.NativeDebuggerAppContextProvider;
import com.android.tools.ndk.run.SymbolDir;
import com.google.idea.blaze.android.projectsystem.BazelToken;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** An implementation of {@link NativeDebuggerAppContextProvider} for the Blaze project system. */
public class BazelNativeDebuggerAppContextProvider
    implements NativeDebuggerAppContextProvider, BazelToken {

  @Override
  public NativeDebuggerAppContext getNativeDebuggerAppContext(
      ApplicationProjectContext applicationProjectContext) {
    final var context = (BazelApplicationProjectContext) applicationProjectContext;
    // TODO(solodkyy): Find out whether any configuration that is supposed to be part of this
    //                 context is passed to the debugger in oher ways and make sure this is the
    //                 only way to set up the debugger.
    return new NativeDebuggerAppContext() {
      @Override
      public Project getProject() {
        return context.getProject();
      }

      @Override
      public String getApplicationId() {
        return context.getApplicationId();
      }

      @Override
      public Collection<SymbolDir> getSymDirs(List<Abi> abis) {
        return Collections.emptyList();
      }

      @Override
      public Map<String, String> getSourceMap() {
        return Collections.emptyMap();
      }

      @Override
      public Map<File, File> getExplicitModuleSymbolMap(Abi abi) {
        return Collections.emptyMap();
      }

      @Override
      public Collection<Module> getModulesToVerify() {
        return Collections.emptyList();
      }
    };
  }
}
