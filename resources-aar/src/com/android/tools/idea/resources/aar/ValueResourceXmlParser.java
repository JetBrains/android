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
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParserException;

/**
 * XML pull parser for value resource files. Provides access the resource namespace resolver
 * for the current tag.
 */
class ValueResourceXmlParser extends CommentTrackingXmlPullParser {
  @NotNull final Map<NamespaceResolver, NamespaceResolver> namespaceResolverCache = new HashMap<>();
  @NotNull final Deque<NamespaceResolver> resolverStack = new ArrayDeque<>(4);

  /**
   * Returns the namespace resolver for the current XML node. The parser has to be positioned on a start tag
   * when this method is called.
   */
  @NotNull
  public ResourceNamespace.Resolver getNamespaceResolver() throws XmlPullParserException {
    Preconditions.checkState(getEventType() == START_TAG);
    if (resolverStack.isEmpty()) {
      return ResourceNamespace.Resolver.EMPTY_RESOLVER;
    }
    NamespaceResolver resolver = resolverStack.getLast();
    return resolver.getNamespaceCount() == 0 ? ResourceNamespace.Resolver.EMPTY_RESOLVER : resolver;
  }

  @Override
  public void setInput(@NotNull Reader reader) throws XmlPullParserException {
    super.setInput(reader);
    resolverStack.clear();
  }

  @Override
  public void setInput(@NotNull InputStream inputStream, @Nullable String encoding) throws XmlPullParserException {
    super.setInput(inputStream, encoding);
    resolverStack.clear();
  }

  @Override
  public int nextToken() throws XmlPullParserException, IOException {
    int token = super.nextToken();
    processToken(token);
    return token;
  }

  @Override
  public int next() throws XmlPullParserException, IOException {
    int token = super.next();
    processToken(token);
    return token;
  }

  private void processToken(int token) throws XmlPullParserException {
    switch (token) {
      case START_TAG: {
        int namespaceCount = getNamespaceCount(getDepth());
        NamespaceResolver parent = resolverStack.isEmpty() ? null : resolverStack.getLast();
        NamespaceResolver current = parent != null && parent.getNamespaceCount() == namespaceCount ?
                                    parent : getOrCreateResolver();
        resolverStack.add(current);
        assert resolverStack.size() == getDepth();
        break;
      }

      case END_TAG:
        resolverStack.removeLast();
        break;
    }
  }

  private NamespaceResolver getOrCreateResolver() throws XmlPullParserException {
    return namespaceResolverCache.computeIfAbsent(new NamespaceResolver(this), Function.identity());
  }
}
