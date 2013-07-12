/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.refactoring.rtl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RtlSupportUsageViewDescriptor implements UsageViewDescriptor {

    private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.refactoring.rtl.RtlSupportUsageViewDescriptor");

    private RtlSupportProperties myProperties;

    public RtlSupportUsageViewDescriptor(RtlSupportProperties properties) {
        myProperties = properties;
    }

    @NotNull
    @Override
    public PsiElement[] getElements() {
        return PsiElement.EMPTY_ARRAY;
    }

    @Override
    public String getProcessedElementsHeader() {
        LOG.warn("Called getProcessedElementsHeader()");
        return "getProcessedElementsHeader()";
    }

    @Override
    public String getCodeReferencesText(int usagesCount, int filesCount) {
        LOG.warn("Called getCodeReferencesText()");
        return "getCodeReferencesText()";
    }

    @Nullable
    @Override
    public String getCommentReferencesText(int usagesCount, int filesCount) {
        LOG.warn("Called getCommentReferencesText()");
        return "getCommentReferencesText()";
    }

    public String getInfo() {
        return AndroidBundle.message("android.refactoring.rtl.addsupport.dialog.apply.button.text");
    }
}
