package org.jetbrains.jps.android;

import com.android.SdkConstants;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.io.TestFileSystemItem;
import org.jetbrains.android.util.AndroidBuildTestingManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.JpsAndroidDexCompilerConfiguration;
import org.jetbrains.jps.android.model.JpsAndroidExtensionService;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.android.model.JpsAndroidSdkType;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleExtensionImpl;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.impl.JpsSimpleElementImpl;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.impl.JpsModuleSerializationDataExtensionImpl;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidBuilderTest extends JpsBuildTestCase {

  private static final String TEST_DATA_PATH = "/jps-plugin/testData/";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(myProject).addResourcePattern("*.txt");
  }

  public void test1() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsModule module = setUpAndroidModule(ArrayUtil.EMPTY_STRING_ARRAY, executor).getFirst();
    rebuildAll();
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
        .file("module.afp.apk")
        .archive("module.apk")
        .file("META-INF")
        .file("res_apk_entry", "res_apk_entry_content")
        .file("classes.dex", "classes_dex_content"));
  }

  public void test2() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsModule module = setUpAndroidModule(new String[]{"src"}, executor).getFirst();
    rebuildAll();
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
      .file("module.afp.apk")
      .archive("module.apk")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content")
      .dir("lib")
      .dir("armeabi")
      .file("mylib.so", "mylib_content"));

    change(getModulePath("src/com/example/simple/MyActivity.java"));
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    assertCompiled(JavaBuilder.BUILDER_NAME, "root/src/com/example/simple/MyActivity.java");

    checkMakeUpToDate(executor);

    change(getModulePath("res/layout/main.xml"));
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_2");
    assertCompiled(JavaBuilder.BUILDER_NAME);

    checkMakeUpToDate(executor);

    change(getModulePath("res/values/strings.xml"),
           "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
           "<resources>\n" +
           "    <string name=\"app_name\">changed_string</string>\n" +
           "</resources>");
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_10");
    checkMakeUpToDate(executor);

    change(getModulePath("res/values/strings.xml"),
           "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
           "<resources>\n" +
           "    <string name=\"app_name\">changed_string</string>\n" +
           "    <string name=\"new_string\">new_string</string>\n" +
           "</resources>");
    executor.setRClassContent("public static int change = 1;");
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_3");
    assertCompiled(JavaBuilder.BUILDER_NAME, "android/generated_sources/module/aapt/com/example/simple/R.java");
    checkMakeUpToDate(executor);

    FileUtil.rename(new File(getModulePath("res/drawable-hdpi/ic_launcher.png")),
                    new File(getModulePath("res/drawable-hdpi/new_name.png")));
    executor.setRClassContent("public static int change = 2;");
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_4");
    assertCompiled(JavaBuilder.BUILDER_NAME, "android/generated_sources/module/aapt/com/example/simple/R.java");
    checkMakeUpToDate(executor);

    FileUtil.writeToFile(new File(getModulePath("res/drawable-hdpi/new_file.png")),
                         "new_file_png_content");
    executor.setRClassContent("public static int change = 3;");
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_5");
    assertCompiled(JavaBuilder.BUILDER_NAME, "android/generated_sources/module/aapt/com/example/simple/R.java");

    checkMakeUpToDate(executor);

    change(getModulePath("libs/armeabi/mylib.so"), "mylib_content_changed");
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_11");
    assertCompiled(JavaBuilder.BUILDER_NAME);

    assertOutput(module, TestFileSystemItem.fs()
      .file("com")
      .file("module.afp.apk")
      .archive("module.apk")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content")
      .dir("lib")
      .dir("armeabi")
      .file("mylib.so", "mylib_content_changed"));

    checkMakeUpToDate(executor);

    change(getModulePath("AndroidManifest.xml"));
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_6");
    assertCompiled(JavaBuilder.BUILDER_NAME);

    checkMakeUpToDate(executor);

    delete(getModulePath("AndroidManifest.xml"));
    copyToProject(getTestDataDirForCurrentTest() + "/changed_manifest.xml",
                  "root/AndroidManifest.xml");
    executor.clear();
    executor.setPackage("com.example.simple1");
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_7");
    assertCompiled(JavaBuilder.BUILDER_NAME,
                   "android/generated_sources/module/aapt/com/example/simple1/R.java",
                   "android/generated_sources/module/build_config/com/example/simple1/BuildConfig.java");

    checkMakeUpToDate(executor);

    change(getModulePath("assets/myasset.txt"));
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_8");
    assertCompiled(JavaBuilder.BUILDER_NAME);

    checkMakeUpToDate(executor);

    FileUtil.writeToFile(new File(getModulePath("assets/new_asset.png")),
                         "new_asset_content");
    executor.clear();
    makeAll().assertSuccessful();
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
      .file("module.afp.apk")
      .archive("module.apk")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content")
      .dir("lib")
      .dir("armeabi")
      .file("mylib.so", "mylib_content_changed"));
  }

  private void checkMakeUpToDate(MyExecutor executor) {
    executor.clear();
    makeAll().assertUpToDate();
    assertEquals("", executor.getLog());
  }

  public void test3() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsModule module = setUpAndroidModule(new String[]{"src", "resources"}, executor).getFirst();

    module.addSourceRoot(JpsPathUtil.pathToUrl(getModulePath("tests")), JavaSourceRootType.TEST_SOURCE);

    final JpsLibrary lib1 = module.addModuleLibrary("lib1", JpsJavaLibraryType.INSTANCE);
    lib1.addRoot(getModulePath("external_jar1.jar"), JpsOrderRootType.COMPILED);

    final JpsLibrary lib2 = module.addModuleLibrary("lib2", JpsJavaLibraryType.INSTANCE);
    lib2.addRoot(new File(getModulePath("libs/external_jar2.jar")), JpsOrderRootType.COMPILED);

    module.getDependenciesList().addLibraryDependency(lib1);

    rebuildAll();
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
      .file("module.afp.apk")
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
    makeAll().assertSuccessful();

    checkBuildLog(executor, "expected_log_1");

    assertOutput(module, TestFileSystemItem.fs()
      .file("java_resource1.txt")
      .file("com")
      .file("module.afp.apk")
      .archive("module.apk")
      .file("resource_inside_jar2.txt")
      .file("java_resource1.txt")
      .file("com")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content"));

    checkMakeUpToDate(executor);

    change(getModulePath("resources/com/example/java_resource3.txt"));

    executor.clear();
    makeAll().assertSuccessful();

    checkBuildLog(executor, "expected_log_2");
    assertOutput(module, TestFileSystemItem.fs()
      .file("java_resource1.txt")
      .file("com")
      .file("module.afp.apk")
      .archive("module.apk")
      .file("resource_inside_jar2.txt")
      .file("java_resource1.txt")
      .file("com")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content"));
  }

  public void testChangeDexSettings() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    setUpAndroidModule(new String[]{"src"}, executor).getFirst();
    rebuildAll();
    checkMakeUpToDate(executor);

    final JpsAndroidExtensionService service = JpsAndroidExtensionService.getInstance();
    final JpsAndroidDexCompilerConfiguration c = service.getDexCompilerConfiguration(myProject);
    assertNotNull(c);
    service.setDexCompilerConfiguration(myProject, c);

    c.setVmOptions("-Xms64m");
    makeAll();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    c.setMaxHeapSize(512);
    makeAll();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);

    c.setOptimize(false);
    makeAll();
    checkBuildLog(executor, "expected_log_2");
    checkMakeUpToDate(executor);
  }

  private String getModulePath(String relativePath) {
    return getAbsolutePath("root/" + relativePath);
  }

  private void checkBuildLog(MyExecutor executor, String expectedLogFile) throws IOException {
    final File file = findFindUnderProjectHome(getTestDataDirForCurrentTest() +
                                               "/" + expectedLogFile + ".txt");
    final String text = FileUtil.loadFile(file, true);
    assertEquals(text, executor.getLog());
  }

  private Pair<JpsModule, File> setUpAndroidModule(String[] sourceRoots, MyExecutor executor) {
    final String moduleName = "module";
    final String testDataRoot = getTestDataDirForCurrentTest();
    final String projectRoot = testDataRoot + "/project";
    final String root = copyToProject(projectRoot, "root");
    final String outputPath = getAbsolutePath("out/production/" + moduleName);
    final String testOutputPath = getAbsolutePath("out/test/" + moduleName);

    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk = addJdkAndAndroidSdk();
    final JpsModule module = addModule(moduleName, ArrayUtil.EMPTY_STRING_ARRAY,
                                       outputPath, testOutputPath, androidSdk);
    module.getContentRootsList().addUrl(JpsPathUtil.pathToUrl(root));

    for (String sourceRoot : sourceRoots) {
      final String sourceRootName = new File(sourceRoot).getName();
      final String copiedSourceRoot = copyToProject(projectRoot + "/" + sourceRoot, "root/" + sourceRootName);
      module.addSourceRoot(JpsPathUtil.pathToUrl(copiedSourceRoot), JavaSourceRootType.SOURCE);
    }
    final JpsAndroidModuleProperties properties = new JpsAndroidModuleProperties();

    properties.MANIFEST_FILE_RELATIVE_PATH = "/AndroidManifest.xml";
    properties.RES_FOLDER_RELATIVE_PATH = "/res";
    properties.ASSETS_FOLDER_RELATIVE_PATH = "/assets";
    properties.LIBS_FOLDER_RELATIVE_PATH = "/libs";
    properties.PACK_TEST_CODE = false;

    module.getContainer().setChild(JpsModuleSerializationDataExtensionImpl.ROLE,
                                   new JpsModuleSerializationDataExtensionImpl(new File(root)));
    module.getContainer().setChild(JpsAndroidModuleExtensionImpl.KIND, new JpsAndroidModuleExtensionImpl(properties));
    final String tempDirectory = FileUtilRt.getTempDirectory();

    executor.addPathPrefix("PROJECT_DIR", getOrCreateProjectDir().getPath());
    executor.addPathPrefix("ANDROID_SDK_DIR", androidSdk.getHomePath());
    executor.addPathPrefix("DATA_STORAGE_ROOT", myDataStorageRoot.getPath());
    executor.addRegexPathPrefix("AAPT_OUTPUT_TMP", FileUtil.toSystemIndependentName(tempDirectory) + "/android_apt_output\\d+tmp");
    executor.addRegexPathPrefix("CLASSPATH_TMP", FileUtil.toSystemIndependentName(tempDirectory) + "/classpath\\d+\\.tmp");
    executor.addRegexPathPrefix("JAVA_PATH", ".*/java");
    executor.addRegexPathPrefix("IDEA_RT_PATH", ".*/idea_rt.jar");
    AndroidBuildTestingManager.startBuildTesting(executor);
    return Pair.create(module, new File(root));
  }

  private String getTestDataDirForCurrentTest() {
    return TEST_DATA_PATH + getTestName(true);
  }

  private JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> addJdkAndAndroidSdk() {
    final String jdkName = "java_sdk";
    addJdk(jdkName);
    final JpsAndroidSdkProperties properties = new JpsAndroidSdkProperties("android-17", jdkName);
    final String sdkPath = getAndroidHomePath() + "/testData/sdk1.5";

    final JpsTypedLibrary<JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>>> library =
      myModel.getGlobal().addSdk("android_sdk", sdkPath, "", JpsAndroidSdkType.INSTANCE,
                                 new JpsSimpleElementImpl<JpsAndroidSdkProperties>(properties));
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
    return new File(PathManager.getHomePath(), "android/android").getPath();
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
  private static class MyExecutor extends AndroidBuildTestingCommandExecutor {

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
            final String outputDir = args[4];
            createTextFile(outputDir + "/" + myPackage.replace('.', '/') + "/R.java",
                           "package " + myPackage + ";\n" +
                           "public class R {" + myRClassContent + "}");
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
        else if ("crunch".equals(args[1])) {
          // resource caching, do nothing
        }
      }
      return new MyProcess(0, "", "");
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
