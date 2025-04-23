/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.jetbrains.android.formatter;

import com.android.xml.AttrNameSplitter;
import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.NameAwareArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.std.CustomArrangementOrderToken;
import java.util.Comparator;
import org.jetbrains.annotations.NotNull;

public final class AndroidAttributeOrder extends CustomArrangementOrderToken {
  static final String ID = "ANDROID_ATTRIBUTE_ORDER";
  public static final AndroidAttributeOrder INSTANCE = new AndroidAttributeOrder();

  private AndroidAttributeOrder() {
    super(ID, "Android attribute order");
  }

  @NotNull
  @Override
  public Comparator<ArrangementEntry> getEntryComparator() {
    return new AttributeComparator<>(AndroidAttributeOrder::getLocalPart);
  }

  @NotNull
  private static String getLocalPart(@NotNull ArrangementEntry attribute) {
    if (!(attribute instanceof NameAwareArrangementEntry)) {
      throw new IllegalArgumentException(attribute.getClass().toString());
    }

    String qualifiedName = ((NameAwareArrangementEntry)attribute).getName();
    assert qualifiedName != null;

    return AttrNameSplitter.findLocalName(qualifiedName);
  }
}
