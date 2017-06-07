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
package com.android.tools.idea.instantapp;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.lint.checks.AndroidPatternMatcher;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.TreeMultimap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_LITERAL;
import static com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_PREFIX;
import static com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_SIMPLE_GLOB;
import static com.android.xml.AndroidManifest.NODE_DATA;
import static com.android.xml.AndroidManifest.NODE_INTENT;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public final class InstantAppUrlFinder {

  @NotNull private final Collection<Element> myActivities;

  public InstantAppUrlFinder(@NotNull MergedManifest manifest) {
    this(manifest.getActivities());
  }

  @VisibleForTesting
  InstantAppUrlFinder(@NotNull Collection<Element> activities) {
    myActivities = activities;
  }

  /**
   * Returns all instant app URLs found in this manifest
   */
  @NotNull
  public ImmutableCollection<String> getAllUrls() {
    TreeMultimap<Integer, String> allUrls = TreeMultimap.create();

    for (Element activity : myActivities) {
      Node node = activity.getFirstChild();
      while (node != null) {
        InstantAppIntentFilterWrapper wrapper = InstantAppIntentFilterWrapper.of(node);
        for (UrlData urlData : wrapper.getAllUrlData()) {
          allUrls.put(wrapper.getOrder(), urlData.getUrl());
        }
        node = node.getNextSibling();
      }
    }

    return ImmutableList.copyOf(allUrls.values());
  }

  /**
   * Gets the default URL for this manifest. Returns an empty string if no URLs are found.
   *
   * @return
   */
  @NotNull
  public String getDefaultUrl() {
    ImmutableCollection<String> urls = getAllUrls();
    if (!urls.isEmpty()) {
      return urls.iterator().next();
    }
    return "";
  }

  public boolean matchesUrl(@NotNull String url) {
    for (Element activity : myActivities) {
      Node node = activity.getFirstChild();
      while (node != null) {
        InstantAppIntentFilterWrapper wrapper = InstantAppIntentFilterWrapper.of(node);
        if (wrapper.matchesUrl(url)) {
          return true;
        }
        node = node.getNextSibling();
      }
    }
    return false;
  }

  @VisibleForTesting
  static final class InstantAppIntentFilterWrapper {
    @Nullable/*No valid element*/ private final Element myElement;
    private final int myOrder;

    private InstantAppIntentFilterWrapper(@Nullable Element element, int order) {
      myElement = element;
      myOrder = order;
    }

    @NotNull
    static InstantAppIntentFilterWrapper of(@NotNull Node node) {
      Element element;
      int order;
      try {
        element = getElement(node);
        order = getOrder(element);
      }
      catch (IllegalArgumentException unused) {
        element = null;
        order = -1;
      }
      return new InstantAppIntentFilterWrapper(element, order);
    }

    @NotNull
    @VisibleForTesting
    static Element getElement(@NotNull Node node) {
      if (node.getNodeType() == Node.ELEMENT_NODE && NODE_INTENT.equals(node.getNodeName())) {
        return (Element)node;
      }
      else {
        throw new IllegalArgumentException();
      }
    }

    @VisibleForTesting
    static int getOrder(@NotNull Element element) {
      String orderValue = element.getAttributeNS(ANDROID_URI, "order");
      if (isNotEmpty(orderValue)) {
        try {
          return Integer.parseUnsignedInt(orderValue);
        }
        catch (NumberFormatException unused) {
          throw new IllegalArgumentException();
        }
      }
      return 0;
    }

    @NotNull
    Collection<UrlData> getAllUrlData() {
      if (myElement == null) {
        return Collections.emptyList();
      }

      List<UrlData> allUrls = new ArrayList<>();
      Node node = myElement.getFirstChild();
      while (node != null) {
        UrlData data = UrlData.of(node);
        if (data.isValid()) {
          allUrls.add(data);
        }
        node = node.getNextSibling();
      }
      return allUrls;
    }

    int getOrder() {
      return myOrder;
    }

    boolean matchesUrl(@NotNull String url) {
      if (myElement != null) {
        Node node = myElement.getFirstChild();
        while (node != null) {
          UrlData data = UrlData.of(node);
          if (data.matchesUrl(url)) {
            return true;
          }
          node = node.getNextSibling();
        }
      }
      return false;
    }
  }


  @VisibleForTesting
  static final class UrlData {
    @NotNull private final String myScheme;
    @NotNull private final String myHost;
    @NotNull private final String myPath;
    @NotNull private final String myPathPrefix;
    @NotNull private final String myPathPattern;
    // Documentation here: https://developer.android.com/guide/topics/manifest/data-element.html
    // port and mimeType should be ignored.

    @NotNull
    static UrlData of(@NotNull Node node) {
      String scheme = "";
      String host = "";
      String path = "";
      String pathPrefix = "";
      String pathPattern = "";
      if (node.getNodeType() == Node.ELEMENT_NODE && NODE_DATA.equals(node.getNodeName())) {
        Element element = (Element)node;
        scheme = element.getAttributeNS(ANDROID_URI, "scheme");
        host = element.getAttributeNS(ANDROID_URI, "host");
        path = element.getAttributeNS(ANDROID_URI, "path");
        pathPrefix = element.getAttributeNS(ANDROID_URI, "pathPrefix");
        pathPattern = element.getAttributeNS(ANDROID_URI, "pathPattern");
      }
      return new UrlData(scheme, host, path, pathPrefix, pathPattern);
    }

    @VisibleForTesting
    UrlData(@NotNull String scheme, @NotNull String host, @NotNull String path, @NotNull String pathPrefix, @NotNull String pathPattern) {
      myScheme = scheme;
      myHost = host;
      myPath = path;
      myPathPrefix = pathPrefix;
      myPathPattern = pathPattern;
    }

    @NotNull
    @VisibleForTesting
    static String convertPatternToExample(@NotNull String pattern) {
      return pattern.replace(".*", "example");
    }

    boolean isValid() {
      String effectivePath = getEffectivePath();
      return isNotEmpty(myScheme) && isNotEmpty(myHost) && effectivePath.startsWith("/");
    }

    @NotNull
    private String getEffectivePath() {
      String path = myPath;
      if (isEmpty(path)) {
        if (isNotEmpty(myPathPrefix)) {
          path = myPathPrefix + "/.*";
        }
        else {
          path = myPathPattern;
        }
      }
      return isNotEmpty(path) ? path : "/";
    }

    @NotNull
    String getUrl() {
      return String.format("%s://%s%s", myScheme, myHost, convertPatternToExample(getEffectivePath()));
    }

    boolean matchesUrl(@NotNull String url) {
      String beg = String.format("%s://%s", myScheme, myHost);
      if (!url.startsWith(beg)) {
        return false;
      }

      String path = url.replaceFirst(beg, "");
      if (isNotEmpty(myPath)) {
        AndroidPatternMatcher matcher = new AndroidPatternMatcher(myPath, PATTERN_LITERAL);
        return matcher.match(path);
      }
      else if (isNotEmpty(myPathPrefix)) {
        AndroidPatternMatcher matcher = new AndroidPatternMatcher(myPathPrefix, PATTERN_PREFIX);
        return matcher.match(path);
      }
      else if (isNotEmpty(myPathPattern)) {
        AndroidPatternMatcher matcher = new AndroidPatternMatcher(myPathPattern, PATTERN_SIMPLE_GLOB);
        return matcher.match(path);
      }
      return path.isEmpty() || path.compareTo("/") == 0;
    }
  }
}
