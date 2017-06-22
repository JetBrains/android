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

import java.util.Collection;
import java.util.HashSet;

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
        UrlData urlData = wrapper.getUrlData();
        if (urlData.isValid()) {
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
  static class InstantAppIntentFilterWrapper {
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
    UrlData getUrlData() {
      UrlData urlData = new UrlData();

      if (myElement != null) {
        Node node = myElement.getFirstChild();
        while (node != null) {
          urlData.addFromNode(node);
          node = node.getNextSibling();
        }
      }
      return urlData;
    }

    int getOrder() {
      return myOrder;
    }

    boolean matchesUrl(@NotNull String url) {
      return getUrlData().matchesUrl(url);
    }
  }

  @VisibleForTesting
  static class UrlData {
    @NotNull private final Collection<String> mySchemes = new HashSet<>();
    @NotNull private final Collection<String> myHosts = new HashSet<>();
    @NotNull private final Collection<String> myPaths = new HashSet<>();
    @NotNull private final Collection<String> myPathPrefixes = new HashSet<>();
    @NotNull private final Collection<String> myPathPatterns = new HashSet<>();
    // Documentation here: https://developer.android.com/guide/topics/manifest/data-element.html
    // port and mimeType should be ignored.

    @VisibleForTesting
    // Test only
    void addFromStrings(@NotNull String scheme, @NotNull String host, @NotNull String path, @NotNull String pathPrefix, @NotNull String pathPattern) {
      addTo(mySchemes, scheme);
      addTo(myHosts, host);
      addTo(myPaths, path);
      addTo(myPathPrefixes, pathPrefix);
      addTo(myPathPatterns, pathPattern);
    }

    @VisibleForTesting
    void addFromNode(@NotNull Node node) {
      if (node.getNodeType() == Node.ELEMENT_NODE && NODE_DATA.equals(node.getNodeName())) {
        Element element = (Element)node;
        addTo(mySchemes, element.getAttributeNS(ANDROID_URI, "scheme"));
        addTo(myHosts, element.getAttributeNS(ANDROID_URI, "host"));
        addTo(myPaths, element.getAttributeNS(ANDROID_URI, "path"));
        addTo(myPathPrefixes, element.getAttributeNS(ANDROID_URI, "pathPrefix"));
        addTo(myPathPatterns, element.getAttributeNS(ANDROID_URI, "pathPattern"));
      }
    }

    private static void addTo(@NotNull Collection<String> collection, @Nullable String string) {
      if (isNotEmpty(string)) {
        collection.add(string);
      }
    }

    @NotNull
    @VisibleForTesting
    static String convertPatternToExample(@NotNull String pattern) {
      return pattern.replace(".*", "example");
    }

    @VisibleForTesting
    boolean isValid() {
      return !mySchemes.isEmpty() && !myHosts.isEmpty() && getEffectivePath().startsWith("/");
    }

    @NotNull
    private String getEffectivePath() {
      String path = myPaths.isEmpty() ? "" : myPaths.iterator().next();
      if (isEmpty(path)) {
        path = myPathPrefixes.isEmpty() ? "" : myPathPrefixes.iterator().next() + "/.*";
      }
      if (isEmpty(path)) {
        path = myPathPatterns.isEmpty() ? "" : myPathPatterns.iterator().next();
      }
      return isNotEmpty(path) ? path : "/";
    }

    @NotNull
    @VisibleForTesting
    String getUrl() {
      if (!isValid()) {
        return "";
      }
      String scheme = mySchemes.iterator().next();
      String host = myHosts.iterator().next();
      return String.format("%s://%s%s", scheme, host, convertPatternToExample(getEffectivePath()));
    }

    @VisibleForTesting
    boolean matchesUrl(@NotNull String url) {
      if (!isValid()) {
        return false;
      }

      boolean schemeMatched = false;
      for (String scheme : mySchemes) {
        if (url.startsWith(scheme + "://")) {
          url = url.replaceFirst(scheme + "://", "");
          schemeMatched = true;
          break;
        }
      }
      if (!schemeMatched) {
        return false;
      }

      boolean hostMatched = false;
      for (String host : myHosts) {
        if (url.startsWith(host)) {
          url = url.replaceFirst(host, "");
          hostMatched = true;
          break;
        }
      }
      if (!hostMatched) {
        return false;
      }

      for (String path : myPaths) {
        if (isNotEmpty(path) && new AndroidPatternMatcher(path, PATTERN_LITERAL).match(url)) {
          return true;
        }
      }

      for (String pathPrefix : myPathPrefixes) {
        if (isNotEmpty(pathPrefix) && new AndroidPatternMatcher(pathPrefix, PATTERN_PREFIX).match(url)) {
          return true;
        }
      }

      for (String pathPattern : myPathPatterns) {
        if (isNotEmpty(pathPattern) && new AndroidPatternMatcher(pathPattern, PATTERN_SIMPLE_GLOB).match(url)) {
          return true;
        }
      }

      if (!myPaths.isEmpty() || !myPathPrefixes.isEmpty() || !myPathPatterns.isEmpty()) {
        return false;
      }

      return url.isEmpty() || url.compareTo("/") == 0;
    }
  }
}
