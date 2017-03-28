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
package org.jetbrains.android.util;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.model.MergedManifest;
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
import static com.android.SdkConstants.INSTANT_APP_URI;
import static com.android.xml.AndroidManifest.NODE_DATA;
import static com.android.xml.AndroidManifest.NODE_INTENT;
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

  @VisibleForTesting
  static final class InstantAppIntentFilterWrapper {
    @Nullable/*No valid element*/ private final Element myElement;
    private final int myOrder;

    private InstantAppIntentFilterWrapper(@Nullable Element element, int order) {
      myElement = element;
      myOrder = order;
    }

    @NotNull
    public static InstantAppIntentFilterWrapper of(@NotNull Node node) {
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
    public static Element getElement(@NotNull Node node) {
      if (node.getNodeType() == Node.ELEMENT_NODE && NODE_INTENT.equals(node.getNodeName())) {
        return (Element)node;
      }
      else {
        throw new IllegalArgumentException();
      }
    }

    @VisibleForTesting
    public static int getOrder(@NotNull Element element) {
      String orderValue = element.getAttributeNS(INSTANT_APP_URI, "order");
      try {
        return Integer.parseUnsignedInt(orderValue);
      }
      catch (NumberFormatException unused) {
        throw new IllegalArgumentException();
      }
    }

    @NotNull
    public Collection<UrlData> getAllUrlData() {
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

    public int getOrder() {
      return myOrder;
    }
  }


  @VisibleForTesting
  static final class UrlData {
    private final String myScheme;
    private final String myHost;
    private final String myPathPattern;

    @NotNull
    public static UrlData of(@NotNull Node node) {
      String scheme = "";
      String host = "";
      String pathPattern = "";
      if (node.getNodeType() == Node.ELEMENT_NODE && NODE_DATA.equals(node.getNodeName())) {
        Element element = (Element)node;
        scheme = element.getAttributeNS(ANDROID_URI, "scheme");
        host = element.getAttributeNS(ANDROID_URI, "host");
        pathPattern = element.getAttributeNS(ANDROID_URI, "pathPattern");
      }
      return new UrlData(scheme, host, pathPattern);
    }

    @VisibleForTesting
    public UrlData(String scheme, String host, String pathPattern) {
      myScheme = scheme;
      myHost = host;
      myPathPattern = pathPattern;
    }

    @NotNull
    @VisibleForTesting
    public static String convertPatternToExample(String pattern) {
      return pattern.replace(".*", "parameter").replace("?", "X");
    }

    public boolean isValid() {
      return isNotEmpty(myScheme) && isNotEmpty(myHost) && isNotEmpty(myPathPattern);
    }

    @NotNull
    public String getUrl() {
      return String.format("%s://%s/%s", myScheme, myHost, convertPatternToExample(myPathPattern));
    }
  }
}
