package org.jetbrains.android;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.dom.manifest.Manifest;

/**
 * Tests for {@link com.android.tools.idea.res.AndroidInnerClassFinder}.
 */
public class AndroidInnerClassFinderTest extends AndroidTestCase {
  private static final String BASE_PATH = "/psiElementFinder/";

  public void testRClasses() {
    myFixture.copyFileToProject(BASE_PATH + "values.xml", "res/values/values.xml");
    PsiFile javaFile =
      myFixture.addFileToProject("src/com/example/Foo.java", "package com.example; class Foo {}");
    assertNotNull(myFixture.getJavaFacade().findClass("p1.p2.R.drawable", javaFile.getResolveScope()));
    assertEquals(1, myFixture.getJavaFacade().findClasses("p1.p2.R.drawable", javaFile.getResolveScope()).length);
  }

  public void testManifestClasses() {
    WriteCommandAction.runWriteCommandAction(getProject(),
                                             () -> Manifest.getMainManifest(myFacet).addPermission().getName().setValue("com.example.SEND_MESSAGE"));
    PsiFile javaFile =
      myFixture.addFileToProject("src/com/example/Foo.java", "package com.example; class Foo {}");
    assertNotNull(myFixture.getJavaFacade().findClass("p1.p2.Manifest.permission", javaFile.getResolveScope()));
    assertEquals(1, myFixture.getJavaFacade().findClasses("p1.p2.Manifest.permission", javaFile.getResolveScope()).length);
  }
}
