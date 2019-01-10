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
package com.android.tools.idea.resources.aar;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.tools.idea.resources.aar.Base128InputStream.StreamFormatException;
import com.intellij.util.ArrayUtil;
import java.io.IOException;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Simple implementation of the {@link ResourceNamespace.Resolver} interface intended to be used
 * together with {@link XmlPullParser}.
 */
final class NamespaceResolver implements ResourceNamespace.Resolver {
  static final NamespaceResolver EMPTY = new NamespaceResolver(ArrayUtil.EMPTY_STRING_ARRAY);

  /** Interleaved prefixes and the corresponding URIs in order of descending priority. */
  @NotNull private final String[] prefixesAndUris;

  NamespaceResolver(@NotNull XmlPullParser parser) throws XmlPullParserException {
    int namespaceCount = parser.getNamespaceCount(parser.getDepth());
    prefixesAndUris = new String[namespaceCount * 2];
    for (int i = 0, j = prefixesAndUris.length; i < namespaceCount; i++) {
      prefixesAndUris[--j] = parser.getNamespaceUri(i);
      prefixesAndUris[--j] = parser.getNamespacePrefix(i);
    }
  }

  private NamespaceResolver(@NotNull String[] prefixesAndUris) {
    this.prefixesAndUris = prefixesAndUris;
  }

  int getNamespaceCount() {
    return prefixesAndUris.length / 2;
  }

  @Override
  @Nullable
  public String prefixToUri(@NotNull String namespacePrefix) {
    for (int i = 0; i < prefixesAndUris.length; i += 2) {
      if (namespacePrefix.equals(prefixesAndUris[i])) {
        return prefixesAndUris[i + 1];
      }
    }
    return null;
  }

  @Override
  @Nullable
  public String uriToPrefix(@NotNull String namespaceUri) {
    for (int i = 0; i < prefixesAndUris.length; i += 2) {
      if (namespaceUri.equals(prefixesAndUris[i + 1])) {
        return prefixesAndUris[i];
      }
    }
    return null;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    NamespaceResolver other = (NamespaceResolver)obj;
    return Arrays.equals(prefixesAndUris, other.prefixesAndUris);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(prefixesAndUris);
  }

  /**
   * Serializes the namespace resolver to the given stream.
   */
  void serialize(@NotNull Base128OutputStream stream) throws IOException {
    stream.writeInt(getNamespaceCount());
    for (String str : prefixesAndUris) {
      stream.writeString(str);
    }
  }

  /**
   * Creates a namespace resolver by reading its contents of the given stream.
   * @see #serialize(Base128OutputStream)
   */
  @NotNull
  static NamespaceResolver deserialize(@NotNull Base128InputStream stream) throws IOException {
    int n = stream.readInt() * 2;
    String[] prefixesAndUris = new String[n];
    for (int i = 0; i < n; i++) {
      String s = stream.readString();
      if (s == null) {
        throw StreamFormatException.invalidFormat();
      }
      prefixesAndUris[i] = s;
    }
    return new NamespaceResolver(prefixesAndUris);
  }
}
