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

import static com.intellij.util.containers.ContainerUtil.sorted;

import com.android.io.CancellableFileIo;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private static final Splitter TOKEN_SPLITTER = Splitter.on(Pattern.compile("\\s+")).omitEmptyStrings().trimResults();
  private static final Splitter QUERY_SPLITTER = Splitter.on('.');

  private final Map<String, String> myProperties;
  private final Map<String, SkinLayoutDefinition> myChildren;

  @Nullable
  public static SkinLayoutDefinition parseFile(@NotNull File file) {
    String contents;
    try {
      contents = CancellableFileIo.readString(file.toPath());
    }
    catch (IOException e) {
      return null;
    }
    return parseString(contents);
  }

  @NotNull
  public static SkinLayoutDefinition parseString(@NotNull String contents) {
    StringBuilder contentsWithoutComments = new StringBuilder();
    for (String line : Splitter.on('\n').split(contents)) {
      if (!line.trim().startsWith("#")) {
        contentsWithoutComments.append(line);
        contentsWithoutComments.append('\n');
      }
    }
    return loadFromTokens(TOKEN_SPLITTER.split(contentsWithoutComments.toString()).iterator());
  }

  private SkinLayoutDefinition(@NotNull Map<String, String> properties, @NotNull Map<String, SkinLayoutDefinition> children) {
    myProperties = properties;
    myChildren = children;
  }

  /**
   * Creates a SkinLayoutDefinition from the token stream.
   *
   * @param tokens a sequence of string tokens
   */
  private static SkinLayoutDefinition loadFromTokens(Iterator<String> tokens) {
    ImmutableMap.Builder<String, SkinLayoutDefinition> children = ImmutableMap.builder();
    ImmutableMap.Builder<String, String> properties = ImmutableMap.builder();
    while (tokens.hasNext()) {
      String key = tokens.next();
      if (key.equals("}")) { // We're done with this block, return.
        break;
      }

      String value = tokens.next();
      if (value.equals("{")) { // Start of a nested block, recursively load that block.
        children.put(key, loadFromTokens(tokens));
      } else {                // Otherwise, it's a string property, and we'll store it.
        properties.put(key, value);
      }
    }
    return new SkinLayoutDefinition(properties.build(), children.build());
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
   *
   * @param queryString a dot-separated list of string keys
   */
  @Nullable
  public String getValue(@NotNull String queryString) {
    int lastDot = queryString.lastIndexOf('.');
    String name = queryString.substring(lastDot + 1);
    SkinLayoutDefinition node = lastDot < 0 ? this : getNode(queryString.substring(0, lastDot));
    return node == null ? null : node.myProperties.get(name);
  }

  /**
   * Returns a sub-node with the given path.
   *
   * @param queryString dot-separated sequence of node names
   * @return the sub-node, or null if not found
   */
  @Nullable
  public SkinLayoutDefinition getNode(@NotNull String queryString) {
    SkinLayoutDefinition result = null;
    SkinLayoutDefinition node = this;
    for (String name : QUERY_SPLITTER.split(queryString)) {
      if (node == null) {
        return null;
      }
      node = node.myChildren.get(name);
      result = node;
    }
    return result;
  }

  /**
   * Returns child nodes of this SkinLayoutDefinition.
   */
  @NotNull
  public Map<String, SkinLayoutDefinition> getChildren() {
    return myChildren;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    makeString(buf, 1);
    return buf.toString();
  }

  /**
   * @param depth number of 2-space indents to apply
   */
  private void makeString(@NotNull StringBuilder buf, int depth) {
    buf.append("{\n");
    for (String key : sorted(myProperties.keySet())) {
      appendSpace(buf, depth);
      buf.append(key);
      buf.append("    ");
      buf.append(myProperties.get(key));
      buf.append("\n");
    }
    for (String key : sorted(myChildren.keySet())) {
      appendSpace(buf, depth);
      buf.append(key);
      buf.append("    ");
      myChildren.get(key).makeString(buf, depth + 1);
    }
    appendSpace(buf, depth - 1);
    buf.append("}\n");
  }

  private static void appendSpace(@NotNull StringBuilder buf, int depth) {
    for (int i = 0; i < depth; i++) {
      buf.append("  ");
    }
  }
}
