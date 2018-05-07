package org.jetbrains.android;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPsiElementFinderTest extends AndroidTestCase {
  private static final String BASE_PATH = "/psiElementFinder/";

  public void testResourceClasses() throws Exception {
    myFixture.copyFileToProject(BASE_PATH + "values.xml", "res/values/values.xml");
    myFixture.copyFileToProject("R.java", "src/p1/p2/R.java");
    final Project project = getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    assertNotNull(facade.findClass("p1.p2.R.drawable", GlobalSearchScope.projectScope(project)));
    assertEquals(1, facade.findClasses("p1.p2.R.drawable", GlobalSearchScope.projectScope(project)).length);
  }
}
