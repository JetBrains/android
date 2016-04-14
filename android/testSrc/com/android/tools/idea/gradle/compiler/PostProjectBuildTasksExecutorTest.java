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
package com.android.tools.idea.gradle.compiler;

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link PostProjectBuildTasksExecutor}.
 */
public class PostProjectBuildTasksExecutorTest extends IdeaTestCase {
  private AndroidGradleModel myAndroidModel1;
  private AndroidGradleModel myAndroidModel2;

  private PostProjectBuildTasksExecutor myExecutor;
  private Module myModule2;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModule2 = createModule("second");

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        addAndroidFacet(myModule);
        addAndroidFacet(myModule2);
      }
    });

    myAndroidModel1 = createMock(AndroidGradleModel.class); // addMockAndroidProject(myModule);
    myAndroidModel2 = createMock(AndroidGradleModel.class); // addMockAndroidProject(module);

    myExecutor = new PostProjectBuildTasksExecutor(myProject);
  }

  @Override
  protected void checkForSettingsDamage(@NotNull List<Throwable> exceptions) {
    // for this test we don't care for this check
  }

  public void testGetMaxJavaLangLevel() {
    expect(myAndroidModel1.getDataBindingEnabled()).andStubReturn(false);
    expect(myAndroidModel2.getDataBindingEnabled()).andStubReturn(false);

    expect(myAndroidModel1.getJavaLanguageLevel()).andStubReturn(LanguageLevel.JDK_1_6);
    expect(myAndroidModel2.getJavaLanguageLevel()).andStubReturn(LanguageLevel.JDK_1_7);

    replay(myAndroidModel1, myAndroidModel2);

    addAndroidModelToFacet(myModule, myAndroidModel1);
    addAndroidModelToFacet(myModule2, myAndroidModel2);

    LanguageLevel maxJavaLangLevel = myExecutor.getMaxJavaLangLevel();
    assertEquals(LanguageLevel.JDK_1_7, maxJavaLangLevel);

    verify(myAndroidModel1, myAndroidModel2);
  }

  public void testGetMaxJavaLangLevel2() {
    expect(myAndroidModel1.getDataBindingEnabled()).andStubReturn(false);
    expect(myAndroidModel2.getDataBindingEnabled()).andStubReturn(false);

    expect(myAndroidModel1.getJavaLanguageLevel()).andStubReturn(LanguageLevel.JDK_1_7);
    expect(myAndroidModel2.getJavaLanguageLevel()).andStubReturn(LanguageLevel.JDK_1_6);

    replay(myAndroidModel1, myAndroidModel2);

    addAndroidModelToFacet(myModule, myAndroidModel1);
    addAndroidModelToFacet(myModule2, myAndroidModel2);

    LanguageLevel maxJavaLangLevel = myExecutor.getMaxJavaLangLevel();
    assertEquals(LanguageLevel.JDK_1_7, maxJavaLangLevel);

    verify(myAndroidModel1, myAndroidModel2);
  }

  public void testGetMaxJavaLangLevel3() {
    expect(myAndroidModel1.getDataBindingEnabled()).andStubReturn(false);
    expect(myAndroidModel2.getDataBindingEnabled()).andStubReturn(false);

    expect(myAndroidModel1.getJavaLanguageLevel()).andStubReturn(LanguageLevel.JDK_1_7);
    expect(myAndroidModel2.getJavaLanguageLevel()).andStubReturn(LanguageLevel.JDK_1_7);

    replay(myAndroidModel1, myAndroidModel2);

    addAndroidModelToFacet(myModule, myAndroidModel1);
    addAndroidModelToFacet(myModule2, myAndroidModel2);

    LanguageLevel maxJavaLangLevel = myExecutor.getMaxJavaLangLevel();
    assertEquals(LanguageLevel.JDK_1_7, maxJavaLangLevel);

    verify(myAndroidModel1, myAndroidModel2);
  }

  public void testGetMaxJavaLangLevel4() {
    expect(myAndroidModel1.getDataBindingEnabled()).andStubReturn(false);
    expect(myAndroidModel2.getDataBindingEnabled()).andStubReturn(false);

    expect(myAndroidModel1.getJavaLanguageLevel()).andStubReturn(LanguageLevel.JDK_1_6);
    expect(myAndroidModel2.getJavaLanguageLevel()).andStubReturn(LanguageLevel.JDK_1_6);

    replay(myAndroidModel1, myAndroidModel2);

    addAndroidModelToFacet(myModule, myAndroidModel1);
    addAndroidModelToFacet(myModule2, myAndroidModel2);

    LanguageLevel maxJavaLangLevel = myExecutor.getMaxJavaLangLevel();
    assertEquals(LanguageLevel.JDK_1_6, maxJavaLangLevel);

    verify(myAndroidModel1, myAndroidModel2);
  }

  private static void addAndroidFacet(@NotNull Module module) {
    FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    try {
      AndroidFacet facet = facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
      facetModel.addFacet(facet);
    } finally {
      facetModel.commit();
    }
  }

  private static void addAndroidModelToFacet(@NotNull Module module, @NotNull AndroidGradleModel androidModel) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    facet.setAndroidModel(androidModel);
  }
}
