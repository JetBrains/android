package org.jetbrains.android.dom;

import com.android.SdkConstants;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.android.AndroidFindUsagesTest;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.inspections.AndroidDomInspection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLibraryProjectTest extends AndroidTestCase {
  private static final String BASE_PATH = "libModule/";

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(BASE_PATH + "LibAndroidManifest.xml", "additionalModules/lib/" + SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyDirectoryToProject("res", "res");
    myFixture.copyDirectoryToProject(BASE_PATH + "res", "additionalModules/lib/res");

    myFixture.enableInspections(AndroidDomInspection.class);
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    addModuleWithAndroidFacet(projectBuilder, modules, "lib", PROJECT_TYPE_LIBRARY, true);
  }

  public void setUpLibraryRClass() {
    if (!StudioFlags.IN_MEMORY_R_CLASSES.get()) {
      myFixture.copyFileToProject(BASE_PATH + "LibR.java", "additionalModules/lib/gen/p1/p2/lib/R.java");
    }
  }

  public void testHighlighting() {
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestManifest(), "res/layout/" + getTestManifest());
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.doHighlighting();
    myFixture.checkHighlighting(true, false, true);
  }

  public void testHighlighting1() {
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestManifest(), "res/layout/" + getTestManifest());
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.doHighlighting();
    myFixture.checkHighlighting(true, false, true);
  }

  public void testHighlighting2() {
    myFixture.copyFileToProject(BASE_PATH + "LibAndroidManifest.xml", "additionalModules/lib/" + SdkConstants.FN_ANDROID_MANIFEST_XML);
    VirtualFile manifestFile = myFixture.copyFileToProject(BASE_PATH + getTestManifest(), SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyDirectoryToProject(BASE_PATH + "res", "additionalModules/lib/res");
    myFixture.configureFromExistingVirtualFile(manifestFile);
    myFixture.doHighlighting();
    myFixture.checkHighlighting(true, false, true);
  }

  public void testJavaHighlighting() {
    setUpLibraryRClass();
    String to = "additionalModules/lib/src/p1/p2/lib/" + getTestName(true) + ".java";
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".java", to);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.doHighlighting();
    myFixture.checkHighlighting(true, true, true);
  }

  public void testCompletion() {
    doTestCompletion();
  }

  public void testCompletion1() {
    doTestCompletion();
  }

  public void testCustomAttrCompletion() {
    myFixture.copyFileToProject(BASE_PATH + "LibView.java", "additionalModules/lib/src/p1/p2/lib/LibView.java");
    myFixture.copyFileToProject(BASE_PATH + "lib_attrs.xml", "additionalModules/lib/res/values/lib_attrs.xml");
    doTestCompletion();
  }

  private void doTestCompletion() {
    String to = "res/layout/" + getTestManifest();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestManifest(), to);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after.xml");
  }

  public void testJavaNavigation() {
    copyRJavaToGeneratedSources();
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".java", "src/p1/p2/Java.java");
    myFixture.configureFromExistingVirtualFile(file);

    PsiElement[] targets =
      GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
    assertNotNull(targets);
    assertEquals(1, targets.length);
    PsiElement targetElement = targets[0];
    assertInstanceOf(targetElement, PsiFile.class);
    assertEquals("main.xml", ((PsiFile)targetElement).getName());
  }

  public void testFileResourceFindUsages() throws Throwable {
    doFindUsagesTest("xml", "additionalModules/lib/res/layout/");
  }

  public void testFileResourceFindUsages1() throws Throwable {
    doFindUsagesTest("xml", "res/layout/");
  }

  public void testFileResourceFindUsagesFromJava() throws Throwable {
    doFindUsagesTest("java", "src/p1/p2/");
  }

  public void testFileResourceFindUsagesFromJava1() throws Throwable {
    doFindUsagesTest("java", "src/p1/p2/lib/");
  }

  public void testFileResourceFindUsagesFromJava2() throws Throwable {
    doFindUsagesTest("java", "additionalModules/lib/src/p1/p2/lib/");
  }

  public void testValueResourceFindUsages() throws Throwable {
    doFindUsagesTest("xml", "additionalModules/lib/res/layout/");
  }

  public void testValueResourceFindUsages1() throws Throwable {
    doFindUsagesTest("xml", "res/layout/");
  }

  private void doFindUsagesTest(String extension, String dir) throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesClass.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesClass1.java", "src/p1/p2/lib/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesClass1.java", "additionalModules/lib/src/p1/p2/lib/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesStyles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject(BASE_PATH + "picture1.png", "additionalModules/lib/res/drawable/picture1.png");
    copyRJavaToGeneratedSources();
    setUpLibraryRClass();

    VirtualFileManager.getInstance().syncRefresh();

    Collection<UsageInfo> references = findCodeUsages(getTestName(false) + "." + extension, dir);
    assertEquals(buildFileList(references), 5, references.size());
  }

  private List<UsageInfo> findCodeUsages(String path, String dir) throws Throwable {
    String newFilePath = dir + path;
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + path, newFilePath);
    Collection<UsageInfo> usages = AndroidFindUsagesTest.findUsages(file, myFixture);
    List<UsageInfo> result = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (!usage.isNonCodeUsage) {
        result.add(usage);
      }
    }
    return result;
  }

  @NotNull
  private String getTestManifest() {
    return getTestName(true) + ".xml";
  }

  private static String buildFileList(Collection<UsageInfo> infos) {
    final StringBuilder result = new StringBuilder();

    for (UsageInfo info : infos) {
      final PsiFile file = info.getFile();
      final VirtualFile vFile = file != null ? file.getVirtualFile() : null;
      final String path = vFile != null ? vFile.getPath() : "null";
      result.append(path).append('\n');
    }
    return result.toString();
  }
}
