/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lint.quickFixes;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.xml.AndroidManifest.ATTRIBUTE_REQUIRED;
import static com.android.xml.AndroidManifest.NODE_MANIFEST;
import static com.android.xml.AndroidManifest.NODE_PERMISSION;
import static com.android.xml.AndroidManifest.NODE_USES_CONFIGURATION;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;
import static com.android.xml.AndroidManifest.NODE_USES_SDK;

import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.common.DefaultLintQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Quickfix for adding a &lt;uses-feature&gt; element with required="false" to
 * the AndroidManifest.xml
 * <p>
 * Note: The quick fix attempts to add the uses-feature tag after tags to adhere
 * to the typical manifest ordering. It finds and adds the element after the following
 * elements if present and skips to the next element up the chain.
 * e.g: if only uses-sdk is present, the new uses-feature tag is added after the uses-sdk
 * <ul>
 *   <li>uses-feature</li>
 *   <li>uses-configuration</li>
 *   <li>uses-sdk</li>
 *   <li>node-permission</li>
 * </ul>
 * If none of the above elements are present, then it adds the uses-feature element
 * as the first child of the manifest element.
 */
public class AddUsesFeatureQuickFix extends DefaultLintQuickFix {

  private final String myFeatureName;

  public AddUsesFeatureQuickFix(@NotNull String featureName) {
    super("Add uses-feature tag");
    myFeatureName = featureName;
  }

  @Override
  public void apply(@NotNull PsiElement startElement,
                    @NotNull PsiElement endElement,
                    @NotNull AndroidQuickfixContexts.Context context) {
    XmlTag parent = PsiTreeUtil.getTopmostParentOfType(startElement, XmlTag.class);
    if (parent == null || !NODE_MANIFEST.equals(parent.getName())) {
      return;
    }
    XmlTag usesFeatureTag = parent.createChildTag(NODE_USES_FEATURE, null, null, false);
    XmlTag ancestor = findLocationForUsesFeature(parent);
    if (ancestor != null) {
      // Add the uses-feature element after all uses-feature tags if any.
      usesFeatureTag = (XmlTag)parent.addAfter(usesFeatureTag, ancestor);
    }
    else {
      usesFeatureTag = parent.addSubTag(usesFeatureTag, true);
    }
    if (usesFeatureTag != null) {
      usesFeatureTag.setAttribute(ATTR_NAME, ANDROID_URI, myFeatureName);
      usesFeatureTag.setAttribute(ATTRIBUTE_REQUIRED, ANDROID_URI, VALUE_FALSE);
    }
  }

  // Find the correct location in the manifest to add the uses-feature tag.
  // https://developer.android.com/guide/topics/manifest/manifest-intro.html
  @Nullable
  private static XmlTag findLocationForUsesFeature(XmlTag parent) {
    // reverse manifest order for location of uses-feature.
    // The reason this is not a static final is to prevent the array creation at
    // clinit time and delay it to when the fix is applied.
    String[] reverseOrderManifestElements = {
      NODE_USES_FEATURE, NODE_USES_CONFIGURATION, NODE_USES_SDK, NODE_PERMISSION
    };
    for (String elementName : reverseOrderManifestElements) {
      XmlTag[] existingTags = parent.findSubTags(elementName);
      int len = existingTags.length;
      if (len > 0) {
        return existingTags[len - 1];
      }
    }
    return null;
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    XmlTag xmlTag = PsiTreeUtil.getTopmostParentOfType(startElement, XmlTag.class);
    return xmlTag != null && NODE_MANIFEST.equals(xmlTag.getName());
  }
}
