// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.formatter;

import static com.android.tools.idea.testing.Facets.withAndroidFacet;

import com.android.tools.idea.util.AndroidTestPaths;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class AndroidXmlFormattingModelBuilderTest extends LightJavaCodeInsightFixtureTestCase {

  public static final String LAYOUT_FILE = AndroidTestPaths.adtSources().resolve("android/testData/nonAndroidResources/res/layout/test.xml")
    .toAbsolutePath().toString();

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