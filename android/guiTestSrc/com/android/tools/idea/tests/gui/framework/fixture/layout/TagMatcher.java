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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.intellij.psi.xml.XmlTag;
import org.fest.swing.annotation.RunsInCurrentThread;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.tools.lint.detector.api.LintUtils.stripIdPrefix;

/**
 * Matcher for XML tags
 */
public abstract class TagMatcher {
  /** Returns true if the given tag is the one we seek */
  @RunsInCurrentThread
  protected abstract boolean isMatching(@NotNull XmlTag tag);

  /**
   * Finds a tag that matches the given android id
   */
  public static class IdMatcher extends TagMatcher {
    private final String myId;

    public IdMatcher(@NotNull String id) {
      myId = stripIdPrefix(id);
    }

    @Override
    protected boolean isMatching(@NotNull XmlTag tag) {
      return stripIdPrefix(tag.getAttributeValue(ATTR_ID, ANDROID_URI)).equals(myId);
    }
  }

  /**
   * Finds a tag that matches the given attribute value
   */
  public static class AttributeMatcher extends TagMatcher {
    private final String myNamespace;
    private final String myAttribute;
    private final String myValue;

    public AttributeMatcher(@NotNull String attribute, @NotNull String namespace, @NotNull String value) {
      myAttribute = attribute;
      myNamespace = namespace;
      myValue = value;
    }

    @Override
    protected boolean isMatching(@NotNull XmlTag tag) {
      return myValue.equals(tag.getAttributeValue(myAttribute, myNamespace));
    }
  }
}
