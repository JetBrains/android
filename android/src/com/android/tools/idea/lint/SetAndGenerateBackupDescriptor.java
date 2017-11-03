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
package com.android.tools.idea.lint;

import com.android.SdkConstants;
import com.android.resources.ResourceUrl;
import com.android.resources.ResourceType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.inspections.lint.SetAttributeQuickFix;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;

/**
 * A {@link DefaultLintQuickFix} implementation responsible for
 * setting the {@link SdkConstants#ATTR_FULL_BACKUP_CONTENT} attribute
 * as well as generating the backup descriptor.
 * Uses composition to call the respective quick fixes.
 * <p>
 * Pre-conditions:
 * <ul>
 *   <li>{@link Sdkconstants#ATTR_ALLOW_BACKUP} is not explicitly set to false.</li>
 *   <li>android:fullBackUpContent attribute is not set.</li>
 *   <li>Value @xml/backup is not an existing file.</li>
 * </ul>
 * <p>
 * At the end of the quick fix, it opens up the backup descriptor in the editor.
 */
class SetAndGenerateBackupDescriptor extends DefaultLintQuickFix {
  private static final String RESOURCE_URL_NAME = "backup_descriptor";

  private final SetAttributeQuickFix mySetAttributeQuickFix;
  private final GenerateBackupDescriptorFix myGenerateDescriptorFix;

  public SetAndGenerateBackupDescriptor() {
    super("Set fullBackupContent attribute and generate descriptor");
    ResourceUrl resourceUrl = ResourceUrl.create(ResourceType.XML, RESOURCE_URL_NAME, false);
    mySetAttributeQuickFix =
      new SetAttributeQuickFix(myName, ATTR_FULL_BACKUP_CONTENT, resourceUrl.toString());
    myGenerateDescriptorFix = new GenerateBackupDescriptorFix(resourceUrl);
  }

  @Override
  public void apply(@NotNull final PsiElement startElement,
                    @NotNull PsiElement endElement,
                    @NotNull AndroidQuickfixContexts.Context context) {
    mySetAttributeQuickFix.apply(startElement, endElement, context);
    myGenerateDescriptorFix.apply(startElement, endElement, context);
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return isAllowBackupEnabled(startElement)
           && mySetAttributeQuickFix.isApplicable(startElement, endElement, contextType)
           && myGenerateDescriptorFix.isApplicable(startElement, endElement, contextType);
  }

  /**
   * @param startElement Element pointing to the an attribute of application
   * @return true iff android:allowBackup=true or the attribute is not set.
   */
  static boolean isAllowBackupEnabled(@NotNull PsiElement startElement) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);
    if (tag == null) {
      return true;
    }
    if (VALUE_FALSE.equals(tag.getAttributeValue(ATTR_ALLOW_BACKUP, ANDROID_URI))) {
      return false;
    }
    return true;
  }
}
