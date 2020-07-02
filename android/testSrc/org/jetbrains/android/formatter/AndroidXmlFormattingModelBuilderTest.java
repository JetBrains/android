// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.formatter;

import com.android.tools.idea.util.AndroidTestPaths;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.annotations.NotNull;

public class AndroidXmlFormattingModelBuilderTest extends LightJavaCodeInsightFixtureTestCase {

  public static final String LAYOUT_FILE = AndroidTestPaths.adtSources().resolve("android/testData/res/layout/test.xml").toAbsolutePath().toString();

  public void testNotEngagedWhenNoAndroidFacet() {
    PsiFile psiFile = myFixture.configureByFile(LAYOUT_FILE);
    assertNull("Precondition not satisfied: project should have no AndroidFacet applied", AndroidFacet.getInstance(psiFile));
    AndroidXmlFormattingModelBuilder androidFormatter = findAndroidXmlFormattingModelBuilder();
    assertFalse(androidFormatter.isEngagedToFormat(psiFile));
  }

  public void testEngagedWhenAndroidFacet() {
    PsiFile psiFile = myFixture.configureByFile(LAYOUT_FILE);
    Module module = ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(psiFile));

    withAndroidFacet(module, () -> {
      assertNotNull("Precondition not satisfied: project should have AndroidFacet applied", AndroidFacet.getInstance(psiFile));
      AndroidXmlFormattingModelBuilder androidFormatter = findAndroidXmlFormattingModelBuilder();
      assertTrue(androidFormatter.isEngagedToFormat(psiFile));
    });
  }

  private void withAndroidFacet(Module module, Runnable runnable) {
    AndroidFacetType facetType = AndroidFacet.getFacetType();
    final AndroidFacet f = facetType.createFacet(module, AndroidFacet.NAME, facetType.createDefaultConfiguration(), null);

    ApplicationManager.getApplication().runWriteAction(() -> {
      final ModifiableFacetModel model = FacetManager.getInstance(module).createModifiableModel();
      model.addFacet(f);
      model.commit();
    });

    try {
      runnable.run();
    }
    finally {
      ApplicationManager.getApplication().runWriteAction(() -> {
        final ModifiableFacetModel model = FacetManager.getInstance(module).createModifiableModel();
        model.removeFacet(f);
        model.commit();
      });
    }
  }

  @NotNull
  private AndroidXmlFormattingModelBuilder findAndroidXmlFormattingModelBuilder() {
    AndroidXmlFormattingModelBuilder androidFormatter = null;
    for (FormattingModelBuilder formatter : LanguageFormatting.INSTANCE.allForLanguage(XMLLanguage.INSTANCE)) {
      if (formatter instanceof AndroidXmlFormattingModelBuilder) {
        androidFormatter = (AndroidXmlFormattingModelBuilder)formatter;
      }
    }

    assertNotNull("No AndroidXmlFormattingModelBuilder found", androidFormatter);
    return androidFormatter;
  }
}