package org.jetbrains.jps.android;

import com.android.SdkConstants;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.io.TestFileSystemBuilder;
import com.intellij.util.io.TestFileSystemItem;
import org.jetbrains.android.util.AndroidBuildTestingManager;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.builder.*;
import org.jetbrains.jps.android.model.*;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleExtensionImpl;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.jetbrains.jps.api.BuildParametersKeys;
import org.jetbrains.jps.builders.BuildResult;
import org.jetbrains.jps.builders.CompileScopeTestBuilder;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.maven.model.impl.*;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.impl.JpsSimpleElementImpl;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.impl.JpsModuleSerializationDataExtensionImpl;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static org.jetbrains.jps.builders.CompileScopeTestBuilder.make;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidBuilderTest extends JpsBuildTestCase {

  private static final String TEST_DATA_PATH = "/jps-plugin/testData/";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(myProject).addResourcePattern("*.txt");
    myBuildParams.put(BuildParametersKeys.FORCE_MODEL_LOADING, Boolean.TRUE.toString());
  }

  public void test1() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsModule module = setUpSimpleAndroidStructure(ArrayUtil.EMPTY_STRING_ARRAY, executor, null).getFirst();
    rebuildAndroidProject();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    assertOutput(module, TestFileSystemItem.fs()
        .dir("com")
        .dir("example")
        .dir("simple")
        .file("BuildConfig.class")
        .file("R.class")
        .end()
        .end()
        .end()
        .archive("module.apk")
        .file("META-INF")
        .file("res_apk_entry", "res_apk_entry_content")
        .file("classes.dex", "classes_dex_content"));
  }

  public void test2() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsModule module = setUpSimpleAndroidStructure(new String[]{"src"}, executor, null).getFirst();
    rebuildAndroidProject();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    assertOutput(module, TestFileSystemItem.fs()
      .dir("com")
      .dir("example")
      .dir("simple")
      .file("BuildConfig.class")
      .file("R.class")
      .file("MyActivity.class")
      .end()
      .end()
      .end()
      .archive("module.apk")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content")
      .dir("lib")
      .dir("armeabi")
      .file("mylib.so", "mylib_content"));

    change(getProjectPath("src/com/example/simple/MyActivity.java"));
    executor.clear();
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    assertCompiled(JavaBuilder.BUILDER_NAME, "root/src/com/example/simple/MyActivity.java");

    checkMakeUpToDate(executor);

    change(getProjectPath("res/layout/main.xml"));
    executor.clear();
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_2");
    assertCompiled(JavaBuilder.BUILDER_NAME);

    checkMakeUpToDate(executor);

    change(getProjectPath("res/values/strings.xml"),
           "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
           "<resources>\n" +
           "    <string name=\"app_name\">changed_string</string>\n" +
           "</resources>");
    executor.clear();
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_10");
    checkMakeUpToDate(executor);

    change(getProjectPath("res/values/strings.xml"),
           "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
           "<resources>\n" +
           "    <string name=\"app_name\">changed_string</string>\n" +
           "    <string name=\"new_string\">new_string</string>\n" +
           "</resources>");
    executor.setRClassContent("public static int change = 1;");
    executor.clear();
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_3");
    assertCompiled(JavaBuilder.BUILDER_NAME, "targets/java-production/module/android/generated_sources/aapt/com/example/simple/R.java");
    checkMakeUpToDate(executor);

    FileUtil.rename(new File(getProjectPath("res/drawable-hdpi/ic_launcher.png")),
                    new File(getProjectPath("res/drawable-hdpi/new_name.png")));
    executor.setRClassContent("public static int change = 2;");
    executor.clear();
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_4");
    assertCompiled(JavaBuilder.BUILDER_NAME, "targets/java-production/module/android/generated_sources/aapt/com/example/simple/R.java");
    checkMakeUpToDate(executor);

    FileUtil.writeToFile(new File(getProjectPath("res/drawable-hdpi/new_file.png")),
                         "new_file_png_content");
    executor.setRClassContent("public static int change = 3;");
    executor.clear();
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_5");
    assertCompiled(JavaBuilder.BUILDER_NAME, "targets/java-production/module/android/generated_sources/aapt/com/example/simple/R.java");

    checkMakeUpToDate(executor);

    change(getProjectPath("libs/armeabi/mylib.so"), "mylib_content_changed");
    executor.clear();
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_11");
    assertCompiled(JavaBuilder.BUILDER_NAME);

    assertOutput(module, TestFileSystemItem.fs()
      .file("com")
      .archive("module.apk")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content")
      .dir("lib")
      .dir("armeabi")
      .file("mylib.so", "mylib_content_changed"));

    checkMakeUpToDate(executor);

    change(getProjectPath("AndroidManifest.xml"));
    executor.clear();
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_6");
    assertCompiled(JavaBuilder.BUILDER_NAME);

    checkMakeUpToDate(executor);

    delete(getProjectPath("AndroidManifest.xml"));
    copyToProject(getDefaultTestDataDirForCurrentTest() + "/changed_manifest.xml",
                  "root/AndroidManifest.xml");
    executor.clear();
    executor.setPackage("com.example.simple1");
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_7");
    assertCompiled(JavaBuilder.BUILDER_NAME,
                   "targets/java-production/module/android/generated_sources/aapt/com/example/simple1/R.java",
                   "targets/java-production/module/android/generated_sources/build_config/com/example/simple1/BuildConfig.java");

    checkMakeUpToDate(executor);

    change(getProjectPath("assets/myasset.txt"));
    executor.clear();
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_8");
    assertCompiled(JavaBuilder.BUILDER_NAME);

    checkMakeUpToDate(executor);

    FileUtil.writeToFile(new File(getProjectPath("assets/new_asset.png")),
                         "new_asset_content");
    executor.clear();
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_9");
    assertCompiled(JavaBuilder.BUILDER_NAME);
    checkMakeUpToDate(executor);

    assertOutput(module, TestFileSystemItem.fs()
      .dir("com")
      .dir("example")
      .dir("simple1")
      .file("BuildConfig.class")
      .file("R.class")
      .end()
      .dir("simple")
      .file("MyActivity.class")
      .end()
      .end()
      .end()
      .archive("module.apk")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content")
      .dir("lib")
      .dir("armeabi")
      .file("mylib.so", "mylib_content_changed"));

    assertTrue(FileUtil.delete(new File(getProjectPath("libs/armeabi/mylib.so"))));
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_12");
    checkMakeUpToDate(executor);

    assertTrue(FileUtil.delete(new File(getProjectPath("libs"))));
    rebuildAndroidProject();
    checkBuildLog(executor, "expected_log_13");
    checkMakeUpToDate(executor);
  }

  public void test3() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsModule module = setUpSimpleAndroidStructure(new String[]{"src", "resources"}, executor, null).getFirst();

    module.addSourceRoot(JpsPathUtil.pathToUrl(getProjectPath("tests")), JavaSourceRootType.TEST_SOURCE);

    final JpsLibrary lib1 = module.addModuleLibrary("lib1", JpsJavaLibraryType.INSTANCE);
    lib1.addRoot(getProjectPath("external_jar1.jar"), JpsOrderRootType.COMPILED);

    final JpsLibrary lib2 = module.addModuleLibrary("lib2", JpsJavaLibraryType.INSTANCE);
    lib2.addRoot(new File(getProjectPath("libs/external_jar2.jar")), JpsOrderRootType.COMPILED);

    module.getDependenciesList().addLibraryDependency(lib1);

    rebuildAndroidProject();
    checkBuildLog(executor, "expected_log");

    assertOutput(module, TestFileSystemItem.fs()
      .file("java_resource1.txt")
      .dir("com")
      .dir("example")
      .file("java_resource3.txt")
      .dir("simple")
      .file("BuildConfig.class")
      .file("R.class")
      .file("MyActivity.class")
      .end()
      .end()
      .end()
      .archive("module.apk")
      .file("resource_inside_jar1.txt")
      .file("java_resource1.txt")
      .dir("com")
      .dir("example")
      .file("java_resource3.txt")
      .end()
      .end()
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content"));

    checkMakeUpToDate(executor);

    module.getDependenciesList().addLibraryDependency(lib2);

    executor.clear();
    buildAndroidProject().assertSuccessful();

    checkBuildLog(executor, "expected_log_1");

    assertOutput(module, TestFileSystemItem.fs()
      .file("java_resource1.txt")
      .file("com")
      .archive("module.apk")
      .file("resource_inside_jar2.txt")
      .file("java_resource1.txt")
      .file("com")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content"));

    checkMakeUpToDate(executor);

    change(getProjectPath("resources/com/example/java_resource3.txt"));

    executor.clear();
    buildAndroidProject().assertSuccessful();

    checkBuildLog(executor, "expected_log_2");
    assertOutput(module, TestFileSystemItem.fs()
      .file("java_resource1.txt")
      .file("com")
      .archive("module.apk")
      .file("resource_inside_jar2.txt")
      .file("java_resource1.txt")
      .file("com")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content"));
    checkMakeUpToDate(executor);

    assertTrue(FileUtil.delete(new File(getProjectPath("external_jar1.jar"))));
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_3");
    assertOutput(module, TestFileSystemItem.fs()
      .file("java_resource1.txt")
      .file("com")
      .archive("module.apk")
      .file("resource_inside_jar1.txt")
      .file("resource_inside_jar2.txt")
      .file("java_resource1.txt")
      .file("com")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content"));
    checkMakeUpToDate(executor);

    assertTrue(FileUtil.delete(new File(getProjectPath("src/java_resource1.txt"))));
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_4");
    assertOutput(module, TestFileSystemItem.fs()
      .file("com")
      .archive("module.apk")
      .file("resource_inside_jar1.txt")
      .file("resource_inside_jar2.txt")
      .file("com")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content"));
    checkMakeUpToDate(executor);

    module.removeSourceRoot(JpsPathUtil.pathToUrl(getProjectPath("resources")), JavaSourceRootType.SOURCE);
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_5");
    checkMakeUpToDate(executor);
  }

  public void test4() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple") {
      @Override
      protected void doCheckJar(@NotNull String jarId, @NotNull String jarPath) {
        if ("proguard_input_jar".equals(jarId)) {
          File tmpDir = null;

          try {
            tmpDir = FileUtil.createTempDirectory("proguard_input_jar_checking", "tmp");
            final File jar = new File(tmpDir, "file.jar");
            FileUtil.copy(new File(jarPath), jar);
            assertOutput(tmpDir.getPath(), TestFileSystemItem.fs()
              .archive("file.jar")
              .dir("com")
              .dir("example")
              .dir("simple")
              .file("BuildConfig.class")
              .file("R.class")
              .file("MyActivity.class")
              .file("MyClass.class"));
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
          finally {
            if (tmpDir != null) {
              FileUtil.delete(tmpDir);
            }
          }
        }
      }
    };
    final JpsModule androidModule = setUpSimpleAndroidStructure(new String[]{"src"}, executor, "android_module").getFirst();

    final String copiedSourceRoot = copyToProject(getDefaultTestDataDirForCurrentTest() +
                                                  "/project/java_module/src", "root/java_module/src");
    final JpsModule javaModule = addModule("java_module", copiedSourceRoot);

    rebuildAndroidProject();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    androidModule.getDependenciesList().addModuleDependency(javaModule);
    buildAndroidProject();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);

    change(getProjectPath("src/com/example/simple/MyActivity.java"),
           "package com.example.simple;\n" +
           "import android.app.Activity;\n" +
           "import android.os.Bundle;\n" +
           "public class MyActivity extends Activity {\n" +
           "    @Override\n" +
           "    public void onCreate(Bundle savedInstanceState) {\n" +
           "        super.onCreate(savedInstanceState);\n" +
           "        final String s = MyClass.getMessage();\n" +
           "    }\n" +
           "}\n");
    buildAndroidProject();
    checkBuildLog(executor, "expected_log_2");
    assertCompiled(JavaBuilder.BUILDER_NAME, "root/src/com/example/simple/MyActivity.java");
    checkMakeUpToDate(executor);

    change(getProjectPath("java_module/src/com/example/simple/MyClass.java"));
    buildAndroidProject();
    checkBuildLog(executor, "expected_log_3");
    assertCompiled(JavaBuilder.BUILDER_NAME, "root/java_module/src/com/example/simple/MyClass.java");
    checkMakeUpToDate(executor);

    final String systemProguardCfgPath = FileUtil.toSystemDependentName(androidModule.getSdk(
      JpsAndroidSdkType.INSTANCE).getHomePath() + "/tools/proguard/proguard-android.txt");
    myBuildParams.put(AndroidCommonUtils.PROGUARD_CFG_PATHS_OPTION,
                      systemProguardCfgPath + File.pathSeparator + getProjectPath("proguard-project.txt"));

    buildAndroidProject();
    checkBuildLog(executor, "expected_log_4");
    assertEquals(Collections.singleton("proguard_input_jar"), executor.getCheckedJars());
    checkMakeUpToDate(executor);

    final JpsAndroidExtensionService service = JpsAndroidExtensionService.getInstance();
    final JpsAndroidDexCompilerConfiguration c = service.getDexCompilerConfiguration(myProject);
    assertNotNull(c);
    service.setDexCompilerConfiguration(myProject, c);
    c.setProguardVmOptions("-Xmx700M");
    buildAndroidProject();
    checkBuildLog(executor, "expected_log_5");
    checkMakeUpToDate(executor);
  }

  public void test5() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk = addJdkAndAndroidSdk();
    addPathPatterns(executor, androidSdk);

    final JpsModule appModule = addAndroidModule("app", new String[]{"src"}, "app", "app", androidSdk).getFirst();
    final JpsModule libModule = addAndroidModule("lib", ArrayUtil.EMPTY_STRING_ARRAY, "lib", "lib", androidSdk).getFirst();

    final JpsAndroidModuleExtension libExtension = AndroidJpsUtil.getExtension(libModule);
    assert libExtension != null;
    final JpsAndroidModuleProperties libProps = ((JpsAndroidModuleExtensionImpl)libExtension).getProperties();
    libProps.PROJECT_TYPE = PROJECT_TYPE_LIBRARY;

    rebuildAndroidProject();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    appModule.getDependenciesList().addModuleDependency(libModule);
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);

    final JpsAndroidModuleExtension appExtension = AndroidJpsUtil.getExtension(appModule);
    assert appExtension != null;
    final JpsAndroidModuleProperties appProps = ((JpsAndroidModuleExtensionImpl)appExtension).getProperties();
    appProps.myIncludeAssetsFromLibraries = true;

    buildAndroidProject();
    checkBuildLog(executor, "expected_log_2");
    checkMakeUpToDate(executor);

    rebuildAndroidProject();
    checkBuildLog(executor, "expected_log_7");
    checkMakeUpToDate(executor);

    change(getProjectPath("lib/assets/lib_asset.txt"));

    buildAndroidProject();
    checkBuildLog(executor, "expected_log_3");
    checkMakeUpToDate(executor);

    change(getProjectPath("app/assets/app_asset.txt"));

    buildAndroidProject();
    checkBuildLog(executor, "expected_log_3");
    checkMakeUpToDate(executor);

    change(getProjectPath("lib/res/values/strings.xml"));

    buildAndroidProject();
    checkBuildLog(executor, "expected_log_4");
    checkMakeUpToDate(executor);

    change(getProjectPath("app/res/values/strings.xml"));

    buildAndroidProject();
    checkBuildLog(executor, "expected_log_5");
    checkMakeUpToDate(executor);

    assertTrue(FileUtil.delete(new File(getProjectPath("lib/assets"))));

    buildAndroidProject();
    checkBuildLog(executor, "expected_log_6");
    checkMakeUpToDate(executor);
  }

  public void test6() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple") {
      @NotNull
      @Override
      protected Process doCreateProcess(@NotNull String[] args, @NotNull Map<? extends String, ? extends String> environment)
        throws Exception {
        if (args[0].endsWith(SdkConstants.FN_AAPT) && "crunch".equals(args[1])) {
          final String outputDir = args[args.length - 1];
          createTextFile(outputDir + "/drawable/ic_launcher1.png", "crunch_output_content");
          return new MyProcess(0, "", "");
        }
        return super.doCreateProcess(args, environment);
      }
    };
    setUpSimpleAndroidStructure(ArrayUtil.EMPTY_STRING_ARRAY, executor, null).getFirst();
    rebuildAndroidProject();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    assertTrue(FileUtil.delete(new File(getProjectPath("res/drawable/ic_launcher1.png"))));
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);
  }

  public void test7() throws Exception {
    final boolean[] class1Deleted = {false};

    final MyExecutor executor = new MyExecutor("com.example.simple") {
      @Override
      protected void doCheckJar(@NotNull String jarId, @NotNull String jarPath) {
        if ("library_package_jar".equals(jarId)) {
          File tmpDir = null;
          try {
            tmpDir = FileUtil.createTempDirectory("library_package_jar_checking", "tmp");
            final File jar = new File(tmpDir, "file.jar");
            FileUtil.copy(new File(jarPath), jar);
            TestFileSystemBuilder fs = TestFileSystemItem.fs()
              .archive("file.jar")
              .dir("lib")
              .file("MyLibClass.class");

            if (!class1Deleted[0]) {
              fs = fs.file("MyLibClass1.class");
            }
            assertOutput(tmpDir.getPath(), fs);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
          finally {
            if (tmpDir != null) {
              FileUtil.delete(tmpDir);
            }
          }
        }
      }
    };
    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk = addJdkAndAndroidSdk();
    addPathPatterns(executor, androidSdk);

    final JpsModule appModule = addAndroidModule("app", new String[]{"src"}, "app", "app", androidSdk).getFirst();
    final JpsModule libModule = addAndroidModule("lib", new String[]{"src"}, "lib", "lib", androidSdk).getFirst();

    final JpsAndroidModuleExtension libExtension = AndroidJpsUtil.getExtension(libModule);
    assert libExtension != null;
    final JpsAndroidModuleProperties libProps = ((JpsAndroidModuleExtensionImpl)libExtension).getProperties();
    libProps.PROJECT_TYPE = PROJECT_TYPE_LIBRARY;

    rebuildAndroidProject();
    checkBuildLog(executor, "expected_log");
    assertEquals(Collections.singleton("library_package_jar"), executor.getCheckedJars());
    checkMakeUpToDate(executor);

    appModule.getDependenciesList().addModuleDependency(libModule);
    buildAndroidProject();
    checkBuildLog(executor, "expected_log_1");
    assertTrue(executor.getCheckedJars().isEmpty());
    checkMakeUpToDate(executor);

    change(getProjectPath("lib/src/lib/MyLibClass.java"));
    buildAndroidProject();
    checkBuildLog(executor, "expected_log_2");
    assertEquals(Collections.singleton("library_package_jar"), executor.getCheckedJars());
    checkMakeUpToDate(executor);

    assertTrue(FileUtil.delete(new File(getProjectPath("lib/src/lib/MyLibClass1.java"))));
    class1Deleted[0] = true;
    buildAndroidProject();
    checkBuildLog(executor, "expected_log_2");
    assertEquals(Collections.singleton("library_package_jar"), executor.getCheckedJars());
    checkMakeUpToDate(executor);

    assertTrue(FileUtil.delete(new File(getProjectPath("lib/src/lib/MyLibClass.java"))));
    buildAndroidProject();
    checkBuildLog(executor, "expected_log_3");
    assertTrue(executor.getCheckedJars().isEmpty());
    checkMakeUpToDate(executor);

    final JpsLibrary appLib = appModule.addModuleLibrary("appLib", JpsJavaLibraryType.INSTANCE);
    appLib.addRoot(getProjectPath("lib/external_jar.jar"), JpsOrderRootType.COMPILED);
    appModule.getDependenciesList().addLibraryDependency(appLib);

    final JpsLibrary libLib = libModule.addModuleLibrary("libLib", JpsJavaLibraryType.INSTANCE);
    libLib.addRoot(new File(getProjectPath("lib/external_jar.jar")), JpsOrderRootType.COMPILED);
    libModule.getDependenciesList().addLibraryDependency(libLib);

    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_4");
    assertTrue(executor.getCheckedJars().isEmpty());
    checkMakeUpToDate(executor);
  }

  public void test8() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    setUpSimpleAndroidStructure(new String[]{"src"}, executor, null).getFirst();
    rebuildAndroidProject();
    checkMakeUpToDate(executor);

    myBuildParams.put(AndroidCommonUtils.RELEASE_BUILD_OPTION, Boolean.TRUE.toString());
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    myBuildParams.put(AndroidCommonUtils.RELEASE_BUILD_OPTION, Boolean.FALSE.toString());
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);

    myBuildParams.remove(AndroidCommonUtils.RELEASE_BUILD_OPTION);
    checkMakeUpToDate(executor);
  }

  public void test9() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk = addJdkAndAndroidSdk();
    addPathPatterns(executor, androidSdk);

    final JpsModule appModule1 = addAndroidModule("app1", new String[]{"src"}, "app1", "app1", androidSdk).getFirst();
    final JpsModule appModule2 = addAndroidModule("app2", new String[]{"src"}, "app2", "app2", androidSdk).getFirst();
    final JpsModule libModule = addAndroidModule("lib", new String[]{"src"}, "lib", "lib", androidSdk).getFirst();

    final JpsAndroidModuleExtension libExtension = AndroidJpsUtil.getExtension(libModule);
    assert libExtension != null;
    final JpsAndroidModuleProperties libProps = ((JpsAndroidModuleExtensionImpl)libExtension).getProperties();
    libProps.PROJECT_TYPE = PROJECT_TYPE_LIBRARY;

    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log");
    assertEquals(1, executor.getCheckedJars().size());
    checkMakeUpToDate(executor);

    appModule1.getDependenciesList().addModuleDependency(libModule);

    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    assertTrue(executor.getCheckedJars().isEmpty());
    checkMakeUpToDate(executor);

    appModule2.getDependenciesList().addModuleDependency(libModule);

    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_2");
    assertTrue(executor.getCheckedJars().isEmpty());
    checkMakeUpToDate(executor);

    final JpsLibrary appLib = appModule1.addModuleLibrary("appLib1", JpsJavaLibraryType.INSTANCE);
    appLib.addRoot(getProjectPath("lib/external_jar.jar"), JpsOrderRootType.COMPILED);
    appModule1.getDependenciesList().addLibraryDependency(appLib);

    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_3");
    assertTrue(executor.getCheckedJars().isEmpty());
    checkMakeUpToDate(executor);

    final JpsLibrary libLib = appModule2.addModuleLibrary("appLib2", JpsJavaLibraryType.INSTANCE);
    libLib.addRoot(new File(getProjectPath("lib/external_jar.jar")), JpsOrderRootType.COMPILED);
    appModule2.getDependenciesList().addLibraryDependency(libLib);

    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_4");
    assertTrue(executor.getCheckedJars().isEmpty());
    checkMakeUpToDate(executor);
  }

  public void testResOverlay() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsModule module = setUpSimpleAndroidStructure(ArrayUtil.EMPTY_STRING_ARRAY, executor, null).getFirst();
    final JpsAndroidModuleProperties props = ((JpsAndroidModuleExtensionImpl)AndroidJpsUtil.getExtension(module)).getProperties();
    props.RES_OVERLAY_FOLDERS = Arrays.asList("/res-overlay");
    rebuildAndroidProject();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    props.RES_OVERLAY_FOLDERS = Collections.emptyList();
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
  }

  public void testChangeDexSettings() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    setUpSimpleAndroidStructure(new String[]{"src"}, executor, null).getFirst();
    rebuildAndroidProject();
    checkMakeUpToDate(executor);

    final JpsAndroidExtensionService service = JpsAndroidExtensionService.getInstance();
    final JpsAndroidDexCompilerConfiguration c = service.getDexCompilerConfiguration(myProject);
    assertNotNull(c);
    service.setDexCompilerConfiguration(myProject, c);

    c.setVmOptions("-Xms64m");
    buildAndroidProject();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    c.setMaxHeapSize(512);
    buildAndroidProject();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);

    c.setOptimize(false);
    buildAndroidProject();
    checkBuildLog(executor, "expected_log_2");
    checkMakeUpToDate(executor);

    c.setForceJumbo(true);
    buildAndroidProject();
    checkBuildLog(executor, "expected_log_3");
    checkMakeUpToDate(executor);

    c.setCoreLibrary(true);
    buildAndroidProject();
    checkBuildLog(executor, "expected_log_4");
    checkMakeUpToDate(executor);
  }

  public void testFilteredResources() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsModule module = setUpSimpleAndroidStructure(new String[]{"src"}, executor, null).getFirst();
    final JpsAndroidModuleProperties props = ((JpsAndroidModuleExtensionImpl)AndroidJpsUtil.getExtension(module)).getProperties();

    rebuildAndroidProject();
    checkMakeUpToDate(executor);

    props.USE_CUSTOM_APK_RESOURCE_FOLDER = true;
    props.CUSTOM_APK_RESOURCE_FOLDER = "/target/filtered-res";
    buildAndroidProject();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    change(getProjectPath("target/filtered-res/values/strings.xml"));
    buildAndroidProject();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);
  }

  public void testCustomManifestPackage() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsModule module = setUpSimpleAndroidStructure(new String[]{"src"}, executor, null, "8").getFirst();
    rebuildAndroidProject();
    checkMakeUpToDate(executor);

    final JpsAndroidModuleExtensionImpl extension =
      (JpsAndroidModuleExtensionImpl)AndroidJpsUtil.getExtension(module);
    assert extension != null;
    final JpsAndroidModuleProperties props = extension.getProperties();

    props.CUSTOM_MANIFEST_PACKAGE = "dev";
    checkMakeUpToDate(executor);

    props.USE_CUSTOM_MANIFEST_PACKAGE = true;
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    props.CUSTOM_MANIFEST_PACKAGE = "dev1";
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);
  }

  public void testAdditionalParameters() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsModule module = setUpSimpleAndroidStructure(new String[]{"src"}, executor, null, "8").getFirst();
    rebuildAndroidProject();
    checkMakeUpToDate(executor);

    final JpsAndroidModuleExtensionImpl extension =
      (JpsAndroidModuleExtensionImpl)AndroidJpsUtil.getExtension(module);
    assert extension != null;
    final JpsAndroidModuleProperties props = extension.getProperties();

    props.ADDITIONAL_PACKAGING_COMMAND_LINE_PARAMETERS = "-0 xml";
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    props.ADDITIONAL_PACKAGING_COMMAND_LINE_PARAMETERS = "-0 txt";
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);
  }

  public void testGeneratedSources() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsModule module = setUpSimpleAndroidStructure(new String[]{"src", "gen"}, executor, null).getFirst();

    rebuildAndroidProject();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    change(getProjectPath("gen/com/example/simple/R.java"),
           AndroidCommonUtils.AUTOGENERATED_JAVA_FILE_HEADER + "\n\n" +
           "package com.example.simple;\n" +
           "public class R {}");

    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    assertCompiled(JavaBuilder.BUILDER_NAME,
                   "targets/java-production/module/android/copied_sources/com/example/simple/MyGeneratedClass.java");
    checkMakeUpToDate(executor);
    change(getProjectPath("gen/com/example/simple/R.java"));

    checkMakeUpToDate(executor);
    change(getProjectPath("gen/com/example/simple/MyGeneratedClass.java"));

    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_3");
    assertCompiled(JavaBuilder.BUILDER_NAME,
                   "targets/java-production/module/android/copied_sources/com/example/simple/MyGeneratedClass.java");
    checkMakeUpToDate(executor);

    change(getProjectPath("gen/com/example/simple/MyGeneratedClass.java"),
           AndroidCommonUtils.AUTOGENERATED_JAVA_FILE_HEADER + "\n\n" +
           "package com.example.simple;\n" +
           "public class MyGeneratedClass {}");

    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_4");
    assertCompiled(JavaBuilder.BUILDER_NAME);
    checkMakeUpToDate(executor);

    change(getProjectPath("gen/com/example/simple/MyGeneratedClass.java"),
           "package com.example.simple;\n" +
           "public class MyGeneratedClass {}");

    change(getProjectPath("src/com/example/simple/MyActivity.java"),
           "package com.example.simple;\n" +
           "import android.app.Activity;\n" +
           "import android.os.Bundle;\n" +
           "public class MyActivity extends Activity {\n" +
           "    public void onCreate(Bundle savedInstanceState) {\n" +
           "        super.onCreate(savedInstanceState);\n" +
           "        new MyGeneratedClass();" +
           "    }\n" +
           "}\n");

    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_5");
    assertCompiled(JavaBuilder.BUILDER_NAME,
                   "root/src/com/example/simple/MyActivity.java",
                   "targets/java-production/module/android/copied_sources/com/example/simple/MyGeneratedClass.java");
    checkMakeUpToDate(executor);

    final JpsJavaCompilerConfiguration compilerConfig = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(myProject);
    final ProcessorConfigProfile profile = compilerConfig.getAnnotationProcessingProfile(module);
    profile.setEnabled(true);
    profile.setOutputRelativeToContentRoot(true);
    profile.setGeneratedSourcesDirectoryName("gen", false);
    final BuildResult result = buildAndroidProject();
    result.assertFailed();
    final List<BuildMessage> warnMessages = result.getMessages(BuildMessage.Kind.WARNING);
    boolean containsForciblyExcludedRootWarn = false;

    for (BuildMessage message : warnMessages) {
      if (message.getMessageText().endsWith("was forcibly excluded by the IDE, so custom generated files won't be compiled")) {
        containsForciblyExcludedRootWarn = true;
      }
    }
    assertTrue(containsForciblyExcludedRootWarn);
  }

  public void testManifestMerging() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk = addJdkAndAndroidSdk();
    addPathPatterns(executor, androidSdk);

    final JpsModule appModule = addAndroidModule("app", new String[]{"src"}, "app", "app", androidSdk).getFirst();
    final JpsModule libModule = addAndroidModule("lib", ArrayUtil.EMPTY_STRING_ARRAY, "lib", "lib", androidSdk).getFirst();

    final JpsAndroidModuleExtension libExtension = AndroidJpsUtil.getExtension(libModule);
    assert libExtension != null;
    final JpsAndroidModuleProperties libProps = ((JpsAndroidModuleExtensionImpl)libExtension).getProperties();
    libProps.PROJECT_TYPE = PROJECT_TYPE_LIBRARY;

    appModule.getDependenciesList().addModuleDependency(libModule);

    final JpsAndroidModuleExtension appExtension = AndroidJpsUtil.getExtension(appModule);
    assert appExtension != null;
    final JpsAndroidModuleProperties appProps = ((JpsAndroidModuleExtensionImpl)appExtension).getProperties();

    appProps.ENABLE_MANIFEST_MERGING = true;
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    appProps.ENABLE_MANIFEST_MERGING = false;
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);

    appProps.ENABLE_MANIFEST_MERGING = true;
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_2");
    checkMakeUpToDate(executor);

    change(getProjectPath("app/AndroidManifest.xml"));
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_3");
    checkMakeUpToDate(executor);

    change(getProjectPath("lib/AndroidManifest.xml"));
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_4");
    checkMakeUpToDate(executor);
  }

  public void testMaven() throws Exception {
    createMavenConfigFile();
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk = addJdkAndAndroidSdk();
    addPathPatterns(executor, androidSdk);

    final JpsModule appModule = addAndroidModule("app", new String[]{"src"}, "app", "app", androidSdk).getFirst();
    final JpsModule libModule = addAndroidModule("lib2", new String[]{"src"}, "lib", "lib2", androidSdk).getFirst();
    final JpsModule libModule1 = addAndroidModule("lib1", new String[]{"src"}, "lib1", "lib1", androidSdk).getFirst();

    JpsMavenExtensionService.getInstance().getOrCreateExtension(appModule);
    final MavenProjectConfiguration mavenConf = ((JpsMavenExtensionServiceImpl)JpsMavenExtensionService.
      getInstance()).getMavenProjectConfiguration(myDataStorageRoot);
    addMavenResourcesConf(mavenConf, "app");
    addMavenResourcesConf(mavenConf, "lib2");
    addMavenResourcesConf(mavenConf, "lib1");

    final JpsAndroidModuleExtension libExtension = AndroidJpsUtil.getExtension(libModule);
    assert libExtension != null;
    final JpsAndroidModuleProperties libProps = ((JpsAndroidModuleExtensionImpl)libExtension).getProperties();
    libProps.PROJECT_TYPE = PROJECT_TYPE_LIBRARY;

    final JpsAndroidModuleExtension libExtension1 = AndroidJpsUtil.getExtension(libModule1);
    assert libExtension1 != null;
    final JpsAndroidModuleProperties libProps1 = ((JpsAndroidModuleExtensionImpl)libExtension1).getProperties();
    libProps1.PROJECT_TYPE = PROJECT_TYPE_LIBRARY;

    appModule.getDependenciesList().addModuleDependency(libModule);
    libModule.getDependenciesList().addModuleDependency(libModule1);

    rebuildAndroidProject();
    checkBuildLog(executor, "expected_log");

    assertOutput(appModule, TestFileSystemItem.fs()
      .file("com")
      .archive("app.apk")
      .dir("lib")
      .file("lib_resource.txt")
      .end()
      .dir("com")
      .file("app_resource.txt")
      .end()
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content"));
    checkMakeUpToDate(executor);

    JpsMavenExtensionService.getInstance().getOrCreateExtension(libModule);
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);

    final JpsModule nonMavenAppModule = addAndroidModule("non_maven_app", new String[]{"src"},
                                                         "app", "non_maven_app", androidSdk).getFirst();
    nonMavenAppModule.getDependenciesList().addModuleDependency(libModule);

    final JpsModule libModule2 = addAndroidModule("lib3", new String[]{"src"}, "lib1", "lib3", androidSdk).getFirst();
    final JpsAndroidModuleExtension libExtension2 = AndroidJpsUtil.getExtension(libModule2);
    assert libExtension2 != null;
    final JpsAndroidModuleProperties libProps2 = ((JpsAndroidModuleExtensionImpl)libExtension2).getProperties();
    libProps2.PROJECT_TYPE = PROJECT_TYPE_LIBRARY;
    libModule1.getDependenciesList().addModuleDependency(libModule2);

    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_2");
  }

  private void createMavenConfigFile() throws IOException {
    final File file = new File(myDataStorageRoot, MavenProjectConfiguration.CONFIGURATION_FILE_RELATIVE_PATH);

    if (!file.exists()) {
      FileUtil.writeToFile(file, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                 "<maven-project-configuration></maven-project-configuration>");
    }
  }

  public void testMaven1() throws Exception {
    createMavenConfigFile();
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk = addJdkAndAndroidSdk();
    addPathPatterns(executor, androidSdk);

    copyToProject(getDefaultTestDataDirForCurrentTest() + "/project/myaar", "root/myaar");
    final JpsModule appModule = addAndroidModule("app", new String[]{"src"}, "app", "app", androidSdk).getFirst();
    final JpsModule libModule = addAndroidModule("lib", new String[]{"src"}, "lib", "lib", androidSdk).getFirst();

    JpsMavenExtensionService.getInstance().getOrCreateExtension(appModule);
    final MavenProjectConfiguration mavenConf = ((JpsMavenExtensionServiceImpl)JpsMavenExtensionService.
      getInstance()).getMavenProjectConfiguration(myDataStorageRoot);
    addMavenResourcesConf(mavenConf, "app");
    addMavenResourcesConf(mavenConf, "lib");

    final JpsAndroidModuleExtension libExtension = AndroidJpsUtil.getExtension(libModule);
    assert libExtension != null;
    final JpsAndroidModuleProperties libProps = ((JpsAndroidModuleExtensionImpl)libExtension).getProperties();
    libProps.PROJECT_TYPE = PROJECT_TYPE_LIBRARY;
    appModule.getDependenciesList().addModuleDependency(libModule);

    rebuildAndroidProject();
    checkMakeUpToDate(executor);

    final JpsLibrary appAarLib = appModule.addModuleLibrary("app_arr", JpsJavaLibraryType.INSTANCE);
    appAarLib.addRoot(getProjectPath("myaar/classes.jar"), JpsOrderRootType.COMPILED);
    appModule.getDependenciesList().addLibraryDependency(appAarLib);
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    appAarLib.addRoot(getProjectPath("myaar/res"), JpsOrderRootType.COMPILED);
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);

    final JpsLibrary libAarLib = libModule.addModuleLibrary("lib_arr", JpsJavaLibraryType.INSTANCE);
    libAarLib.addRoot(getProjectPath("myaar/classes.jar"), JpsOrderRootType.COMPILED);
    libAarLib.addRoot(getProjectPath("myaar/res"), JpsOrderRootType.COMPILED);
    libModule.getDependenciesList().addLibraryDependency(libAarLib);
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_2");
    checkMakeUpToDate(executor);

    final JpsAndroidModuleExtension appExtension = AndroidJpsUtil.getExtension(appModule);
    final JpsAndroidModuleProperties appProps = ((JpsAndroidModuleExtensionImpl)appExtension).getProperties();
    appProps.myIncludeAssetsFromLibraries = true;
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_3");
    checkMakeUpToDate(executor);

    appAarLib.addRoot(getProjectPath("myaar/libs/myjar.jar"), JpsOrderRootType.COMPILED);
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_4");
    checkMakeUpToDate(executor);
  }

  private void addMavenResourcesConf(MavenProjectConfiguration mavenConf, String appNames) {
    final MavenModuleResourceConfiguration appMavenConf = new MavenModuleResourceConfiguration();
    appMavenConf.id = new MavenIdBean("com.text", appNames, "1");
    appMavenConf.directory = appNames;
    appMavenConf.delimitersPattern = "";
    final ResourceRootConfiguration appResConf = new ResourceRootConfiguration();
    appResConf.directory = getProjectPath(appNames + "/src");
    appMavenConf.resources.add(appResConf);
    mavenConf.moduleConfigurations.put(appNames, appMavenConf);
  }

  public void testProGuardWithJar() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple") {
      @Override
      protected void doCheckJar(@NotNull String jarId, @NotNull String jarPath) {
        if ("proguard_input_jar".equals(jarId)) {
          File tmpDir = null;

          try {
            tmpDir = FileUtil.createTempDirectory("proguard_input_jar_checking", "tmp");
            final File jar = new File(tmpDir, "file.jar");
            FileUtil.copy(new File(jarPath), jar);
            assertOutput(tmpDir.getPath(), TestFileSystemItem.fs()
              .archive("file.jar")
              .dir("com")
              .dir("example")
              .dir("simple")
              .file("BuildConfig.class")
              .file("R.class")
              .file("MyActivity.class"));
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
          finally {
            if (tmpDir != null) {
              FileUtil.delete(tmpDir);
            }
          }
        }
      }
    };
    final JpsModule module = setUpSimpleAndroidStructure(new String[]{"src"}, executor, null).getFirst();

    module.addSourceRoot(JpsPathUtil.pathToUrl(getProjectPath("tests")), JavaSourceRootType.TEST_SOURCE);

    final JpsLibrary lib = module.addModuleLibrary("lib", JpsJavaLibraryType.INSTANCE);
    lib.addRoot(new File(getProjectPath("libs/external_jar.jar")), JpsOrderRootType.COMPILED);
    module.getDependenciesList().addLibraryDependency(lib);

    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
    assert extension instanceof JpsAndroidModuleExtensionImpl;
    final JpsAndroidModuleProperties properties = ((JpsAndroidModuleExtensionImpl)extension).getProperties();
    assert properties != null;
    properties.RUN_PROGUARD = true;
    properties.myProGuardCfgFiles = Arrays.asList(
      "file://%MODULE_SDK_HOME%/tools/proguard/proguard-android.txt",
      VfsUtilCore.pathToUrl(getProjectPath("proguard-project.txt")));
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log");

    checkMakeUpToDate(executor);
    doBuild(CompileScopeTestBuilder.rebuild().allModules().targetTypes(
      AndroidManifestMergingTarget.MyTargetType.INSTANCE,
      AndroidDexBuildTarget.MyTargetType.INSTANCE,
      AndroidResourceCachingBuildTarget.MyTargetType.INSTANCE,
      AndroidResourcePackagingBuildTarget.MyTargetType.INSTANCE,
      AndroidPackagingBuildTarget.MyTargetType.INSTANCE,
      AndroidLibraryPackagingTarget.MyTargetType.INSTANCE,
      AndroidPackagingBuildTarget.MyTargetType.INSTANCE)).assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
  }

  public void testPreDexing() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");

    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk = addJdkAndAndroidSdk();
    addPathPatterns(executor, androidSdk);
    final JpsModule appModule = addAndroidModule("app", new String[]{"src"}, "app", "app", androidSdk).getFirst();
    final JpsModule libModule = addAndroidModule("lib2", new String[]{"src"}, "lib", "lib2", androidSdk).getFirst();
    final JpsModule libModule1 = addAndroidModule("lib1", new String[]{"src"}, "lib1", "lib1", androidSdk).getFirst();

    final JpsAndroidModuleExtension libExtension = AndroidJpsUtil.getExtension(libModule);
    assert libExtension != null;
    final JpsAndroidModuleProperties libProps = ((JpsAndroidModuleExtensionImpl)libExtension).getProperties();
    libProps.PROJECT_TYPE = PROJECT_TYPE_LIBRARY;

    final JpsAndroidModuleExtension libExtension1 = AndroidJpsUtil.getExtension(libModule1);
    assert libExtension1 != null;
    final JpsAndroidModuleProperties libProps1 = ((JpsAndroidModuleExtensionImpl)libExtension1).getProperties();
    libProps1.PROJECT_TYPE = PROJECT_TYPE_LIBRARY;

    appModule.getDependenciesList().addModuleDependency(libModule);
    libModule.getDependenciesList().addModuleDependency(libModule1);

    final JpsLibrary lib = appModule.addModuleLibrary("ext_lib", JpsJavaLibraryType.INSTANCE);
    lib.addRoot(new File(getProjectPath("app/libs/external_jar.jar")), JpsOrderRootType.COMPILED);
    appModule.getDependenciesList().addLibraryDependency(lib);

    final JpsLibrary lib1 = appModule.addModuleLibrary("ext_lib_1", JpsJavaLibraryType.INSTANCE);
    lib1.addRoot(new File(getProjectPath("lib/libs/external_jar_1.jar")), JpsOrderRootType.COMPILED);
    libModule.getDependenciesList().addLibraryDependency(lib1);

    doBuild(CompileScopeTestBuilder.rebuild().allModules().targetTypes(
      AndroidManifestMergingTarget.MyTargetType.INSTANCE,
      AndroidDexBuildTarget.MyTargetType.INSTANCE,
      AndroidResourceCachingBuildTarget.MyTargetType.INSTANCE,
      AndroidResourcePackagingBuildTarget.MyTargetType.INSTANCE,
      AndroidPackagingBuildTarget.MyTargetType.INSTANCE,
      AndroidLibraryPackagingTarget.MyTargetType.INSTANCE,
      AndroidPackagingBuildTarget.MyTargetType.INSTANCE)).assertSuccessful();
    checkBuildLog(executor, "expected_log");

    executor.clear();
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");

    executor.clear();
    doBuild(CompileScopeTestBuilder.make().allModules().targetTypes(
      AndroidManifestMergingTarget.MyTargetType.INSTANCE,
      AndroidDexBuildTarget.MyTargetType.INSTANCE,
      AndroidResourceCachingBuildTarget.MyTargetType.INSTANCE,
      AndroidResourcePackagingBuildTarget.MyTargetType.INSTANCE,
      AndroidPackagingBuildTarget.MyTargetType.INSTANCE,
      AndroidLibraryPackagingTarget.MyTargetType.INSTANCE,
      AndroidPackagingBuildTarget.MyTargetType.INSTANCE)).assertUpToDate();

    executor.clear();
    rebuildAndroidProject();
    checkBuildLog(executor, "expected_log_2");

    final JpsAndroidModuleExtension appExtension = AndroidJpsUtil.getExtension(appModule);
    assert appExtension != null;
    final JpsAndroidModuleProperties appProps = ((JpsAndroidModuleExtensionImpl)appExtension).getProperties();

    checkMakeUpToDate(executor);
    appProps.ENABLE_PRE_DEXING = false;
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_3");

    checkMakeUpToDate(executor);
    appProps.ENABLE_PRE_DEXING = true;
    buildAndroidProject().assertSuccessful();
    checkBuildLog(executor, "expected_log_4");
  }

  private void checkMakeUpToDate(MyExecutor executor) {
    executor.clear();
    buildAndroidProject().assertUpToDate();
    assertEquals("", executor.getLog());
  }

  private BuildResult buildAndroidProject() {
    return doBuild(addAllAndroidTargets(make()));
  }

  private static CompileScopeTestBuilder addAllAndroidTargets(CompileScopeTestBuilder originalScope) {
    return originalScope.allModules().allArtifacts().targetTypes(AndroidManifestMergingTarget.MyTargetType.INSTANCE,
                                                                 AndroidDexBuildTarget.MyTargetType.INSTANCE,
                                                                 AndroidResourceCachingBuildTarget.MyTargetType.INSTANCE,
                                                                 AndroidResourcePackagingBuildTarget.MyTargetType.INSTANCE,
                                                                 AndroidPackagingBuildTarget.MyTargetType.INSTANCE,
                                                                 AndroidLibraryPackagingTarget.MyTargetType.INSTANCE,
                                                                 AndroidPackagingBuildTarget.MyTargetType.INSTANCE);
  }

  private void rebuildAndroidProject() {
    doBuild(addAllAndroidTargets(CompileScopeTestBuilder.rebuild())).assertSuccessful();
  }

  private String getProjectPath(String relativePath) {
    return getAbsolutePath("root/" + relativePath);
  }

  private void checkBuildLog(MyExecutor executor, String expectedLogFile) throws IOException {
    final File file = findFindUnderProjectHome(getTestDataDirForCurrentTest(getTestName(true)) +
                                               "/" + expectedLogFile + ".txt");
    final String text = FileUtil.loadFile(file, true);
    assertEquals(AndroidBuildTestingCommandExecutor.normalizeExpectedLog(text, executor.getLog()),
                 AndroidBuildTestingCommandExecutor.normalizeLog(executor.getLog()));
  }

  private Pair<JpsModule, Path> setUpSimpleAndroidStructure(String[] sourceRoots, MyExecutor executor, String contentRootDir) {
    return setUpSimpleAndroidStructure(sourceRoots, executor, contentRootDir, getTestName(true));
  }

  private Pair<JpsModule, Path> setUpSimpleAndroidStructure(String[] sourceRoots,
                                                            MyExecutor executor,
                                                            String contentRootDir,
                                                            String testDirName) {
    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk = addJdkAndAndroidSdk();
    addPathPatterns(executor, androidSdk);
    return addAndroidModule("module", sourceRoots, contentRootDir, null, androidSdk, testDirName);
  }

  private void addPathPatterns(MyExecutor executor, JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk) {
    final String tempDirectory = FileUtilRt.getTempDirectory();

    executor.addPathPrefix("PROJECT_DIR", getOrCreateProjectDir().getPath());
    executor.addPathPrefix("ANDROID_SDK_DIR", androidSdk.getHomePath());
    executor.addPathPrefix("DATA_STORAGE_ROOT", myDataStorageRoot.getPath());
    executor.addRegexPathPatternPrefix("AAPT_OUTPUT_TMP", FileUtil.toSystemIndependentName(tempDirectory) + "/android_apt_output\\d*tmp");
    executor.addRegexPathPatternPrefix("COMBINED_ASSETS_TMP", FileUtil.toSystemIndependentName(tempDirectory) +
                                                              "/android_combined_assets\\d*tmp");
    executor.addRegexPathPatternPrefix("COMBINED_RESOURCES_TMP", FileUtil.toSystemIndependentName(tempDirectory) +
                                                              "/android_combined_resources\\d*tmp");
    executor.addRegexPathPatternPrefix("CLASSPATH_TMP", FileUtil.toSystemIndependentName(tempDirectory) + "/classpath\\d*\\.jar");
    executor.addRegexPathPattern("JAVA_PATH", ".*/java");
    executor.addRegexPathPattern("IDEA_RT_PATH", ".*/idea_rt.jar");
    executor.addRegexPathPattern("PROGUARD_INPUT_JAR", ".*/proguard_input\\S*\\.jar");

    // for running on buildserver
    executor.addRegexPathPattern("IDEA_RT_PATH", ".*/production/java-runtime");

    AndroidBuildTestingManager.startBuildTesting(executor);
  }

  private Pair<JpsModule, Path> addAndroidModule(String moduleName,
                                                 String[] sourceRoots,
                                                 String contentRootDir,
                                                 String dstContentRootDir,
                                                 JpsSdk<? extends JpsElement> androidSdk) {
    return addAndroidModule(moduleName, sourceRoots, contentRootDir,
                            dstContentRootDir, androidSdk, getTestName(true));
  }

  private Pair<JpsModule, Path> addAndroidModule(String moduleName,
                                                 String[] sourceRoots,
                                                 String contentRootDir,
                                                 String dstContentRootDir,
                                                 JpsSdk<? extends JpsElement> androidSdk,
                                                 String testDirName) {
    final String testDataRoot = getTestDataDirForCurrentTest(testDirName);
    final String projectRoot = testDataRoot + "/project";
    final String moduleContentRoot = contentRootDir != null
                                     ? new File(projectRoot, contentRootDir).getPath()
                                     : projectRoot;
    final String dstRoot = dstContentRootDir != null ? "root/" + dstContentRootDir : "root";
    final String root = copyToProject(moduleContentRoot, dstRoot);
    final String outputPath = getAbsolutePath("out/production/" + moduleName);
    final String testOutputPath = getAbsolutePath("out/test/" + moduleName);

    final JpsModule module = addModule(moduleName, ArrayUtil.EMPTY_STRING_ARRAY,
                                       outputPath, testOutputPath, androidSdk);
    module.getContentRootsList().addUrl(JpsPathUtil.pathToUrl(root));

    for (String sourceRoot : sourceRoots) {
      final String sourceRootName = new File(sourceRoot).getName();
      final String copiedSourceRoot = copyToProject(moduleContentRoot + "/" + sourceRoot, dstRoot + "/" + sourceRootName);
      module.addSourceRoot(JpsPathUtil.pathToUrl(copiedSourceRoot), JavaSourceRootType.SOURCE);
    }
    final JpsAndroidModuleProperties properties = new JpsAndroidModuleProperties();

    properties.MANIFEST_FILE_RELATIVE_PATH = "/AndroidManifest.xml";
    properties.RES_FOLDER_RELATIVE_PATH = "/res";
    properties.ASSETS_FOLDER_RELATIVE_PATH = "/assets";
    properties.LIBS_FOLDER_RELATIVE_PATH = "/libs";
    properties.GEN_FOLDER_RELATIVE_PATH_APT = "/gen";
    properties.GEN_FOLDER_RELATIVE_PATH_AIDL = "/gen";
    properties.PACK_TEST_CODE = false;

    module.getContainer().setChild(JpsModuleSerializationDataExtensionImpl.ROLE,
                                   new JpsModuleSerializationDataExtensionImpl(Paths.get(root)));
    module.getContainer().setChild(JpsAndroidModuleExtensionImpl.KIND, new JpsAndroidModuleExtensionImpl(properties));
    return Pair.create(module, Paths.get(root));
  }

  private String getDefaultTestDataDirForCurrentTest() {
    return getTestDataDirForCurrentTest(getTestName(true));
  }

  private static String getTestDataDirForCurrentTest(String testDirName) {
    return TEST_DATA_PATH + testDirName;
  }

  private JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> addJdkAndAndroidSdk() {
    final String jdkName = "java_sdk";
    addJdk(jdkName);
    final JpsAndroidSdkProperties properties = new JpsAndroidSdkProperties("android-17", jdkName);
    final String sdkPath = getAndroidHomePath() + "/testData/sdk1.5";

    final JpsTypedLibrary<JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>>> library =
      myModel.getGlobal().addSdk("android_sdk", sdkPath, "", JpsAndroidSdkType.INSTANCE,
                                 new JpsSimpleElementImpl<>(properties));
    library.addRoot(new File(sdkPath + "/platforms/android-1.5/android.jar"), JpsOrderRootType.COMPILED);
    //library.addRoot(new File(getAndroidHomePath() + "/testData/android.jar"), JpsOrderRootType.COMPILED);
    return library.getProperties();
  }

  @Override
  protected File findFindUnderProjectHome(String relativePath) {
    final String homePath = getAndroidHomePath();
    final File file = new File(homePath, FileUtil.toSystemDependentName(relativePath));

    if (!file.exists()) {
      throw new IllegalArgumentException("Cannot find file '" + relativePath + "' under '" + homePath + "' directory");
    }
    return file;
  }

  @NotNull
  private static String getAndroidHomePath() {
    final String androidHomePath = System.getProperty("android.home.path");

    if (androidHomePath != null) {
      return androidHomePath;
    }

    String adtPath = PathManager.getHomePath() + "/../adt/idea/android";
    if (new File(adtPath).exists()) {
      return adtPath;
    }

    return PathManagerEx.findFileUnderCommunityHome("android/android").getPath();
  }

  private static void createTextFile(@NotNull String path, @NotNull String text) throws IOException {
    final File f = new File(path);
    final File parent = f.getParentFile();

    if (!parent.exists() && !parent.mkdirs()) {
      throw new IOException("Cannot create directory " + parent.getPath());
    }
    final OutputStream stream = new FileOutputStream(f);

    try {
      stream.write(text.getBytes(Charset.defaultCharset()));
    }
    finally {
      stream.close();
    }
  }

  @SuppressWarnings("SSBasedInspection")
  public static class MyExecutor extends AndroidBuildTestingCommandExecutor {

    private String myRClassContent = "";

    private String myPackage;

    public MyExecutor(String aPackage) {
      myPackage = aPackage;
    }

    void setPackage(String aPackage) {
      myPackage = aPackage;
    }

    void setRClassContent(String RClassContent) {
      myRClassContent = RClassContent;
    }

    @NotNull
    @Override
    protected Process doCreateProcess(@NotNull String[] args, @NotNull Map<? extends String, ? extends String> environment)
      throws Exception {
      final int idx = ArrayUtilRt.find(args, "org.jetbrains.android.compiler.tools.AndroidDxRunner");

      if (idx >= 0) {
        final String outputPath = args[idx + 2];
        createTextFile(outputPath, "classes_dex_content");
        return new MyProcess(0, "", "");
      }

      if (args[0].endsWith(SdkConstants.FN_AAPT)) {
        if ("package".equals(args[1])) {
          if ("-m".equals(args[2])) {
            final String outputDir = getAaptOutputDirFromArgs(args);
            if (outputDir != null) {
              createTextFile(outputDir + "/" + myPackage.replace('.', '/') + "/R.java",
                             "package " + myPackage + ";\n" +
                             "public class R {" + myRClassContent + "}");

              if ("-G".equals(args[args.length - 2])) {
                createTextFile(args[args.length - 1], "generated_proguard_file_by_aapt");
              }
            }
          }
          else if ("-S".equals(args[2])) {
            final String outputPath = args[args.length - 1];
            final ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputPath)));

            try {
              appendEntry(zos, "res_apk_entry", "res_apk_entry_content".getBytes());
            }
            finally {
              zos.close();
            }
          }
        }
      }
      return new MyProcess(0, "", "");
    }

    private static String getAaptOutputDirFromArgs(@NotNull String[] args) {
      for (int i = 0; i < args.length; i++) {
        final String arg = args[i];
        if ("-J".equals(arg) && i + 1 < args.length) {
          return args[i + 1];
        }
      }
      return null;
    }

    private static void appendEntry(ZipOutputStream zos, String name, byte[] content) throws Exception {
      final ZipEntry e = new ZipEntry(name);
      e.setMethod(ZipEntry.STORED);
      e.setSize(content.length);
      CRC32 crc = new CRC32();
      crc.update(content);
      e.setCrc(crc.getValue());
      zos.putNextEntry(e);
      zos.write(content, 0, content.length);
      zos.closeEntry();
    }
  }
}
