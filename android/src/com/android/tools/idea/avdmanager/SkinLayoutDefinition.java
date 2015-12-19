/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.annotations.VisibleForTesting;
import com.android.repository.io.FileOp;
import com.android.tools.idea.templates.TemplateUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Allows access to Device Skin Layout files. The layout file syntax is of the form:
 * <pre>
 * key {
 *   subkey {
 *     arbitrary-subkey {
 *       keypair-key   keypair-value
 *     }
 *   }
 * }
 * </pre>
 */
public class SkinLayoutDefinition {

  @VisibleForTesting static final Pattern ourQuerySeparator = Pattern.compile("\\.");
  @VisibleForTesting static final Pattern ourWhitespacePattern = Pattern.compile("\\s+");

  @Nullable
  public static SkinLayoutDefinition parseFile(@NotNull File file, @NotNull FileOp fop) {
    String contents;
    try {
      contents = fop.toString(file, Charsets.UTF_8);
    }
    catch (IOException e) {
      return null;
    }
    return loadFromTokens(Splitter.on(ourWhitespacePattern).omitEmptyStrings().trimResults().split(contents).iterator());
  }

  /**
   * Populate myProperties and myChildren from the token stream
   * @param tokens a queue of string tokens
   */
  @VisibleForTesting
  static SkinLayoutDefinition loadFromTokens(Iterator<String> tokens) {
    String key;
    String value;
    SkinLayoutDefinition definition = new SkinLayoutDefinition();
    while (tokens.hasNext()) {
      key = tokens.next();
      if (key.equals("}")) { // We're done with this block, return
        break;
      } else {
        value = tokens.next();
        if (value.equals("{")) { // Start of a nested block, recursively load that block
          definition.myChildren.put(key, loadFromTokens(tokens));
        } else {                // Otherwise, it's a string property, and we'll store it
          definition.myProperties.put(key, value);
        }
      }
    }
    return definition;
  }

  private Map<String, String> myProperties = Maps.newHashMap();
  private Map<String, SkinLayoutDefinition> myChildren = Maps.newHashMap();

  private SkinLayoutDefinition() {
    // Private constructor. Use #parseFile to obtain an instance
  }

  /**
   * Returns the property associated with the given query string or null if no such property exists.
   * Example: Given
   * <pre>
   *   foo {
   *     bar {
   *       abc 123
   *     }
   *     baz {
   *       hello world
   *     }
   *   }
   * </pre>
   * The query string "foo.bar.abc" would return the string "123" and the query string "foo.baz.hello" would return "world."
   * The query string "foo.bar.def" would return null because the key referenced does not exist.
   * The query string "foo.bar" would return null because it represents an incomplete path.
   * @param queryString a dot-separated list of string keys
   */
  @Nullable
  public String get(@NotNull String queryString) {
    return get(Splitter.on(ourQuerySeparator).split(queryString).iterator());
  }

  @Nullable
  private String get(@NotNull Iterator<String> queryIterator) {
    if (!queryIterator.hasNext()) {
      return null;
    }
    String key = queryIterator.next();
    if (!queryIterator.hasNext()) {
      return myProperties.get(key);
    } else {
      SkinLayoutDefinition child = myChildren.get(key);
      if (child != null) {
        return child.get(queryIterator);
      } else {
        return null;
      }
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    makeString(sb, 1);
    return sb.toString();
  }

  /**
   * @param depth number of 2-space indents to apply
   */
  private void makeString(@NotNull StringBuilder sb, int depth) {
    sb.append("{\n");
    for (String key : sort(myProperties.keySet())) {
      appendSpace(sb, depth);
      sb.append(key);
      sb.append("    ");
      sb.append(myProperties.get(key));
      sb.append("\n");
    }
    for (String key : sort(myChildren.keySet())) {
      appendSpace(sb, depth);
      sb.append(key);
      sb.append("    ");
      myChildren.get(key).makeString(sb, depth + 1);
    }
    appendSpace(sb, depth - 1);
    sb.append("}\n");
  }

  private static List<String> sort(Set<String> set) {
    ArrayList<String> list = new ArrayList<String>(set);
    Collections.sort(list);
    return list;
  }
  private static void appendSpace(@NotNull StringBuilder sb, int depth) {
    for (int i = 0; i < depth; i++) {
      sb.append("  ");
    }
  }
}
