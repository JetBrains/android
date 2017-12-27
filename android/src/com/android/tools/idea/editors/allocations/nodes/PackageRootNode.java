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
package com.android.tools.idea.editors.allocations.nodes;

import com.android.ddmlib.AllocationInfo;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class PackageRootNode extends PackageNode implements MainTreeNode {

  @NotNull
  private final Pattern myFilter;

  public PackageRootNode(@NotNull String name, @NotNull Pattern filter) {
    super(name);
    myFilter = filter;
  }

  public PackageRootNode(@NotNull String name, @NotNull String filter) {
    this(name, globToRegex(filter));
  }

  @Override
  public void insert(@NotNull AllocationInfo alloc) {
    StackTraceElement[] trace = alloc.getStackTrace();
    String[] packages;
    if (trace.length > 0) {
      int match = 0;
      for (int i = 0; i < trace.length; i++) {
        if (myFilter.matcher(trace[i].getClassName()).matches()) {
          match = i;
          break;
        }
      }
      // TODO don't use the last trace, but use a user defined filter.
      String name = trace[match].getClassName();
      int ix = name.indexOf('$');
      name = ix >= 0 ? name.substring(0, ix) : name;
      packages = name.split("\\.");
    } else {
      packages = new String[] { "< Unknown >" };
    }
    insert(packages, alloc, 0);
  }

  static Pattern globToRegex(@NotNull String glob) {
    String regex = "";
    int ix = glob.indexOf('*');
    while (ix != -1) {
      regex += Pattern.quote(glob.substring(0, ix));
      regex += ".*";
      glob = glob.substring(ix + 1);
      ix = glob.indexOf('*');
    }
    regex += Pattern.quote(glob);
    return Pattern.compile(".*" + regex + ".*");
  }
}
