package org.jetbrains.android;

import com.android.SdkConstants;
import com.android.tools.lint.checks.CommentDetector;
import com.android.tools.lint.checks.TextViewDetector;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.inspections.lint.AndroidAddStringResourceQuickFix;
import org.jetbrains.android.inspections.lint.AndroidLintExternalAnnotator;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionToolProvider;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API;

public class AndroidLintTest extends AndroidTestCase {
  @NonNls private static final String BASE_PATH = "/lint/";
  @NonNls private static final String BASE_PATH_GLOBAL = BASE_PATH + "global/";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    AndroidLintInspectionBase.invalidateInspectionShortName2IssueMap();
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    if ("testImlFileOutsideContentRoot".equals(getName())) {
      addModuleWithAndroidFacet(projectBuilder, modules, "module1", true);
      addModuleWithAndroidFacet(projectBuilder, modules, "module2", true);
    } else if ("testAppCompatMethod".equals(getName())) {
      addModuleWithAndroidFacet(projectBuilder, modules, "appcompat", false);
    }
  }

  public void testHardcodedQuickfix() throws Exception {
    doTestHardcodedQuickfix();
  }

  public void testHardcodedQuickfix1() throws Exception {
    doTestHardcodedQuickfix();
  }

  public void testHardcodedString() throws Exception {
    doTestHighlighting(new AndroidLintInspectionToolProvider.AndroidLintHardcodedTextInspection(), "/res/layout/layout.xml", "xml");
  }

  private void doTestHardcodedQuickfix() throws IOException {
    String copyTo = false ? "AndroidManifest.xml" : "/res/layout/layout.xml";
    doTestHighlighting(new AndroidLintInspectionToolProvider.AndroidLintHardcodedTextInspection(), copyTo, "xml");
    final AndroidAddStringResourceQuickFix action =
      AndroidTestUtils
        .getIntentionAction(myFixture, AndroidAddStringResourceQuickFix.class, AndroidBundle.message("add.string.resource.intention.text"));
    assertNotNull(action);
    assertTrue(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));

    new WriteCommandAction(myFixture.getProject(), "") {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        ((AndroidAddStringResourceQuickFix)action)
          .invokeIntention(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), "hello");
      }
    }.execute();

    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after.xml");
  }

  public void testContentDescription() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintContentDescriptionInspection(),
                  AndroidBundle.message("android.lint.fix.add.content.description"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testContentDescription1() throws Exception {
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintContentDescriptionInspection(),
                "/res/layout/layout.xml", "xml");
  }

  public void testAdapterViewChildren() throws Exception {
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintAdapterViewChildrenInspection(),
                "/res/layout/layout.xml", "xml");
  }

  public void testScrollViewChildren() throws Exception {
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintScrollViewCountInspection(),
                "/res/layout/layout.xml", "xml");
  }

  public void testMissingPrefix() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintMissingPrefixInspection(),
                  AndroidBundle.message("android.lint.fix.add.android.prefix"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testMissingPrefix1() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintMissingPrefixInspection(),
                  AndroidBundle.message("android.lint.fix.add.android.prefix"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testMissingPrefix2() throws Exception {
    // lint.xml which disables the missing prefix
    myFixture.copyFileToProject(getGlobalTestDir() + "/lint.xml", "lint.xml");
    doTestHighlighting(new AndroidLintInspectionToolProvider.AndroidLintMissingPrefixInspection(), "/res/layout/layout.xml", "xml");
  }

  public void testMissingPrefix3() throws Exception {
    // lint.xml which changes the severity to warning (is normally error)
    myFixture.copyFileToProject(getGlobalTestDir() + "/lint.xml", "lint.xml");
    doTestHighlighting(new AndroidLintInspectionToolProvider.AndroidLintMissingPrefixInspection(), "/res/layout/layout.xml", "xml");
  }

  public void testMissingPrefix4() throws Exception {
    // lint.xml which suppresses this specific error path
    myFixture.copyFileToProject(getGlobalTestDir() + "/lint.xml", "lint.xml");
    doTestHighlighting(new AndroidLintInspectionToolProvider.AndroidLintMissingPrefixInspection(), "/res/layout/layout.xml", "xml");
  }

  public void testDuplicatedIds() throws Exception {
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintDuplicateIdsInspection(),
                "/res/layout/layout.xml", "xml");
  }

  public void testParcelCreator() throws Exception {
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintParcelCreatorInspection(),
                "/src/test/pkg/ParcelableDemo.java", "java");
  }

  public void testAuthString() throws Exception {
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintAuthLeakInspection(),
                "/src/test/pkg/AuthDemo.java", "java");
  }

  public void testInefficientWeight() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintInefficientWeightInspection(),
                  AndroidBundle.message("android.lint.fix.replace.with.zero.dp"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testBaselineWeights() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintDisableBaselineAlignmentInspection(),
                  AndroidBundle.message("android.lint.fix.set.baseline.attribute"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testObsoleteLayoutParams() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintObsoleteLayoutParamInspection(),
                  AndroidBundle.message("android.lint.fix.remove.attribute"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testConvertToDp() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintPxUsageInspection(),
                  AndroidBundle.message("android.lint.fix.convert.to.dp"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testConvertToDp1() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintPxUsageInspection(),
                  AndroidBundle.message("android.lint.fix.convert.to.dp"),
                  "/res/values/convertToDp.xml", "xml");
  }

  public void testScrollViewSize() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintScrollViewSizeInspection(),
                  AndroidBundle.message("android.lint.fix.set.to.wrap.content"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testUnusedAttribute() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintUnusedAttributeInspection(),
                  "Suppress With tools:targetApi Attribute",
                  "/res/layout/layout.xml", "xml");
  }

  public void testExportedService() throws Exception {
    deleteManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintExportedServiceInspection(),
                  AndroidBundle.message("android.lint.fix.add.permission.attribute"),
                  "AndroidManifest.xml", "xml");
  }

  public void testExportedContentProvider() throws Exception {
    deleteManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintExportedContentProviderInspection(),
                  "Set exported=\"false\"", "AndroidManifest.xml", "xml");
  }

  public void testExportedReceiver() throws Exception {
    deleteManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintExportedReceiverInspection(),
                  "Set permission attribute", "AndroidManifest.xml", "xml");
  }

  public void testEditText() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintTextFieldsInspection(),
                  AndroidBundle.message("android.lint.fix.add.input.type.attribute"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testUselessLeaf() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintUselessLeafInspection(),
                  AndroidBundle.message("android.lint.fix.remove.unnecessary.view"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testUselessParent() throws Exception {
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintUselessParentInspection(),
                "/res/layout/layout.xml", "xml");
  }

  public void testTypographyDashes() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintTypographyDashesInspection(),
                  AndroidBundle.message("android.lint.fix.replace.with.suggested.characters"),
                  "/res/values/typography.xml", "xml");
  }

  public void testTypographyQuotes() throws Exception {
    // Re-enable typography quotes, normally off
    myFixture.copyFileToProject(getGlobalTestDir() + "/lint.xml", "lint.xml");
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintTypographyQuotesInspection(),
                  AndroidBundle.message("android.lint.fix.replace.with.suggested.characters"),
                  "/res/values/typography.xml", "xml");
  }

  public void testGridLayoutAttribute() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintGridLayoutInspection(),
                  "Update to myns:layout_column",
                  "/res/layout/grid_layout.xml", "xml");
  }

  public void testGridLayoutAttributeMissing() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintGridLayoutInspection(),
                  "Update to app:layout_column",
                  "/res/layout/grid_layout.xml", "xml");
  }

  public void testAlwaysShowAction() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintAlwaysShowActionInspection(),
                  "Replace with ifRoom", "/res/menu/menu.xml", "xml");
  }

  public void testPaddingStartQuickFix() throws Exception {
    deleteManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/AndroidManifest.xml", "AndroidManifest.xml");
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintRtlCompatInspection(),
                  "Set paddingLeft", "/res/layout/layout.xml", "xml");
  }

  public void testAppCompatMethod() throws Exception {
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    for (Module module : modules) {
      if (module != myModule && AndroidFacet.getInstance(module) != null) {
        deleteManifest(module);
      }
    }
    myFixture.copyFileToProject(getGlobalTestDir() + "/AndroidManifest.xml", "additionalModules/appcompat/AndroidManifest.xml");
    myFixture.copyFileToProject(getGlobalTestDir() + "/ActionBarActivity.java.txt", "src/android/support/v7/app/ActionBarActivity.java");
    myFixture.copyFileToProject(getGlobalTestDir() + "/ActionMode.java.txt", "src/android/support/v7/view/ActionMode.java");
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintAppCompatMethodInspection(),
                  "Replace with getSupportActionBar()", "/src/test/pkg/AppCompatTest.java", "java");
  }

  public void testUseValueOf() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintUseValueOfInspection(),
                  "Replace with valueOf()", "/src/test/pkg/UseValueOf.java", "java");
  }

  public void testEditEncoding() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintEnforceUTF8Inspection(),
                  "Replace with utf-8", "/res/layout/layout.xml", "xml");
  }

  /* Inspection disabled; these tests make network connection to MavenCentral and can change every time there
     is a new version available (which makes for unstable tests)

  public void testNewerAvailable() throws Exception {
    GradleDetector.REMOTE_VERSION.setEnabledByDefault(true);
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintNewerVersionAvailableInspection(),
                  "Update to 17.0.0", "build.gradle", "gradle");
  }
  */

  public void testGradlePlus() throws Exception {
    // Needs a valid SDK; can't use the mock one in the test data.
    AndroidSdkData prevSdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (prevSdkData == null) {
      String recentSdkPath = AndroidTestBase.getRecentSdkPath();
      String platformDir = AndroidTestBase.getRecentPlatformDir();
      if (recentSdkPath == null || platformDir == null) {
        System.out.println("Not running " + this.getClass() + "#" + getName() + ": Needs SDK with Support Repo installed");
        return;
      }
      Sdk androidSdk = createAndroidSdk(recentSdkPath, platformDir);
      AndroidPlatform androidPlatform = AndroidPlatform.getInstance(androidSdk);
      assertNotNull(androidPlatform);
      // Put default platforms in the list before non-default ones so they'll be looked at first.
      AndroidSdkUtils.setSdkData(androidPlatform.getSdkData());
    }

    //noinspection ConstantConditions
    File sdk = AndroidSdkUtils.tryToChooseAndroidSdk().getLocation();
    File appcompat = new File(sdk, "extras/android/m2repository/com/android/support/appcompat-v7/19.0.1".replace('/', File.separatorChar));
    if (!appcompat.exists()) {
      System.out.println("Not running " + this.getClass() + "#" + getName() + ": Needs SDK with Support Repo installed and " +
                         "expected to find " + appcompat);
      return;
    }

    // NOTE: The android support repository must be installed in the SDK used by the test!
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintGradleDynamicVersionInspection(),
                  "Replace with specific version", "build.gradle", "gradle");

    AndroidSdkUtils.setSdkData(prevSdkData);
  }

  public void testObsoleteDependency() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintGradleDependencyInspection(),
                  "Change to 18.0", "build.gradle", "gradle");
  }

  public void testObsoleteLongDependency() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintGradleDependencyInspection(),
                  "Change to 18.0", "build.gradle", "gradle");
  }

  public void testGradleDeprecation() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintGradleDeprecatedInspection(),
                  "Replace with com.android.library", "build.gradle", "gradle");
  }

  public void testWrongQuote() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintNotInterpolatedInspection(),
                  "Replace single quotes with double quotes", "build.gradle", "gradle");
  }

  public void testMissingAppIcon() throws Exception {
    deleteManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintMissingApplicationIconInspection(),
                  "Set application icon", "AndroidManifest.xml", "xml");
  }

  public void testMissingLeanbackSupport() throws Exception {
    deleteManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintMissingLeanbackSupportInspection(),
                  "Add uses-feature tag", "AndroidManifest.xml", "xml");
  }

  public void testPermissionImpliesHardware() throws Exception {
    deleteManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintPermissionImpliesUnsupportedHardwareInspection(),
                  "Add uses-feature tag", "AndroidManifest.xml", "xml");
  }

  public void testMissingTvBanner() throws Exception {
    deleteManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintMissingTvBannerInspection(),
                  "Set banner attribute", "AndroidManifest.xml", "xml");
  }

  public void testInvalidUsesTagAttribute() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintInvalidUsesTagAttributeInspection(),
                  "Replace with \"media\"",
                  "res/xml/automotive_app_desc.xml", "xml");
  }

  /* Disabled: The mipmap check now only warns about mipmap usage in Gradle projects that use
   * density filtering. Re-enable this if we broaden the mipmap check, or if we update the AndroidLintTest
   * to also check Gradle projects.
  public void testMipmap() throws Exception {
    deleteManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/R.java", "/src/p1/p2/R.java");
    myFixture.copyFileToProject(getGlobalTestDir() + "/MyCode.java", "/src/p1/p2/MyCode.java");
    myFixture.copyFileToProject(getGlobalTestDir() + "/icon.png", "/res/drawable-mdpi/icon.png");
    myFixture.copyFileToProject(getGlobalTestDir() + "/icon.png", "/res/drawable-hdpi/icon.png");
    myFixture.copyFileToProject(getGlobalTestDir() + "/icon.png", "/res/drawable-xhdpi/icon.png");

    // Apply quickfix and check that the manifest file is updated
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintMipmapIconsInspection(), "Convert @drawable/icon to @mipmap/icon",
                  "AndroidManifest.xml", "xml");

    // Make sure files were moved
    assertNotNull(myFixture.findFileInTempDir("res/mipmap-mdpi/icon.png"));
    assertNotNull(myFixture.findFileInTempDir("res/mipmap-hdpi/icon.png"));
    assertNotNull(myFixture.findFileInTempDir("res/mipmap-xhdpi/icon.png"));

    // Make sure code references (in addition to Manifest XML file reference checked above) have been updated
    myFixture.checkResultByFile("src/p1/p2/MyCode.java", getGlobalTestDir() + "/MyCode_after.java", true);

    // The R.java file should not have been edited:
    myFixture.checkResultByFile("src/p1/p2/R.java", getGlobalTestDir() + "/R.java", true);
  }
  */

  public void testAllowBackup() throws Exception {
    deleteManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintAllowBackupInspection(),
                  "Set backup attribute", "AndroidManifest.xml", "xml");
  }

  public void testRemoveByteOrderMarks() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintByteOrderMarkInspection(),
                  "Remove byte order marks", "/res/layout/layout.xml", "xml");
  }

  public void testCommitToApply() throws Exception {
    deleteManifest();
    // Need to use targetSdkVersion 9
    myFixture.copyFileToProject(getGlobalTestDir() + "/AndroidManifest.xml", "AndroidManifest.xml");
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintCommitPrefEditsInspection(),
                  "Replace commit() with apply()", "/src/test/pkg/CommitToApply.java", "java");
  }

  public void testMissingIntDefSwitch() throws Exception {
    myFixture.addFileToProject("/src/android/support/annotation/IntDef.java", "package android.support.annotation;\n" +
                                                                              "\n" +
                                                                              "import java.lang.annotation.Retention;\n" +
                                                                              "import java.lang.annotation.RetentionPolicy;\n" +
                                                                              "import java.lang.annotation.Target;\n" +
                                                                              "\n" +
                                                                              "import static java.lang.annotation.ElementType.ANNOTATION_TYPE;\n" +
                                                                              "import static java.lang.annotation.ElementType.FIELD;\n" +
                                                                              "import static java.lang.annotation.ElementType.METHOD;\n" +
                                                                              "import static java.lang.annotation.ElementType.PARAMETER;\n" +
                                                                              "import static java.lang.annotation.RetentionPolicy.CLASS;\n" +
                                                                              "import static java.lang.annotation.RetentionPolicy.SOURCE;\n" +
                                                                              "\n" +
                                                                              "@Retention(CLASS)\n" +
                                                                              "@Target({ANNOTATION_TYPE})\n" +
                                                                              "public @interface IntDef {\n" +
                                                                              "    long[] value() default {};\n" +
                                                                              "    boolean flag() default false;\n" +
                                                                              "}\n");
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintSwitchIntDefInspection(),
                  "Add Missing @IntDef Constants", "/src/test/pkg/MissingIntDefSwitch.java", "java");
  }

  public void testIncludeParams() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintIncludeLayoutParamInspection(),
                  "Set layout_height", "/res/layout/layout.xml", "xml");
  }

  public void testInnerclassSeparator() throws Exception {
    deleteManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintInnerclassSeparatorInspection(),
                  "Replace with .MyActivity$Inner", "AndroidManifest.xml", "xml");
  }

  public void testMenuTitle() throws Exception {
    deleteManifest();
    // Need to use targetSdkVersion 11
    myFixture.copyFileToProject(getGlobalTestDir() + "/AndroidManifest.xml", "AndroidManifest.xml");
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintMenuTitleInspection(),
                  "Set title", "/res/menu/menu.xml", "xml");
  }

  public void testFragmentIds() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintMissingIdInspection(),
                  "Set id", "/res/layout/layout.xml", "xml");
  }

  public void testOldTargetApi() throws Exception {
    deleteManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintOldTargetApiInspection(),
                  "Update targetSdkVersion to " + HIGHEST_KNOWN_STABLE_API, "AndroidManifest.xml", "xml");
  }

  /*
  public void testOldTargetApiGradle() throws Exception {
    // Doesn't work in incremental mode because this issue is also used for manifest files;
    // we don't well support implementations pointing to different detectors for each file type
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintOldTargetApiInspection(),
                  "Change to 17.0.0", "build.gradle", "gradle");
  }
  */

  public void testPropertyFiles() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintPropertyEscapeInspection(),
                  "Replace with C\\:\\\\foo\\\\bar", "local.properties", "properties");
  }

  public void testReferenceTypes() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintReferenceTypeInspection(),
                  "Replace with @string/", "/res/values/strings.xml", "xml");
  }

  public void testSelectableText() throws Exception {
    TextViewDetector.SELECTABLE.setEnabledByDefault(true);

    deleteManifest();
    // Need to use targetSdkVersion 11
    myFixture.copyFileToProject(getGlobalTestDir() + "/AndroidManifest.xml", "AndroidManifest.xml");
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintSelectableTextInspection(), "Set android:textIsSelectable=true",
                  "/res/layout/layout.xml", "xml");
  }

  public void testSignatureOrSystem() throws Exception {
    deleteManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintSignatureOrSystemPermissionsInspection(),
                  "Replace with signature", "AndroidManifest.xml", "xml");
  }

  public void testSp() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintSpUsageInspection(),
                  "Replace with sp", "/res/values/styles.xml", "xml");
  }

  public void testStopShip() throws Exception {
    CommentDetector.STOP_SHIP.setEnabledByDefault(true);
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintStopShipInspection(), "Remove STOPSHIP", "/src/test/pkg/StopShip.java",
                  "java");
  }

  public void testStringToInt() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintStringShouldBeIntInspection(),
                  "Replace with integer", "build.gradle", "gradle");
  }

  public void testStringTypos() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintTyposInspection(),
                  "Replace with \"the\"", "/res/values-no/strings.xml", "xml");
  }

  // Regression test for http://b.android.com/186465
  public void testStringTyposCDATA() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintTyposInspection(),
                  "Replace with \"the\"", "/res/values-no/strings.xml", "xml");
  }

  public void testWrongViewCall() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintWrongCallInspection(),
                  "Replace call with draw()", "/src/test/pkg/WrongViewCall.java", "java");
  }

  public void testWrongCase() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintWrongCaseInspection(),
                  "Replace with merge", "/res/layout/layout.xml", "xml");
  }

  public void testProguard() throws Exception {
    createManifest();
    final VirtualFile proguardCfgPath = myFixture.copyFileToProject(getGlobalTestDir() + "/proguard.cfg", "proguard.cfg");
    myFacet.getProperties().RUN_PROGUARD = true;
    myFacet.getProperties().myProGuardCfgFiles = Collections.singletonList(proguardCfgPath.getUrl());

    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintProguardInspection());
  }

  public void testManifestOrder() throws Exception {
    deleteManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/AndroidManifest.xml", "AndroidManifest.xml");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintManifestOrderInspection());
  }

  public void testButtonsOrder() throws Exception {
    deleteManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/AndroidManifest.xml", "AndroidManifest.xml");
    myFixture.copyFileToProject(getGlobalTestDir() + "/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(getGlobalTestDir() + "/layout.xml", "res/layout/layout.xml");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintButtonOrderInspection());
  }

  public void testViewType() throws Exception {
    myFixture.copyFileToProject(getGlobalTestDir() + "/MyActivity.java", "src/p1/p2/MyActivity.java");
    myFixture.copyFileToProject(getGlobalTestDir() + "/layout.xml", "res/layout/layout.xml");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintWrongViewCastInspection());
  }

  public void testViewTypeStub() throws Exception {
    // Regression test for 183136: don't take id references to imply a
    // view type of the referencing type
    myFixture.copyFileToProject(getGlobalTestDir() + "/stub_inflated_layout.xml", "res/layout/stub_inflated_layout.xml");
    myFixture.copyFileToProject(getGlobalTestDir() + "/main.xml", "res/layout/main.xml");
    myFixture.copyFileToProject(getGlobalTestDir() + "/WrongCastActivity.java", "src/p1/p2/WrongCastActivity.java");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintWrongViewCastInspection());
  }

  public void testDuplicateIcons() throws Exception {
    myFixture.copyFileToProject(getGlobalTestDir() + "/dup1.png", "res/drawable/dup1.png");
    myFixture.copyFileToProject(getGlobalTestDir() + "/dup2.png", "res/drawable/dup2.png");
    myFixture.copyFileToProject(getGlobalTestDir() + "/other.png", "res/drawable/other.png");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintIconDuplicatesInspection());
  }

  public void testCallSuper() throws Exception {
    myFixture.copyFileToProject(getGlobalTestDir() + "/CallSuperTest.java", "src/p1/p2/CallSuperTest.java");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintMissingSuperCallInspection());
  }

  public void testSuppressingInXml1() throws Exception {
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintHardcodedTextInspection(),
                "/res/layout/layout.xml", "xml");
  }

  public void testSuppressingInXml2() throws Exception {
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintHardcodedTextInspection(),
                "/res/layout/layout.xml", "xml");
  }

  public void testSuppressingInXml3() throws Exception {
    createManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/layout.xml", "res/layout/layout.xml");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintHardcodedTextInspection());
  }

  public void testSuppressingInJava() throws Exception {
    createManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/MyActivity.java", "src/p1/p2/MyActivity.java");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintUseValueOfInspection());
  }

  public void testLintInJavaFile() throws Exception {
    createManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/MyActivity.java", "src/p1/p2/MyActivity.java");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintUseValueOfInspection());
  }

  public void testApiCheck1() throws Exception {
    createManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/MyActivity.java", "src/p1/p2/MyActivity.java");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintNewApiInspection());
  }

  public void testApiCheck1b() throws Exception {
    // Check adding a @TargetApi annotation in a Java file to suppress
    createManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintNewApiInspection(),
                  "Add @TargetApi(HONEYCOMB) Annotation",
                  "/src/p1/p2/MyActivity.java", "java");
  }

  public void testApiCheck1c() throws Exception {
    // Check adding a @SuppressLint annotation in a Java file to suppress
    createManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintNewApiInspection(),
                  "Suppress: Add @SuppressLint(\"NewApi\") annotation",
                  "/src/p1/p2/MyActivity.java", "java");
  }

  public void testApiCheck1d() throws Exception {
    // Check adding a tools:targetApi attribute in an XML file to suppress
    createManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintNewApiInspection(),
                  "Suppress With tools:targetApi Attribute",
                  "/res/layout/layout.xml", "xml");
  }

  public void testApiCheck1e() throws Exception {
    // Check adding a tools:suppress attribute in an XML file to suppress
    createManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintNewApiInspection(),
                  "Suppress: Add tools:ignore=\"NewApi\" attribute",
                  "/res/layout/layout.xml", "xml");
  }

  public void testApiCheck1f() throws Exception {
    // Check adding a version-check conditional in a Java file
    createManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintNewApiInspection(),
                  "Surround with if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) { ... }",
                  "/src/p1/p2/MyActivity.java", "java");
  }

  public void testImlFileOutsideContentRoot() throws Exception {
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, "additionalModules/module1/" + SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, "additionalModules/module2/" + SdkConstants.FN_ANDROID_MANIFEST_XML);
    final String testDir = BASE_PATH_GLOBAL + "apiCheck1";
    myFixture.copyFileToProject(testDir + "/MyActivity.java", "additionalModules/module1/src/p1/p2/MyActivity.java");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintNewApiInspection(), testDir, new AnalysisScope(getProject()));
  }

  public void testApiInlined() throws Exception {
    createManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/MyActivity.java", "src/p1/p2/MyActivity.java");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintInlinedApiInspection());
  }

  public void testApiOverride() throws Exception {
    createManifest();
    createProjectProperties();

    // We need a build target >= 1 but also *smaller* than 17. Ensure this is the case
    AndroidPlatform platform = AndroidPlatform.getInstance(myFacet.getModule());
    if (platform != null && platform.getApiLevel() < 17) {
      myFixture.copyFileToProject(getGlobalTestDir() + "/MyActivity.java", "src/p1/p2/MyActivity.java");
      doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintOverrideInspection());
    } else {
      // TODO: else try to find and set a target on the project such that the above returns true
    }
  }

  public void testParcelLoader() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintParcelClassLoaderInspection(),
                  "Use getClass().getClassLoader()",
                  "/src/test/pkg/ParcelClassLoaderTest.java", "java");
  }

  public void testParcelLoader2() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintParcelClassLoaderInspection(),
                  "Use getClass().getClassLoader()",
                  "/src/test/pkg/ParcelClassLoaderTest.java", "java");
  }

  public void testDeprecation() throws Exception {
    // Need to use minSdkVersion >= 3 to get all the deprecation warnings to kick in
    deleteManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/AndroidManifest.xml", "AndroidManifest.xml");
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintDeprecatedInspection(),
                "/res/layout/deprecation.xml", "xml");
  }

  /**
   * Quick fix is available on singleLine="true" and does the right thing
   */
  public void testSingleLine() throws Exception {
    deleteManifest();
    myFixture.copyFileToProject(BASE_PATH_GLOBAL + "deprecation/AndroidManifest.xml", "AndroidManifest.xml");
    myFixture.enableInspections(new AndroidLintInspectionToolProvider.AndroidLintDeprecatedInspection());
    myFixture.configureFromExistingVirtualFile(
      myFixture.copyFileToProject(BASE_PATH + "singleLine.xml", "res/layout/singleLine.xml"));
    final IntentionAction action = AndroidTestUtils.getIntentionAction(myFixture, "Replace singleLine=\"true\" with maxLines=\"1\"");
    assertNotNull(action);
    doTestWithAction("xml", action);
  }

  /**
   * Specialized quick fix is not available on singleLine="false"
   */
  public void testSingleLineFalse() throws Exception {
    deleteManifest();
    myFixture.copyFileToProject(BASE_PATH_GLOBAL + "deprecation/AndroidManifest.xml", "AndroidManifest.xml");
    myFixture.enableInspections(new AndroidLintInspectionToolProvider.AndroidLintDeprecatedInspection());
    myFixture.configureFromExistingVirtualFile(
      myFixture.copyFileToProject(BASE_PATH + "singleLineFalse.xml", "res/layout/singleLineFalse.xml"));
    final IntentionAction action = AndroidTestUtils.getIntentionAction(myFixture, "Replace singleLine=\"true\" with maxLines=\"1\"");
    assertNull(action);
  }

  public void testUnprotectedSmsBroadcastReceiver() throws Exception {
    deleteManifest();
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintUnprotectedSMSBroadcastReceiverInspection(),
                  "Set permission attribute", "AndroidManifest.xml", "xml");
  }

  public void testActivityRegistered() throws Exception {
    createManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/MyActivity.java", "src/p1/p2/MyActivity.java");
    myFixture.copyFileToProject(getGlobalTestDir() + "/MyDerived.java", "src/p1/p2/MyDerived.java");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintRegisteredInspection());
  }

  private void doGlobalInspectionTest(@NotNull AndroidLintInspectionBase inspection) {
    doGlobalInspectionTest(inspection, getGlobalTestDir(), new AnalysisScope(myModule));
  }

  private String getGlobalTestDir() {
    return BASE_PATH_GLOBAL + getTestName(true);
  }

  private void doTestNoFix(@NotNull AndroidLintInspectionBase inspection, @NotNull String copyTo, @NotNull String extension)
    throws IOException {
    doTestHighlighting(inspection, copyTo, extension);

    IntentionAction action = null;

    for (IntentionAction a : myFixture.getAvailableIntentions()) {
      if (a instanceof AndroidLintExternalAnnotator.MyFixingIntention) {
        action = a;
      }
    }
    assertNull(action);
  }

  private void doTestWithFix(@NotNull AndroidLintInspectionBase inspection,
                             @NotNull String message,
                             @NotNull String copyTo,
                             @NotNull String extension)
    throws IOException {
    final IntentionAction action = doTestHighlightingAndGetQuickfix(inspection, message, copyTo, extension);
    assertNotNull(action);
    doTestWithAction(extension, action);
  }

  private void doTestWithAction(@NotNull String extension, @NotNull final IntentionAction action) {
    assertTrue(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));

    new WriteCommandAction(myFixture.getProject(), "") {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        action.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      }
    }.execute();

    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after." + extension);
  }

  @Nullable
  private IntentionAction doTestHighlightingAndGetQuickfix(@NotNull AndroidLintInspectionBase inspection,
                                                           @NotNull String message,
                                                           @NotNull String copyTo,
                                                           @NotNull String extension) throws IOException {
    doTestHighlighting(inspection, copyTo, extension);
    return AndroidTestUtils.getIntentionAction(myFixture, message);
  }

  private void doTestHighlighting(@NotNull AndroidLintInspectionBase inspection, @NotNull String copyTo, @NotNull String extension)
    throws IOException {
    myFixture.enableInspections(inspection);
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + "." + extension, copyTo);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.doHighlighting();
    myFixture.checkHighlighting(true, false, false);
  }
}
