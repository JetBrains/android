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
package com.android.tools.idea.res;

import com.android.SdkConstants;
import com.android.ide.common.symbols.Symbol;
import com.android.ide.common.symbols.SymbolIo;
import com.android.ide.common.symbols.SymbolTable;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.resources.ResourceNameKeyedMap;
import com.android.resources.ResourceType;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility methods to extract information from R.txt files.
 */
class RDotTxtParser {

  private static Logger ourLog;
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').omitEmptyStrings().trimResults();

  @NotNull
  static Map<String, Integer> getIds(@NotNull final File rFile) {
    try {
      SymbolTable symbolTable = SymbolIo.readFromAapt(rFile, null);
      return symbolTable.getSymbols().row(ResourceType.ID).entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> Integer.decode(e.getValue().getValue())));
    }
    catch (IOException e) {
      getLog().warn("Unable to read file: " + rFile.getPath(), e);
      return Collections.emptyMap();
    }
  }

  /**
   * For styleable array entries.
   * <p>
   * Search R.txt file, {@code rFile}, for the styleable with {@code styleableName} and return the
   * array of attribute ids, in the order specified by the list {@code attrs}. Returns null if the
   * styleable is not found.
   */
  @Nullable
  static Integer[] getDeclareStyleableArray(@NotNull final File rFile, final List<AttrResourceValue> attrs, final String styleableName) {
    try {
      SymbolTable symbolTable = SymbolIo.readFromAapt(rFile, null);
      Symbol styleable = symbolTable.getSymbols().row(ResourceType.STYLEABLE).get(styleableName);

      if (styleable == null) {
        getLog().debug("Unable to find styleable: " + styleableName);
        return null;
      }

      ImmutableList<String> values = styleable.getChildren();
      if (values.size() != attrs.size()) {
        getLog().warn(String.format("Styleable does not match attributes size (%d styleable != %d attributes)",
                                    values.size(), attrs.size()));
        return null;
      }

      String arrayValues = styleable.getValue().trim();
      if (arrayValues.length() < 2) {
        getLog().warn("Incorrect styleable array definition for: " + styleableName);
        return null;
      }

      // Remove array brackets
      arrayValues = arrayValues.substring(1, arrayValues.length() - 1);

      Map<String, ResourceNameKeyedMap<Integer>> namespacesMap = new HashMap<>();
      int idx = 0;
      for (String intValue : COMMA_SPLITTER.split(arrayValues)) {
        String attributeKey = values.get(idx++);

        // Split namespace and resource value
        int nsSeparatorIdx = attributeKey.indexOf(':');
        String namespace = nsSeparatorIdx != -1 ? attributeKey.substring(0, nsSeparatorIdx) : null;
        String name = nsSeparatorIdx != -1 ? attributeKey.substring(nsSeparatorIdx + 1) : attributeKey;

        ResourceNameKeyedMap<Integer> styleableValuesMap = namespacesMap.get(namespace);
        if (styleableValuesMap == null) {
          styleableValuesMap = new ResourceNameKeyedMap<>();
          namespacesMap.put(namespace, styleableValuesMap);
        }
        styleableValuesMap.put(name, Integer.decode(intValue));
      }

      Integer[] results = new Integer[values.size()];
      idx = 0;
      for (AttrResourceValue attr : attrs) {
        // TODO: Add support for arbitrary namespaces when it's properly implemented in AttrResourceValue
        ResourceNameKeyedMap<Integer> styleableValuesMap = namespacesMap.get(attr.isFramework() ? SdkConstants.ANDROID_NS_NAME : null);
        results[idx++] = styleableValuesMap != null ? styleableValuesMap.get(attr.getName()) : null;
      }
      return results;
    }
    catch (IOException e) {
      getLog().warn("Unable to read file: " + rFile.getPath(), e);
      return null;
    }
  }

  private static Logger getLog() {
    if (ourLog == null) {
      ourLog = Logger.getInstance(RDotTxtParser.class);
    }
    return ourLog;
  }
}
