package org.jetbrains.android.sdk;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.android.AndroidSdkResolveScopeProvider;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.augment.AndroidPsiElementFinder;
import org.jetbrains.android.dom.wrappers.FileResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkSourcesBrowsingTest extends AndroidTestCase {
  @NonNls private static final String BASE_PATH = "sdkSourcesBrowsing/";
  @NonNls private static final String SDK_SOURCES_PATH_PREFIX = '/' + BASE_PATH + "sdk_sources_";
  @NonNls private static final String MODULE_DIR = "module";
  public static final String SDK_SOURCES_TARGET_PATH = "/sdk_sources";

  public void testSdkWithEmptySources() throws Exception {
    configureAndroidSdkWithSources(SDK_SOURCES_PATH_PREFIX + "1");

    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + "MyActivity1.java", MODULE_DIR + "/src/p1/p2/MyActivity.java");
    myFixture.configureFromExistingVirtualFile(f);
    myFixture.checkHighlighting(false, false, false);
  }

  public void testNavigationToSources() throws Exception {
    final String sdkSourcesPath = configureAndroidSdkWithSources(SDK_SOURCES_PATH_PREFIX + "2");

    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + "MyActivity2.java", MODULE_DIR + "/src/p1/p2/MyActivity.java");
    myFixture.configureFromExistingVirtualFile(f);

    PsiElement element = GotoDeclarationAction.findTargetElement(
      myFixture.getProject(), myFixture.getEditor(),
      myFixture.getEditor().getCaretModel().getOffset());
    assertNotNull(element);
    element = element.getNavigationElement();
    assertNotNull(element);
    final PsiFile activityPsiFile = element.getContainingFile();
    assertNotNull(activityPsiFile);
    final VirtualFile activityVFile = activityPsiFile.getVirtualFile();
    assertNotNull(activityVFile);

    final String expectedActivityFilePath = FileUtil.toSystemIndependentName(sdkSourcesPath + "/android/app/Activity.java");
    assertTrue("Expected: " + expectedActivityFilePath + "\nActual: " + activityVFile.getPath(),
               FileUtil.pathsEqual(expectedActivityFilePath, activityVFile.getPath()));
  }

  public void testSdkSourcesHighlighting1() throws Exception {
    myFixture.allowTreeAccessForAllFiles();
    final String sdkSourcesPath = configureMockSdk();
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(sdkSourcesPath + "/android/app/Activity.java");
    assertNotNull(file);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(false, false, false);
  }

  public void testSdkSourcesHighlighting2() throws Exception {
    myFixture.allowTreeAccessForAllFiles();
    final String sdkSourcesPath = configureMockSdk();
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(sdkSourcesPath + "/android/app/ActivityThread.java");
    assertNotNull(file);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(false, false, false);
  }

  public void testSdkSourcesHighlighting3() throws Exception {
    myFixture.allowTreeAccessForAllFiles();
    final String sdkSourcesPath = configureMockSdk();
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(sdkSourcesPath + "/util/UtilClass.java");
    assertNotNull(file);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(false, false, false);
  }

  public void testProjectSourcesHighlighting() throws Exception {
    myFixture.allowTreeAccessForAllFiles();
    configureMockSdk();
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + "MyActivity3.java", MODULE_DIR + "/src/p1/p2/MyActivity.java");
    myFixture.configureFromExistingVirtualFile(f);
    myFixture.checkHighlighting(false, false, false);
  }

  public void testNavigationToResources1() throws Exception {
    doTestNavigationToResource(new LogicalPosition(19, 35), 1, XmlAttributeValue.class);
  }

  public void testNavigationToResources2() throws Exception {
    doTestNavigationToResource(new LogicalPosition(20, 35), 2, XmlAttributeValue.class);
  }

  public void testNavigationToResources3() throws Exception {
    doTestNavigationToResource(new LogicalPosition(21, 35), 1, XmlAttributeValue.class);
  }

  public void testNavigationToResources4() throws Exception {
    doTestNavigationToResource(new LogicalPosition(22, 35), 1, XmlAttributeValue.class);
  }

  public void testNavigationToResources5() throws Exception {
    doTestNavigationToResource(new LogicalPosition(24, 43), 1, XmlAttributeValue.class);
  }

  public void testNavigationToResources6() throws Exception {
    doTestNavigationToResource(new LogicalPosition(25, 43), 2, XmlAttributeValue.class);
  }

  public void testNavigationToResources7() throws Exception {
    doTestNavigationToResource(new LogicalPosition(26, 43), 1, XmlAttributeValue.class);
  }

  public void testNavigationToResources8() throws Exception {
    doTestNavigationToResource(new LogicalPosition(27, 43), 1, XmlAttributeValue.class);
  }

  public void testNavigationToResources9() throws Exception {
    doTestNavigationToResource(new LogicalPosition(29, 46), 1, FileResourceElementWrapper.class);
  }

  public void testNavigationToResources10() throws Exception {
    doTestNavigationToResource(new LogicalPosition(30, 46), 1, FileResourceElementWrapper.class);
  }

  public void testFindingInternalResourceClasses() throws Exception {
    configureMockSdk();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    PsiClass activity = facade.findClass("android.app.Activity", GlobalSearchScope.allScope(getProject()));
    assertNotNull(activity);

    GlobalSearchScope scope = activity.getNavigationElement().getResolveScope();
    assertInstanceOf(scope, AndroidSdkResolveScopeProvider.MyJdkScope.class);

    assertNotNull(facade.findClass(AndroidPsiElementFinder.INTERNAL_R_CLASS_QNAME, scope));
    assertNotNull(facade.findClass(AndroidPsiElementFinder.INTERNAL_R_CLASS_QNAME + ".string", scope));

    PsiClass[] classes = facade.findClasses(AndroidPsiElementFinder.INTERNAL_R_CLASS_QNAME, scope);
    assertEquals(1, classes.length);
    classes = facade.findClasses(AndroidPsiElementFinder.INTERNAL_R_CLASS_QNAME + ".string", scope);
    assertEquals(1, classes.length);
  }

  public void testNoDuplicateAndroidSdkClassesFound() throws Exception {
    configureMockSdk();
    PsiClass[] classes = myFixture.getJavaFacade().findClasses(
      "android.app.Activity", GlobalSearchScope.allScope(myFixture.getProject()));
    assertEquals(1, classes.length);
  }

  private void doTestNavigationToResource(LogicalPosition position, int expectedCount, Class<?> aClass) {
    myFixture.allowTreeAccessForAllFiles();
    final String sdkSourcesPath = configureMockSdk();

    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(sdkSourcesPath + "/android/app/Activity.java");
    myFixture.configureFromExistingVirtualFile(file);

    myFixture.getEditor().getCaretModel().moveToLogicalPosition(position);

    PsiElement[] elements = GotoDeclarationAction.findAllTargetElements(
      myFixture.getProject(), myFixture.getEditor(),
      myFixture.getEditor().getCaretModel().getOffset());
    assertEquals(expectedCount, elements.length);

    for (PsiElement element : elements) {
      assertInstanceOf(LazyValueResourceElementWrapper.computeLazyElement(element), aClass);
    }
  }

  private String configureMockSdk() {
    final String mockSdkPath = BASE_PATH + "mock_sdk";
    final VirtualFile mockSdkSourcesDir = myFixture.copyDirectoryToProject(mockSdkPath + "/sources", SDK_SOURCES_TARGET_PATH);
    VirtualFile classesJarFile = JarFileSystem.getInstance().
      findFileByPath(getTestDataPath() + "/" + mockSdkPath + "/classes.jar!/");
    assert classesJarFile != null;
    Sdk sdk = ProjectJdkTable.getInstance().createSdk("android_mock_sdk", AndroidSdkType.getInstance());
    SdkModificator modificator = sdk.getSdkModificator();
    final AndroidSdkAdditionalData data = new AndroidSdkAdditionalData(sdk);
    data.setBuildTargetHashString("android-17");
    modificator.setSdkAdditionalData(data);
    final String testSdkPath = getTestSdkPath();
    modificator.setHomePath(testSdkPath);
    modificator.removeAllRoots();
    modificator.addRoot(classesJarFile, OrderRootType.CLASSES);
    modificator.addRoot(mockSdkSourcesDir, OrderRootType.SOURCES);
    final VirtualFile resDir = LocalFileSystem.getInstance().findFileByPath(testSdkPath + "/platforms/android-1.5/data/res");
    modificator.addRoot(resDir, OrderRootType.CLASSES);
    modificator.commitChanges();
    ModuleRootModificationUtil.setModuleSdk(myModule, sdk);
    return mockSdkSourcesDir.getPath();
  }

  private String configureAndroidSdkWithSources(String... sdkSourcesPaths) {
    addAndroidSdk(myModule, getTestSdkPath(), getPlatformDir());
    final VirtualFile sdkSourcesDir = myFixture.copyDirectoryToProject(sdkSourcesPaths[0], SDK_SOURCES_TARGET_PATH);

    for (int i = 1; i < sdkSourcesPaths.length; i++) {
      myFixture.copyDirectoryToProject(sdkSourcesPaths[i], SDK_SOURCES_TARGET_PATH);
    }
    assert sdkSourcesDir.isDirectory();
    final Sdk sdk = ModuleRootManager.getInstance(myFixture.getModule()).getSdk();
    assert  sdk != null;
    final SdkModificator modificator = sdk.getSdkModificator();
    modificator.addRoot(sdkSourcesDir, OrderRootType.SOURCES);
    modificator.commitChanges();
    return sdkSourcesDir.getPath();
  }

  @Override
  protected boolean isToAddSdk() {
    return false;
  }

  @Override
  protected String getContentRootPath() {
    return "/" + MODULE_DIR;
  }
}
