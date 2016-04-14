/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.res2.DataBindingResourceItem;
import com.android.ide.common.res2.DataBindingResourceType;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Keeps resource items that are related to data binding, extracted from layout files.
 */
public class PsiDataBindingResourceItem extends DataBindingResourceItem {
  private XmlTag myXmlTag;
  private Map<String, String> myData;

  public PsiDataBindingResourceItem(@NonNull String name, DataBindingResourceType type, XmlTag xmlTag) {
    super(name, type);
    myXmlTag = xmlTag;
    myData = Maps.newHashMap();
    for (String data : type.attributes) {
      myData.put(data, StringUtil.unescapeXml(myXmlTag.getAttributeValue(data)));
    }
  }

  /**
   * Use {@link #getTypeDeclaration()} to get the type instead of this method.
   */
  public String getExtra(String name) {
    return myData.get(name);
  }

  /**
   * Same as sanitized the output of {@link #getExtra(String)} with {@link SdkConstants#ATTR_TYPE}.
   */
  @Nullable
  public String getTypeDeclaration() {
    String type = getExtra(SdkConstants.ATTR_TYPE);
    return type == null ? null : type.replace('$', '.');
  }

  public XmlTag getXmlTag() {
    return myXmlTag;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    PsiDataBindingResourceItem item = (PsiDataBindingResourceItem)o;

    if (!myData.equals(item.myData)) return false;
    if (!myXmlTag.equals(item.myXmlTag)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myXmlTag.hashCode();
    result = 31 * result + myData.hashCode();
    return result;
  }
}
