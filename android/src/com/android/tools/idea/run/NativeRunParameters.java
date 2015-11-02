/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NativeRunParameters implements JDOMExternalizable {
  private static final String SYMBOL_DIRS = "symbol_dirs";
  private static final String SYMBOL_PATH = "symbol_path";

  public boolean HYBRID_DEBUG = true;
  // Serialized manually in readExternal/writeExternal since DefaultJDOMExternalizer supports only primitive types.
  private List<String> mySymbolDirs = Lists.newLinkedList();
  public String WORKING_DIR = "";
  public String TARGET_LOGGING_CHANNELS = "lldb process:gdb-remote packets";

  public boolean isHybridDebug() {
    return HYBRID_DEBUG;
  }

  public void setHybridDebug(boolean hybridDebug) {
    HYBRID_DEBUG = hybridDebug;
  }

  @NotNull
  public List<String> getSymbolDirs() {
    return mySymbolDirs;
  }

  public void setSymbolDirs(@NotNull List<String> symDirs) {
    mySymbolDirs.clear();
    mySymbolDirs.addAll(symDirs);
  }

  public void addSymbolDir(@NotNull String symDir) {
    if (mySymbolDirs.indexOf(symDir) == -1) {
      mySymbolDirs.add(symDir);
    }
  }

  @NotNull
  public String getWorkingDir() {
    return WORKING_DIR;
  }

  public void setWorkingDir(@NotNull String workingDir) {
    WORKING_DIR = workingDir;
  }

  @NotNull
  public String getTargetLoggingChannels() {
    return TARGET_LOGGING_CHANNELS;
  }

  public void setTargetLoggingChannels(@NotNull String targetLoggingChannels) {
    TARGET_LOGGING_CHANNELS = targetLoggingChannels;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    mySymbolDirs = JDOMExternalizer.loadStringsList(element, SYMBOL_DIRS, SYMBOL_PATH);
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    JDOMExternalizer.saveStringsList(element, SYMBOL_DIRS, SYMBOL_PATH, mySymbolDirs.toArray(new String[]{}));
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
