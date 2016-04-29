package com.android.tools.idea.gradle.eclipse;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.PropertiesUtil;
import com.android.tools.idea.templates.TemplateManager;
import com.android.utils.Pair;
import com.android.utils.SdkUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import org.jetbrains.android.AndroidTestCase;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.eclipse.GradleImport.*;
import static com.android.tools.idea.gradle.eclipse.ImportSummary.*;
import static com.google.common.base.Charsets.UTF_8;
import static java.io.File.separator;
import static java.io.File.separatorChar;

/**
 * Unit tests for the Gradle importer.
 * <p>
 * TODO:
 * -  Test emitting proguard rules in android-library (check build.gradle declaration)
 * -  Test compile options
 * -  Skip instrumentation stuff if same as default?
 * -  Test having more than one SDK proguard list?
 * -  Escaped groovy paths
 * -  Path variable in .classpath
 * -  Case where a lib is both in libs/ and in .classpath (cull and only include once)
 * -  Filename clashes (collapsing into libs/, etc.)
 * -  Finding a lib that is using a workspace path which isn't found but is a direct child
 * -  Instrumentation libs/ .jar which gets replaced by a gradle dependency
 */
@SuppressWarnings("ConcatenationWithEmptyString")
public class GradleImportTest extends AndroidTestCase { // Only because we need VFS to locate the template folder for the gradle wrapper
  private static File createProject(String name, String pkg) throws IOException {
    File dir = Files.createTempDir();
    return createProject(dir, name, pkg);
  }

  private static File createProject(File dir, String name, String pkg) throws IOException {
    createDotProject(dir, name, true);
    File src = new File("src");
    File gen = new File("gen");

    createSampleJavaSource(dir, "src", pkg, "MyActivity");
    createSampleJavaSource(dir, "gen", pkg, "R");

    createClassPath(dir,
                    new File("bin", "classes"),
                    Arrays.asList(src, gen),
                    Collections.<File>emptyList());
    createProjectProperties(dir, "android-22", null, null, null,
                            Collections.<File>emptyList());
    createAndroidManifest(dir, pkg, 8, 22, null);

    createDefaultStrings(dir);
    createDefaultIcon(dir);

    return dir;
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testResolveExpressions() throws Exception {
    File root = Files.createTempDir();
    File projectDir = new File(root, "dir1" + separator + "dir2" + separator + "dir3" +
                                     separator + "dir4" + separator + "prj");
    projectDir.mkdirs();
    createProject(projectDir, "test1", "test.pkg");
    File var1 = new File(root, "sub1" + separator + "sub2" + separator + "sub3");
    var1.mkdirs();
    File var4 = new File(projectDir.getParentFile(), "var4");
    var4.mkdirs();
    File tpl = new File(projectDir.getParentFile().getParentFile().getParentFile(), "TARGET" +
                                                                                    separator + "android" + separator + "third-party");
    tpl.mkdirs();
    File supportLib = new File(tpl, "android-support-v4.r19.jar");
    supportLib.createNewFile();

    Files.write(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<projectDescription>\n"
                + "\t<name>UnitTest</name>\n"
                + "\t<comment></comment>\n"
                + "\t<projects>\n"
                + "\t</projects>\n"
                + "\t<buildSpec>\n"
                + "\t\t<buildCommand>\n"
                + "\t\t\t<name>org.eclipse.jdt.core.javabuilder</name>\n"
                + "\t\t\t<arguments>\n"
                + "\t\t\t</arguments>\n"
                + "\t\t</buildCommand>\n"
                + "\t</buildSpec>\n"
                + "\t<natures>\n"
                + "\t\t<nature>org.eclipse.jdt.core.javanature</nature>\n"
                + "\t</natures>\n"
                + "\t<linkedResources>\n"
                + "\t\t<link>\n"
                + "\t\t\t<name>MYLIBS</name>\n"
                + "\t\t\t<type>2</type>\n"
                + "\t\t\t<locationURI>MYLIBS</locationURI>\n"
                + "\t\t</link>\n"
                + "\t\t<link>\n"
                + "\t\t\t<name>3rd_java_libs</name>\n"
                + "\t\t\t<type>2</type>\n"
                + "\t\t\t<locationURI>PARENT-3-PROJECT_LOC/TARGET/android/third-party</locationURI>\n"
                + "\t\t</link>\n"
                + "\t\t<link>\n"
                + "\t\t\t<name>jnilibs</name>\n"
                + "\t\t\t<type>2</type>\n"
                + "\t\t\t<locationURI>virtual:/virtual</locationURI>\n"
                + "\t\t</link>\n"
                + "\t</linkedResources>\n"
                + "\t<variableList>\n"
                + "\t\t<variable>\n"
                + "\t\t\t<name>MY_VAR_1</name>\n"
                + "\t\t\t<value>" + SdkUtils.fileToUrl(var1) + "</value>\n"
                + "\t\t</variable>\n"
                + "\t\t<variable>\n"
                + "\t\t\t<name>MY_VAR_2</name>\n"
                + "\t\t\t<value>$%7BMY_VAR_1%7D</value>\n"
                + "\t\t</variable>\n"
                + "\t\t<variable>\n"
                + "\t\t\t<name>MY_VAR_3</name>\n"
                + "\t\t\t<value>$%7BPROJECT_LOC%7D/src</value>\n"
                + "\t\t</variable>\n"
                + "\t\t<variable>\n"
                + "\t\t\t<name>MY_VAR_4</name>\n"
                + "\t\t\t<value>$%7BPARENT-1-PROJECT_LOC%7D/var4</value>\n"
                + "\t\t</variable>\n"
                + "\t\t<variable>\n"
                + "\t\t\t<name>MY_VAR_5</name>\n"
                + "\t\t\t<value>$%7BPARENT_LOC%7D/var4</value>\n"
                + "\t\t</variable>\n"
                + "\t</variableList>\n"
                + "</projectDescription>",
                new File(projectDir, ".project"), UTF_8);

    GradleImport importer = new GradleImport();
    EclipseProject project = EclipseProject.getProject(importer, projectDir);
    importer.getPathMap();

    // Test absolute paths
    assertEquals(var1, project.resolveVariableExpression(var1.getPath()));
    assertEquals(var1, project.resolveVariableExpression(var1.getAbsolutePath()));
    assertEquals(var1.getCanonicalFile(),
                 project.resolveVariableExpression(var1.getCanonicalPath()));
    assertEquals(var1, project.resolveVariableExpression(var1.getPath().replace('/',
                                                                                separatorChar))); // on Windows, make sure we handle workspace files with forwards


    // Test project relative paths
    String relative = "src" + separator + "test" + separator + "pkg" + separator
                      + "MyActivity.java";
    assertEquals(new File(projectDir, relative), project.resolveVariableExpression(relative));
    assertEquals(new File(projectDir, relative), project.resolveVariableExpression(
      relative.replace('/', separatorChar)));

    // Test workspace paths
    // This is handled by testLibraries2

    // Test path variables
    assertEquals(var1, project.resolveVariableExpression("MY_VAR_1"));
    assertEquals(var1, project.resolveVariableExpression("MY_VAR_2"));
    assertEquals(new File(projectDir, "src"), project.resolveVariableExpression("MY_VAR_3"));
    assertEquals(var4, project.resolveVariableExpression("MY_VAR_4"));
    assertEquals(var4, project.resolveVariableExpression("MY_VAR_5"));

    // Test linked variables
    assertEquals(supportLib, project.resolveVariableExpression(
      "3rd_java_libs/android-support-v4.r19.jar"));

    // Test user-supplied values
    assertEquals(var1, project.resolveVariableExpression("MY_VAR_1"));
    importer.getPathMap().put("MY_VAR_1", projectDir);
    assertEquals(projectDir, project.resolveVariableExpression("MY_VAR_1"));
    importer.getPathMap().put("/some/unresolved/path", var4);
    assertEquals(var4, project.resolveVariableExpression("/some/unresolved/path"));

    // Setup for workspace tests

    assertNull(project.resolveVariableExpression("MY_GLOBAL_VAR"));
    final File workspace = new File(root, "workspace");
    workspace.mkdirs();
    File prefs = new File(workspace, ".metadata" + separator +
                                     ".plugins" + separator +
                                     "org.eclipse.core.runtime" + separator +
                                     ".settings" + separator +
                                     "org.eclipse.jdt.core.prefs");
    prefs.getParentFile().mkdirs();
    File global1 = var1.getParentFile();
    Files.write(""
                + "eclipse.preferences.version=1\n"
                + "org.eclipse.jdt.core.classpathVariable.MY_GLOBAL_VAR="
                + global1.getPath().replace(separatorChar,'/').replace(":","\\:") + "\n"
                + "org.eclipse.jdt.core.codeComplete.visibilityCheck=enabled\n"
                + "org.eclipse.jdt.core.compiler.codegen.inlineJsrBytecode=enabled\n"
                + "org.eclipse.jdt.core.compiler.codegen.targetPlatform=1.6\n"
                + "org.eclipse.jdt.core.compiler.compliance=1.6\n"
                + "org.eclipse.jdt.core.compiler.problem.assertIdentifier=error\n"
                + "org.eclipse.jdt.core.compiler.problem.enumIdentifier=error\n"
                + "org.eclipse.jdt.core.compiler.source=1.6\n"
                + "org.eclipse.jdt.core.formatter.tabulation.char=space", prefs, UTF_8);
    File global2 = var4.getParentFile();
    prefs = new File(workspace, ".metadata" + separator +
                                ".plugins" + separator +
                                "org.eclipse.core.runtime" + separator +
                                ".settings" + separator +
                                "org.eclipse.core.resources.prefs");
    prefs.getParentFile().mkdirs();
    Files.write(""
                + "eclipse.preferences.version=1\n"
                + "pathvariable.MY_GLOBAL_VAR_2="
                + global2.getPath().replace(separatorChar,'/').replace(":", "\\:") + "\n"
                + "version=1", prefs, UTF_8);

    importer.setEclipseWorkspace(workspace);

    // Test global path variables

    assertEquals(global1, project.resolveVariableExpression("MY_GLOBAL_VAR"));
    assertEquals(var1, project.resolveVariableExpression("MY_GLOBAL_VAR/sub3"));
    assertEquals(var1, project.resolveVariableExpression("MY_GLOBAL_VAR" + separator + "sub3"));

    // Test workspace linked resources
    assertEquals(global2, project.resolveVariableExpression("MY_GLOBAL_VAR_2"));

    deleteDir(projectDir);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testBasic() throws Exception {
    File projectDir = createProject("test1", "test.pkg");

    // Add some files in there that we are ignoring
    new File(projectDir, "ic_launcher-web.png").createNewFile();
    new File(projectDir, "Android.mk").createNewFile();
    new File(projectDir, "build.properties").createNewFile();
    new File(projectDir, "local.properties").createNewFile();
    new File(projectDir, "src" + separator + ".git").mkdir();
    new File(projectDir, "src" + separator + ".svn").mkdir();
    File unhandled = new File(projectDir, "unhandledDir1" + separator + "unhandledDir2");
    unhandled.mkdirs();
    new File(unhandled, "unhandledFile").createNewFile();
    new File(unhandled, "unhandledDir3").mkdirs();
    new File(unhandled.getParentFile(), "unhandledDir4").mkdirs();
    new File(projectDir, "lint.xml").createNewFile();

    // Make sure we handle less common file extensions: see issue 78459
    createIcon(projectDir, "other_icon.PNG");
    createLayout(projectDir, "other_layout.XML");

    // Project being imported
    assertEquals(""
                 + ".classpath\n"
                 + ".project\n"
                 + "Android.mk\n"
                 + "AndroidManifest.xml\n"
                 + "build.properties\n"
                 + "gen\n"
                 + "  test\n"
                 + "    pkg\n"
                 + "      R.java\n"
                 + "ic_launcher-web.png\n"
                 + "lint.xml\n"
                 + "local.properties\n"
                 + "project.properties\n"
                 + "res\n"
                 + "  drawable\n"
                 + "    ic_launcher.xml\n"
                 + "  drawable-hdpi\n"
                 + "    other_icon.PNG\n"
                 + "  layout\n"
                 + "    other_layout.XML\n"
                 + "  values\n"
                 + "    strings.xml\n"
                 + "src\n"
                 + "  .git\n"
                 + "  .svn\n"
                 + "  test\n"
                 + "    pkg\n"
                 + "      MyActivity.java\n"
                 + "unhandledDir1\n"
                 + "  unhandledDir2\n"
                 + "    unhandledDir3\n"
                 + "    unhandledFile\n"
                 + "  unhandledDir4\n",
                 fileTree(projectDir, true));

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_UNHANDLED
                                 + "* Android.mk\n"
                                 + "* build.properties\n"
                                 + "* ic_launcher-web.png\n"
                                 + "* unhandledDir1/\n"
                                 + "* unhandledDir1/unhandledDir2/\n"
                                 + "* unhandledDir1/unhandledDir2/unhandledFile\n"
                                 + MSG_FOLDER_STRUCTURE
                                 + "* AndroidManifest.xml => app/src/main/AndroidManifest.xml\n"
                                 + "* lint.xml => app/lint.xml\n"
                                 + "* res/ => app/src/main/res/\n"
                                 + "* src/ => app/src/main/java/\n"
                                 + "* other_icon.PNG => other_icon.png\n"
                                 + "* other_layout.XML => other_layout.xml\n"
                                 + MSG_FOOTER,
                                 true /* checkBuild */);

    // Imported contents
    assertEquals(""
                 + "app\n"
                 + "  build.gradle\n"
                 + "  lint.xml\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          pkg\n"
                 + "            MyActivity.java\n"
                 + "      res\n"
                 + "        drawable\n"
                 + "          ic_launcher.xml\n"
                 + "        drawable-hdpi\n"
                 + "          other_icon.png\n"
                 + "        layout\n"
                 + "          other_layout.xml\n"
                 + "        values\n"
                 + "          strings.xml\n"
                 + "build.gradle\n"
                 + "import-summary.txt\n"
                 + (getTestSdkPathLocal() != null ? "local.properties\n" : "")
                 + "settings.gradle\n",
                 fileTree(imported, true));

    deleteDir(projectDir);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testImportWithoutMinSdkVersion() throws Exception {
    // Regression test for importing project which does not explicitly set minSdkVersion
    // and/or targetSdkVersion; this would earlier result in "-1" being written into
    // build.gradle which fails the build with "> Cannot invoke method exclude() on null object"
    File projectDir = createProject("test1", "test.pkg");

    // Remove <uses-sdk ...>
    File manifestFile = new File(projectDir, FN_ANDROID_MANIFEST_XML);
    String manifestContents = Files.toString(manifestFile,  UTF_8);
    int index = manifestContents.indexOf("<uses-sdk");
    int endIndex = manifestContents.indexOf('>', index);
    assertFalse(index == -1);
    assertFalse(endIndex == -1);
    manifestContents = manifestContents.substring(0, index) +
                       manifestContents.substring(endIndex + 1);
    Files.write(manifestContents, manifestFile, UTF_8);

    File imported = checkProject(projectDir, ""
                                             + MSG_HEADER
                                             + MSG_FOLDER_STRUCTURE
                                             + "* AndroidManifest.xml => app/src/main/AndroidManifest.xml\n"
                                             + "* res/ => app/src/main/res/\n"
                                             + "* src/ => app/src/main/java/\n"
                                             + MSG_FOOTER,
                                 true /* checkBuild */);
    deleteDir(projectDir);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testMoveRsResourcesAndAidl() throws Exception {
    File projectDir = createProject("test1", "test.pkg");
    createSampleAidlFile(projectDir, "src", "test.pkg");
    createSampleTimeZoneData(projectDir, "src");
    createSampleRsFile(projectDir, "src", "test.pkg");

    // Project being imported
    assertEquals(""
                 + ".classpath\n"
                 + ".project\n"
                 + "AndroidManifest.xml\n"
                 + "gen\n"
                 + "  test\n"
                 + "    pkg\n"
                 + "      R.java\n"
                 + "project.properties\n"
                 + "res\n"
                 + "  drawable\n"
                 + "    ic_launcher.xml\n"
                 + "  values\n"
                 + "    strings.xml\n"
                 + "src\n"
                 + "  test\n"
                 + "    pkg\n"
                 + "      IHardwareService.aidl\n"
                 + "      MyActivity.java\n"
                 + "      latency.rs\n"
                 + "  zoneinfo-global\n"
                 + "    Pacific\n"
                 + "      Honolulu.ics\n",
                 fileTree(projectDir, true));

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_FOLDER_STRUCTURE
                                 + DEFAULT_MOVED
                                 + "* src/test/pkg/IHardwareService.aidl => app/src/main/aidl/test/pkg/IHardwareService.aidl\n"
                                 + "* src/test/pkg/latency.rs => app/src/main/rs/latency.rs\n"
                                 + "* src/zoneinfo-global/Pacific/Honolulu.ics => app/src/main/resources/zoneinfo-global/Pacific/Honolulu.ics\n"
                                 + MSG_FOOTER,
                                 //true /* checkBuild */);
                                 // Turning off check builds because on some Jenkins machines this is triggering an exception
                                 // from the RenderScript compiler, presumably because it's running on a too-old version
                                 // of Ubuntu:
                                 //
                                 //  android-sdk-linux/build-tools/23.0.0/llvm-rs-cc: error while loading shared libraries: libncurses.so.5:
                                 //  cannot open shared object file: No such file or directory
                                 false /* checkBuild */);

    // Imported contents
    assertEquals(""
                 + "app\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      aidl\n"
                 + "        test\n"
                 + "          pkg\n"
                 + "            IHardwareService.aidl\n"
                 + "      java\n"
                 + "        test\n"
                 + "          pkg\n"
                 + "            MyActivity.java\n"
                 + "      res\n"
                 + "        drawable\n"
                 + "          ic_launcher.xml\n"
                 + "        values\n"
                 + "          strings.xml\n"
                 + "      resources\n"
                 + "        zoneinfo-global\n"
                 + "          Pacific\n"
                 + "            Honolulu.ics\n"
                 + "      rs\n"
                 + "        latency.rs\n"
                 + "build.gradle\n"
                 + "import-summary.txt\n"
                 + (getTestSdkPathLocal() != null ? "local.properties\n" : "")
                 + "settings.gradle\n",
                 fileTree(imported, true));

    deleteDir(projectDir);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testImportAndroidSample() throws Exception {
    String testSdkPath = getTestSdkPathLocal();
    if (testSdkPath == null) {
      return;
    }
    File samples = new File(testSdkPath, "samples");
    if (!samples.exists()) {
      return;
    }
    File[] platforms = samples.listFiles();
    if (platforms == null) {
      return;
    }
    File projectDir = null;
    for (File platform : platforms) {
      if (platform.isDirectory()) {
        String name = platform.getName();
        if (name.startsWith("android-")) {
          try {
            int version = Integer.parseInt(name.substring("android-".length()));
            if (version > GradleImport.CURRENT_COMPILE_VERSION) {
              // skip versions higher than the default compileSdkVersion since it's not specified in the ApiDemos
              // project and we'll pick up the default
              continue;
            }
          } catch (NumberFormatException e) {
            // e.g. android-L
            continue;
          }
        }
        File apiDemos = new File(platform, "legacy" + separator + "ApiDemos");
        if (apiDemos.isDirectory()) {
          projectDir = apiDemos;
          break;
        }
      }
    }
    if (projectDir == null) {
      return;
    }

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_UNHANDLED
                                 + "* README.txt\n"
                                 + "* tests/\n"
                                 + "* tests/build.properties\n"
                                 + MSG_FOLDER_STRUCTURE
                                 + "* AndroidManifest.xml => app/src/main/AndroidManifest.xml\n"
                                 + "* assets/ => app/src/main/assets/\n"
                                 + "* res/ => app/src/main/res/\n"
                                 + "* src/ => app/src/main/java/\n"
                                 + "* src/com/example/android/apis/app/IRemoteService.aidl => app/src/main/aidl/com/example/android/apis/app/IRemoteService.aidl\n"
                                 + "* src/com/example/android/apis/app/IRemoteServiceCallback.aidl => app/src/main/aidl/com/example/android/apis/app/IRemoteServiceCallback.aidl\n"
                                 + "* src/com/example/android/apis/app/ISecondary.aidl => app/src/main/aidl/com/example/android/apis/app/ISecondary.aidl\n"
                                 + "* tests/src/ => app/src/androidTest/java/\n"
                                 + MSG_FOOTER,

                                 // Temporarily disabled: As of build tools 21, aapt no longer allows the (invalid) references
                                 // to @+android:id in the samples, so building fails. The samples need to be updated, so don't
                                 // attempt to build them for now.
                                 //true /* checkBuild */);
                                 false /* checkBuild */);

    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testNoProjectMetadata() throws Exception {
    File projectDir = createProject("test1", "test.pkg");

    // Add some files in there that we are ignoring
    new File(projectDir, "ic_launcher-web.png").createNewFile();
    new File(projectDir, "Android.mk").createNewFile();
    new File(projectDir, "build.properties").createNewFile();
    new File(projectDir, "local.properties").createNewFile();
    new File(projectDir, "src" + separator + ".git").mkdir();
    new File(projectDir, "src" + separator + ".svn").mkdir();
    File unhandled = new File(projectDir, "unhandledDir1" + separator + "unhandledDir2");
    unhandled.mkdirs();
    new File(unhandled, "unhandledFile").createNewFile();
    new File(unhandled, "unhandledDir3").mkdirs();
    new File(unhandled.getParentFile(), "unhandledDir4").mkdirs();
    new File(projectDir, ".classpath").delete();
    new File(projectDir, ".project").delete();

    // Project being imported
    assertEquals(""
                 + "Android.mk\n"
                 + "AndroidManifest.xml\n"
                 + "build.properties\n"
                 + "gen\n"
                 + "  test\n"
                 + "    pkg\n"
                 + "      R.java\n"
                 + "ic_launcher-web.png\n"
                 + "local.properties\n"
                 + "project.properties\n"
                 + "res\n"
                 + "  drawable\n"
                 + "    ic_launcher.xml\n"
                 + "  values\n"
                 + "    strings.xml\n"
                 + "src\n"
                 + "  .git\n"
                 + "  .svn\n"
                 + "  test\n"
                 + "    pkg\n"
                 + "      MyActivity.java\n"
                 + "unhandledDir1\n"
                 + "  unhandledDir2\n"
                 + "    unhandledDir3\n"
                 + "    unhandledFile\n"
                 + "  unhandledDir4\n",
                 fileTree(projectDir, true));

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_UNHANDLED
                                 + "* Android.mk\n"
                                 + "* build.properties\n"
                                 + "* ic_launcher-web.png\n"
                                 + "* unhandledDir1/\n"
                                 + "* unhandledDir1/unhandledDir2/\n"
                                 + "* unhandledDir1/unhandledDir2/unhandledFile\n"
                                 + MSG_FOLDER_STRUCTURE
                                 + DEFAULT_MOVED
                                 + MSG_FOOTER,
                                 true /* checkBuild */);

    // Imported contents
    assertEquals(""
                 + "app\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          pkg\n"
                 + "            MyActivity.java\n"
                 + "      res\n"
                 + "        drawable\n"
                 + "          ic_launcher.xml\n"
                 + "        values\n"
                 + "          strings.xml\n"
                 + "build.gradle\n"
                 + "import-summary.txt\n"
                 + (getTestSdkPathLocal() != null ? "local.properties\n" : "")
                 + "settings.gradle\n",
                 fileTree(imported, true));

    deleteDir(projectDir);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testNoProjectProperties() throws Exception {
    // Missing project.properties
    File projectDir = createProject("testError3", "test.pkg");

    new File(projectDir, FN_PROJECT_PROPERTIES).delete();

    final AtomicReference<GradleImport> importReference = new AtomicReference<GradleImport>();
    File imported = checkProject(projectDir, ""
                                             + MSG_HEADER
                                             + MSG_FOLDER_STRUCTURE
                                             + DEFAULT_MOVED
                                             + MSG_FOOTER,
                                 true /* checkBuild */, new ImportCustomizer() {
        @Override
        public void customize(GradleImport importer) {
          importReference.set(importer);
        }
      });

    deleteDir(projectDir);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testLibraries() throws Exception {
    File root = Files.createTempDir();
    File app = createLibrary(root, "test.lib2.pkg", false);

    // ADT Directory structure created by the above:
    assertEquals(""
                 + "App\n"
                 + "  .classpath\n"
                 + "  .gitignore\n"
                 + "  .project\n"
                 + "  AndroidManifest.xml\n"
                 + "  gen\n"
                 + "    test\n"
                 + "      pkg\n"
                 + "        R.java\n"
                 + "  project.properties\n"
                 + "  res\n"
                 + "    drawable\n"
                 + "      ic_launcher.xml\n"
                 + "    values\n"
                 + "      strings.xml\n"
                 + "  src\n"
                 + "    test\n"
                 + "      pkg\n"
                 + "        MyActivity.java\n"
                 + "Lib1\n"
                 + "  .classpath\n"
                 + "  .project\n"
                 + "  AndroidManifest.xml\n"
                 + "  gen\n"
                 + "    test\n"
                 + "      lib\n"
                 + "        pkg\n"
                 + "          R.java\n"
                 + "  project.properties\n"
                 + "  src\n"
                 + "    test\n"
                 + "      lib\n"
                 + "        pkg\n"
                 + "          MyLibActivity.java\n"
                 + "Lib2\n"
                 + "  .classpath\n"
                 + "  .project\n"
                 + "  AndroidManifest.xml\n"
                 + "  gen\n"
                 + "    test\n"
                 + "      lib2\n"
                 + "        pkg\n"
                 + "          R.java\n"
                 + "  project.properties\n"
                 + "  src\n"
                 + "    test\n"
                 + "      lib2\n"
                 + "        pkg\n"
                 + "          MyLib2Activity.java\n"
                 + "subdir1\n"
                 + "  subdir2\n"
                 + "    JavaLib\n"
                 + "      .classpath\n"
                 + "      .gitignore\n"
                 + "      .project\n"
                 + "      src\n"
                 + "        test\n"
                 + "          lib2\n"
                 + "            pkg\n"
                 + "              Utilities.java\n",
                 fileTree(root, true));

    File imported = checkProject(app,
                                 ""
                                  + MSG_HEADER
                                  + MSG_MANIFEST
                                  + MSG_UNHANDLED
                                  + "From App:\n"
                                  + "* .gitignore\n"
                                  + "From JavaLib:\n"
                                  + "* .gitignore\n"
                                  + MSG_FOLDER_STRUCTURE
                                  + "In JavaLib:\n"
                                  + "* src/ => javaLib/src/main/java/\n"
                                  + "In Lib1:\n"
                                  + "* AndroidManifest.xml => lib1/src/main/AndroidManifest.xml\n"
                                  + "* src/ => lib1/src/main/java/\n"
                                  + "In Lib2:\n"
                                  + "* AndroidManifest.xml => lib2/src/main/AndroidManifest.xml\n"
                                  + "* src/ => lib2/src/main/java/\n"
                                  + "In App:\n"
                                  + "* AndroidManifest.xml => app/src/main/AndroidManifest.xml\n"
                                  + "* res/ => app/src/main/res/\n"
                                  + "* src/ => app/src/main/java/\n"
                                  + MSG_FOOTER,
                                 false /* checkBuild */);

    // Imported project
    assertEquals(""
                 + "app\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          pkg\n"
                 + "            MyActivity.java\n"
                 + "      res\n"
                 + "        drawable\n"
                 + "          ic_launcher.xml\n"
                 + "        values\n"
                 + "          strings.xml\n"
                 + "build.gradle\n"
                 + "import-summary.txt\n"
                 + "javaLib\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      java\n"
                 + "        test\n"
                 + "          lib2\n"
                 + "            pkg\n"
                 + "              Utilities.java\n"
                 + "lib1\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          lib\n"
                 + "            pkg\n"
                 + "              MyLibActivity.java\n"
                 + "lib2\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          lib2\n"
                 + "            pkg\n"
                 + "              MyLib2Activity.java\n"
                 + (getTestSdkPathLocal() != null ? "local.properties\n" : "")
                 + "settings.gradle\n",
                 fileTree(imported, true));

    // Let's peek at some of the key files to make sure we codegen'ed the right thing
    //noinspection PointlessBooleanExpression,ConstantConditions
    assertEquals(""
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "}\n" : "")
                 + "apply plugin: 'java'\n",
                 Files.toString(new File(imported, "javaLib" + separator + "build.gradle"), UTF_8)
                   .replace(NL, "\n"));

    // Let's peek at some of the key files to make sure we codegen'ed the right thing
    //noinspection ConstantConditions
    assertEquals(""
                 + "// Top-level build file where you can add configuration options common to all sub-projects/modules.\n"
                 + (DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n"
                    + "\n"
                    + "allprojects {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "}\n" : ""),
                 Files.toString(new File(imported, "build.gradle"), UTF_8)
                   .replace(NL, "\n"));

    //noinspection PointlessBooleanExpression,ConstantConditions
    assertEquals(""
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                 + "apply plugin: 'com.android.application'\n"
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                 + "\n"
                 + "android {\n"
                 + "    compileSdkVersion 22\n"
                 + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                 + "\n"
                 + "    defaultConfig {\n"
                 + "        applicationId \"test.pkg\"\n"
                 + "        minSdkVersion 8\n"
                 + "        targetSdkVersion 22\n"
                 + "    }\n"
                 + "\n"
                 + "    buildTypes {\n"
                 + "        release {\n"
                 + "            minifyEnabled false\n"
                 + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                 + "        }\n"
                 + "    }\n"
                 + "}\n"
                 + "\n"
                 + "dependencies {\n"
                 + "    compile project(':lib1')\n"
                 + "    compile project(':lib2')\n"
                 + "    compile project(':javaLib')\n"
                 + "}\n",
                 Files.toString(new File(imported, "app" + separator + "build.gradle"), UTF_8)
                   .replace(NL,"\n"));
    //noinspection PointlessBooleanExpression,ConstantConditions
    assertEquals(""
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                 + "apply plugin: 'com.android.library'\n"
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                 + "\n"
                 + "android {\n"
                 + "    compileSdkVersion 18\n"
                 + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                 + "\n"
                 + "    defaultConfig {\n"
                 + "        minSdkVersion 8\n"
                 + "        targetSdkVersion 8\n"
                 + "    }\n"
                 + "\n"
                 + "    buildTypes {\n"
                 + "        release {\n"
                 + "            minifyEnabled false\n"
                 + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                 + "        }\n"
                 + "    }\n"
                 + "}\n"
                 + "\n"
                 + "dependencies {\n"
                 + "    compile project(':lib1')\n"
                 + "}\n",
                 Files.toString(new File(imported, "lib2" + separator + "build.gradle"), UTF_8)
                   .replace(NL, "\n"));
    assertEquals(""
                 + "include ':javaLib'\n"
                 + "include ':lib1'\n"
                 + "include ':lib2'\n"
                 + "include ':app'\n",
                 Files.toString(new File(imported, "settings.gradle"), UTF_8)
                   .replace(NL, "\n"));

    deleteDir(root);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private static File createLibrary(File root, String lib2Pkg, boolean startLibrariesAt1)
    throws IOException {
    // Plain Java library, used by Library 1 and App
    String javaLibName = "JavaLib";
    String javaLibRelative = "subdir1" + separator + "subdir2" + separator + javaLibName;
    File javaLib = new File(root, javaLibRelative);
    javaLib.mkdirs();
    String javaLibPkg = "test.lib2.pkg";
    createDotProject(javaLib, javaLibName, false);
    File javaLibSrc = new File("src");
    createSampleJavaSource(javaLib, "src", javaLibPkg, "Utilities");
    createClassPath(javaLib,
                    new File("bin"),
                    Collections.singletonList(javaLibSrc),
                    Collections.<File>emptyList());

    // Make Android library 1

    String lib1Name = "Lib1";
    File lib1 = new File(root, lib1Name);
    lib1.mkdirs();
    String lib1Pkg = "test.lib.pkg";
    createDotProject(lib1, lib1Name, true);
    File lib1Src = new File("src");
    File lib1Gen = new File("gen");
    createSampleJavaSource(lib1, "src", lib1Pkg, "MyLibActivity");
    createSampleJavaSource(lib1, "gen", lib1Pkg, "R");
    createClassPath(lib1,
                    new File("bin", "classes"),
                    Arrays.asList(lib1Src, lib1Gen),
                    Collections.<File>emptyList());
    createProjectProperties(lib1, "android-19", null, true, null,
                            // Using \ instead of File.separator deliberately to test path conversion
                            // handling: you can import a Windows relative path on a non-Windows system
                            // and vice versa
                            Collections.singletonList(new File(".." + '\\' + javaLibRelative)),
                            startLibrariesAt1);
    createAndroidManifest(lib1, lib1Pkg, -1, -1, "<application/>");

    String lib2Name = "Lib2";
    File lib2 = new File(root, lib2Name);
    lib2.mkdirs();
    createDotProject(lib2, lib2Name, true);
    File lib2Src = new File("src");
    File lib2Gen = new File("gen");
    createSampleJavaSource(lib2, "src", lib2Pkg, "MyLib2Activity");
    createSampleJavaSource(lib2, "gen", lib2Pkg, "R");
    createClassPath(lib2,
                    new File("bin", "classes"),
                    Arrays.asList(lib2Src, lib2Gen),
                    Collections.<File>emptyList());
    createProjectProperties(lib2, "android-18", null, true, null,
                            // Deliberately using / instead of Files.separator, for opposite
                            // test of file separator for lib1 above
                            Collections.singletonList(new File(".." + '/' + lib1Name)));
    createAndroidManifest(lib2, lib2Pkg, 7, -1, "<application/>");

    // Main app project, depends on library1, library2 and java lib
    String appName = "App";
    File app = new File(root, appName);
    app.mkdirs();
    String appPkg = "test.pkg";
    createDotProject(app, appName, true);
    File appSrc = new File("src");
    File appGen = new File("gen");
    createSampleJavaSource(app, "src", appPkg, "MyActivity");
    createSampleJavaSource(app, "gen", appPkg, "R");
    createClassPath(app,
                    new File("bin", "classes"),
                    Arrays.asList(appSrc, appGen),
                    Collections.<File>emptyList());
    createProjectProperties(app, "android-22", null, null, null,
                            Arrays.asList(
                              new File(".." + separator + lib1Name),
                              new File(".." + separator + lib2Name),
                              new File(".." + separator + javaLibRelative)));
    createAndroidManifest(app, appPkg, 8, 22, null);
    createDefaultStrings(app);
    createDefaultIcon(app);

    // Add some files in there that we are ignoring
    new File(app, ".gitignore").createNewFile();
    new File(javaLib, ".gitignore").createNewFile();
    return app;
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testReplaceJar() throws Exception {
    // Add in some well known jars and make sure they get migrated as dependencies
    File projectDir = createProject("test1", "test.pkg");
    File libs = new File(projectDir, "libs");
    libs.mkdirs();
    new File(libs, "android-support-v4.jar").createNewFile();
    new File(libs, "android-support-v7-gridlayout.jar").createNewFile();
    new File(libs, "android-support-v7-appcompat.jar").createNewFile();

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_REPLACED_JARS
                                 + "android-support-v4.jar => com.android.support:support-v4:22.2.1\n"
                                 + "android-support-v7-appcompat.jar => com.android.support:appcompat-v7:22.2.1\n"
                                 + "android-support-v7-gridlayout.jar => com.android.support:gridlayout-v7:22.2.1\n"
                                 + MSG_FOLDER_STRUCTURE
                                 + DEFAULT_MOVED
                                 + (getTestSdkPathLocal() == null ? MSG_MISSING_REPO_1 + "null\n" + MSG_MISSING_REPO_2 : "")
                                 + MSG_FOOTER,
                                 true /* checkBuild */);

    // Imported contents
    assertEquals(""
                 + "app\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          pkg\n"
                 + "            MyActivity.java\n"
                 + "      res\n"
                 + "        drawable\n"
                 + "          ic_launcher.xml\n"
                 + "        values\n"
                 + "          strings.xml\n"
                 + "build.gradle\n"
                 + "import-summary.txt\n"
                 + (getTestSdkPathLocal() != null ? "local.properties\n" : "")
                 + "settings.gradle\n",
                 fileTree(imported, true));

    //noinspection PointlessBooleanExpression,ConstantConditions
    assertEquals(""
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                 + "apply plugin: 'com.android.application'\n"
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                 + "\n"
                 + "android {\n"
                 + "    compileSdkVersion 22\n"
                 + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                 + "\n"
                 + "    defaultConfig {\n"
                 + "        applicationId \"test.pkg\"\n"
                 + "        minSdkVersion 8\n"
                 + "        targetSdkVersion 22\n"
                 + "    }\n"
                 + "\n"
                 + "    buildTypes {\n"
                 + "        release {\n"
                 + "            minifyEnabled false\n"
                 + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                 + "        }\n"
                 + "    }\n"
                 + "}\n"
                 + "\n"
                 + "dependencies {\n"
                 + "    compile 'com.android.support:support-v4:22.2.1'\n"
                 + "    compile 'com.android.support:appcompat-v7:22.2.1'\n"
                 + "    compile 'com.android.support:gridlayout-v7:22.2.1'\n"
                 + "}\n",
                 Files.toString(new File(imported, "app" + separator + "build.gradle"), UTF_8)
                   .replace(NL, "\n"));

    deleteDir(projectDir);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testOptions() throws Exception {
    // Check options like turning off jar replacement and leaving module names capitalized
    File projectDir = createProject("Test1", "test.pkg");
    File libs = new File(projectDir, "libs");
    libs.mkdirs();
    new File(libs, "android-support-v4.jar").createNewFile();
    new File(libs, "android-support-v7-gridlayout.jar").createNewFile();
    new File(libs, "android-support-v7-appcompat.jar").createNewFile();
    new File(libs, "armeabi").mkdirs();

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_FOLDER_STRUCTURE
                                 + "* AndroidManifest.xml => Test1/src/main/AndroidManifest.xml\n"
                                 + "* libs/android-support-v4.jar => Test1/libs/android-support-v4.jar\n"
                                 + "* libs/android-support-v7-appcompat.jar => Test1/libs/android-support-v7-appcompat.jar\n"
                                 + "* libs/android-support-v7-gridlayout.jar => Test1/libs/android-support-v7-gridlayout.jar\n"
                                 + "* res/ => Test1/src/main/res/\n"
                                 + "* src/ => Test1/src/main/java/\n"
                                 + MSG_FOOTER,
                                 false /* checkBuild */,
                                 new ImportCustomizer() {
                                   @Override
                                   public void customize(GradleImport importer) {
                                     importer.setGradleNameStyle(false);
                                     importer.setReplaceJars(false);
                                     importer.setReplaceLibs(false);
                                   }
                                 });

    // Imported contents
    assertEquals(""
                 + "Test1\n"
                 + "  build.gradle\n"
                 + "  libs\n"
                 + "    android-support-v4.jar\n"
                 + "    android-support-v7-appcompat.jar\n"
                 + "    android-support-v7-gridlayout.jar\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          pkg\n"
                 + "            MyActivity.java\n"
                 + "      res\n"
                 + "        drawable\n"
                 + "          ic_launcher.xml\n"
                 + "        values\n"
                 + "          strings.xml\n"
                 + "build.gradle\n"
                 + "import-summary.txt\n"
                 + (getTestSdkPathLocal() != null ? "local.properties\n" : "")
                 + "settings.gradle\n",
                 fileTree(imported, true));

    //noinspection PointlessBooleanExpression,ConstantConditions
    assertEquals(""
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                 + "apply plugin: 'com.android.application'\n"
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                 + "\n"
                 + "android {\n"
                 + "    compileSdkVersion 22\n"
                 + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                 + "\n"
                 + "    defaultConfig {\n"
                 + "        applicationId \"test.pkg\"\n"
                 + "        minSdkVersion 8\n"
                 + "        targetSdkVersion 22\n"
                 + "    }\n"
                 + "\n"
                 + "    buildTypes {\n"
                 + "        release {\n"
                 + "            minifyEnabled false\n"
                 + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                 + "        }\n"
                 + "    }\n"
                 + "}\n"
                 + "\n"
                 + "dependencies {\n"
                 + "    compile files('libs/android-support-v4.jar')\n"
                 + "    compile files('libs/android-support-v7-appcompat.jar')\n"
                 + "    compile files('libs/android-support-v7-gridlayout.jar')\n"
                 + "}\n",
                 Files.toString(new File(imported, "Test1" + separator + "build.gradle"), UTF_8)
                   .replace(NL, "\n"));

    deleteDir(projectDir);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testJni() throws Exception {
    File root = Files.createTempDir();
    final File sdkLocation = new File(root, "sdk");
    sdkLocation.mkdirs();
    final File ndkLocation = new File(root, "ndk");
    ndkLocation.mkdirs();
    File projectDir = new File(root, "project");
    projectDir.mkdirs();
    createProject(projectDir, "testJni", "test.pkg");
    createDotProject(projectDir, "testJni", true, true);
    File jni = new File(projectDir, "jni");
    jni.mkdirs();
    File makefile = new File(jni, "Android.mk");
    Files.write(""
                + "LOCAL_PATH := $(call my-dir)\n"
                + "\n"
                + "include $(CLEAR_VARS)\n"
                + "\n"
                + "LOCAL_MODULE    := hello-jni\n"
                + "LOCAL_SRC_FILES := hello-jni.c\n"
                + "\n"
                + "include $(BUILD_SHARED_LIBRARY)",
                makefile, UTF_8);
    new File(jni, "Application.mk").createNewFile();
    new File(jni, "HelloJni.cpp").createNewFile();
    new File(jni, "hello-jni.c").createNewFile();

    File libs = new File(projectDir, "libs");
    libs.mkdirs();
    File armeabi = new File(libs, "armeabi");
    armeabi.mkdirs();
    new File(armeabi, "libexternal.so").createNewFile();
    new File(armeabi, "libhello-jni.so").createNewFile();
    File mips = new File(libs, "mips");
    mips.mkdirs();
    new File(mips, "libexternal.so").createNewFile();
    new File(mips, "libhello-jni.so").createNewFile();

    Files.write(
      escapeProperty("sdk.dir", sdkLocation.getPath()) + "\n" +
      escapeProperty("ndk.dir", ndkLocation.getPath()) + "\n",
      new File(projectDir, FN_LOCAL_PROPERTIES), UTF_8);

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_FOLDER_STRUCTURE
                                 + "* AndroidManifest.xml => testJni/src/main/AndroidManifest.xml\n"
                                 + "* jni/ => testJni/src/main/jni/\n"
                                 + "* libs/armeabi/libexternal.so => testJni/src/main/jniLibs/armeabi/libexternal.so\n"
                                 + "* libs/mips/libexternal.so => testJni/src/main/jniLibs/mips/libexternal.so\n"
                                 + "* res/ => testJni/src/main/res/\n"
                                 + "* src/ => testJni/src/main/java/\n"
                                 + MSG_FOOTER,
                                 false /* checkBuild */,
                                 new ImportCustomizer() {
                                   @Override
                                   public void customize(GradleImport importer) {
                                     assertFalse(importer.isImportIntoExisting());
                                     assertFalse(importer.isPerModuleRepositories());

                                     importer.setGradleNameStyle(false);
                                     importer.setSdkLocation(null);
                                     importer.setReplaceJars(false);
                                     importer.setReplaceLibs(false);
                                   }
                                 });

    // Imported contents
    assertEquals(""
                 + "build.gradle\n"
                 + "import-summary.txt\n"
                 + "local.properties\n"
                 + "settings.gradle\n"
                 + "testJni\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          pkg\n"
                 + "            MyActivity.java\n"
                 + "      jni\n"
                 + "        Android.mk\n"
                 + "        Application.mk\n"
                 + "        HelloJni.cpp\n"
                 + "        hello-jni.c\n"
                 + "      jniLibs\n"
                 + "        armeabi\n"
                 + "          libexternal.so\n"
                 + "        mips\n"
                 + "          libexternal.so\n"
                 + "      res\n"
                 + "        drawable\n"
                 + "          ic_launcher.xml\n"
                 + "        values\n"
                 + "          strings.xml\n",
                 fileTree(imported, true));

    //noinspection PointlessBooleanExpression,ConstantConditions
    assertEquals(""
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                 + "apply plugin: 'com.android.application'\n"
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                 + "\n"
                 + "android {\n"
                 + "    compileSdkVersion 22\n"
                 + "    buildToolsVersion \"" + MIN_BUILD_TOOLS_VERSION + "\"\n"
                 + "\n"
                 + "    defaultConfig {\n"
                 + "        applicationId \"test.pkg\"\n"
                 + "        minSdkVersion 8\n"
                 + "        targetSdkVersion 22\n"
                 + "\n"
                 + "        ndk {\n"
                 + "            moduleName \"hello-jni\"\n"
                 + "        }\n"
                 + "    }\n"
                 + "\n"
                 + "    buildTypes {\n"
                 + "        release {\n"
                 + "            minifyEnabled false\n"
                 + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                 + "        }\n"
                 + "    }\n"
                 + "}\n",
                 Files.toString(new File(imported, "testJni" + separator + "build.gradle"), UTF_8)
                   .replace(NL, "\n"));

    assertEquals(sdkLocation.getPath(),
                 PropertiesUtil.getProperties(new File(imported, FN_LOCAL_PROPERTIES)).
                   getProperty("sdk.dir"));
    assertEquals(ndkLocation.getPath(),
                 PropertiesUtil.getProperties(new File(imported, FN_LOCAL_PROPERTIES)).
                   getProperty("ndk.dir"));

    deleteDir(root);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testJniLibs() throws Exception {
    // Check that ABI libs are copied to the right place
    File projectDir = createProject("Test1", "test.pkg");
    File libs = new File(projectDir, "libs");
    libs.mkdirs();
    new File(libs, "android-support-v4.jar").createNewFile();
    File armeabi = new File(libs, "armeabi");
    armeabi.mkdirs();
    new File(armeabi, "libfoo.so").createNewFile();

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_FOLDER_STRUCTURE
                                 + "* AndroidManifest.xml => Test1/src/main/AndroidManifest.xml\n"
                                 + "* libs/android-support-v4.jar => Test1/libs/android-support-v4.jar\n"
                                 + "* libs/armeabi/libfoo.so => Test1/src/main/jniLibs/armeabi/libfoo.so\n"
                                 + "* res/ => Test1/src/main/res/\n"
                                 + "* src/ => Test1/src/main/java/\n"
                                 + MSG_FOOTER,
                                 false /* checkBuild */,
                                 new ImportCustomizer() {
                                   @Override
                                   public void customize(GradleImport importer) {
                                     importer.setGradleNameStyle(false);
                                     importer.setReplaceJars(false);
                                     importer.setReplaceLibs(false);
                                   }
                                 });

    // Imported contents
    assertEquals(""
                 + "Test1\n"
                 + "  build.gradle\n"
                 + "  libs\n"
                 + "    android-support-v4.jar\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          pkg\n"
                 + "            MyActivity.java\n"
                 + "      jniLibs\n"
                 + "        armeabi\n"
                 + "          libfoo.so\n"
                 + "      res\n"
                 + "        drawable\n"
                 + "          ic_launcher.xml\n"
                 + "        values\n"
                 + "          strings.xml\n"
                 + "build.gradle\n"
                 + "import-summary.txt\n"
                 + (getTestSdkPathLocal() != null ? "local.properties\n" : "")
                 + "settings.gradle\n",
                 fileTree(imported, true));

    deleteDir(projectDir);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testInstrumentation1() throws Exception {
    File root = Files.createTempDir();
    File projectDir = new File(root, "project");
    projectDir.mkdirs();
    createProject(projectDir, "Test2", "test.pkg");
    createDotProject(projectDir, "Test2", true, true);

    File tests = new File(projectDir, "tests");
    tests.mkdirs();
    Files.write(""
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "  package=\"my.test.pkg.name\"\n"
                + "  android:versionCode=\"1\"\n"
                + "  android:versionName=\"1.0\" >\n"
                + "\n"
                + "  <instrumentation\n"
                + "    android:name=\"android.test.InstrumentationTestRunner\"\n"
                + "    android:targetPackage=\"test.pkg\""
                + "    android:functionalTest=\"false\"\n"
                + "    android:handleProfiling=\"true\" />\n"
                + "\n"
                + "  <uses-sdk\n"
                + "    android:minSdkVersion=\"7\"\n"
                + "    android:targetSdkVersion=\"15\" />\n"
                + "\n"
                + "  <application\n"
                + "    android:icon=\"@android:drawable/sym_def_app_icon\"\n"
                + "    android:label=\"My Unit Test Instrumentation Tests\" >\n"
                + "    <uses-library android:name=\"android.test.runner\" />\n"
                + "  </application>\n"
                + "\n"
                + "</manifest>",
                new File(tests, FN_ANDROID_MANIFEST_XML), UTF_8);
    File testSrc = new File(tests, "src");
    testSrc.mkdirs();
    File testPkg = new File(testSrc, "mytestpkg");
    testPkg.mkdirs();
    new File(testPkg, "MyUnitTest.java").createNewFile();
    File testRes = new File(tests, "res");
    testRes.mkdirs();
    File testValues = new File(testRes, "values");
    testValues.mkdirs();
    new File(testValues, "strings.xml").createNewFile();
    File testLibs = new File(tests, "libs");
    testLibs.mkdirs();
    new File(testLibs, "myTestSupportLib.jar").createNewFile();

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_FOLDER_STRUCTURE
                                 + "* AndroidManifest.xml => Test2/src/main/AndroidManifest.xml\n"
                                 + "* res/ => Test2/src/main/res/\n"
                                 + "* src/ => Test2/src/main/java/\n"
                                 + "* tests/libs/myTestSupportLib.jar => Test2/libs/myTestSupportLib.jar\n"
                                 + "* tests/res/ => Test2/src/androidTest/res/\n"
                                 + "* tests/src/ => Test2/src/androidTest/java/\n"
                                 + MSG_FOOTER,
                                 false /* checkBuild */,
                                 new ImportCustomizer() {
                                   @Override
                                   public void customize(GradleImport importer) {
                                     importer.setGradleNameStyle(false);
                                   }
                                 });

    // Imported contents
    assertEquals(""
                 + "Test2\n"
                 + "  build.gradle\n"
                 + "  libs\n"
                 + "    myTestSupportLib.jar\n"
                 + "  src\n"
                 + "    androidTest\n"
                 + "      java\n"
                 + "        mytestpkg\n"
                 + "          MyUnitTest.java\n"
                 + "      res\n"
                 + "        values\n"
                 + "          strings.xml\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          pkg\n"
                 + "            MyActivity.java\n"
                 + "      res\n"
                 + "        drawable\n"
                 + "          ic_launcher.xml\n"
                 + "        values\n"
                 + "          strings.xml\n"
                 + "build.gradle\n"
                 + "import-summary.txt\n"
                 + (getTestSdkPathLocal() != null ? "local.properties\n" : "")
                 + "settings.gradle\n",
                 fileTree(imported, true));

    //noinspection PointlessBooleanExpression,ConstantConditions
    assertEquals(""
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                 + "apply plugin: 'com.android.application'\n"
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                 + "\n"
                 + "android {\n"
                 + "    compileSdkVersion 22\n"
                 + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                 + "\n"
                 + "    defaultConfig {\n"
                 + "        applicationId \"test.pkg\"\n"
                 + "        minSdkVersion 8\n"
                 + "        targetSdkVersion 22\n"
                 + "\n"
                 + "        testApplicationId \"my.test.pkg.name\"\n"
                 + "        testInstrumentationRunner \"android.test.InstrumentationTestRunner\"\n"
                 + "        testFunctionalTest false\n"
                 + "        testHandlingProfiling true\n"
                 + "    }\n"
                 + "\n"
                 + "    buildTypes {\n"
                 + "        release {\n"
                 + "            minifyEnabled false\n"
                 + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                 + "        }\n"
                 + "    }\n"
                 + "}\n"
                 + "\n"
                 + "dependencies {\n"
                 + "    androidTestCompile files('libs/myTestSupportLib.jar')\n"
                 + "}\n",
                 Files.toString(new File(imported, "Test2" + separator + "build.gradle"), UTF_8)
                   .replace(NL, "\n"));

    deleteDir(root);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testInstrumentation2() throws Exception {
    // Like testInstrumentation1, but the unit test is found in a sibling directory
    // (which also means various paths should be relative - ../ etc)
    File root = Files.createTempDir();
    File projectDir = new File(root, "project");
    projectDir.mkdirs();
    createProject(projectDir, "Test2", "test.pkg");
    createDotProject(projectDir, "Test2", true, true);

    File tests = new File(root, "tests");
    tests.mkdirs();
    Files.write(""
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "  package=\"my.test.pkg.name\"\n"
                + "  android:versionCode=\"1\"\n"
                + "  android:versionName=\"1.0\" >\n"
                + "\n"
                + "  <instrumentation\n"
                + "    android:name=\"android.test.InstrumentationTestRunner\"\n"
                + "    android:targetPackage=\"test.pkg\""
                + "    android:functionalTest=\"false\"\n"
                + "    android:handleProfiling=\"true\" />\n"
                + "\n"
                + "  <uses-sdk\n"
                + "    android:minSdkVersion=\"7\"\n"
                + "    android:targetSdkVersion=\"15\" />\n"
                + "\n"
                + "  <application\n"
                + "    android:icon=\"@android:drawable/sym_def_app_icon\"\n"
                + "    android:label=\"My Unit Test Instrumentation Tests\" >\n"
                + "    <uses-library android:name=\"android.test.runner\" />\n"
                + "  </application>\n"
                + "\n"
                + "</manifest>",
                new File(tests, FN_ANDROID_MANIFEST_XML), UTF_8);
    File testSrc = new File(tests, "src");
    testSrc.mkdirs();
    File testPkg = new File(testSrc, "mytestpkg");
    testPkg.mkdirs();
    new File(testPkg, "MyUnitTest.java").createNewFile();
    File testRes = new File(tests, "res");
    testRes.mkdirs();
    File testValues = new File(testRes, "values");
    testValues.mkdirs();
    new File(testValues, "strings.xml").createNewFile();
    File testLibs = new File(tests, "libs");
    testLibs.mkdirs();
    new File(testLibs, "myTestSupportLib.jar").createNewFile();

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_FOLDER_STRUCTURE
                                 + "* AndroidManifest.xml => app/src/main/AndroidManifest.xml\n"
                                 + "* res/ => app/src/main/res/\n"
                                 + "* src/ => app/src/main/java/\n"
                                 + "* $ROOT_PARENT/tests/libs/myTestSupportLib.jar => app/libs/myTestSupportLib.jar\n"
                                 + "* $ROOT_PARENT/tests/res/ => app/src/androidTest/res/\n"
                                 + "* $ROOT_PARENT/tests/src/ => app/src/androidTest/java/\n"
                                 + MSG_FOOTER,
                                 false /* checkBuild */,
                                 new ImportCustomizer() {
                                   @Override
                                   public void customize(GradleImport importer) {
                                   }
                                 });

    deleteDir(root);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testReplaceSourceLibraryProject() throws Exception {
    // Make a library project which looks like it can just be replaced by a project

    File root = Files.createTempDir();
    // Pretend lib2 is ActionBarSherlock; it should then be stripped out and replaced
    // by a set of dependencies
    File app = createLibrary(root, "com.actionbarsherlock", true);

    File imported = checkProject(app, "" +
                                      MSG_HEADER +
                                      MSG_MANIFEST +
                                      MSG_UNHANDLED +
                                      "From App:\n" +
                                      "* .gitignore\n" +
                                      "From JavaLib:\n" +
                                      "* .gitignore\n" +
                                      MSG_REPLACED_LIBS +
                                      "Lib2 =>\n" +
                                      "    com.actionbarsherlock:actionbarsherlock:4.4.0@aar\n" +
                                      "    com.android.support:support-v4:18.0.0\n" +
                                      MSG_FOLDER_STRUCTURE
                                      // TODO: The summary should describe the library!!
                                      +
                                      "In JavaLib:\n" +
                                      "* src/ => javaLib/src/main/java/\n" +
                                      "In Lib1:\n" +
                                      "* AndroidManifest.xml => lib1/src/main/AndroidManifest.xml\n" +
                                      "* src/ => lib1/src/main/java/\n" +
                                      "In App:\n" +
                                      "* AndroidManifest.xml => app/src/main/AndroidManifest.xml\n" +
                                      "* res/ => app/src/main/res/\n" +
                                      "* src/ => app/src/main/java/\n" +
                                      (getTestSdkPathLocal() == null ? MSG_MISSING_REPO_1 + "null\n" + MSG_MISSING_REPO_2 : "") +
                                      MSG_FOOTER, false /* checkBuild */);

    // Imported project; note how lib2 is gone
    assertEquals(""
                 + "app\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          pkg\n"
                 + "            MyActivity.java\n"
                 + "      res\n"
                 + "        drawable\n"
                 + "          ic_launcher.xml\n"
                 + "        values\n"
                 + "          strings.xml\n"
                 + "build.gradle\n"
                 + "import-summary.txt\n"
                 + "javaLib\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      java\n"
                 + "        test\n"
                 + "          lib2\n"
                 + "            pkg\n"
                 + "              Utilities.java\n"
                 + "lib1\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          lib\n"
                 + "            pkg\n"
                 + "              MyLibActivity.java\n"
                 + (getTestSdkPathLocal() != null ? "local.properties\n" : "")
                 + "settings.gradle\n",
                 fileTree(imported, true));

    //noinspection PointlessBooleanExpression,ConstantConditions
    assertEquals(""
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                 + "apply plugin: 'com.android.application'\n"
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                 + "\n"
                 + "android {\n"
                 + "    compileSdkVersion 22\n"
                 + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                 + "\n"
                 + "    defaultConfig {\n"
                 + "        applicationId \"test.pkg\"\n"
                 + "        minSdkVersion 8\n"
                 + "        targetSdkVersion 22\n"
                 + "    }\n"
                 + "\n"
                 + "    buildTypes {\n"
                 + "        release {\n"
                 + "            minifyEnabled false\n"
                 + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                 + "        }\n"
                 + "    }\n"
                 + "}\n"
                 + "\n"
                 + "dependencies {\n"
                 + "    compile project(':lib1')\n"
                 + "    compile project(':javaLib')\n"
                 + "    compile 'com.actionbarsherlock:actionbarsherlock:4.4.0@aar'\n"
                 + "    compile 'com.android.support:support-v4:18.0.0'\n"
                 + "}\n",
                 Files.toString(new File(imported, "app" + separator + "build.gradle"), UTF_8)
                   .replace(NL, "\n"));

    deleteDir(root);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testMissingRepositories() throws Exception {
    File root = Files.createTempDir();
    final File sdkLocation = new File(root, "sdk");
    sdkLocation.mkdirs();
    File projectDir = new File(root, "project");
    projectDir.mkdirs();
    createProject(projectDir, "test1", "test.pkg");
    File libs = new File(projectDir, "libs");
    libs.mkdirs();
    new File(libs, "android-support-v4.jar").createNewFile();

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_REPLACED_JARS
                                 + "android-support-v4.jar => com.android.support:support-v4:22.+\n"
                                 + MSG_FOLDER_STRUCTURE
                                 + DEFAULT_MOVED
                                 + MSG_MISSING_REPO_1
                                 + "$ROOT_PARENT/sdk\n"
                                 + MSG_MISSING_REPO_2
                                 + MSG_FOOTER,
                                 false /* checkBuild */, new ImportCustomizer() {
        @Override
        public void customize(GradleImport importer) {
          importer.setSdkLocation(sdkLocation);
        }
      });

    deleteDir(root);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testMissingPlayRepositories() throws Exception {
    File root = Files.createTempDir();
    final File sdkLocation = new File(root, "sdk");
    sdkLocation.mkdirs();
    File projectDir = new File(root, "project");
    projectDir.mkdirs();
    createProject(projectDir, "test1", "test.pkg");
    File libs = new File(projectDir, "libs");
    libs.mkdirs();
    new File(libs, "gcm.jar").createNewFile();

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_REPLACED_JARS
                                 + "gcm.jar => com.google.android.gms:play-services:+\n"
                                 + MSG_FOLDER_STRUCTURE
                                 + DEFAULT_MOVED
                                 + MSG_MISSING_GOOGLE_REPOSITORY_1
                                 + "$ROOT_PARENT/sdk\n"
                                 + MSG_MISSING_GOOGLE_REPOSITORY_2
                                 + MSG_FOOTER,
                                 false /* checkBuild */, new ImportCustomizer() {
        @Override
        public void customize(GradleImport importer) {
          importer.setSdkLocation(sdkLocation);
        }
      });

    // Imported project: confirm that gcm.jar is not in the output tree
    assertEquals(""
                 + "app\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          pkg\n"
                 + "            MyActivity.java\n"
                 + "      res\n"
                 + "        drawable\n"
                 + "          ic_launcher.xml\n"
                 + "        values\n"
                 + "          strings.xml\n"
                 + "build.gradle\n"
                 + "import-summary.txt\n"
                 + "local.properties\n"
                 + "settings.gradle\n",
                 fileTree(imported, true));

    deleteDir(root);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testGuessedVersion() throws Exception {
    File root = Files.createTempDir();
    final File sdkLocation = new File(root, "sdk");
    sdkLocation.mkdirs();
    File projectDir = new File(root, "project");
    projectDir.mkdirs();
    createProject(projectDir, "test1", "test.pkg");
    File libs = new File(projectDir, "libs");
    libs.mkdirs();
    new File(libs, "guava-13.0.1.jar").createNewFile();

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_REPLACED_JARS
                                 + "guava-13.0.1.jar => com.google.guava:guava:13.0.1\n"
                                 + MSG_GUESSED_VERSIONS
                                 + "guava-13.0.1.jar => version 13.0.1 in com.google.guava:guava:13.0.1\n"
                                 + MSG_FOLDER_STRUCTURE
                                 + DEFAULT_MOVED
                                 + MSG_FOOTER,
                                 false /* checkBuild */, new ImportCustomizer() {
        @Override
        public void customize(GradleImport importer) {
          importer.setSdkLocation(sdkLocation);
        }
      });

    deleteDir(root);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testClassPathFilePaths() throws Exception {
    // Test a project where the .classpath file contains additional
    // issues: workspace-local dependencies for projects,
    // absolute paths to the framework, etc.

    File root = Files.createTempDir();
    File projectDir = new File(root, "prj");
    projectDir.mkdirs();
    projectDir = createProject(projectDir, "1 Weird 'name' of project!", "test.pkg");
    File lib = new File(root, "android-support-v7-appcompat");
    lib.mkdirs();

    File classpath = new File(projectDir, ".classpath");
    assertTrue(classpath.exists());
    classpath.delete();
    //noinspection SpellCheckingInspection
    Files.write(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<classpath>\n"
                + "\t<classpathentry kind=\"src\" path=\"src\"/>\n"
                + "\t<classpathentry kind=\"src\" path=\"gen\"/>\n"
                + "\t<classpathentry kind=\"con\" path=\"com.android.ide.eclipse.adt.ANDROID_FRAMEWORK\"/>\n"
                + "\t<classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.DEPENDENCIES\"/>\n"
                + "\t<classpathentry kind=\"lib\" path=\"libs/basic-http-client-android-0.88.jar\"/>\n"
                + "\t<classpathentry kind=\"lib\" path=\"/opt/android-sdk/platforms/android-14/android.jar\">\n"
                + "\t\t<attributes>\n"
                + "\t\t\t<attribute name=\"javadoc_location\" value=\"file:/opt/android-sdk/docs/reference\"/>\n"
                + "\t\t</attributes>\n"
                + "\t\t<accessrules>\n"
                + "\t\t\t<accessrule kind=\"nonaccessible\" pattern=\"com/android/internal/**\"/>\n"
                + "\t\t</accessrules>\n"
                + "\t</classpathentry>\n"
                + "\t<classpathentry kind=\"lib\" path=\"libs/htmlcleaner-2.6.jar\"/>\n"
                + "\t<classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.LIBRARIES\"/>\n"
                + "\t<classpathentry combineaccessrules=\"false\" kind=\"src\" path=\"/android-support-v7-appcompat\"/>\n"
                + "\t<classpathentry kind=\"output\" path=\"bin/classes\"/>\n"
                + "</classpath>",
                classpath, UTF_8);

    //noinspection SpellCheckingInspection
    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_FOLDER_STRUCTURE
                                 + "* $ROOT_PARENT/android-support-v7-appcompat/ => _1Weirdnameofproject/src/main/java/\n"
                                 + "* AndroidManifest.xml => _1Weirdnameofproject/src/main/AndroidManifest.xml\n"
                                 + "* res/ => _1Weirdnameofproject/src/main/res/\n"
                                 + "* src/ => _1Weirdnameofproject/src/main/java/\n"
                                 + MSG_FOOTER,
                                 false /* checkBuild */, new ImportCustomizer() {
        @Override
        public void customize(GradleImport importer) {
          importer.setGradleNameStyle(false);
        }
      });

    deleteDir(projectDir);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private static Pair<File,File> createLibrary2(File library1Dir) throws Exception {
    File root = Files.createTempDir();

    // Workspace Setup
    // /Library1, compiled with 1.7, and depends on an external jar outside the project (guava)
    // /Library2 (depends on /Library1)
    // /AndroidLibraryProject (depend on /Library1, /Library2)
    // /AndroidAppProject (depends on /AndroidLibraryProject)
    // In addition to make things complicated, /Library1 can live outside the workspace
    // (based on the path we pass in)
    // and /Library2 lives in a subdirectory of the workspace

    // Plain Java library, used by Library 1 and App
    // Make Java Library library 1
    String lib1Name = "Library1";
    File lib1 = library1Dir.isAbsolute() ? library1Dir :
                new File(root, library1Dir.getPath());
    lib1.mkdirs();
    String lib1Pkg = "test.lib1.pkg";
    createDotProject(lib1, lib1Name, false);
    createSampleJavaSource(lib1, "src", lib1Pkg, "Library1");
    File guavaPath = new File(root, "some" + separator + "path" + separator +
                                    "guava-13.0.1.jar");
    guavaPath.getParentFile().mkdirs();
    guavaPath.createNewFile();
    Files.write(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<classpath>\n"
                + "\t<classpathentry kind=\"src\" path=\"src\"/>\n"
                + "\t<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/Java 7\"/>\n"
                + "\t<classpathentry exported=\"true\" kind=\"lib\" path=\"" + guavaPath.getAbsoluteFile().getCanonicalFile().getPath() + "\"/>\n"
                + "\t<classpathentry kind=\"output\" path=\"bin\"/>\n"
                + "</classpath>",
                new File(lib1, ".classpath"), UTF_8);
    createEclipseSettingsFile(lib1, "1.6");

    // Make Java Library 2
    String lib2Name = "Library2";
    File lib2 = new File(root, lib2Name);
    lib2.mkdirs();
    createDotProject(lib2, lib2Name, false);
    String lib2Pkg = "test.lib2.pkg";
    createSampleJavaSource(lib2, "src", lib2Pkg, "Library2");
    Files.write(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<classpath>\n"
                + "\t<classpathentry kind=\"src\" path=\"src\"/>\n"
                + "\t<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/Java 7\"/>\n"
                + "\t<classpathentry combineaccessrules=\"false\" kind=\"src\" path=\"/Library1\"/>\n"
                + "\t<classpathentry kind=\"output\" path=\"bin\"/>\n"
                + "</classpath>",
                new File(lib2, ".classpath"), UTF_8);
    createEclipseSettingsFile(lib2, "1.7");

    // Make Android Library Project 1
    String androidLibName = "AndroidLibrary";
    File androidLib = new File(root, androidLibName);
    androidLib.mkdirs();
    createDotProject(androidLib, androidLibName, true);
    String androidLibPkg = "test.android.lib.pkg";
    createSampleJavaSource(androidLib, "src", androidLibPkg, "AndroidLibrary");
    createSampleJavaSource(androidLib, "gen", androidLibPkg, "R");
    Files.write(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<classpath>\n"
                + "\t<classpathentry kind=\"src\" path=\"src\"/>\n"
                + "\t<classpathentry kind=\"src\" path=\"gen\"/>\n"
                + "\t<classpathentry kind=\"con\" path=\"com.android.ide.eclipse.adt.ANDROID_FRAMEWORK\"/>\n"
                + "\t<classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.LIBRARIES\"/>\n"
                + "\t<classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.DEPENDENCIES\"/>\n"
                + "\t<classpathentry combineaccessrules=\"false\" exported=\"true\" kind=\"src\" path=\"/Library1\"/>\n"
                + "\t<classpathentry combineaccessrules=\"false\" exported=\"true\" kind=\"src\" path=\"/Library2\"/>\n"
                + "\t<classpathentry kind=\"output\" path=\"bin/classes\"/>\n"
                + "</classpath>", new File(androidLib, ".classpath"), UTF_8);
    createProjectProperties(androidLib, "android-18", null, true, null,
                            // Note how Android library projects don't point to non-Android projects
                            // in the project.properties file; only via the .classpath file!
                            Collections.<File>emptyList());
    createAndroidManifest(androidLib, androidLibPkg, 7, -1, "");

    // Main app project, depends on library project
    String appName = "AndroidApp";
    File app = new File(root, appName);
    app.mkdirs();
    String appPkg = "test.pkg";
    createDotProject(app, appName, true);
    File appSrc = new File("src");
    File appGen = new File("gen");
    createSampleJavaSource(app, "src", appPkg, "AppActivity");
    createSampleJavaSource(app, "gen", appPkg, "R");
    createClassPath(app,
                    new File("bin", "classes"),
                    Arrays.asList(appSrc, appGen),
                    Collections.<File>emptyList());
    createProjectProperties(app, "android-22", null, null, null,
                            Collections.singletonList(new File(".." + separator + androidLibName)));
    createAndroidManifest(app, appPkg, 8, 22, null);
    createDefaultStrings(app);
    createDefaultIcon(app);

    // Add some files in there that we are ignoring
    new File(app, ".gitignore").createNewFile();

    return Pair.of(root, app);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testLibraries2() throws Exception {
    Pair<File,File> pair = createLibrary2(new File("Library1"));
    File root = pair.getFirst();
    File app = pair.getSecond();

    // ADT Directory structure created by the above:
    assertEquals(""
                 + "AndroidApp\n"
                 + "  .classpath\n"
                 + "  .gitignore\n"
                 + "  .project\n"
                 + "  AndroidManifest.xml\n"
                 + "  gen\n"
                 + "    test\n"
                 + "      pkg\n"
                 + "        R.java\n"
                 + "  project.properties\n"
                 + "  res\n"
                 + "    drawable\n"
                 + "      ic_launcher.xml\n"
                 + "    values\n"
                 + "      strings.xml\n"
                 + "  src\n"
                 + "    test\n"
                 + "      pkg\n"
                 + "        AppActivity.java\n"
                 + "AndroidLibrary\n"
                 + "  .classpath\n"
                 + "  .project\n"
                 + "  AndroidManifest.xml\n"
                 + "  gen\n"
                 + "    test\n"
                 + "      android\n"
                 + "        lib\n"
                 + "          pkg\n"
                 + "            R.java\n"
                 + "  project.properties\n"
                 + "  src\n"
                 + "    test\n"
                 + "      android\n"
                 + "        lib\n"
                 + "          pkg\n"
                 + "            AndroidLibrary.java\n"
                 + "Library1\n"
                 + "  .classpath\n"
                 + "  .project\n"
                 + "  .settings\n"
                 + "    org.eclipse.jdt.core.prefs\n"
                 + "  src\n"
                 + "    test\n"
                 + "      lib1\n"
                 + "        pkg\n"
                 + "          Library1.java\n"
                 + "Library2\n"
                 + "  .classpath\n"
                 + "  .project\n"
                 + "  .settings\n"
                 + "    org.eclipse.jdt.core.prefs\n"
                 + "  src\n"
                 + "    test\n"
                 + "      lib2\n"
                 + "        pkg\n"
                 + "          Library2.java\n"
                 + "some\n"
                 + "  path\n"
                 + "    guava-13.0.1.jar\n",
                 fileTree(root, true));

    final AtomicReference<GradleImport> importReference = new AtomicReference<GradleImport>();
    File imported = checkProject(app,
                                 ""
                                  + MSG_HEADER
                                  + MSG_MANIFEST
                                  + MSG_UNHANDLED
                                  + "* .gitignore\n"
                                  + MSG_REPLACED_JARS
                                  + "guava-13.0.1.jar => com.google.guava:guava:13.0.1\n"
                                  + MSG_GUESSED_VERSIONS
                                  + "guava-13.0.1.jar => version 13.0.1 in com.google.guava:guava:13.0.1\n"
                                  + MSG_FOLDER_STRUCTURE
                                  + "In Library1:\n"
                                  + "* src/ => library1/src/main/java/\n"
                                  + "In Library2:\n"
                                  + "* src/ => library2/src/main/java/\n"
                                  + "In AndroidLibrary:\n"
                                  + "* AndroidManifest.xml => androidLibrary/src/main/AndroidManifest.xml\n"
                                  + "* src/ => androidLibrary/src/main/java/\n"
                                  + "In AndroidApp:\n"
                                  + "* AndroidManifest.xml => androidApp/src/main/AndroidManifest.xml\n"
                                  + "* res/ => androidApp/src/main/res/\n"
                                  + "* src/ => androidApp/src/main/java/\n"
                                  + MSG_FOOTER,
                                 false /* checkBuild */, new ImportCustomizer() {
        @Override
        public void customize(GradleImport importer) {
          importReference.set(importer);
        }
      });
    assertEquals("{/Library1=" + new File(root, "Library1").getCanonicalPath() +
                 ", /Library2=" + new File(root, "Library2").getCanonicalPath() +"}",
                 describePathMap(importReference.get()));

    // Imported project
    assertEquals(""
                 + "androidApp\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          pkg\n"
                 + "            AppActivity.java\n"
                 + "      res\n"
                 + "        drawable\n"
                 + "          ic_launcher.xml\n"
                 + "        values\n"
                 + "          strings.xml\n"
                 + "androidLibrary\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          android\n"
                 + "            lib\n"
                 + "              pkg\n"
                 + "                AndroidLibrary.java\n"
                 + "build.gradle\n"
                 + "import-summary.txt\n"
                 + "library1\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      java\n"
                 + "        test\n"
                 + "          lib1\n"
                 + "            pkg\n"
                 + "              Library1.java\n"
                 + "library2\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      java\n"
                 + "        test\n"
                 + "          lib2\n"
                 + "            pkg\n"
                 + "              Library2.java\n"
                 + (getTestSdkPathLocal() != null ? "local.properties\n" : "")
                 + "settings.gradle\n",
                 fileTree(imported, true));

    // Let's peek at some of the key files to make sure we codegen'ed the right thing
    //noinspection PointlessBooleanExpression,ConstantConditions
    assertEquals(""
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "}\n" : "")
                 + "apply plugin: 'java'\n"
                 + "\n"
                 + "dependencies {\n"
                 + "    compile 'com.google.guava:guava:13.0.1'\n"
                 + "}\n",
                 Files.toString(new File(imported, "library1" + separator + "build.gradle"), UTF_8)
                   .replace(NL, "\n"));
    //noinspection PointlessBooleanExpression,ConstantConditions
    assertEquals(""
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                 + "apply plugin: 'com.android.application'\n"
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                 + "\n"
                 + "android {\n"
                 + "    compileSdkVersion 22\n"
                 + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                 + "\n"
                 + "    defaultConfig {\n"
                 + "        applicationId \"test.pkg\"\n"
                 + "        minSdkVersion 8\n"
                 + "        targetSdkVersion 22\n"
                 + "    }\n"
                 + "\n"
                 + "    buildTypes {\n"
                 + "        release {\n"
                 + "            minifyEnabled false\n"
                 + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                 + "        }\n"
                 + "    }\n"
                 + "}\n"
                 + "\n"
                 + "dependencies {\n"
                 + "    compile project(':androidLibrary')\n"
                 + "}\n",
                 Files.toString(new File(imported, "androidApp" + separator + "build.gradle"), UTF_8)
                   .replace(NL,"\n"));

    //noinspection PointlessBooleanExpression,ConstantConditions
    assertEquals(""
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                 + "apply plugin: 'com.android.library'\n"
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                 + "\n"
                 + "android {\n"
                 + "    compileSdkVersion 18\n"
                 + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                 + "\n"
                 + "    defaultConfig {\n"
                 + "        minSdkVersion 8\n"
                 + "        targetSdkVersion 8\n"
                 + "    }\n"
                 + "\n"
                 + "    buildTypes {\n"
                 + "        release {\n"
                 + "            minifyEnabled false\n"
                 + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                 + "        }\n"
                 + "    }\n"
                 + "}\n"
                 + "\n"
                 + "dependencies {\n"
                 + "    compile project(':library1')\n"
                 + "    compile project(':library2')\n"
                 + "}\n",
                 Files.toString(new File(imported, "androidLibrary" + separator + "build.gradle"), UTF_8)
                   .replace(NL,"\n"));

    //noinspection PointlessBooleanExpression,ConstantConditions
    assertEquals(""
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "}\n" : "")
                 + "apply plugin: 'java'\n"
                 + "\n"
                 + "dependencies {\n"
                 + "    compile 'com.google.guava:guava:13.0.1'\n"
                 + "}\n",
                 Files.toString(new File(imported, "library1" + separator + "build.gradle"), UTF_8)
                   .replace(NL, "\n"));
    //noinspection PointlessBooleanExpression,ConstantConditions
    assertEquals(""
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "}\n" : "")
                 + "apply plugin: 'java'\n"
                 + "\n"
                 + "sourceCompatibility = \"1.7\"\n"
                 + "targetCompatibility = \"1.7\"\n"
                 + "\n"
                 + "dependencies {\n"
                 + "    compile project(':library1')\n"
                 + "}\n",
                 Files.toString(new File(imported, "library2" + separator + "build.gradle"), UTF_8)
                   .replace(NL, "\n"));

    // TODO: Should this ONLY include the root module?
    assertEquals(""
                 + "include ':library1'\n"
                 + "include ':library2'\n"
                 + "include ':androidLibrary'\n"
                 + "include ':androidApp'\n",
                 Files.toString(new File(imported, "settings.gradle"), UTF_8)
                   .replace(NL, "\n"));

    //noinspection ConstantConditions
    assertEquals(""
                 + "// Top-level build file where you can add configuration options common to all sub-projects/modules.\n"
                 + (DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n"
                    + "\n"
                    + "allprojects {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "}\n" : ""),
                 Files.toString(new File(imported, "build.gradle"), UTF_8)
                   .replace(NL, "\n"));

    deleteDir(root);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testLibrariesWithWorkspaceMapping1() throws Exception {
    // Provide manually edited workspace mapping /Library1 = actual dir
    final String library1Path = "subdir1" + separator + "subdir2" + separator +
                                "UnrelatedName";
    final File library1Dir = new File(library1Path);
    Pair<File,File> pair = createLibrary2(library1Dir);
    final File root = pair.getFirst();
    File app = pair.getSecond();

    final AtomicReference<GradleImport> importReference = new AtomicReference<GradleImport>();
    File imported = checkProject(app,
                                 ""
                                  + MSG_HEADER
                                  + MSG_MANIFEST
                                  + MSG_UNHANDLED
                                  + "* .gitignore\n"
                                  + MSG_REPLACED_JARS
                                  + "guava-13.0.1.jar => com.google.guava:guava:13.0.1\n"
                                  + MSG_GUESSED_VERSIONS
                                  + "guava-13.0.1.jar => version 13.0.1 in com.google.guava:guava:13.0.1\n"
                                  + MSG_FOLDER_STRUCTURE
                                  + "In Library1:\n"
                                  + "* src/ => library1/src/main/java/\n"
                                  + "In Library2:\n"
                                  + "* src/ => library2/src/main/java/\n"
                                  + "In AndroidLibrary:\n"
                                  + "* AndroidManifest.xml => androidLibrary/src/main/AndroidManifest.xml\n"
                                  + "* src/ => androidLibrary/src/main/java/\n"
                                  + "In AndroidApp:\n"
                                  + "* AndroidManifest.xml => androidApp/src/main/AndroidManifest.xml\n"
                                  + "* res/ => androidApp/src/main/res/\n"
                                  + "* src/ => androidApp/src/main/java/\n"
                                  + MSG_FOOTER,
                                 false /* checkBuild */, new ImportCustomizer() {
        @Override
        public void customize(GradleImport importer) {
          importReference.set(importer);
          importer.getPathMap().put("/Library1", new File(root, library1Path));
        }
      });
    assertEquals("{/Library1=" + new File(root, library1Path).getCanonicalPath()
                 + ", /Library2=" + new File(root, "Library2").getCanonicalPath() + "}",
                 describePathMap(importReference.get()));
    deleteDir(root);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void test65167() throws Exception {
    // Regression test for https://code.google.com/p/android/issues/detail?id=65167
    Pair<File,File> pair = createLibrary2(new File("Library1"));
    File root = pair.getFirst();
    File app = pair.getSecond();

    File libs = new File(app, "libs");
    libs.mkdirs();
    new File(libs, "unknown-lib.jar").createNewFile();

    final AtomicReference<GradleImport> importReference = new AtomicReference<GradleImport>();
    File imported = checkProject(app,
                                 ""
                                  + MSG_HEADER
                                  + MSG_MANIFEST
                                  + MSG_UNHANDLED
                                  + "* .gitignore\n"
                                  + MSG_REPLACED_JARS
                                  + "guava-13.0.1.jar => com.google.guava:guava:13.0.1\n"
                                  + MSG_GUESSED_VERSIONS
                                  + "guava-13.0.1.jar => version 13.0.1 in com.google.guava:guava:13.0.1\n"
                                  + MSG_FOLDER_STRUCTURE
                                  + "In Library1:\n"
                                  + "* src/ => library1/src/main/java/\n"
                                  + "In Library2:\n"
                                  + "* src/ => library2/src/main/java/\n"
                                  + "In AndroidLibrary:\n"
                                  + "* AndroidManifest.xml => androidLibrary/src/main/AndroidManifest.xml\n"
                                  + "* src/ => androidLibrary/src/main/java/\n"
                                  + "In AndroidApp:\n"
                                  + "* AndroidManifest.xml => androidApp/src/main/AndroidManifest.xml\n"
                                  + "* libs/unknown-lib.jar => androidApp/libs/unknown-lib.jar\n"
                                  + "* res/ => androidApp/src/main/res/\n"
                                  + "* src/ => androidApp/src/main/java/\n"
                                  + MSG_FOOTER,
                                 false /* checkBuild */, new ImportCustomizer() {
        @Override
        public void customize(GradleImport importer) {
          importReference.set(importer);
        }
      });
    deleteDir(root);
    deleteDir(imported);
  }


  @SuppressWarnings({"ResultOfMethodCallIgnored", "SpellCheckingInspection"})
  public void testImportModule() throws Exception {
    // Create a Gradle project that we'll be importing a new module into
    File projectDir = createProject("test1", "test.pkg");
    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_FOLDER_STRUCTURE
                                 + DEFAULT_MOVED
                                 + MSG_FOOTER,
                                 false /* checkBuild */);
    // Pre-module import state of the Gradle project:
    assertEquals(""
                 + "app\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          pkg\n"
                 + "            MyActivity.java\n"
                 + "      res\n"
                 + "        drawable\n"
                 + "          ic_launcher.xml\n"
                 + "        values\n"
                 + "          strings.xml\n"
                 + "build.gradle\n"
                 + "import-summary.txt\n"
                 + (getTestSdkPathLocal() != null ? "local.properties\n" : "")
                 + "settings.gradle\n",
                 fileTree(imported, true));

    // Now create a second project, and import that as a *module*
    File moduleDir = createProject("test2", "test.my.pkg");
    File destDir = new File(imported, "newmodule");
    destDir.mkdirs();
    checkImport(imported, moduleDir,
                ""
                 + MSG_HEADER
                 + MSG_FOLDER_STRUCTURE
                 + "* AndroidManifest.xml => newmodule/src/main/AndroidManifest.xml\n"
                 + "* res/ => newmodule/src/main/res/\n"
                 + "* src/ => newmodule/src/main/java/\n"
                 + MSG_FOOTER,
                true /* checkBuild */, null, destDir, imported);

    // Imported contents
    assertEquals(""
                 + "build.gradle\n"
                 + "src\n"
                 + "  main\n"
                 + "    AndroidManifest.xml\n"
                 + "    java\n"
                 + "      test\n"
                 + "        my\n"
                 + "          pkg\n"
                 + "            MyActivity.java\n"
                 + "    res\n"
                 + "      drawable\n"
                 + "        ic_launcher.xml\n"
                 + "      values\n"
                 + "        strings.xml\n",
                 fileTree(destDir, true));

    // Check that it's in the right place:
    assertEquals(""
                 + "app\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          pkg\n"
                 + "            MyActivity.java\n"
                 + "      res\n"
                 + "        drawable\n"
                 + "          ic_launcher.xml\n"
                 + "        values\n"
                 + "          strings.xml\n"
                 + "build.gradle\n"
                 + "import-summary.txt\n"
                 + (getTestSdkPathLocal() != null ? "local.properties\n" : "")
                 + "newmodule\n"
                 + "  build.gradle\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          my\n"
                 + "            pkg\n"
                 + "              MyActivity.java\n"
                 + "      res\n"
                 + "        drawable\n"
                 + "          ic_launcher.xml\n"
                 + "        values\n"
                 + "          strings.xml\n"
                 + "settings.gradle\n",
                 fileTree(imported, true));

    // Make sure that settings.gradle did the right thing
    assertEquals(""
                 + "include ':app'\n"
                 + "include ':test2'\n",
                 Files.toString(new File(imported, "settings.gradle"), UTF_8)
                   .replace(NL, "\n"));

    deleteDir(moduleDir);
    deleteDir(projectDir);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testModuleNamesFromDir() throws Exception {
    // Regression test for issue where there is no .project file and
    // the project name has to be inferred from the directory name instead
    Pair<File,File> pair = createLibrary2(new File("Library1"));
    File root = pair.getFirst();
    File app = pair.getSecond();

    File propertyFile = new File(app, ".project");
    propertyFile.delete();

    final AtomicReference<GradleImport> importReference = new AtomicReference<GradleImport>();
    File imported = checkProject(app,
                                 ""
                                  + MSG_HEADER
                                  + MSG_MANIFEST
                                  + MSG_UNHANDLED
                                  + "* .gitignore\n"
                                  + MSG_REPLACED_JARS
                                  + "guava-13.0.1.jar => com.google.guava:guava:13.0.1\n"
                                  + MSG_GUESSED_VERSIONS
                                  + "guava-13.0.1.jar => version 13.0.1 in com.google.guava:guava:13.0.1\n"
                                  + MSG_FOLDER_STRUCTURE
                                  + "In Library1:\n"
                                  + "* src/ => library1/src/main/java/\n"
                                  + "In Library2:\n"
                                  + "* src/ => library2/src/main/java/\n"
                                  + "In AndroidLibrary:\n"
                                  + "* AndroidManifest.xml => androidLibrary/src/main/AndroidManifest.xml\n"
                                  + "* src/ => androidLibrary/src/main/java/\n"
                                  + "In AndroidApp:\n"
                                  + "* AndroidManifest.xml => androidApp/src/main/AndroidManifest.xml\n"
                                  + "* res/ => androidApp/src/main/res/\n"
                                  + "* src/ => androidApp/src/main/java/\n"
                                  + MSG_FOOTER,
                                 false /* checkBuild */, new ImportCustomizer() {
        @Override
        public void customize(GradleImport importer) {
          importReference.set(importer);
        }
      });
    deleteDir(root);
    deleteDir(imported);
  }

  private static String describePathMap(GradleImport importer) throws IOException {
    Map<String, File> map = importer.getPathMap();
    List<String> keys = Lists.newArrayList(map.keySet());
    Collections.sort(keys);
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    boolean first = true;
    for (String key : keys) {
      File file = map.get(key);
      if (file != null) {
        file = file.getCanonicalFile();
      }
      if (first) {
        first = false;
      } else {
        sb.append(", ");
      }
      sb.append(key);
      sb.append("=");
      sb.append(file);
    }
    sb.append("}");
    return sb.toString();
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testLibrariesWithWorkspaceMapping2() throws Exception {
    // Provide manually edited workspace location; importer reads workspace data
    // to find it
    final String library1Path = "subdir1" + separator + "subdir2" + separator +
                                "UnrelatedName";
    final File library1Dir = new File(library1Path);
    Pair<File,File> pair = createLibrary2(library1Dir);
    final File root = pair.getFirst();
    File app = pair.getSecond();
    final File library1AbsDir = new File(root, library1Path);

    final File workspace = new File(root, "workspace");
    workspace.mkdirs();
    File metadata = new File(workspace, ".metadata");
    metadata.mkdirs();
    new File(metadata, "version.ini").createNewFile();
    assertTrue(GradleImport.isEclipseWorkspaceDir(workspace));
    File projects = new File(metadata, ".plugins" + separator + "org.eclipse.core.resources" +
                                       separator + ".projects");
    projects.mkdirs();
    File library1 = new File(projects, "Library1");
    library1.mkdirs();
    File location = new File(library1, ".location");
    byte[] data = ("blahblahblahURI//" + SdkUtils.fileToUrl(library1AbsDir) +
                   "\000blahblahblah").getBytes(Charsets.UTF_8);
    Files.write(data, location);

    final AtomicReference<GradleImport> importReference = new AtomicReference<GradleImport>();
    File imported = checkProject(app,
                                 ""
                                  + MSG_HEADER
                                  + MSG_MANIFEST
                                  + MSG_UNHANDLED
                                  + "* .gitignore\n"
                                  + MSG_REPLACED_JARS
                                  + "guava-13.0.1.jar => com.google.guava:guava:13.0.1\n"
                                  + MSG_GUESSED_VERSIONS
                                  + "guava-13.0.1.jar => version 13.0.1 in com.google.guava:guava:13.0.1\n"
                                  + MSG_FOLDER_STRUCTURE
                                  + "In Library1:\n"
                                  + "* src/ => library1/src/main/java/\n"
                                  + "In Library2:\n"
                                  + "* src/ => library2/src/main/java/\n"
                                  + "In AndroidLibrary:\n"
                                  + "* AndroidManifest.xml => androidLibrary/src/main/AndroidManifest.xml\n"
                                  + "* src/ => androidLibrary/src/main/java/\n"
                                  + "In AndroidApp:\n"
                                  + "* AndroidManifest.xml => androidApp/src/main/AndroidManifest.xml\n"
                                  + "* res/ => androidApp/src/main/res/\n"
                                  + "* src/ => androidApp/src/main/java/\n"
                                  + MSG_FOOTER,
                                 false /* checkBuild */, new ImportCustomizer() {
        @Override
        public void customize(GradleImport importer) {
          importReference.set(importer);
          importer.setEclipseWorkspace(workspace);
        }
      });
    assertEquals("{/Library1=" + new File(root, library1Path).getCanonicalPath()
                 + ", /Library2=" + new File(root, "Library2").getCanonicalPath() + "}",
                 describePathMap(importReference.get()));
    deleteDir(root);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testLibrariesWithWorkspacePathVars() throws Exception {
    // Provide manually edited workspace location which contains workspace locations
    final String library1Path = "subdir1" + separator + "subdir2" + separator +
                                "UnrelatedName";
    final File library1Dir = new File(library1Path);
    Pair<File,File> pair = createLibrary2(library1Dir);
    final File root = pair.getFirst();
    File app = pair.getSecond();
    final File library1AbsDir = new File(root, library1Path);

    final File workspace = new File(root, "workspace");
    workspace.mkdirs();
    File metadata = new File(workspace, ".metadata");
    metadata.mkdirs();
    new File(metadata, "version.ini").createNewFile();
    assertTrue(GradleImport.isEclipseWorkspaceDir(workspace));
    File projects = new File(metadata, ".plugins" + separator + "org.eclipse.core.resources" +
                                       separator + ".projects");
    projects.mkdirs();
    File library1 = new File(projects, "Library1");
    library1.mkdirs();
    File location = new File(library1, ".location");
    byte[] data = ("blahblahblahURI//" + SdkUtils.fileToUrl(library1AbsDir) +
                   "\000blahblahblah").getBytes(Charsets.UTF_8);
    Files.write(data, location);

    final AtomicReference<GradleImport> importReference = new AtomicReference<GradleImport>();
    File imported = checkProject(app,
                                 ""
                                  + MSG_HEADER
                                  + MSG_MANIFEST
                                  + MSG_UNHANDLED
                                  + "* .gitignore\n"
                                  + MSG_REPLACED_JARS
                                  + "guava-13.0.1.jar => com.google.guava:guava:13.0.1\n"
                                  + MSG_GUESSED_VERSIONS
                                  + "guava-13.0.1.jar => version 13.0.1 in com.google.guava:guava:13.0.1\n"
                                  + MSG_FOLDER_STRUCTURE
                                  + "In Library1:\n"
                                  + "* src/ => library1/src/main/java/\n"
                                  + "In Library2:\n"
                                  + "* src/ => library2/src/main/java/\n"
                                  + "In AndroidLibrary:\n"
                                  + "* AndroidManifest.xml => androidLibrary/src/main/AndroidManifest.xml\n"
                                  + "* src/ => androidLibrary/src/main/java/\n"
                                  + "In AndroidApp:\n"
                                  + "* AndroidManifest.xml => androidApp/src/main/AndroidManifest.xml\n"
                                  + "* res/ => androidApp/src/main/res/\n"
                                  + "* src/ => androidApp/src/main/java/\n"
                                  + MSG_FOOTER,
                                 false /* checkBuild */, new ImportCustomizer() {
        @Override
        public void customize(GradleImport importer) {
          importReference.set(importer);
          importer.setEclipseWorkspace(workspace);
        }
      });
    assertEquals("{/Library1=" + new File(root, library1Path).getCanonicalPath()
                 + ", /Library2=" + new File(root, "Library2").getCanonicalPath() + "}",
                 describePathMap(importReference.get()));
    deleteDir(root);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testErrorHandling1() throws Exception {
    // Broken .classpath file
    File projectDir = createProject("testError1", "test.pkg");

    File classPath = new File(projectDir, ".classpath");
    Files.write(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<classpath>\n"
                + "        <classpathentry kind=\"src\" path=\"src\"/\n" // <== XML error
                + "        <classpathentry kind=\"src\" path=\"gen\"/>\n"
                + "        <classpathentry kind=\"con\" path=\"com.android.ide.eclipse.adt.ANDROID_FRAMEWORK\"/>\n"
                + "        <classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.LIBRARIES\"/>\n"
                + "        <classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.DEPENDENCIES\"/>\n"
                + "        <classpathentry kind=\"output\" path=\"bin/classes\"/>\n"
                + "</classpath>", classPath, UTF_8);

    final AtomicReference<GradleImport> importReference = new AtomicReference<GradleImport>();
    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + "\n"
                                 + " * $ROOT/.classpath:\n"
                                 + "Invalid XML file: $ROOT/.classpath:\n"
                                 + "Element type \"classpathentry\" must be followed by either attribute specifications, \">\" or \"/>\".\n"
                                 + MSG_FOOTER,
                                 false /* checkBuild */, new ImportCustomizer() {
        @Override
        public void customize(GradleImport importer) {
          importReference.set(importer);
        }
      });

    assertEquals("[$CLASSPATH_FILE:\n"
                 + "Invalid XML file: $CLASSPATH_FILE:\n"
                 + "Element type \"classpathentry\" must be followed by either attribute "
                 + "specifications, \">\" or \"/>\".]",
                 importReference.get().getErrors().toString().replace(
                   classPath.getPath(), "$CLASSPATH_FILE").
                   replace(classPath.getCanonicalPath(), "$CLASSPATH_FILE"));

    deleteDir(projectDir);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testErrorHandling2() throws Exception {
    // Broken manifest
    File root = Files.createTempDir();
    // Place project in a deep subdirectory such that it does not leave a broken
    // sibling project for other unit tests to discover as an instrumentation test
    File projectDir = new File(root, "sub1" + separator + "sub2" + separator + "sub3");
    projectDir.mkdirs();
    createProject(projectDir, "testError2", "test.pkg");

    File manifest = new File(projectDir, "AndroidManifest.xml");
    Files.write(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<x>\n", manifest, UTF_8);

    final AtomicReference<GradleImport> importReference = new AtomicReference<GradleImport>();
    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + "\n"
                                 + " * $ROOT/AndroidManifest.xml:\n"
                                 + "Invalid XML file: $ROOT/AndroidManifest.xml:\n"
                                 + "XML document structures must start and end within the same entity.\n"
                                 + MSG_FOOTER,
                                 false /* checkBuild */, new ImportCustomizer() {
        @Override
        public void customize(GradleImport importer) {
          importReference.set(importer);
        }
      });

    assertEquals("[$MANIFEST_FILE:\n"
                 + "Invalid XML file: $MANIFEST_FILE:\n"
                 + "XML document structures must start and end within the same entity.]",
                 importReference.get().getErrors().toString().replace(
                   manifest.getPath(), "$MANIFEST_FILE").
                   replace(manifest.getCanonicalPath(), "$MANIFEST_FILE"));

    deleteDir(root);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testProguardMigration() throws Exception {
    // Check that ABI libs are copied to the right place
    File projectDir = createProject("Test1", "test.pkg");
    Files.write(""
                + "proguard.config=${sdk.dir}/tools/proguard/proguard-android.txt:proguard-project.txt:proguard/proguard.pro:${user.home}/proguard/shared.pro\n"
                + "\n"
                + "# Indicates whether an apk should be generated for each density.\n"
                + "split.density=false\n"
                + "# Project target.\n"
                + "target=android-16\n",
                new File(projectDir, FN_PROJECT_PROPERTIES), UTF_8);

    File proguard = new File(projectDir, "proguard");
    proguard.mkdirs();
    Files.write(""
                + "-optimizationpasses 2\n"
                + "-dontusemixedcaseclassnames\n"
                + "-dontskipnonpubliclibraryclasses\n"
                + "-dontpreverify\n",
                new File(proguard, "proguard.pro"), UTF_8);
    new File(projectDir, "proguard-project.txt").createNewFile();

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_FOLDER_STRUCTURE
                                 + "* AndroidManifest.xml => app/src/main/AndroidManifest.xml\n"
                                 + "* proguard-project.txt => app/proguard-project.txt\n"
                                 + "* proguard/proguard.pro => app/proguard.pro\n"
                                 + "* res/ => app/src/main/res/\n"
                                 + "* src/ => app/src/main/java/\n"
                                 + MSG_USER_HOME_PROGUARD
                                 + "${user.home}/proguard/shared.pro\n"
                                 + MSG_FOOTER,
                                 false /* checkBuild */);

    // Imported contents
    assertEquals(""
                 + "app\n"
                 + "  build.gradle\n"
                 + "  proguard-project.txt\n"
                 + "  proguard.pro\n"
                 + "  src\n"
                 + "    main\n"
                 + "      AndroidManifest.xml\n"
                 + "      java\n"
                 + "        test\n"
                 + "          pkg\n"
                 + "            MyActivity.java\n"
                 + "      res\n"
                 + "        drawable\n"
                 + "          ic_launcher.xml\n"
                 + "        values\n"
                 + "          strings.xml\n"
                 + "build.gradle\n"
                 + "import-summary.txt\n"
                 + (getTestSdkPathLocal() != null ? "local.properties\n" : "")
                 + "settings.gradle\n",
                 fileTree(imported, true));

    //noinspection PointlessBooleanExpression,ConstantConditions
    assertEquals(""
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                 + "apply plugin: 'com.android.application'\n"
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                 + "\n"
                 + "android {\n"
                 + "    compileSdkVersion 16\n"
                 + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                 + "\n"
                 + "    defaultConfig {\n"
                 + "        applicationId \"test.pkg\"\n"
                 + "        minSdkVersion 8\n"
                 + "        targetSdkVersion 22\n"
                 + "    }\n"
                 + "\n"
                 + "    buildTypes {\n"
                 + "        release {\n"
                 + "            minifyEnabled true\n"
                 + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-project.txt', 'proguard.pro'\n"
                 + "        }\n"
                 + "    }\n"
                 + "}\n",
                 Files.toString(new File(imported, "app" + separator + "build.gradle"), UTF_8)
                   .replace(NL, "\n"));

    deleteDir(projectDir);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testPreviewPlatform() throws Exception {
    File root = Files.createTempDir();
    File projectDir = new File(root, "project");
    projectDir.mkdirs();
    createProject(projectDir, "Test2", "test.pkg");
    createDotProject(projectDir, "Test2", true, true);

    // Write out Manifest and project.properties files which point to L as a preview platform
    Files.write(""
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "  package=\"test.pkg\"\n"
                + "  android:versionCode=\"1\"\n"
                + "  android:versionName=\"1.0\" >\n"
                + "\n"
                + "  <uses-sdk\n"
                + "    android:minSdkVersion=\"L\"\n"
                + "    android:targetSdkVersion=\"L\" />\n"
                + "\n"
                + "  <application\n"
                + "    android:icon=\"@android:drawable/sym_def_app_icon\"\n"
                + "    android:label=\"My Unit Test Instrumentation Tests\" >\n"
                + "    <uses-library android:name=\"android.test.runner\" />\n"
                + "  </application>\n"
                + "\n"
                + "</manifest>",
                new File(projectDir, FN_ANDROID_MANIFEST_XML), UTF_8);

    Files.write("# blah blah blah\n"
                + "target=android-L\n",
                new File(projectDir, FN_PROJECT_PROPERTIES), UTF_8);

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_FOLDER_STRUCTURE
                                 + "* AndroidManifest.xml => Test2/src/main/AndroidManifest.xml\n"
                                 + "* res/ => Test2/src/main/res/\n"
                                 + "* src/ => Test2/src/main/java/\n"
                                 + MSG_FOOTER,
                                 false /* checkBuild */,
                                 new ImportCustomizer() {
                                   @Override
                                   public void customize(GradleImport importer) {
                                     importer.setGradleNameStyle(false);
                                   }
                                 });

    //noinspection PointlessBooleanExpression,ConstantConditions
    assertEquals(""
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                 + "apply plugin: 'com.android.application'\n"
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                 + "\n"
                 + "android {\n"
                 + "    compileSdkVersion 'android-L'\n"
                 + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                 + "\n"
                 + "    defaultConfig {\n"
                 + "        applicationId \"test.pkg\"\n"
                 + "        minSdkVersion 'L'\n"
                 + "        targetSdkVersion 'L'\n"
                 + "    }\n"
                 + "\n"
                 + "    buildTypes {\n"
                 + "        release {\n"
                 + "            minifyEnabled false\n"
                 + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                 + "        }\n"
                 + "    }\n"
                 + "}\n",
                 Files.toString(new File(imported, "Test2" + separator + "build.gradle"), UTF_8)
                   .replace(NL, "\n"));

    deleteDir(root);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testAddOnPlatform() throws Exception {
    File root = Files.createTempDir();
    File projectDir = new File(root, "project");
    projectDir.mkdirs();
    createProject(projectDir, "Test2", "test.pkg");
    createDotProject(projectDir, "Test2", true, true);

    // Write project.properties file which points to the add-on
    Files.write("# Project target.\n" +
                "target=Google Inc.:Google APIs:18\n",
                new File(projectDir, FN_PROJECT_PROPERTIES), UTF_8);

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_FOLDER_STRUCTURE
                                 + "* AndroidManifest.xml => Test2/src/main/AndroidManifest.xml\n"
                                 + "* res/ => Test2/src/main/res/\n"
                                 + "* src/ => Test2/src/main/java/\n"
                                 + MSG_FOOTER,
                                 false /* checkBuild */,
                                 new ImportCustomizer() {
                                   @Override
                                   public void customize(GradleImport importer) {
                                     importer.setGradleNameStyle(false);
                                   }
                                 });

    //noinspection PointlessBooleanExpression,ConstantConditions
    assertEquals(""
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                 + "apply plugin: 'com.android.application'\n"
                 + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                 + "\n"
                 + "android {\n"
                 + "    compileSdkVersion 'Google Inc.:Google APIs:18'\n"
                 + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                 + "\n"
                 + "    defaultConfig {\n"
                 + "        applicationId \"test.pkg\"\n"
                 + "        minSdkVersion 8\n"
                 + "        targetSdkVersion 22\n"
                 + "    }\n"
                 + "\n"
                 + "    buildTypes {\n"
                 + "        release {\n"
                 + "            minifyEnabled false\n"
                 + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                 + "        }\n"
                 + "    }\n"
                 + "}\n",
                 Files.toString(new File(imported, "Test2" + separator + "build.gradle"), UTF_8).replace(NL, "\n"));

    deleteDir(root);
    deleteDir(imported);
  }

  @SuppressWarnings({"ResultOfMethodCallIgnored", "SpellCheckingInspection"})
  public void testRiskyPathChars() throws Exception {
    File root = Files.createTempDir();
    File projectDir = createProject("PathChars", "test.pkg");

    final File destDir = new File(root, "My Code" + separator + "Source & Data" + separator + "Foo's Bar");
    destDir.mkdirs();

    checkProject(projectDir,
                 ""
                 + MSG_HEADER
                 + MSG_RISKY_PROJECT_LOCATION
                 + "$DESTDIR/My Code/Source & Data/Foo's Bar\n"
                 + "           -           ---        - -   \n"
                 + MSG_FOLDER_STRUCTURE
                 + "* AndroidManifest.xml => app/src/main/AndroidManifest.xml\n"
                 + "* res/ => app/src/main/res/\n"
                 + "* src/ => app/src/main/java/\n"
                 + MSG_FOOTER,
                 false /* checkBuild */, null, destDir, root);

    deleteDir(root);
  }

  @SuppressWarnings("SpellCheckingInspection")
  public void testIgnoreFile() {
    assertTrue(GradleImport.isIgnoredFile(new File(".git")));
    assertTrue(GradleImport.isIgnoredFile(new File(".hg")));
    assertTrue(GradleImport.isIgnoredFile(new File(".svn")));
    assertTrue(GradleImport.isIgnoredFile(new File("foo" + File.separator + ".git")));
    assertTrue(GradleImport.isIgnoredFile(new File("foo" + File.separator + ".hg")));
    assertTrue(GradleImport.isIgnoredFile(new File("foo" + File.separator + ".svn")));
    assertTrue(GradleImport.isIgnoredFile(new File("foo~")));
    assertFalse(GradleImport.isIgnoredFile(new File(".gitt")));
    assertFalse(GradleImport.isIgnoredFile(new File("~")));
    assertFalse(GradleImport.isIgnoredFile(new File("~foo")));
  }

  public void testSdkNdkSetters() {
    GradleImport importer = new GradleImport();
    File ndkLocation = new File("ndk");
    File sdkLocation = new File("sdk");
    importer.setNdkLocation(ndkLocation);
    importer.setSdkLocation(sdkLocation);
    assertSame(sdkLocation, importer.getSdkLocation());
    assertSame(ndkLocation, importer.getNdkLocation());
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testEncoding() throws Exception {
    // Checks that we properly convert source files from other encodings to UTF-8.
    // The following scenarios are tested:
    //  - For files that have a specific encoding associated with that file, use it
    //  - For XML files that specify an encoding in the prologue, use it
    //  - For files that have a specific encoding specified by a BOM, use it
    //  - For files that are in a project where there is a project-specific encoding, use it
    //  - For all other files, use the default encoding specified in the workspace

    File root = Files.createTempDir();
    File app = createLibrary(root, "test.lib2.pkg", false);

    // Write some source files where encoding matters
    // The Project App will have a default project encoding of MacRoman
    // The workspace will have a default encoding of windows1252
    // Some individual files will specify a file encoding of iso-8859-1

    Charset macRoman;
    Charset windows1252;
    Charset utf32;
    Charset iso8859 = Charsets.ISO_8859_1;
    try {
      macRoman = Charset.forName("MacRoman");
      windows1252 = Charset.forName("windows-1252");
      utf32 = Charset.forName("UTF_32");
    } catch (UnsupportedCharsetException uce) {
      System.err.println("This test machine does not have all the charsets we need: "
                         + uce.getCharsetName() + ": skipping test");
      return;
    }

    String java = ""
                  + "package test.pkg;\n"
                  + "\n"
                  + "public class Text {\n"
                  + "\tpublic static final String TEXT_1 = \"This is plain\";\n"
                  + "\tpublic static final String TEXT_2 = \"\u00e6\u00d8\u00e5\";\n"
                  + "\tpublic static final String TEXT_3 = \"10\u00a3\";\n"
                  + "}\n";
    String xml = ""
                 + "<?xml version=\"1.0\" encoding=\"" + windows1252.name() + "\"?>\n"
                 + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                 + "    android:layout_width=\"match_parent\"\n"
                 + "    android:layout_height=\"match_parent\"\n"
                 + "    android:orientation=\"vertical\" >\n"
                 + "<!-- \u00a3 -->\n"
                 + "</LinearLayout>";

    File appFile = new File(root, "App/src/test/pkg/Text.java".replace('/', separatorChar));
    File lib1File = new File(root, "Lib1/src/test/pkg/Text.java".replace('/', separatorChar));
    File lib2File = new File(root, "Lib2/src/test/pkg/Text.java".replace('/', separatorChar));
    File xmlFile = new File(root, "App/res/layout/foo.xml".replace('/', separatorChar));

    appFile.getParentFile().mkdirs();
    lib1File.getParentFile().mkdirs();
    lib2File.getParentFile().mkdirs();
    xmlFile.getParentFile().mkdirs();

    Files.write(java, appFile, iso8859);
    Files.write(java, lib1File, macRoman);
    Files.write(java, lib2File, windows1252);
    Files.write(xml, xmlFile, windows1252);

    assertEquals(java, Files.toString(appFile, iso8859));
    assertEquals(java, Files.toString(lib1File, macRoman));
    assertEquals(java, Files.toString(lib2File, windows1252));
    assertEquals(xml, Files.toString(xmlFile, windows1252));

    // Make sure that these contents don't happen to be the same regardless of encoding
    assertFalse(java.equals(Files.toString(appFile, UTF_8)));
    assertFalse(java.equals(Files.toString(lib1File, UTF_8)));
    assertFalse(java.equals(Files.toString(lib2File, UTF_8)));
    assertFalse(xml.equals(Files.toString(xmlFile, UTF_8)));

    // Write App project specific encoding, and file specific encoding
    File file = new File(root, "App" + separator + ".settings" + separator
                               + "org.eclipse.core.resources.prefs");
    file.getParentFile().mkdirs();
    Files.write(""
                + "eclipse.preferences.version=1\n"
                + "encoding//src/test/pkg/Text.java=" + iso8859.name() + "\n"
                + "encoding/<project>=" + macRoman.name(), file, Charsets.US_ASCII);

    // Write Lib1 project specific encoding
    file = new File(root, "Lib1" + separator + ".settings" + separator
                          + "org.eclipse.core.resources.prefs");
    file.getParentFile().mkdirs();
    Files.write(""
                + "eclipse.preferences.version=1\n"
                + "encoding/<project>=" + macRoman.name(), file, Charsets.US_ASCII);

    // Write workspace default encoding, used for the Lib2 file
    final File workspace = new File(root, "workspace");
    workspace.mkdirs();
    File metadata = new File(workspace, ".metadata");
    metadata.mkdirs();
    new File(metadata, "version.ini").createNewFile();
    assertTrue(GradleImport.isEclipseWorkspaceDir(workspace));
    File resourceFile = new File(workspace, (".metadata/.plugins/org.eclipse.core.runtime/"
                                             + ".settings/org.eclipse.core.resources.prefs").replace('/', separatorChar));
    resourceFile.getParentFile().mkdirs();
    Files.write(""
                + "eclipse.preferences.version=1\n"
                + "encoding=" + windows1252.name() + "\n"
                + "version=1", resourceFile, Charsets.US_ASCII);

    File imported = checkProject(app,
                                 ""
                                  + MSG_HEADER
                                  + MSG_MANIFEST
                                  + MSG_UNHANDLED
                                  + "From App:\n"
                                  + "* .gitignore\n"
                                  + "From JavaLib:\n"
                                  + "* .gitignore\n"
                                  + MSG_FOLDER_STRUCTURE
                                  + "In JavaLib:\n"
                                  + "* src/ => javaLib/src/main/java/\n"
                                  + "In Lib1:\n"
                                  + "* AndroidManifest.xml => lib1/src/main/AndroidManifest.xml\n"
                                  + "* src/ => lib1/src/main/java/\n"
                                  + "In Lib2:\n"
                                  + "* AndroidManifest.xml => lib2/src/main/AndroidManifest.xml\n"
                                  + "* src/ => lib2/src/main/java/\n"
                                  + "In App:\n"
                                  + "* AndroidManifest.xml => app/src/main/AndroidManifest.xml\n"
                                  + "* res/ => app/src/main/res/\n"
                                  + "* src/ => app/src/main/java/\n"
                                  + MSG_FOOTER,
                                 false /* checkBuild */, new ImportCustomizer() {
        @Override
        public void customize(GradleImport importer) {
          importer.setEclipseWorkspace(workspace);
        }
      });

    // Read back text files *as UTF-8* and make sure it's correct
    File newAppFile = new File(imported, "app/src/main/java/test/pkg/Text.java".replace('/',
                                                                                        separatorChar));
    File newLib1File = new File(imported, "lib1/src/main/java/test/pkg/Text.java".replace('/',
                                                                                          separatorChar));
    File newLib2File = new File(imported, "lib2/src/main/java/test/pkg/Text.java".replace('/',
                                                                                          separatorChar));
    File newXmlFile = new File(imported, "app/src/main/res/layout/foo.xml".replace('/',
                                                                                   separatorChar));
    assertTrue(newAppFile.exists());
    assertTrue(newLib1File.exists());
    assertTrue(newLib2File.exists());
    assertTrue(newXmlFile.exists());

    assertEquals(java, Files.toString(newAppFile, UTF_8));
    assertEquals(java, Files.toString(newLib1File, UTF_8));
    assertEquals(java, Files.toString(newLib2File, UTF_8));
    assertFalse(xml.equals(Files.toString(newXmlFile, UTF_8))); // references old encoding
    assertEquals(xml.replace(windows1252.name(), "utf-8"), Files.toString(newXmlFile, UTF_8));

    deleteDir(root);
    deleteDir(imported);
  }

  public void testIsTextFile() {
    assertTrue(GradleImport.isTextFile(new File("foo.java")));
    assertTrue(GradleImport.isTextFile(new File("foo.xml")));
    assertTrue(GradleImport.isTextFile(new File("parent" + separator + "foo.xml")));
    assertTrue(GradleImport.isTextFile(new File("parent" + separator + "foo.h")));
    assertTrue(GradleImport.isTextFile(new File("parent" + separator + "foo.c")));
    assertTrue(GradleImport.isTextFile(new File("parent" + separator + "foo.cpp")));
    assertTrue(GradleImport.isTextFile(new File("parent" + separator + "foo.properties")));
    assertTrue(GradleImport.isTextFile(new File("parent" + separator + "foo.aidl")));
    assertTrue(GradleImport.isTextFile(new File("parent" + separator + "foo.rs")));
    assertTrue(GradleImport.isTextFile(new File("parent" + separator + "foo.fs")));
    assertTrue(GradleImport.isTextFile(new File("parent" + separator + "foo.rsh")));
    assertTrue(GradleImport.isTextFile(new File("parent" + separator + "README.txt")));
    assertTrue(GradleImport.isTextFile(new File("parent" + separator + "build.gradle")));
    assertTrue(GradleImport.isTextFile(new File("parent" + separator + "proguard.cfg")));
    assertTrue(GradleImport.isTextFile(new File("parent" + separator + "optimize.pro")));

    assertFalse(GradleImport.isTextFile(new File("parent" + separator + "Foo.class")));
    assertFalse(GradleImport.isTextFile(new File("parent" + separator + "foo.jar")));
    assertFalse(GradleImport.isTextFile(new File("parent" + separator + "foo.png")));
    assertFalse(GradleImport.isTextFile(new File("parent" + separator + "foo.9.png")));
    assertFalse(GradleImport.isTextFile(new File("parent" + separator + "foo.jpg")));
    assertFalse(GradleImport.isTextFile(new File("parent" + separator + "foo.so")));
    assertFalse(GradleImport.isTextFile(new File("parent" + separator + "foo.dll")));
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testFindLatestSupportLib1() throws Exception {
    // Test that we find the latest available version of the 19.x support libraries
    File projectDir = createProject("test1", "test.pkg");

    createProjectProperties(projectDir, "android-19", null, null, null,
                            Collections.<File>emptyList());

    File libs = new File(projectDir, "libs");
    libs.mkdirs();
    new File(libs, "android-support-v4.jar").createNewFile();
    new File(libs, "android-support-v7-gridlayout.jar").createNewFile();
    new File(libs, "android-support-v7-appcompat.jar").createNewFile();

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_REPLACED_JARS
                                 + "android-support-v4.jar => com.android.support:support-v4:19.1.0\n"
                                 + "android-support-v7-appcompat.jar => com.android.support:appcompat-v7:19.1.0\n"
                                 + "android-support-v7-gridlayout.jar => com.android.support:gridlayout-v7:19.1.0\n"
                                 + MSG_FOLDER_STRUCTURE
                                 + DEFAULT_MOVED
                                 + (getTestSdkPathLocal() == null ? MSG_MISSING_REPO_1 + "null\n" + MSG_MISSING_REPO_2 : "")
                                 + MSG_FOOTER,
                                 false /* checkBuild */);

    deleteDir(projectDir);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testFindLatestSupportLib2() throws Exception {
    // Like testFindLatestSupportLib1, but uses a preview platform (L)
    // Test that we find the latest available version of the 19.x support libraries
    File projectDir = createProject("test1", "test.pkg");

    createProjectProperties(projectDir, "android-L", null, null, null,
                            Collections.<File>emptyList());

    File libs = new File(projectDir, "libs");
    libs.mkdirs();
    new File(libs, "android-support-v4.jar").createNewFile();
    new File(libs, "android-support-v7-gridlayout.jar").createNewFile();
    new File(libs, "android-support-v7-appcompat.jar").createNewFile();

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_REPLACED_JARS
                                 + "android-support-v4.jar => com.android.support:support-v4:21.0.3\n"
                                 + "android-support-v7-appcompat.jar => com.android.support:appcompat-v7:21.0.3\n"
                                 + "android-support-v7-gridlayout.jar => com.android.support:gridlayout-v7:21.0.3\n"
                                 + MSG_FOLDER_STRUCTURE
                                 + DEFAULT_MOVED
                                 + (getTestSdkPathLocal() == null ? MSG_MISSING_REPO_1 + "null\n" + MSG_MISSING_REPO_2 : "")
                                 + MSG_FOOTER,
                                 false /* checkBuild */);

    deleteDir(projectDir);
    deleteDir(imported);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testUnresolvedLibrary() throws Exception {
    // Test that we recognize and handle unresolved support libraries
    File projectDir = createProject("test1", "test.pkg");

    //noinspection ConstantConditions
    createProjectProperties(projectDir, "android-19", null, null, null,
                            Collections.<File>emptyList());
    // Append unresolved library project references
    String s = Files.toString(new File(projectDir, FN_PROJECT_PROPERTIES), Charsets.UTF_8);
    s += "\n";
    s += String.format(ANDROID_LIBRARY_REFERENCE_FORMAT, 1) + "=../appcompat_v7\n";
    s += String.format(ANDROID_LIBRARY_REFERENCE_FORMAT, 2) + "=../support-v4\n";
    //noinspection SpellCheckingInspection
    s += String.format(ANDROID_LIBRARY_REFERENCE_FORMAT, 3) + "=../extras/android/compatibility/v7/gridlayout\n";
    Files.write(s, new File(projectDir, FN_PROJECT_PROPERTIES), Charsets.UTF_8);

    File imported = checkProject(projectDir,
                                 ""
                                 + MSG_HEADER
                                 + MSG_REPLACED_LIBS
                                 + "appcompat-v7 => [com.android.support:appcompat-v7:19.1.0]\n"
                                 + "gridlayout-v7 => [com.android.support:gridlayout-v7:19.1.0]\n"
                                 + "support-v4 => [com.android.support:support-v4:19.1.0]\n"
                                 + MSG_FOLDER_STRUCTURE
                                 + DEFAULT_MOVED
                                 + (getTestSdkPathLocal() == null ? MSG_MISSING_REPO_1 + "null\n" + MSG_MISSING_REPO_2 : "")
                                 + MSG_FOOTER,
                                 false /* checkBuild */);

    deleteDir(projectDir);
    deleteDir(imported);
  }

  // --- Unit test infrastructure from this point on ----

  @SuppressWarnings({"ResultOfMethodCallIgnored", "SpellCheckingInspection"})
  private static void createEclipseSettingsFile(File prj, String languageLevel)
    throws IOException {
    File file = new File(prj, ".settings" + separator + "org.eclipse.jdt.core.prefs");
    file.getParentFile().mkdirs();
    Files.write("" +
                "eclipse.preferences.version=1\n" +
                "org.eclipse.jdt.core.compiler.codegen.inlineJsrBytecode=enabled\n" +
                "org.eclipse.jdt.core.compiler.codegen.targetPlatform=" +
                languageLevel +
                "\n" +
                "org.eclipse.jdt.core.compiler.codegen.unusedLocal=preserve\n" +
                "org.eclipse.jdt.core.compiler.compliance=" +
                languageLevel +
                "\n" +
                "org.eclipse.jdt.core.compiler.debug.lineNumber=generate\n" +
                "org.eclipse.jdt.core.compiler.debug.localVariable=generate\n" +
                "org.eclipse.jdt.core.compiler.debug.sourceFile=generate\n" +
                "org.eclipse.jdt.core.compiler.problem.assertIdentifier=error\n" +
                "org.eclipse.jdt.core.compiler.problem.enumIdentifier=error\n" +
                "org.eclipse.jdt.core.compiler.source=" +
                languageLevel, file, Charsets.UTF_8);
  }

  interface ImportCustomizer {
    void customize(GradleImport importer);
  }

  private static File checkProject(File adtProjectDir,
                                   String expectedSummary, boolean checkBuild) throws Exception {
    return checkProject(adtProjectDir, expectedSummary, checkBuild, null);
  }

  private static File checkProject(File adtProjectDir,
                                   String expectedSummary, boolean checkBuild,
                                   @Nullable ImportCustomizer customizer) throws Exception {
    File destDir = Files.createTempDir();
    return checkProject(adtProjectDir, expectedSummary, checkBuild, customizer, destDir, destDir);
  }

  private static File checkProject(File adtProjectDir,
                                   String expectedSummary, boolean checkBuild,
                                   @Nullable ImportCustomizer customizer,
                                   File destDir, File rootDir) throws Exception {
    return checkImport(null, adtProjectDir, expectedSummary, checkBuild, customizer, destDir, rootDir);
  }

  private static File checkImport(
    @Nullable File gradleProjectDir,
    @NonNull File adtProjectDir,
    @NonNull String expectedSummary,
    boolean checkBuild,
    @Nullable ImportCustomizer customizer,
    @NonNull File destDir,
    @NonNull File rootDir) throws Exception {
    assertTrue(GradleImport.isAdtProjectDir(adtProjectDir));
    List<File> projects = Collections.singletonList(adtProjectDir);
    GradleImport importer = new GradleImport();

    boolean isImport = gradleProjectDir != null;
    if (isImport) {
      importer.setImportIntoExisting(true);
    } else {
      gradleProjectDir = destDir;
    }

    String sdkPath = getTestSdkPathLocal();
    if (sdkPath != null) {
      importer.setSdkLocation(new File(sdkPath));
    }

    File wrapper = findGradleWrapper();
    if (wrapper != null) {
      importer.setGradleWrapperLocation(wrapper);
    }

    if (customizer != null) {
      customizer.customize(importer);
    }
    importer.importProjects(projects);
    importer.getSummary().setWrapErrorMessages(false);

    if (isImport) {
      Map<File,File> map = Maps.newHashMap();
      map.put(adtProjectDir, destDir);
      importer.exportIntoProject(gradleProjectDir, true, true, map);
    } else {
      importer.exportProject(destDir, false);
    }
    String summary = Files.toString(new File(gradleProjectDir, IMPORT_SUMMARY_TXT), UTF_8);
    summary = summary.replace("\r", "");
    summary = stripOutRiskyPathMessage(summary, rootDir);

    String testSdkPath = getTestSdkPathLocal();
    if (testSdkPath != null) {
      summary = summary.replace(testSdkPath, "$ADT_TEST_SDK_PATH");
    }
    summary = summary.replace(separatorChar, '/');
    summary = summary.replace(adtProjectDir.getPath().replace(separatorChar,'/'), "$ROOT");
    File parentFile = adtProjectDir.getParentFile();
    if (parentFile != null) {
      summary = summary.replace(parentFile.getPath().replace(separatorChar,'/'),
                                "$ROOT_PARENT");
    }
    assertEquals(expectedSummary, summary);

    if (checkBuild) {
      assertBuildsCleanly(gradleProjectDir, true);
    }

    return gradleProjectDir;
  }

  private static String stripOutRiskyPathMessage(String summary, File rootDir) {
    int index = summary.indexOf(MSG_RISKY_PROJECT_LOCATION);
    if (index == -1) {
      return summary;
    }
    index += MSG_RISKY_PROJECT_LOCATION.length();
    String path = rootDir.getPath();
    assertTrue(summary.startsWith(path, index));
    int nextLineIndex = summary.indexOf('\n', index) + 1;
    return summary.substring(0, index) + "$DESTDIR" +
           summary.substring(index + path.length(), nextLineIndex) +
           "        " + summary.substring(nextLineIndex + path.length());
  }

  @Nullable
  private static File findGradleWrapper() throws IOException {
    File templates = TemplateManager.getTemplateRootFolder();
    if (templates != null) {
      File wrapper = TemplateManager.getWrapperLocation(templates);
      if (wrapper.exists()) {
        return wrapper;
      }
    }

    return null;
  }

  private static boolean isWindows() {
    return SdkUtils.startsWithIgnoreCase(System.getProperty("os.name"), "windows");
  }

  public static void assertBuildsCleanly(File base, boolean allowWarnings) throws Exception {
    File gradlew = new File(base, isWindows() ? FN_GRADLE_WRAPPER_WIN : FN_GRADLE_WRAPPER_UNIX);
    if (!gradlew.exists()) {
      // Not using a wrapper; can't easily test building (we don't have a gradle prebuilt)
      return;
    }
    File pwd = base.getAbsoluteFile();
    List<String> args = Lists.newArrayList();
    args.add(gradlew.getPath());
    args.add("assembleDebug");
    GradleUtil.addLocalMavenRepoInitScriptCommandLineOption(args);
    GeneralCommandLine cmdLine = new GeneralCommandLine(args).withWorkDirectory(pwd);
    CapturingProcessHandler process = new CapturingProcessHandler(cmdLine);
    // Building currently takes about 30s, so a 5min timeout should give a safe margin.
    int timeoutInMilliseconds = 5 * 60 * 1000;
    ProcessOutput processOutput = process.runProcess(timeoutInMilliseconds, true);
    if (processOutput.isTimeout()) {
      throw new TimeoutException("\"gradlew assembleDebug\" did not terminate within test timeout value.\n" +
                                 "[stdout]\n" +
                                 processOutput.getStdout() + "\n" +
                                 "[stderr]\n" +
                                 processOutput.getStderr() + "\n");
    }
    String errors = processOutput.getStderr();
    String output = processOutput.getStdout();
    int exitCode = processOutput.getExitCode();

    int expectedExitCode = 0;
    if (output.contains("BUILD FAILED") && errors.contains(
      "Could not find any version that matches com.android.tools.build:gradle:")) {
      // We ignore this assertion. We got here because we are using a version of the
      // Android Gradle plug-in that is not available in Maven Central yet.
      expectedExitCode = 1;
    } else {
      assertTrue(output + "\n" + errors, output.contains("BUILD SUCCESSFUL"));
      if (!allowWarnings) {
        assertEquals(output + "\n" + errors, "", errors);
      }
    }
    assertEquals(expectedExitCode, exitCode);
    System.out.println("Built project successfully; output was:\n" + output);
  }

  private static String fileTree(File file, boolean includeDirs) {
    StringBuilder sb = new StringBuilder(1000);
    appendFiles(sb, includeDirs, file, 0);
    return sb.toString();
  }

  private static void appendFiles(StringBuilder sb, boolean includeDirs, File file, int depth) {
    // Skip output
    if ((depth == 1 || depth == 2) && file.getName().equals("build")) {
      return;
    }

    // Skip wrapper, since it may or may not be present for unit tests
    if (depth == 1) {
      String name = file.getName();
      if (name.equals(DOT_GRADLE)
          || name.equals(FD_GRADLE)
          || name.equals(FN_GRADLE_WRAPPER_UNIX)
          || name.equals(FN_GRADLE_WRAPPER_WIN)) {
        return;
      }
    }

    boolean isDirectory = file.isDirectory();
    if (depth > 0 && (!isDirectory || includeDirs)) {
      for (int i = 0; i < depth - 1; i++) {
        sb.append("  ");
      }
      sb.append(file.getName());
      sb.append("\n");
    }

    if (isDirectory) {
      File[] children = file.listFiles();
      if (children != null) {
        Arrays.sort(children, new Comparator<File>() {
          @Override
          public int compare(File file, File file2) {
            return file.getName().compareTo(file2.getName());
          }
        });
        for (File child : children) {
          appendFiles(sb, includeDirs, child, depth + 1);
        }
      }
    }
  }

  private static void createDotProject(
    @NonNull File projectDir,
    String name,
    boolean addAndroidNature) throws IOException {
    createDotProject(projectDir, name, addAndroidNature, addAndroidNature);
  }

  private static void createDotProject(
    @NonNull File projectDir,
    String name,
    boolean addAndroidNature, boolean addNdkNature) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
              + "<projectDescription>\n"
              + "\t<name>").append(name).append("</name>\n"
                                                + "\t<comment></comment>\n"
                                                + "\t<projects>\n"
                                                + "\t</projects>\n"
                                                + "\t<buildSpec>\n"
                                                + "\t\t<buildCommand>\n"
                                                + "\t\t\t<name>com.android.ide.eclipse.adt.ResourceManagerBuilder</name>\n"
                                                + "\t\t\t<arguments>\n"
                                                + "\t\t\t</arguments>\n"
                                                + "\t\t</buildCommand>\n"
                                                + "\t\t<buildCommand>\n"
                                                + "\t\t\t<name>com.android.ide.eclipse.adt.PreCompilerBuilder</name>\n"
                                                + "\t\t\t<arguments>\n"
                                                + "\t\t\t</arguments>\n"
                                                + "\t\t</buildCommand>\n"
                                                + "\t\t<buildCommand>\n"
                                                + "\t\t\t<name>org.eclipse.jdt.core.javabuilder</name>\n"
                                                + "\t\t\t<arguments>\n"
                                                + "\t\t\t</arguments>\n"
                                                + "\t\t</buildCommand>\n"
                                                + "\t\t<buildCommand>\n"
                                                + "\t\t\t<name>com.android.ide.eclipse.adt.ApkBuilder</name>\n"
                                                + "\t\t\t<arguments>\n"
                                                + "\t\t\t</arguments>\n"
                                                + "\t\t</buildCommand>\n"
                                                + "\t</buildSpec>\n"
                                                + "\t<natures>\n");
    if (addAndroidNature) {
      sb.append("\t\t<nature>com.android.ide.eclipse.adt.AndroidNature</nature>\n");
    }
    if (addNdkNature) {
      sb.append("\t\t<nature>org.eclipse.cdt.core.cnature</nature>\n");
      sb.append("\t\t<nature>org.eclipse.cdt.core.ccnature</nature>\n");
    }
    sb.append("\t\t<nature>org.eclipse.jdt.core.javanature</nature>\n"
              + "\t</natures>\n"
              + "</projectDescription>\n");
    Files.write(sb.toString(), new File(projectDir, ".project"), UTF_8);
  }

  private static void createClassPath(
    @NonNull File projectDir,
    @Nullable File output,
    @NonNull List<File> sources,
    @NonNull List<File> jars) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
              + "<classpath>\n");
    for (File source : sources) {
      sb.append("\t<classpathentry kind=\"src\" path=\"").append(source.getPath()).
        append("\"/>\n");
    }
    sb.append("\t<classpathentry kind=\"con\" path=\"com.android.ide.eclipse.adt.ANDROID_FRAMEWORK\"/>\n"
              + "\t<classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.LIBRARIES\"/>\n"
              + "\t<classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.DEPENDENCIES\"/>\n");
    for (File jar : jars) {
      sb.append("<classpathentry exported=\"true\" kind=\"lib\" path=\"").append(jar.getPath()).append("\"/>\n");
    }
    if (output != null) {
      sb.append("\t<classpathentry kind=\"output\" path=\"").append(output.getPath()).append("\"/>\n");
    }
    sb.append("</classpath>");
    Files.write(sb.toString(), new File(projectDir, ".classpath"), UTF_8);
  }

  private static void createProjectProperties(
    @NonNull File projectDir,
    @Nullable String target,
    Boolean mergeManifest,
    Boolean isLibrary,
    @Nullable String proguardConfig,
    @NonNull List<File> libraries) throws IOException {
    createProjectProperties(projectDir, target, mergeManifest, isLibrary, proguardConfig, libraries, true);
  }

  private static void createProjectProperties(
    @NonNull File projectDir,
    @Nullable String target,
    Boolean mergeManifest,
    Boolean isLibrary,
    @Nullable String proguardConfig,
    @NonNull List<File> libraries,
    boolean startLibrariesAt1) throws IOException {
    StringBuilder sb = new StringBuilder();

    sb.append("# This file is automatically generated by Android Tools.\n"
              + "# Do not modify this file -- YOUR CHANGES WILL BE ERASED!\n"
              + "#\n"
              + "# This file must be checked in Version Control Systems.\n"
              + "#\n"
              + "# To customize properties used by the Ant build system edit\n"
              + "# \"ant.properties\", and override values to adapt the script to your\n"
              + "# project structure.\n"
              + "#\n");
    if (proguardConfig != null) {
      sb.append("# To enable ProGuard to shrink and obfuscate your code, uncomment this "
                + "(available properties: sdk.dir, user.home):\n");
      // TODO: When using this, escape proguard properly
      sb.append(proguardConfig);
      sb.append("\n");
    }

    if (target != null) {
      String escaped = escapeProperty("target", target);
      sb.append("# Project target.\n").append(escaped).append("\n");
    }

    if (mergeManifest != null) {
      String escaped = escapeProperty("manifestmerger.enabled",
                                      Boolean.toString(mergeManifest));
      sb.append(escaped).append("\n");
    }

    if (isLibrary != null) {
      String escaped = escapeProperty("android.library", Boolean.toString(isLibrary));
      sb.append(escaped).append("\n");
    }

    for (int i = 0, n = libraries.size(); i < n; i++) {
      String path = libraries.get(i).getPath();
      // Libraries normally start at index 1, but I want to handle cases where they start
      // at 0 too so we have a test parameter for that
      int index = i + (startLibrariesAt1 ? 1 : 0);
      String escaped = escapeProperty("android.library.reference." + Integer.toString(index),
                                      path);
      sb.append(escaped).append("\n");
    }

    Files.write(sb.toString(), new File(projectDir, "project.properties"), UTF_8);
  }

  private static  String escapeProperty(@NonNull String key, @NonNull String value)
    throws IOException {
    Properties properties = new Properties();
    properties.setProperty(key, value);
    StringWriter writer = new StringWriter();
    properties.store(writer, null);
    return writer.toString();
  }

  private static void createAndroidManifest(
    @NonNull File projectDir,
    @NonNull String packageName,
    int minSdkVersion,
    int targetSdkVersion,
    @Nullable String customApplicationBlock) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
              + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
              + "    package=\"").append(packageName).append("\"\n"
              + "    android:versionCode=\"1\"\n"
              + "    android:versionName=\"1.0\" >\n"
              + "\n");
    if (minSdkVersion != -1 || targetSdkVersion != -1) {
      sb.append("    <uses-sdk\n");
      if (minSdkVersion >= 1) {
        sb.append("        android:minSdkVersion=\"8\"\n");
      }
      if (targetSdkVersion >= 1) {
        sb.append("        android:targetSdkVersion=\"22\"\n");
      }
      sb.append("     />\n");
      sb.append("\n");
    }
    if (customApplicationBlock != null) {
      sb.append(customApplicationBlock);
    } else {
      sb.append(""
                + "    <application\n"
                + "        android:allowBackup=\"true\"\n"
                + "        android:icon=\"@drawable/ic_launcher\"\n"
                + "        android:label=\"@string/app_name\"\n"
                + "    >\n"
                + "    </application>\n");
    }

    sb.append("\n"
              + "</manifest>\n");
    Files.write(sb.toString(), new File(projectDir, ANDROID_MANIFEST_XML), UTF_8);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private static File createSourceFile(@NonNull File projectDir, String relative,
                                       String contents) throws IOException {
    File file = new File(projectDir, relative.replace('/', separatorChar));
    file.getParentFile().mkdirs();
    Files.write(contents, file, UTF_8);
    return file;
  }

  private static File createSampleJavaSource(@NonNull File projectDir, String src, String pkg,
                                             String name) throws IOException {
    return createSourceFile(projectDir, src + '/' + pkg.replace('.','/') + '/' + name +
                                        DOT_JAVA, ""
                                                  + "package " + pkg + ";\n"
                                                  + "public class " + name + " {\n"
                                                  + "}\n");
  }

  private static File createSampleTimeZoneData(@NonNull File projectDir, String src) throws IOException {
    return createSourceFile(projectDir, src + "/zoneinfo-global/Pacific/Honolulu.ics", ""
                                                                  + "BEGIN:VCALENDAR\n"
                                                                  + "END:VCALENDAR\n");
  }

  private static File createSampleAidlFile(@NonNull File projectDir, String src, String pkg)
    throws IOException {
    return createSourceFile(projectDir, src + '/' + pkg.replace('.','/') +
                                        "/IHardwareService.aidl", ""
                                                                  + "package " + pkg + ";\n"
                                                                  + "\n"
                                                                  + "/** {@hide} */\n"
                                                                  + "interface IHardwareService\n"
                                                                  + "{\n"
                                                                  + "    // Vibrator support\n"
                                                                  + "    void vibrate(long milliseconds);\n"
                                                                  + "    void vibratePattern(in long[] pattern, int repeat, IBinder token);\n"
                                                                  + "    void cancelVibrate();\n"
                                                                  + "\n"
                                                                  + "    // flashlight support\n"
                                                                  + "    boolean getFlashlightEnabled();\n"
                                                                  + "    void setFlashlightEnabled(boolean on);\n"
                                                                  + "    void enableCameraFlash(int milliseconds);\n"
                                                                  + "\n"
                                                                  + "    // sets the brightness of the backlights (screen, keyboard, button) 0-255\n"
                                                                  + "    void setBacklights(int brightness);\n"
                                                                  + "\n"
                                                                  + "    // for the phone\n"
                                                                  + "    void setAttentionLight(boolean on);\n"
                                                                  + "}");
  }

  private static File createSampleRsFile(@NonNull File projectDir, String src, String pkg)
    throws IOException {
    return createSourceFile(projectDir, src + '/' + pkg.replace('.', '/') + '/' + "latency.rs",
                            ""
                            + "#pragma version(1)\n"
                            + "#pragma rs java_package_name(com.android.rs.cpptests)\n"
                            + "#pragma rs_fp_relaxed\n"
                            + "\n"
                            + "void root(const uint32_t *v_in, uint32_t *v_out) {\n"
                            + "\n"
                            + "}");
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private static void createDefaultStrings(File dir) throws IOException {
    File strings = new File(dir, "res" + separator + "values" + separator + "strings.xml");
    strings.getParentFile().mkdirs();
    Files.write(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "\n"
                + "    <string name=\"app_name\">Unit Test</string>\n"
                + "\n"
                + "</resources>", strings, UTF_8);
  }

  private static void createDefaultIcon(File dir) throws IOException {
    createIcon(dir, "ic_launcher.xml");
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private static void createIcon(File dir, String name) throws IOException {
    boolean isPng = name.toLowerCase(Locale.US).endsWith(DOT_PNG);
    String folder = isPng ? "drawable-hdpi" : "drawable";
    File icon = new File(dir, "res" + separator + folder + separator + name);
    icon.getParentFile().mkdirs();
    if (isPng) {
      //noinspection UndesirableClassUsage
      BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
      ImageIO.write(image, "PNG", icon);
    } else {
      assertTrue("Unsupported file extension for " + name, name.toLowerCase(Locale.US).endsWith(DOT_XML));
      Files.write("" +
                  "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                  "    <solid android:color=\"#00000000\"/>\n" +
                  "    <stroke android:width=\"1dp\" color=\"#ff000000\"/>\n" +
                  "    <padding android:left=\"1dp\" android:top=\"1dp\"\n" +
                  "        android:right=\"1dp\" android:bottom=\"1dp\" />\n" +
                  "</shape>", icon, UTF_8);
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private static void createLayout(File dir, String name) throws IOException {
    File strings = new File(dir, "res" + separator + "layout" + separator + name);
    strings.getParentFile().mkdirs();
    Files.write(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<merge/>\n", strings, UTF_8);
  }

  private static void deleteDir(File root) {
    if (root.exists()) {
      File[] files = root.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory()) {
            deleteDir(file);
          } else {
            boolean deleted = file.delete();
            assert deleted : file;
          }
        }
      }
      boolean deleted = root.delete();
      assert deleted : root;
    }
  }

  /** Environment variable or system property containing the full path to an SDK install */
  public static final String SDK_PATH_PROPERTY = "ADT_TEST_SDK_PATH";

  @Nullable
  protected static String getTestSdkPathLocal() {
    String override = System.getProperty(SDK_PATH_PROPERTY);
    if (override != null) {
      assertTrue(override, new File(override).exists());
      return override;
    }
    override = System.getenv(SDK_PATH_PROPERTY);
    if (override != null) {
      return override;
    }

    return null;
  }

  private static final String BUILD_TOOLS_VERSION;
  static {
    String candidate = CURRENT_BUILD_TOOLS_VERSION;
    String sdkLocation = getTestSdkPathLocal();
    if (sdkLocation != null) {
      FakeProgressIndicator progress = new FakeProgressIndicator();
      AndroidSdkHandler sdkHandler = AndroidSdkHandler.getInstance(new File(sdkLocation));
      final BuildToolInfo buildTool = sdkHandler.getLatestBuildTool(progress);
      progress.assertNoErrorsOrWarnings();
      if (buildTool != null) {
        candidate = buildTool.getRevision().toString();
      }
    }

    BUILD_TOOLS_VERSION = candidate;
  }

  private static final String DEFAULT_MOVED = ""
                                              + "* AndroidManifest.xml => app/src/main/AndroidManifest.xml\n"
                                              + "* res/ => app/src/main/res/\n"
                                              + "* src/ => app/src/main/java/\n";
}
