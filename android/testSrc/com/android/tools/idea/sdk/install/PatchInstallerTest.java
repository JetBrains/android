/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.sdk.install;

import com.android.repository.api.*;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.impl.meta.SchemaModuleUtil;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.android.tools.idea.sdk.install.PatchInstaller;
import com.google.common.collect.ImmutableList;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

/**
 * Tests for {@link PatchInstaller}.
 */
public class PatchInstallerTest extends TestCase {
  private static MockFileOp ourFileOp;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ourFileOp = new MockFileOp();
  }

  private static final String PATCHER_V1 =
    "<sdk:repository\n" +
    "        xmlns:sdk=\"http://schemas.android.com/repository/android/generic/01\"\n" +
    "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
    "    <localPackage path=\"patcher;v1\">\n" +
    "        <type-details xsi:type=\"sdk:genericDetailsType\"/>\n" +
    "        <revision>\n" +
    "            <major>1</major>\n" +
    "        </revision>\n" +
    "        <display-name>patcher v1</display-name>\n" +
    "    </localPackage>\n" +
    "</sdk:repository>";

  private static final String PKG_V2 =
    "<sdk:repository\n" +
    "        xmlns:sdk=\"http://schemas.android.com/repository/android/generic/01\"\n" +
    "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
    "    <localPackage path=\"pkg\">\n" +
    "        <type-details xsi:type=\"sdk:genericDetailsType\"/>\n" +
    "        <revision>\n" +
    "            <major>2</major>\n" +
    "        </revision>\n" +
    "        <display-name>test pkg</display-name>\n" +
    "    </localPackage>\n" +
    "</sdk:repository>";

  private static final String REMOTE =
    "<sdk:repository\n" +
    "        xmlns:sdk=\"http://schemas.android.com/repository/android/generic/01\"\n" +
    "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
    "    <remotePackage path=\"pkg\">\n" +
    "        <type-details xsi:type=\"sdk:genericDetailsType\"/>\n" +
    "        <revision>\n" +
    "            <major>5</major>\n" +
    "        </revision>\n" +
    "        <display-name>test pkg</display-name>\n" +
    "        <dependencies>\n" +
    "            <dependency path=\"patcher;v1\"/>\n" +
    "        </dependencies>\n" +
    "        <archives>\n" +
    "            <archive>\n" +
    "                <complete>\n" +
    "                    <size>65536</size>\n" +
    "                    <checksum>2822ae37115ebf13412bbef91339ee0d9454525e</checksum>\n" +
    "                    <url>http://example.com/2/arch1</url>\n" +
    "                </complete>\n" +
    "            </archive>\n" +
    "        </archives>\n" +
    "    </remotePackage>\n" +
    "</sdk:repository>\n";

  public void testGetPatcher() throws Exception {
    FakeProgressIndicator progress = new FakeProgressIndicator();
    ourFileOp.recordExistingFile("/sdk/pkg/package.xml", PKG_V2);
    ourFileOp.recordExistingFile("/sdk/pkg/sourceFile");
    ourFileOp.recordExistingFile("/sdk/patcher/v1/package.xml", PATCHER_V1);
    ourFileOp.recordExistingFile("/sdk/patcher/v1/patcher.jar");
    RepoManager repoManager = new RepoManagerImpl(ourFileOp);
    repoManager.setLocalPath(new File("/sdk"));
    repoManager.loadSynchronously(0, progress, null, null);
    RemotePackage p = getRemotePackage(repoManager, progress);

    File result = new PatchInstaller(p, repoManager, ourFileOp).getPatcherFile(repoManager.getPackages().getLocalPackages(), progress);
    progress.assertNoErrorsOrWarnings();
    assertEquals("/sdk/patcher/v1/patcher.jar", result.getPath());
  }

  public void testRunInstaller() throws Exception {
    FakeProgressIndicator progress = new FakeProgressIndicator();
    File localPackageLocation = new File("/sdk/pkg");
    ourFileOp.recordExistingFile(new File(localPackageLocation.getPath(), "sourceFile").getPath(),
                           "the source to which the diff will be applied");
    File patchFile = new File("/patchfile");
    ourFileOp.recordExistingFile(patchFile.getPath(), "the patch contents");
    boolean result = PatchInstaller.runPatcher(
      progress, localPackageLocation, patchFile, FakeRunner.class, FakeUIBase.class, FakeUI.class);

    progress.assertNoErrorsOrWarnings();
    assertTrue(result);
    assertTrue(FakeRunner.ourDidRun);
  }

  private static RemotePackage getRemotePackage(@NotNull RepoManager repoManager, @NotNull ProgressIndicator progress)
    throws JAXBException {
    InputStream remoteInput = new ByteArrayInputStream(REMOTE.getBytes());
    ImmutableList<SchemaModule> modules = ImmutableList.of(RepoManager.getGenericModule());
    Repository r = (Repository)SchemaModuleUtil
      .unmarshal(remoteInput, modules, repoManager.getResourceResolver(progress), true, progress);
    RemotePackage p = r.getRemotePackage().get(0);
    ConstantSourceProvider provider = new ConstantSourceProvider("http://example.com", "dummy", modules);
    p.setSource(provider.getSources(null, null, progress, false).get(0));
    return p;
  }

  private static class FakeRunner {
    public static boolean ourDidRun;
    private static boolean ourLoggerInitted;

    public static void initLogger() {
      ourLoggerInitted = true;
    }

    public static boolean doInstall(String patchPath, FakeUIBase ui, String sourcePath) {
      assertEquals("/patchfile", patchPath);
      assertTrue(ourFileOp.exists(new File(sourcePath, "sourceFile")));
      assertTrue(ui instanceof FakeUI);
      ourDidRun = true;
      return ourLoggerInitted;
    }
  }

  private static class FakeUIBase {

  }

  private static class FakeUI extends FakeUIBase {
    public FakeUI(ProgressIndicator progress) {}
  }
}
