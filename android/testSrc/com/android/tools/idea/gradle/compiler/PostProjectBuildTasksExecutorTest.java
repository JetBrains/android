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

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link PostProjectBuildTasksExecutor}.
 */
public class PostProjectBuildTasksExecutorTest extends IdeaTestCase {
  private IdeaAndroidProject myAndroidProject1;
  private IdeaAndroidProject myAndroidProject2;

  private PostProjectBuildTasksExecutor myExecutor;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final Module module = createModule("second");

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        addAndroidFacet(myModule);
        addAndroidFacet(module);
      }
    });

    myAndroidProject1 = addMockAndroidProject(myModule);
    myAndroidProject2 = addMockAndroidProject(module);

    myExecutor = new PostProjectBuildTasksExecutor(myProject);
  }

  public void testGetMaxJavaLangLevel() {
    expect(myAndroidProject1.getJavaLanguageLevel()).andReturn(LanguageLevel.JDK_1_6);
    expect(myAndroidProject2.getJavaLanguageLevel()).andReturn(LanguageLevel.JDK_1_7);
    replay(myAndroidProject1, myAndroidProject2);

    LanguageLevel maxJavaLangLevel = myExecutor.getMaxJavaLangLevel();
    assertEquals(LanguageLevel.JDK_1_7, maxJavaLangLevel);

    verify(myAndroidProject1, myAndroidProject2);
  }

  public void testGetMaxJavaLangLevel2() {
    expect(myAndroidProject1.getJavaLanguageLevel()).andReturn(LanguageLevel.JDK_1_7);
    expect(myAndroidProject2.getJavaLanguageLevel()).andReturn(LanguageLevel.JDK_1_6);
    replay(myAndroidProject1, myAndroidProject2);

    LanguageLevel maxJavaLangLevel = myExecutor.getMaxJavaLangLevel();
    assertEquals(LanguageLevel.JDK_1_7, maxJavaLangLevel);

    verify(myAndroidProject1, myAndroidProject2);
  }

  public void testGetMaxJavaLangLevel3() {
    expect(myAndroidProject1.getJavaLanguageLevel()).andReturn(LanguageLevel.JDK_1_7);
    expect(myAndroidProject2.getJavaLanguageLevel()).andReturn(LanguageLevel.JDK_1_7);
    replay(myAndroidProject1, myAndroidProject2);

    LanguageLevel maxJavaLangLevel = myExecutor.getMaxJavaLangLevel();
    assertEquals(LanguageLevel.JDK_1_7, maxJavaLangLevel);

    verify(myAndroidProject1, myAndroidProject2);
  }

  public void testGetMaxJavaLangLevel4() {
    expect(myAndroidProject1.getJavaLanguageLevel()).andReturn(LanguageLevel.JDK_1_6);
    expect(myAndroidProject2.getJavaLanguageLevel()).andReturn(LanguageLevel.JDK_1_6);
    replay(myAndroidProject1, myAndroidProject2);

    LanguageLevel maxJavaLangLevel = myExecutor.getMaxJavaLangLevel();
    assertEquals(LanguageLevel.JDK_1_6, maxJavaLangLevel);

    verify(myAndroidProject1, myAndroidProject2);
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

  @NotNull
  private static IdeaAndroidProject addMockAndroidProject(@NotNull Module module) {
    IdeaAndroidProject androidProject = createMock(IdeaAndroidProject.class);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    facet.setIdeaAndroidProject(androidProject);
    return androidProject;
  }
}
