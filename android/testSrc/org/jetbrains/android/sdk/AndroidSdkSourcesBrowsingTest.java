package org.jetbrains.android.sdk;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.AndroidTestCase;
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

  private String configureMockSdk() {
    final String mockSdkPath = BASE_PATH + "mock_sdk";
    final VirtualFile mockSdkSourcesDir = myFixture.copyDirectoryToProject(mockSdkPath + "/sources", SDK_SOURCES_TARGET_PATH);
    VirtualFile classesJarFile = JarFileSystem.getInstance().
      findFileByPath(getTestDataPath() + "/" + mockSdkPath + "/classes.jar!/");
    assert classesJarFile != null;
    Sdk sdk = ProjectJdkTable.getInstance().createSdk("android_mock_sdk", AndroidSdkType.getInstance());
    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(mockSdkPath);
    SdkModificator modificator = sdk.getSdkModificator();
    modificator.removeAllRoots();
    modificator.addRoot(classesJarFile, OrderRootType.CLASSES);
    modificator.addRoot(mockSdkSourcesDir, OrderRootType.SOURCES);
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
    assert sdkSourcesDir != null && sdkSourcesDir.isDirectory();
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
