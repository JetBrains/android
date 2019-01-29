/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.res.aar;

import com.android.ide.common.symbols.Symbol;
import com.android.ide.common.symbols.SymbolIo;
import com.android.ide.common.symbols.SymbolTable;
import com.android.resources.ResourceType;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility methods to extract information from R.txt files.
 */
class RDotTxtParser {
  private static Logger ourLog;

  @NotNull
  static Map<String, Integer> getIds(@NotNull final File rFile) {
    try {
      SymbolTable symbolTable = SymbolIo.readFromAapt(rFile, null);
      return symbolTable.getSymbols().row(ResourceType.ID).values().stream()
                        .collect(Collectors.toMap(Symbol::getCanonicalName, e -> ((Symbol.NormalSymbol)e).getIntValue()));
    }
    catch (IOException e) {
      getLog().warn("Unable to read file: " + rFile.getPath(), e);
      return Collections.emptyMap();
    }
  }

  private static Logger getLog() {
    if (ourLog == null) {
      ourLog = Logger.getInstance(RDotTxtParser.class);
    }
    return ourLog;
  }
}
