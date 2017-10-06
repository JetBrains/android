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

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Utility methods to extract information from R.txt files.
 */
class RDotTxtParser {

  private static final String INT_ID = "int id ";
  private static final int INT_ID_LEN = INT_ID.length();
  private static Logger ourLog;
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').omitEmptyStrings().trimResults();

  @NotNull
  static Collection<String> getIdNames(final File rFile) {
    try {
      return Files.readLines(rFile, Charsets.UTF_8, new LineProcessor<Collection<String>>() {
        Collection<String> idNames = new ArrayList<String>(32);

        @Override
        public boolean processLine(@NotNull String line) throws IOException {
          if (!line.startsWith(INT_ID)) {
            return true;
          }
          int i = line.indexOf(' ', INT_ID_LEN);
          assert i != -1 : "File not in expected format: " + rFile.getPath() + "\n" +
                           "Expected the ids to be in the format int id <name> <number>";
          idNames.add(line.substring(INT_ID_LEN, i));
          return true;
        }

        @Override
        public Collection<String> getResult() {
          return idNames;
        }
      });
    }
    catch (IOException e) {
      getLog().warn("Unable to read file: " + rFile.getPath(), e);
      return Collections.emptyList();
    }
  }

  /**
   * For styleable array entries.
   * <p>
   * Search R.txt file, {@code r}, for the styleable with {@code styleableName} and return the
   * array of attribute ids, in the order specified by the list {@code attrs}. Returns null if the
   * styleable is not found.
   */
  @Nullable
  static Integer[] getDeclareStyleableArray(File r, final List<AttrResourceValue> attrs, final String styleableName) {
    try {
      return Files.readLines(r, Charsets.UTF_8, new LineProcessor<Integer[]>() {
        private static final String ARRAY_STYLEABLE = "int[] styleable ";
        private static final String INT_STYLEABLE = "int styleable ";

        private final String myArrayStart = ARRAY_STYLEABLE + styleableName + " { ";
        private final String myEntryStart = INT_STYLEABLE + styleableName + "_";

        private Integer[] myValuesList;
        private String[] myDeclaredAttrs;
        private int myAttrsFound;

        @Override
        public boolean processLine(@NotNull String line) throws IOException {
          if (line.startsWith(myArrayStart)) {
            // line must be of the form "int[] styleable name { value1, value2, ..., valueN }"
            // extract " value1, value2, ..., valueN "
            String valuesList = line.substring(myArrayStart.length(), line.length() - 1);
            int valuesCount = StringUtil.countChars(valuesList, ',') + 1;

            // The declared styleable doesn't match the size of this list of values so ignore this styleable declaration
            if (valuesCount != attrs.size()) {
              // Do not keep looking for the attr indexes
              return false;
            }
            myValuesList = new Integer[valuesCount];
            myDeclaredAttrs = new String[valuesCount];
            int idx = 0;

            for (String s : COMMA_SPLITTER.split(valuesList)) {
              myValuesList[idx++] = Integer.decode(s);
            }
            return true;
          } else if (myValuesList != null && line.startsWith(myEntryStart)) {
            int lastSpace = line.lastIndexOf(' ');
            String name = line.substring(INT_STYLEABLE.length(), lastSpace);
            int index = Integer.parseInt(line.substring(lastSpace + 1));
            myDeclaredAttrs[index] = name;
            myAttrsFound++;
            // return false if all entries have been found.
            return myAttrsFound != myDeclaredAttrs.length;
          }

          // Not a line we care about, continue processing.
          return true;
        }

        @Override
        public Integer[] getResult() {
          if (myValuesList == null || myDeclaredAttrs == null) {
            return null;
          }
          // The order of entries in a declare-styleable in the source xml and in R.txt may be different.
          // It's essential that the order of entries match the order of attrs. So, we reorder the entries.
          int index = 0;
          for (AttrResourceValue attr : attrs) {
            String name = ResourceClassGenerator.getResourceName(styleableName, attr);
            for (int i = index; i < myDeclaredAttrs.length; i++) {
              String declaredAttr = myDeclaredAttrs[i];
              if (declaredAttr.equals(name)) {
                ArrayUtil.swap(myDeclaredAttrs, i, index);
                ArrayUtil.swap(myValuesList, i, index);
                break;
              }
            }
            // b/65813064
            // Disabled for 3.0 since some Wear support libs are redeclaring styleables with the attribute names
            // changed from camel case to snake case. The attributes still have the same IDs so, for that case, the
            // assertion is not needed and it's breaking beta releases (with assertions enabled).
            // This code does not exist anymore in master
            //assert myDeclaredAttrs[index].equals(name) : name + " does not equal " + myDeclaredAttrs[index];
            index++;
          }
          return myValuesList;
        }
      });
    }
    catch (IOException e) {
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
