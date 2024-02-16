package org.jetbrains.android.sdk;

import static com.google.common.truth.Truth.assertThat;

import com.android.testutils.TestUtils;
import com.android.tools.idea.res.AndroidInternalRClassFinder;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.AndroidSdkResolveScopeProvider;
import org.jetbrains.android.AndroidTestCase;

/**
 * Tests that link an SDK up to a simple project and verify that various code browsing features
 * work.
 */
// Suppress potential null return value warnings. In that case, the test will fail normally.
@SuppressWarnings("ConstantConditions")
public class AndroidSdkSourcesBrowsingTest extends AndroidTestCase {

  /**
   * Path under {@link #getTestDataPath()} where data files for this test class live.
   */
  private static final String TEST_ROOT_PATH = "sdkSourcesBrowsing/";

  private static final String DUMMY_PROJECT_PATH = TEST_ROOT_PATH + "dummy_project";

  /**
   * A prebuilt jar of all classes in dummy_project
   */
  private static final String DUMMY_CLASSES_JAR = "build/classes.jar";

  public void testHighlighting_FindsClassesInTheSamePackage() throws Exception {
    myFixture.allowTreeAccessForAllFiles();
    String projectRoot = initializeDummyProject();
    myFixture.configureFromExistingVirtualFile(LocalFileSystem.getInstance().findFileByPath(projectRoot + "/app/AppThread.java"));

    myFixture.checkHighlighting(false, false, false);
  }

  public void testHighlighting_FindsClassesInTheAndroidSdk() throws Exception {
    myFixture.allowTreeAccessForAllFiles();
    String projectRoot = initializeDummyProject();
    myFixture.configureFromExistingVirtualFile(LocalFileSystem.getInstance().findFileByPath(projectRoot + "/app/SomeActivity.java"));

    myFixture.checkHighlighting(false, false, false);
  }

  public void testHighlighting_FindsClassesByWildcardImport() throws Exception {
    myFixture.allowTreeAccessForAllFiles();
    String projectRoot = initializeDummyProject();
    myFixture.configureFromExistingVirtualFile(LocalFileSystem.getInstance().findFileByPath(projectRoot + "/util/UtilClass.java"));

    myFixture.checkHighlighting(false, false, false);
  }

  public void testHighlighting_EmptyProjectFindsClassesInTheAndroidSdkByImport() throws Exception {
    myFixture.allowTreeAccessForAllFiles();
    myFixture.configureFromExistingVirtualFile(
      myFixture.copyFileToProject(TEST_ROOT_PATH + "to_copy/MyActivity_WithImports.java", "/src/p1/p2/MyActivity.java"));

    myFixture.checkHighlighting(false, false, false);
  }

  public void testHighlighting_EmptyProjectFindsClassesInTheAndroidSdkByQualifiedName() throws Exception {
    myFixture.allowTreeAccessForAllFiles();
    myFixture.configureFromExistingVirtualFile(
      myFixture.copyFileToProject(TEST_ROOT_PATH + "to_copy/MyActivity_FullyQualified.java", "/src/p1/p2/MyActivity.java"));

    myFixture.checkHighlighting(false, false, false);
  }

  public void testNavigation_CanGoToBaseClassInTheAndroidSdk() throws Exception {
    myFixture.allowTreeAccessForAllFiles();
    myFixture.configureFromExistingVirtualFile(
      myFixture.copyFileToProject(TEST_ROOT_PATH + "to_copy/MyActivity_WithCaret.java", "/src/p1/p2/MyActivity.java"));

    PsiElement element = GotoDeclarationAction.findTargetElement(
      myFixture.getProject(), myFixture.getEditor(),
      myFixture.getEditor().getCaretModel().getOffset());
    VirtualFile activityVFile = element.getNavigationElement().getContainingFile().getVirtualFile();

    String expectedActivityFilePath = TestUtils.resolvePlatformPath("android.jar") + "!/android/app/Activity.class";
    assertTrue("Expected: " + expectedActivityFilePath + "\nActual: " + activityVFile.getPath(),
               FileUtil.pathsEqual(expectedActivityFilePath, activityVFile.getPath()));
  }

  public void testNavigation_CanGoToStringResource() throws Exception {
    // Caret on "cancel" in "int string_resource = android.R.string.cancel;"
    verifySuccessfulResourceNavigation(new LogicalPosition(26, 50), "strings.xml", XmlAttributeValue.class);
  }

  public void testNavigation_CanGoToDrawableResource() throws Exception {
    // Caret on menuitem_background int drawable_resource = android.R.drawable.menuitem_background;
    verifySuccessfulResourceNavigation(new LogicalPosition(27, 56), "menuitem_background.xml", XmlFile.class);
  }

  /**
   * This method places the caret at some {@code cursorPosition} in the test data source file
   * {@code app/SomeActivity.java}, which should have a reference to an android.R resource ID at
   * that location. This method then executes a navigation action and verifies it points at an
   * instance of {@code expectedPsiClass} in {@code expectedFile}.
   */
  private void verifySuccessfulResourceNavigation(
    LogicalPosition cursorPosition, String expectedFile, Class<? extends PsiElement> expectedPsiClass) {

    myFixture.allowTreeAccessForAllFiles();
    String projectRoot = initializeDummyProject();

    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(projectRoot + "/app/SomeActivity.java");

    myFixture.configureFromExistingVirtualFile(file);

    myFixture.getEditor().getCaretModel().moveToLogicalPosition(cursorPosition);

    PsiElement[] elements = GotoDeclarationAction.findAllTargetElements(
      myFixture.getProject(), myFixture.getEditor(),
      myFixture.getEditor().getCaretModel().getOffset());
    assertThat(elements.length).isAtLeast(1); // For strings.xml, also matches localized versions

    PsiElement element = elements[0];
    assertInstanceOf(element, expectedPsiClass);
    assertEquals(expectedFile, element.getContainingFile().getName());
  }

  public void testFindClass_CanFindInternalSdkReferences() throws Exception {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    PsiClass activity = facade.findClass("android.app.Activity", GlobalSearchScope.allScope(getProject()));

    GlobalSearchScope scope = activity.getNavigationElement().getResolveScope();
    assertInstanceOf(scope, AndroidSdkResolveScopeProvider.MyJdkScope.class);

    assertNotNull(facade.findClass(AndroidInternalRClassFinder.INTERNAL_R_CLASS_QNAME, scope));
    assertNotNull(facade.findClass(AndroidInternalRClassFinder.INTERNAL_R_CLASS_QNAME + ".string", scope));

    PsiClass[] classes = facade.findClasses(AndroidInternalRClassFinder.INTERNAL_R_CLASS_QNAME, scope);
    assertEquals(1, classes.length);
    classes = facade.findClasses(AndroidInternalRClassFinder.INTERNAL_R_CLASS_QNAME + ".string", scope);
    assertEquals(1, classes.length);
  }

  public void testFindClass_NoDuplicateAndroidSdkClassesFound() throws Exception {
    PsiClass[] classes = myFixture.getJavaFacade().findClasses(
      "android.app.Activity", GlobalSearchScope.allScope(myFixture.getProject()));
    assertEquals(1, classes.length);
  }

  /**
   * This method prepares a simple project which can be used by tests to confirm that the project
   * was hooked up in expected ways. When finished, it returns the full path to the source
   * directory under the project, which can be useful for finding source files in the project.
   *
   * <p>If a test only cares about a single source file that references the SDK directly, then this
   * method is not needed.
   */
  private String initializeDummyProject() {
    VirtualFile sourcesDir = myFixture.copyDirectoryToProject('/' + DUMMY_PROJECT_PATH, "/src");
    VirtualFile classesJar = JarFileSystem.getInstance().findFileByPath(sourcesDir.getPath() + "/" + DUMMY_CLASSES_JAR + "!/");

    Sdk sdk = ModuleRootManager.getInstance(myFixture.getModule()).getSdk();
    SdkModificator modificator = sdk.getSdkModificator();
    modificator.addRoot(sourcesDir, OrderRootType.SOURCES);
    modificator.addRoot(classesJar, OrderRootType.CLASSES);
    WriteAction.run(modificator::commitChanges);

    return sourcesDir.getPath();
  }
}
