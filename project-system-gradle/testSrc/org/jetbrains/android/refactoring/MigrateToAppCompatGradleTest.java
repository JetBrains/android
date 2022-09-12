/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.refactoring;

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.adtimport.GradleImport;
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestModuleUtil;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.function.BiFunction;

import static com.android.tools.idea.testing.TestProjectPaths.ANDROIDX_SIMPLE;
import static com.android.tools.idea.testing.TestProjectPaths.MIGRATE_TO_APP_COMPAT;

/**
 * This class tests Migration to AppCompat for a Gradle project.
 * The JPS scenario is handled by {@link MigrateToAppCompatTest}.
 */
public class MigrateToAppCompatGradleTest extends AndroidGradleTestCase {

  public void testMigrationRefactoring() throws Exception {
    Ref<GoogleMavenArtifactId> ref = new Ref<>();
    loadProject(MIGRATE_TO_APP_COMPAT);
    runProcessor((artifact, version) -> {
      ref.set(artifact);
      return MigrateToAppCompatProcessor.DEFAULT_MIGRATION_FACTORY.apply(artifact, version);
    });
    assertFalse(ref.get().isAndroidxLibrary());

    GradleVersion version = GradleProjectSystemUtil.getAndroidGradleModelVersionInUse(getProject());
    String configName = GradleUtil.mapConfigurationName("implementation", version, false);
    assertEquals("apply plugin: 'com.android.application'\n" +
                 "\n" +
                 "android {\n" +
                 "    compileSdkVersion " + GradleImport.CURRENT_COMPILE_VERSION + "\n" +
                 "    namespace \"com.example.google.migrate2appcompat\"\n" +
                 "    defaultConfig {\n" +
                 "        applicationId \"com.example.google.migrate2appcompat\"\n" +
                 "        minSdkVersion 23\n" +
                 "        targetSdkVersion " + GradleImport.CURRENT_COMPILE_VERSION + "\n" +
                 "    }\n" +
                 "    buildTypes {\n" +
                 "        release {\n" +
                 "            minifyEnabled false\n" +
                 "        }\n" +
                 "    }\n" +
                 "}\n" +
                 "dependencies {\n" +
                 "    api project(':mylibrary')\n" +
                 "}\n",
                 getTextForFile("app/build.gradle"));

    // Verify that appcompat is added to the deepest Android module that is *not* a local aar module.
    assertEquals("apply plugin: 'com.android.library'\n" +
                 "\n" +
                 "android {\n" +
                 "    compileSdkVersion " + GradleImport.CURRENT_COMPILE_VERSION + "\n" +
                 "    namespace \"com.example.appandmodules.mylibarybase\"\n" +
                 "    defaultConfig {\n" +
                 "        minSdkVersion 23\n" +
                 "        targetSdkVersion " + GradleImport.CURRENT_COMPILE_VERSION + "\n" +
                 "        versionCode 1\n" +
                 "        versionName \"1.0\"\n" +
                 "    }\n" +
                 "}\n" +
                 "dependencies {\n" +
                 "    api project(':library-debug')\n" +
                 "    " + configName + " '" + getAppCompatGradleCoordinate() + "'\n" + // Unclear why the indentation does not work on added dependencies?
                 "}\n",
                 getTextForFile("mylibrarybase/build.gradle"));

    assertEquals("<resources>\n" +
                 "    <!-- Base application theme. -->\n" +
                 "    <style name=\"AppTheme\" parent=\"Theme.AppCompat.Light.DarkActionBar\">\n" +
                 "        <item name=\"colorAccent\">#ffff5722</item>\n" +
                 "        <item name=\"colorPrimary\">#ffff5722</item>\n" +
                 "        <item name=\"colorPrimaryDark\">#ffbf360c</item>\n" +
                 "        <item name=\"android:windowContentTransitions\">true</item>\n" +
                 "        <item name=\"android:windowAllowEnterTransitionOverlap\">true</item>\n" +
                 "        <item name=\"android:windowAllowReturnTransitionOverlap\">true</item>\n" +
                 "        <item name=\"android:navigationBarColor\">@android:color/darker_gray</item>\n" +
                 "        <item name=\"background\">?attr/selectableItemBackground</item>\n" +
                 "    </style>\n" +
                 "    <style name=\"ThemeOverlay.AccentSecondary\" parent=\"ThemeOverlay.AppCompat\">\n" +
                 "        <item name=\"colorAccent\">#1141caca</item>\n" +
                 "    </style>\n" +
                 "</resources>\n",
                 getTextForFile("app/src/main/res/values/styles.xml"));

    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "          xmlns:tools=\"http://schemas.android.com/tools\">\n" +
                 "\n" +
                 "    <application\n" +
                 "        android:allowBackup=\"true\"\n" +
                 "        android:label=\"@string/app_name\"\n" +
                 "        android:theme=\"@style/Theme.AppCompat\"\n" +
                 "        tools:ignore=\"GoogleAppIndexingWarning\">\n" +
                 "        <activity android:name=\".MainActivity\"\n" +
                 "            android:theme=\"@style/Theme.AppCompat.Light.DarkActionBar\">\n" +
                 "            <intent-filter>\n" +
                 "                <action android:name=\"android.intent.action.MAIN\"/>\n" +
                 "\n" +
                 "                <category android:name=\"android.intent.category.LAUNCHER\"/>\n" +
                 "            </intent-filter>\n" +
                 "        </activity>\n" +
                 "    </application>\n" +
                 "\n" +
                 "</manifest>\n",
                 getTextForFile("app/src/main/AndroidManifest.xml"));

    assertEquals("package com.example.google.migrate2appcompat;\n" +
                 "\n" +
                 "import android.content.Context;\n" +
                 "import android.support.v7.widget.AppCompatTextView;\n" +
                 "\n" +
                 "public class CustomView extends AppCompatTextView {\n" +
                 "    public CustomView(Context context) {\n" +
                 "        super(context);\n" +
                 "    }\n" +
                 "}\n",
                 getTextForFile("app/src/main/java/com/example/google/migrate2appcompat/CustomView.java"));

    assertEquals("package com.example.google.migrate2appcompat;\n" +
                 "\n" +
                 "import android.app.Activity;\n" +
                 "import android.os.Bundle;\n" +
                 "import android.support.v4.app.Fragment;\n" +
                 "import android.support.v4.app.FragmentManager;\n" +
                 "import android.support.v4.view.MenuItemCompat;\n" +
                 "import android.support.v7.app.ActionBar;\n" +
                 "import android.support.v7.app.AppCompatActivity;\n" +
                 "import android.support.v7.widget.ShareActionProvider;\n" +
                 "import android.view.Menu;\n" +
                 "\n" +
                 "public class MainActivity extends AppCompatActivity {\n" +
                 "\n" +
                 "    private ShareActionProvider mShareActionProvider;\n" +
                 "\n" +
                 "    @Override\n" +
                 "    protected void onCreate(Bundle savedInstanceState) {\n" +
                 "        super.onCreate(savedInstanceState);\n" +
                 "        setContentView(R.layout.activity_main);\n" +
                 "        ActionBar actionBar = getSupportActionBar();\n" +
                 "        FragmentManager manager = getSupportFragmentManager();\n" +
                 "    }\n" +
                 "\n" +
                 "    @Override\n" +
                 "    public boolean onCreateOptionsMenu(Menu menu) {\n" +
                 "        getMenuInflater().inflate(R.menu.main, menu);\n" +
                 "        mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(menu.findItem(R.id.share));\n" +
                 "        return true;\n" +
                 "    }\n" +
                 "\n" +
                 "    public static class MyFragment extends Fragment {\n" +
                 "        public MyFragment() {\n" +
                 "        }\n" +
                 "\n" +
                 "        @Override\n" +
                 "        public void onAttach(Activity context) {\n" +
                 "            super.onAttach(context);\n" +
                 "        }\n" +
                 "    }\n" +
                 "}\n",
                 getTextForFile("app/src/main/java/com/example/google/migrate2appcompat/MainActivity.java"));

    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<menu xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                 "    <item android:id=\"@+id/new_game\"\n" +
                 "          android:title=\"@string/item\"\n" +
                 "          app:showAsAction=\"ifRoom\"/>\n" +
                 "    <item android:id=\"@+id/share\"\n" +
                 "          android:title=\"@string/share\"\n" +
                 "          app:showAsAction=\"always\"\n" +
                 "          app:actionProviderClass=\"android.support.v7.widget.ShareActionProvider\"/>\n" +
                 "</menu>\n",
                 getTextForFile("app/src/main/res/menu/main.xml"));

    // Also checks modifying method parameters.
    assertEquals("package com.example.appandmodules.mylibrary;\n" +
                 "\n" +
                 "import android.support.v7.app.ActionBar;\n" +
                 "import android.support.v7.app.AppCompatActivity;\n" +
                 "\n" +
                 "public class MyLibraryUtility {\n" +
                 "\n" +
                 "    public static ActionBar uiFunction(AppCompatActivity activity) {\n" +
                 "        // does something with the activity\n" +
                 "        return activity.getSupportActionBar();\n" +
                 "    }\n" +
                 "}\n",
                 getTextForFile("mylibrary/src/main/java/com/example/appandmodules/mylibrary/MyLibraryUtility.java"));

    // Checks that we can migrate ActionProvider to it's support version
    assertEquals("package com.example.appandmodules.mylibarybase;\n" +
                 "\n" +
                 "import android.support.v4.view.ActionProvider;\n" +
                 "import android.support.v4.view.MenuItemCompat;\n" +
                 "import android.view.MenuItem;\n" +
                 "\n" +
                 "public class SpecialLibraryUtility {\n" +
                 "\n" +
                 "    public static ActionProvider theActionProvider(MenuItem item) {\n" +
                 "        return MenuItemCompat.getActionProvider(item);\n" +
                 "    }\n" +
                 "}\n",
                 getTextForFile("mylibrarybase/src/main/java/com/example/appandmodules/mylibarybase/SpecialLibraryUtility.java"));
  }

  /**
   * Test to ensure that with a hierarchy of modules, only the deepest module
   * is used for adding appcompat.
   * @throws Exception
   */
  public void testModulesNeedingAppCompat() throws Exception {
    loadProject(MIGRATE_TO_APP_COMPAT);
    MigrateToAppCompatProcessor processor = new MigrateToAppCompatProcessor(getProject());
    Module myLibraryBaseModule = ModuleSystemUtil.getMainModule(TestModuleUtil.findModule(getProject(), "mylibrarybase"));
    assertNotNull(myLibraryBaseModule);
    Set<Module> expected = ImmutableSet.of(myLibraryBaseModule);
    Set<Module> result = processor.computeModulesNeedingAppCompat();
    assertEquals(expected, result);
  }

  /**
   * Regression test for b/80091217
   * Ignored until the androidx artifacts are merged
   */
  public void testMigrateOnAndroidXProject() throws Exception {
    Ref<GoogleMavenArtifactId> ref = new Ref<>();
    loadProject(ANDROIDX_SIMPLE);
    runProcessor((artifact, version) -> {
      ref.set(artifact);
      return MigrateToAppCompatProcessor.DEFAULT_MIGRATION_FACTORY.apply(artifact, version);
    });

    assertTrue(ref.get().isAndroidxLibrary());
  }

  /**
   * Regression test for b/112313451
   * Test that a project with AndroidX dependencies uses the AndroidX classes.
   */
  public void testMigrateOnAndroidXProject2() throws Exception {
    final String mainActivityPath = "app/src/main/java/com/example/google/androidx/MainActivity.kt";

    loadProject(ANDROIDX_SIMPLE);
    VirtualFile mainActivityFile = PlatformTestUtil.getOrCreateProjectBaseDir(myFixture.getProject()).findFileByRelativePath(mainActivityPath);
    String mainActivityKt = getTextForFile(mainActivityPath);
    mainActivityKt = mainActivityKt.replaceAll("import androidx.appcompat.app.AppCompatActivity",
                              "import android.app.Activity");
    String finalMainActivityKt = mainActivityKt.replaceAll("AppCompatActivity",
                              "Activity");
    WriteAction.run(() -> {
      myFixture.openFileInEditor(mainActivityFile);
      Document document = myFixture.getEditor().getDocument();
      document.setText(finalMainActivityKt);
      PsiDocumentManager.getInstance(getProject()).commitDocument(document);
    });
    runProcessor(MigrateToAppCompatProcessor.DEFAULT_MIGRATION_FACTORY);
    mainActivityKt = getTextForFile(mainActivityPath);
    assertFalse(mainActivityKt.contains("import android.app.Activity"));
  }

  private void runProcessor(@NotNull BiFunction<GoogleMavenArtifactId, String, AppCompatStyleMigration> factory) {
    new MigrateToAppCompatProcessor(getProject(), factory).run();
  }

  private static String getAppCompatGradleCoordinate() {
    return RepositoryUrlManager.get().getArtifactStringCoordinate(GoogleMavenArtifactId.APP_COMPAT_V7,
                                                                  v -> v.getMajor() == Math.min(28, GradleImport.CURRENT_COMPILE_VERSION),
                                                                  false);
  }
}
