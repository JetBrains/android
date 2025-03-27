/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run;

import static com.android.tools.idea.run.AndroidRunConfiguration.LAUNCH_DEEP_LINK;
import static com.android.tools.idea.run.configuration.execution.TestUtilsKt.createApp;
import static com.android.tools.idea.util.ModuleExtensionsKt.getAndroidFacet;
import static java.lang.reflect.Modifier.isStatic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.TestUtils;
import com.android.tools.deployer.model.App;
import com.android.tools.idea.execution.common.stats.RunStats;
import com.android.tools.idea.run.activity.launch.DeepLinkLaunch;
import com.android.tools.idea.run.activity.launch.LaunchOptionState;
import com.android.tools.idea.run.editor.NoApksProvider;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.util.JDOMUtil;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.jdom.JDOMException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public class AndroidRunConfigurationTest {
  @Rule
  public AndroidProjectRule myProjectRule = AndroidProjectRule.inMemory();
  private AndroidRunConfiguration myRunConfiguration;

  private Path dataPath = TestUtils.getWorkspaceRoot().resolve("tools/adt/idea/android/testData/runConfiguration/");

  /*
   * WARNING:
   *  This maps contains serialized fields from AndroidRunConfiguration and their corresponding types.
   *
   *  Modifying the types of these fields is highly dangerous and most likely NOT INTENDED as it
   *  runs into the risk of the current version of Studio not able to read the run configuration
   *  created by a previous version of Studio.
   *
   *  Furthermore, the compatibility does not raise any exceptions and can result in strange
   *  behaviors such build steps missing.
   */
  private final Map<String, String> ANDROID_RUN_CONFIG_FIELDS = Map.ofEntries(
    Map.entry("ANDROID_RUN_CONFIGURATION_SCHEMA_VERSION", "int"),
    Map.entry("DEPLOY", "boolean"),
    Map.entry("DEPLOY_APK_FROM_BUNDLE", "boolean"),
    Map.entry("DEPLOY_AS_INSTANT", "boolean"),
    Map.entry("ARTIFACT_NAME", "class java.lang.String"),
    Map.entry("PM_INSTALL_OPTIONS", "class java.lang.String"),
    Map.entry("ALL_USERS", "boolean"),
    Map.entry("ALWAYS_INSTALL_WITH_PM", "boolean"),
    Map.entry("ALLOW_ASSUME_VERIFIED", "boolean"),
    Map.entry("CLEAR_APP_STORAGE", "boolean"),
    Map.entry("DYNAMIC_FEATURES_DISABLED_LIST", "class java.lang.String"),
    Map.entry("ACTIVITY_EXTRA_FLAGS", "class java.lang.String"),
    Map.entry("MODE", "class java.lang.String"),
    Map.entry("RESTORE_ENABLED", "boolean"),
    Map.entry("RESTORE_FILE", "class java.lang.String"),
    Map.entry("RESTORE_FRESH_INSTALL_ONLY", "boolean")
    );

  private final Map<String, String> ANDROID_RUN_CONFIG_BASE_FIELDS = Map.ofEntries(
    Map.entry("CLEAR_LOGCAT", "boolean"),
    Map.entry("SHOW_LOGCAT_AUTOMATICALLY", "boolean")
  );

  @Before
  public void setUp() throws Exception {
    ConfigurationFactory configurationFactory = AndroidRunConfigurationType.getInstance().getFactory();
    myRunConfiguration = new AndroidRunConfiguration(myProjectRule.getProject(), configurationFactory);
  }

  /**
   * Verifies that public fields, which are saved in configuration files (workspace.xml) are
   * not accidentally renamed or changed type.
   */
  @Test
  public void persistentFieldNamesAndTypes() {
    matchFields(myRunConfiguration.getClass(), ANDROID_RUN_CONFIG_FIELDS);
    matchFields(myRunConfiguration.getClass().getSuperclass(), ANDROID_RUN_CONFIG_BASE_FIELDS);
  }

  private static  <T> void matchFields(Class<T> target, Map<String, String> expectedFields) {
    for (Field field : target.getDeclaredFields()) {
      String name = field.getName();

      if (isStatic(field.getModifiers())) {
        continue;
      }

      if (!name.toUpperCase().equals(name)) {
        continue;
      }

      String actualType = field.getType().toString();
      String expectedType = expectedFields.get(field.getName());

      Assert.assertEquals(name + " should have type of " + expectedType + " instead of " + actualType,
                          expectedType, actualType);
    }

    for (String name : expectedFields.keySet()) {
      Assert.assertTrue(Arrays.stream(target.getDeclaredFields()).anyMatch(field -> field.getName().equals(name)));
    }
  }

  @Test
  public void testContributorsAmStartOptionsIsInlinedWithAmStartCommand() throws Exception {
    myRunConfiguration.setLaunchActivity("com.example.mypackage.MyActivity");

    ConsoleView consolePrinter = Mockito.mock(ConsoleView.class);
    IDevice device = Mockito.mock(IDevice.class);
    when(device.getVersion()).thenReturn(new AndroidVersion(AndroidVersion.VersionCodes.S_V2));

    final App app =
      createApp(device, "com.example.mypackage", Collections.emptyList(), Collections.singletonList("com.example.mypackage.MyActivity"));
    myRunConfiguration.launch(app,
                              device,
                              getAndroidFacet(myProjectRule.getModule()),
                              "--start-profiler file",
                              false,
                              new NoApksProvider(),
                              consolePrinter,
                              new RunStats(myProjectRule.getProject()));
    verify(device).executeShellCommand(eq("am start -n com.example.mypackage/com.example.mypackage.MyActivity " +
                                          "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER --start-profiler file"),
                                       any(), anyLong(), any());
  }

  @Test
  public void testEmptyContributorsAmStartOptions() throws Exception {
    myRunConfiguration.setLaunchActivity("com.example.mypackage.MyActivity");

    ConsoleView consolePrinter = Mockito.mock(ConsoleView.class);
    IDevice device = Mockito.mock(IDevice.class);
    when(device.getVersion()).thenReturn(new AndroidVersion(AndroidVersion.VersionCodes.S_V2));
    final App app =
      createApp(device, "com.example.mypackage", Collections.emptyList(), Collections.singletonList("com.example.mypackage.MyActivity"));
    myRunConfiguration.launch(app,
                              device,
                              getAndroidFacet(myProjectRule.getModule()),
                              "",
                              false,
                              new NoApksProvider(),
                              consolePrinter,
                              new RunStats(myProjectRule.getProject()));
    verify(device).executeShellCommand(eq("am start -n com.example.mypackage/com.example.mypackage.MyActivity " +
                 "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER"),
                 any(), anyLong(), any());

    when(device.getVersion()).thenReturn(new AndroidVersion(AndroidVersion.VersionCodes.TIRAMISU));
    myRunConfiguration.launch(app,
                              device,
                              getAndroidFacet(myProjectRule.getModule()),
                              "",
                              false,
                              new NoApksProvider(),
                              consolePrinter,
                              new RunStats(myProjectRule.getProject()));
    verify(device).executeShellCommand(eq("am start -n com.example.mypackage/com.example.mypackage.MyActivity " +
                                          "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER --splashscreen-show-icon"),
                                       any(), anyLong(), any());
  }


  @Test
  public void testDeepLinkLaunch() throws Exception {

   testDeepLink("example://host/path", "", "am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'example://host/path'");
   testDeepLink("example://host/path", "-D", "am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'example://host/path' -D");
   testDeepLink("https://example.com/example?foo=bar&baz=duck", "", "am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://example.com/example?foo=bar&baz=duck'");
   testDeepLink("text'with'single'quotes", "", "am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'text'\\''with'\\''single'\\''quotes'");
   testDeepLink("example://host/path", "", "am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'example://host/path'");

  }

  @Test
  public void testDeserialization() throws IOException, JDOMException {
    File xmlFile = dataPath.resolve("config01.xml").toFile();
    myRunConfiguration.readExternal(JDOMUtil.load(xmlFile));

    // Simple Values
    Assert.assertTrue(myRunConfiguration.DEPLOY);
    Assert.assertTrue(myRunConfiguration.DEPLOY_APK_FROM_BUNDLE);
    Assert.assertTrue(myRunConfiguration.DEPLOY_AS_INSTANT);
    Assert.assertEquals("an artifact name", myRunConfiguration.ARTIFACT_NAME);
    Assert.assertEquals("some PM install options", myRunConfiguration.PM_INSTALL_OPTIONS);
    Assert.assertTrue(myRunConfiguration.ALL_USERS);
    Assert.assertFalse(myRunConfiguration.ALWAYS_INSTALL_WITH_PM);
    Assert.assertTrue(myRunConfiguration.CLEAR_APP_STORAGE);
    Assert.assertTrue(myRunConfiguration.ALLOW_ASSUME_VERIFIED);

    // Other States
    LaunchOptionState s1 = myRunConfiguration.getLaunchOptionState(myRunConfiguration.MODE);
    Assert.assertEquals("DEFAULT_ACTIVITY", s1.getId());

    DeepLinkLaunch.State s2 = (DeepLinkLaunch.State) myRunConfiguration.getLaunchOptionState(LAUNCH_DEEP_LINK);
    Assert.assertEquals("a very deep link", s2.DEEP_LINK);
  }

  private void testDeepLink(String link, String extraFlags, String expectedCommand) throws Exception {
    ConsoleView consolePrinter = Mockito.mock(ConsoleView.class);
    IDevice device = Mockito.mock(IDevice.class);
    when(device.getVersion()).thenReturn(new AndroidVersion(AndroidVersion.VersionCodes.S_V2));
    final App app =
      createApp(device, "com.example.mypackage", Collections.emptyList(), Collections.singletonList("com.example.mypackage.MyActivity"));

    myRunConfiguration.setLaunchUrl(link);

    myRunConfiguration.launch(app,
                              device,
                              getAndroidFacet(myProjectRule.getModule()),
                              extraFlags,
                              false,
                              new NoApksProvider(),
                              consolePrinter,
                              new RunStats(myProjectRule.getProject()));
    verify(device).executeShellCommand(eq(expectedCommand), any(), anyLong(), any());
  }
}
