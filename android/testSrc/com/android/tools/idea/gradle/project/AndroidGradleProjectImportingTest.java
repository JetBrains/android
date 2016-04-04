package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.intellij.openapi.module.ModuleManager;
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class AndroidGradleProjectImportingTest extends GradleImportingTestCase {
  @Ignore("Fails with Unknown host 'maven.labs.intellij.net: unknown error'")
  @Test
  public void testJavaFacet() throws Exception {
    createProjectSubFile("settings.gradle", "rootProject.name = 'multiproject'\n" +
                                            "include ':android', ':lib'");


    createProjectSubFile("android/src/main/AndroidManifest.xml", "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                                                 "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                                                 "          package=\"org.robovm.store\">\n" +
                                                                 "\n" +
                                                                 "    <application\n" +
                                                                 "            android:allowBackup=\"true\"\n" +
                                                                 "            android:icon=\"@drawable/icon\"\n" +
                                                                 "            android:label=\"@string/app_name\"\n" +
                                                                 "            android:theme=\"@style/RoboActionBarTheme\">\n" +
                                                                 "        <activity\n" +
                                                                 "                android:name=\".StoreAppActivity\"\n" +
                                                                 "                android:label=\"@string/app_name\">\n" +
                                                                 "            <intent-filter>\n" +
                                                                 "                <action android:name=\"android.intent.action.MAIN\"/>\n" +
                                                                 "                <category android:name=\"android.intent.category.LAUNCHER\"/>\n" +
                                                                 "            </intent-filter>\n" +
                                                                 "        </activity>\n" +
                                                                 "    </application>\n" +
                                                                 "</manifest>\n");
    createProjectSubFile("android/build.gradle", "buildscript {\n" +
                                                 "    repositories {\n" +
                                                 "    maven {\n" +
                                                 "        url 'http://maven.labs.intellij.net/repo1'\n" +
                                                 "    }\n" +
                                                 "    }\n" +
                                                 "    dependencies {\n" +
                                                 "        classpath 'com.android.tools.build:gradle:1.2.3'\n" +
                                                 "    }\n" +
                                                 "}\n" +
                                                 "apply plugin: 'com.android.application'\n" +
                                                 "android {\n" +
                                                 "    compileSdkVersion 23\n" +
                                                 "    buildToolsVersion '23.0.1'\n" +
                                                "}\n");
    createProjectSubFile("lib/build.gradle", "");
    importProject();
    assertEquals(3, ModuleManager.getInstance(myProject).getModules().length);
    assertNotNull(AndroidGradleFacet.getInstance(getModule("android")));
    assertNotNull(JavaGradleFacet.getInstance(getModule("lib")));
  }

  @Override
  protected void collectAllowedRoots(List<String> roots) throws IOException {
    super.collectAllowedRoots(roots);
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @Parameterized.Parameters(name = "with Gradle-{0}")
  public static Collection<Object[]> data() throws Throwable {
    return Arrays.asList(new Object[][]{{BASE_GRADLE_VERSION}});
  }
}
