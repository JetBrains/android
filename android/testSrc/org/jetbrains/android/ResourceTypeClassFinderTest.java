package org.jetbrains.android;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;

/**
 * @author Eugene.Kudelevsky
 */
public class ResourceTypeClassFinderTest extends AndroidTestCase {
  private static final String BASE_PATH = "/psiElementFinder/";

  public void testResourceClasses() {
    myFixture.copyFileToProject(BASE_PATH + "values.xml", "res/values/values.xml");
    PsiFile javaFile =
      myFixture.addFileToProject("src/com/example/Foo.java", "package com.example; class Foo {}");
    copyRJavaToGeneratedSources();
    final Project project = getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    assertNotNull(facade.findClass("p1.p2.R.drawable", javaFile.getResolveScope()));
    assertEquals(1, facade.findClasses("p1.p2.R.drawable", javaFile.getResolveScope()).length);
  }
}
