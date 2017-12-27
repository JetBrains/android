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
package com.android.tools.idea.templates;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.internal.androidTarget.MockPlatformTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.TestUtils;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.template.ConvertJavaToKotlinDefaultImpl;
import com.android.tools.idea.npw.template.ConvertJavaToKotlinProvider;
import com.android.tools.idea.npw.template.TemplateValueInjector;
import com.intellij.mock.MockApplicationEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.extensions.Extensions.getRootArea;
import static org.mockito.MockitoAnnotations.initMocks;

public class TemplateValueInjectorTest{

  private static int PREVIEW_VERSION = 104;

  @Mock
  private AndroidVersionsInfo myMockAndroidVersionsInfo;
  private Disposable myDisposable;

  @Before
  public void setUp() throws Exception {
    initMocks(this);

    myDisposable = Disposer.newDisposable();
    MockApplicationEx instance = new MockApplicationEx(myDisposable);
    instance.registerService(EmbeddedDistributionPaths.class, new EmbeddedDistributionPaths());
    ApplicationManager.setApplication(instance, myDisposable);

    String kotlinEpName = ConvertJavaToKotlinProvider.EP_NAME.getName();
    if (!getRootArea().hasExtensionPoint(kotlinEpName)) {
      getRootArea().registerExtensionPoint(kotlinEpName, ConvertJavaToKotlinDefaultImpl.class.getName());
      Disposer.register(myDisposable, () -> getRootArea().unregisterExtensionPoint(kotlinEpName));
    }
  }

  @After
  public void tearDown() throws Exception {
    Disposer.dispose(myDisposable);
  }

  @Test
  public void toolsBuildVersionInTemplates() {
    MockFileOp fop = new MockFileOp();
    recordBuildTool26rc1(fop);
    AndroidSdkHandler sdkHandler =
      new AndroidSdkHandler(new File("/sdk"), null, fop);
    FakeProgressIndicator progress = new FakeProgressIndicator();
    final BuildToolInfo buildToolInfo = sdkHandler.getBuildToolInfo(new Revision(26, 0, 0, 1), progress);
    progress.assertNoErrors();

    MockPlatformTarget androidTarget = new MockPlatformTarget(PREVIEW_VERSION, 0) {
      @NonNull
      @Override
      public AndroidVersion getVersion() {
        return new AndroidVersion(PREVIEW_VERSION - 1, "TEST_CODENAME");
      }
      @Override
      public BuildToolInfo getBuildToolInfo() {
        return buildToolInfo;
      }
    };

    AndroidVersionsInfo.VersionItem versionItem = myMockAndroidVersionsInfo.new VersionItem(androidTarget);

    Map<String,Object> templateValues = new HashMap<>();
    TemplateValueInjector injector = new TemplateValueInjector(templateValues);
    injector.setBuildVersion(versionItem, null);

    assertThat(templateValues).isNotEmpty();
    assertThat(templateValues.get(TemplateMetadata.ATTR_BUILD_API)).isEqualTo(PREVIEW_VERSION);
    assertThat(templateValues.get(TemplateMetadata.ATTR_BUILD_TOOLS_VERSION)).isEqualTo("26.0.0 rc1");
    assertThat(templateValues.get(TemplateMetadata.ATTR_KOTLIN_VERSION)).isNotNull();
  }

  private static void recordBuildTool26rc1(MockFileOp fop) {
    fop.recordExistingFile("/sdk/build-tools/android-O/package.xml",
                           "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                           + "<ns2:repository xmlns:ns2=\"http://schemas.android.com/repository/android/common/01\"\n"
                           + " xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/addon2/01\"\n"
                           + " xmlns:ns4=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\"\n"
                           + " xmlns:ns5=\"http://schemas.android.com/repository/android/generic/01\"\n"
                           + " xmlns:ns6=\"http://schemas.android.com/sdk/android/repo/repository2/01\">\n"
                           + " <license id=\"license-B234E149\" type=\"text\"/>"
                           + " <localPackage path=\"build-tools;26.0.0-rc1\" obsolete=\"false\">\n"
                           + " <type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"ns5:genericDetailsType\"/>\n"
                           + " <revision><major>26</major><minor>0</minor><micro>0</micro>\n"
                           + " <preview>1</preview></revision><display-name>Android SDK Build-Tools 26 rc1</display-name>\n"
                           + " <uses-license ref=\"license-B234E149\"/>\n"
                           + " </localPackage>\n"
                           + " </ns2:repository>");
  }
}
