/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.android.quickfix;

import com.android.test.testutils.TestUtils;
import com.intellij.codeInsight.ImportFilter;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.facet.impl.FacetUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.android.InTextDirectivesUtils;

public abstract class AbstractAndroidQuickFixMultiFileTest extends AbstractQuickFixMultiFileTest {

    @NotNull
    @Override
    protected String getTestDataPath() {
      return TestUtils.resolveWorkspacePath("tools/adt/idea/android-kotlin").toString() + "/";
    }

    @Override
    protected void setUp() {
        super.setUp();
        addAndroidFacet();
        Extensions.getRootArea().getExtensionPoint(ImportFilter.EP_NAME).registerExtension(new KotlinTestImportFilter());
    }

    @Override
    protected void tearDown() {
        try {
            Extensions.getRootArea().getExtensionPoint(ImportFilter.EP_NAME).unregisterExtension(new KotlinTestImportFilter());
            AndroidFacet facet = FacetManager.getInstance(myFixture.getModule()).getFacetByType(AndroidFacet.getFacetType().getId());
            FacetUtil.deleteFacet(facet);
        } finally {
            super.tearDown();
        }
    }

    private void addAndroidFacet() {
        FacetManager facetManager = FacetManager.getInstance(myFixture.getModule());
        AndroidFacet facet = facetManager.createFacet(AndroidFacet.getFacetType(), "Android", null);

        ModifiableFacetModel facetModel = facetManager.createModifiableModel();
        facetModel.addFacet(facet);
        ApplicationManager.getApplication().runWriteAction(facetModel::commit);
    }

    // Adapted from the Kotlin test framework (after taking over android-kotlin sources).
    private static class KotlinTestImportFilter extends ImportFilter {
        @Override
        public boolean shouldUseFullyQualifiedName(@NotNull PsiFile file,
                                                   @NotNull String fqName) {
            return InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.getText(), "// DO_NOT_IMPORT:").contains(fqName);
        }
    }
}
