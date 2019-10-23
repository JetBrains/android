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
package org.jetbrains.android.dom.converters;

import com.android.SdkConstants;
import com.android.xml.AndroidManifest;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.WrappingConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link Converter} that references the right element based on the meta-data android:name attribute value.
 *
 * <p>When the android:name is "android.support.PARENT_ACTIVITY" the value is handled as an activity class. The default is handling the
 * android:value of meta-data as a ResourceValue.
 */
public class MetadataValueConverter extends WrappingConverter {
  private ResourceReferenceConverter myResourceReferenceConverter;
  private PackageClassConverter myActivityClassConverter;

  public MetadataValueConverter() {
    // Expanded suggestions list might be too long and take a while to load.
    myResourceReferenceConverter = new ResourceReferenceConverter();
    myResourceReferenceConverter.setExpandedCompletionSuggestion(false);
    myActivityClassConverter = new PackageClassConverter.Builder()
      .withExtendClassNames(SdkConstants.CLASS_ACTIVITY)
      .build();
  }

  @Nullable
  @Override
  public Converter getConverter(@NotNull GenericDomValue domElement) {
    XmlTag xmlTag = domElement.getXmlTag();
    boolean isActivityClassName =
      xmlTag != null && AndroidManifest.VALUE_PARENT_ACTIVITY.equals(xmlTag.getAttributeValue("name", SdkConstants.ANDROID_URI));
    return isActivityClassName ? myActivityClassConverter : myResourceReferenceConverter;
  }
}
