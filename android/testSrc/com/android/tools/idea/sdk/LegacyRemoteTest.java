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
package com.android.tools.idea.sdk;

import com.android.prefs.AndroidLocation;
import com.android.repository.Revision;
import com.android.repository.api.Channel;
import com.android.repository.api.ConstantSourceProvider;
import com.android.repository.api.RepoManager;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.*;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.legacy.LegacyRemoteRepoLoader;
import com.android.sdklib.repository.legacy.remote.internal.DownloadCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.AndroidTestCase;

import java.io.File;
import java.net.URL;
import java.util.Map;


/**
 * Tests for {@link LegacyRemoteRepoLoader}.
 */
public class LegacyRemoteTest extends AndroidTestCase {
  public void testLegacyRemoteSdk() throws Exception {
    MockFileOp fop = new MockFileOp();
    final AndroidSdkHandler handler = new AndroidSdkHandler(null, fop);
    FakeProgressIndicator progress = new FakeProgressIndicator();
    RepoManager mgr = handler.getSdkManager(progress);
    progress.assertNoErrorsOrWarnings();
    mgr.getSourceProviders().clear();
    progress.assertNoErrorsOrWarnings();
    
    mgr.registerSourceProvider(
      new ConstantSourceProvider("http://www.example.com/testRepo", "Repo", ImmutableList.of(AndroidSdkHandler.getRepositoryModule(),
                                                                                             RepoManager.getGenericModule())));
    mgr.registerSourceProvider(
      new ConstantSourceProvider("http://www.example.com/testRepo2", "Repo2", ImmutableList.of(AndroidSdkHandler.getRepositoryModule(),
                                                                                               RepoManager.getGenericModule())));
    progress.assertNoErrorsOrWarnings();

    FakeSettingsController settings = new FakeSettingsController(false);
    LegacyRemoteRepoLoader sdk = new LegacyRemoteRepoLoader();
    sdk.setDownloadCache(new DownloadCache(fop, DownloadCache.Strategy.ONLY_CACHE));
    mgr.setFallbackRemoteRepoLoader(sdk);
    FakeDownloader downloader = new FakeDownloader(fop);
    // TODO: find a better way to get it into the cache/have the fallback load it
    fop.recordExistingFile(fop.getAgnosticAbsPath(AndroidLocation.getFolder() + "cache/sdkbin-1_951b49ff-test_epo"),
                           FileUtil.loadFile(new File(getClass().getResource("data/repository_sample_10.xml").toURI())));

    downloader.registerUrl(new URL("http://www.example.com/testRepo2"), getClass().getResourceAsStream("data/repository2_sample_1.xml"));
    downloader.registerUrl(new URL("http://www.example.com/testRepo"), getClass().getResourceAsStream("data/repository_sample_10.xml"));
    FakeProgressRunner runner = new FakeProgressRunner();

    mgr.load(0, Lists.<RepoManager.RepoLoadedCallback>newArrayList(), Lists.<RepoManager.RepoLoadedCallback>newArrayList(),
             Lists.<Runnable>newArrayList(), runner, downloader, settings, true);
    runner.getProgressIndicator().assertNoErrorsOrWarnings();
    RepositoryPackages packages = mgr.getPackages();

    Map<String, UpdatablePackage> consolidatedPkgs = packages.getConsolidatedPkgs();
    assertEquals(12, consolidatedPkgs.size());
    assertEquals(12, packages.getNewPkgs().size());

    settings.setChannel(Channel.create(1));
    mgr.load(0, Lists.<RepoManager.RepoLoadedCallback>newArrayList(), Lists.<RepoManager.RepoLoadedCallback>newArrayList(),
             Lists.<Runnable>newArrayList(), runner, downloader, settings, true);
    runner.getProgressIndicator().assertNoErrorsOrWarnings();
    packages = mgr.getPackages();

    consolidatedPkgs = packages.getConsolidatedPkgs();
    assertEquals(14, consolidatedPkgs.size());
    UpdatablePackage doc = consolidatedPkgs.get("docs");
    assertEquals(new Revision(43), doc.getRemote().getVersion());
    UpdatablePackage pastry = consolidatedPkgs.get("platforms;android-Pastry");
    TypeDetails pastryDetails = pastry.getRepresentative().getTypeDetails();
    assertInstanceOf(pastryDetails, DetailsTypes.PlatformDetailsType.class);
    DetailsTypes.PlatformDetailsType platformDetails = (DetailsTypes.PlatformDetailsType)pastryDetails;
    assertEquals(5, platformDetails.getApiLevel());
    assertEquals("Pastry", platformDetails.getCodename());
    assertEquals(1, platformDetails.getLayoutlib().getApi());

    // TODO: more specific checks
  }
}
