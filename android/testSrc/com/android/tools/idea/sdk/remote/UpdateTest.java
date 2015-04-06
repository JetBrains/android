/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.sdk.remote;

import com.android.SdkConstants;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.io.MockFileOp;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.IDescription;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.NoPreviewRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.LocalSdk;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import junit.framework.TestCase;

import java.io.File;
import java.util.Arrays;

public class UpdateTest extends TestCase {

    private MockFileOp mFOp;
    private LocalSdk mLS;
    private Multimap<PkgType, RemotePkgInfo> mRemotePkgs;
    private IDescription mSource;

    @Override
    protected void setUp() {
        mFOp = new MockFileOp();
        mLS = new LocalSdk(mFOp);
        mRemotePkgs = TreeMultimap.create();
        mSource = new IDescription() {
            @Override
            public String getShortDescription() {
                return "source";
            }

            @Override
            public String getLongDescription() {
                return "mock sdk repository source";
            }
        };

        mLS.setLocation(new File("/sdk"));
    }

    public final void testComputeUpdates_Tools() throws Exception {
        addLocalTool("22.3.4", "18");
        addRemoteTool(new FullRevision(23), new FullRevision(19));
        addRemoteTool(new FullRevision(23, 0, 1, 2), new FullRevision(19));

        addLocalPlatformTool("1.0.2");
        addRemotePlatformTool(new FullRevision(1, 0, 3));
        addRemotePlatformTool(new FullRevision(2, 0, 4, 5));

        addLocalBuildTool("18.0.0");
        addLocalBuildTool("19.0.0");
        addRemoteBuildTool(new FullRevision(18, 0, 1));
        addRemoteBuildTool(new FullRevision(19, 1, 2));

        LocalPkgInfo[] allLocalPkgs = mLS.getPkgsInfos(PkgType.PKG_ALL);

        UpdateResult result = Update.computeUpdates(allLocalPkgs, mRemotePkgs);

        assertNotNull(result);
        TestCase.assertEquals("[" +
                              "<LocalToolPkgInfo <PkgDesc Type=tools FullRev=22.3.4 MinPlatToolsRev=18.0.0> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=tools FullRev=23.0.0 MinPlatToolsRev=19.0.0>>>\n" +

                              "<LocalPlatformToolPkgInfo <PkgDesc Type=platform_tools FullRev=1.0.2> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=platform_tools FullRev=1.0.3>>>\n" +

                              "<LocalBuildToolPkgInfo <PkgDesc Type=build_tools FullRev=18.0.0>>\n" +

                              "<LocalBuildToolPkgInfo <PkgDesc Type=build_tools FullRev=19.0.0>>" +
                              "]", Arrays.toString(allLocalPkgs).replace(", ", "\n"));
        assertEquals(
                "[" +
                "<LocalToolPkgInfo <PkgDesc Type=tools FullRev=22.3.4 MinPlatToolsRev=18.0.0> " +
                      "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=tools FullRev=23.0.0 MinPlatToolsRev=19.0.0>>>\n" +

                "<LocalPlatformToolPkgInfo <PkgDesc Type=platform_tools FullRev=1.0.2> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=platform_tools FullRev=1.0.3>>>" +
                "]",
                result.getUpdatedPkgs().toString().replace(", ", "\n"));
        assertEquals(
                "[" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=tools FullRev=23.0.1 rc2 MinPlatToolsRev=19.0.0>>\n" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=platform_tools FullRev=2.0.4 rc5>>\n" +

                 "<RemotePkgInfo Source:source <PkgDesc Type=build_tools FullRev=18.0.1>>\n" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=build_tools FullRev=19.1.2>>" +
                 "]",
                result.getNewPkgs().toString().replace(", ", "\n"));
    }

    public final void testComputeUpdates_DocExtras() throws Exception {
        final AndroidVersion api18 = new AndroidVersion("18");
        final AndroidVersion api19 = new AndroidVersion("19");

        final IdDisplay vendor = new IdDisplay("android", "The Android");

        addLocalDoc (api18, "1");
        addRemoteDoc(api18, new MajorRevision(2));
        addRemoteDoc(api19, new MajorRevision(3));

        addLocalExtra("18.0.1", vendor, "support");
        addLocalExtra("18.0.2", vendor, "compat");
        addRemoteExtra(new NoPreviewRevision(18, 3, 4), vendor, "support",  "The Support Lib");
        addRemoteExtra(new NoPreviewRevision(18, 5, 6), vendor, "compat",   "The Compat Lib");
        addRemoteExtra(new NoPreviewRevision(19, 7, 8), vendor, "whatever", "The Whatever Lib");

        LocalPkgInfo[] allLocalPkgs = mLS.getPkgsInfos(PkgType.PKG_ALL);

        UpdateResult result = Update.computeUpdates(allLocalPkgs, mRemotePkgs);

        assertNotNull(result);
        TestCase.assertEquals("[" +
                              "<LocalDocPkgInfo <PkgDesc Type=doc Android=API 18 MajorRev=1> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=doc Android=API 19 MajorRev=3>>>\n" +

                              "<LocalExtraPkgInfo <PkgDesc Type=extra Vendor=android [The Android] Path=compat FullRev=18.0.2> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=extra Vendor=android [The Android] Path=compat FullRev=18.5.6>>>\n" +

                              "<LocalExtraPkgInfo <PkgDesc Type=extra Vendor=android [The Android] Path=support FullRev=18.0.1> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=extra Vendor=android [The Android] Path=support FullRev=18.3.4>>>" +
                              "]", Arrays.toString(allLocalPkgs).replace(", ", "\n"));
        assertEquals(
                "[" +
                "<LocalDocPkgInfo <PkgDesc Type=doc Android=API 18 MajorRev=1> " +
                     "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=doc Android=API 19 MajorRev=3>>>\n" +

                "<LocalExtraPkgInfo <PkgDesc Type=extra Vendor=android [The Android] Path=compat FullRev=18.0.2> " +
                       "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=extra Vendor=android [The Android] Path=compat FullRev=18.5.6>>>\n" +
                "<LocalExtraPkgInfo <PkgDesc Type=extra Vendor=android [The Android] Path=support FullRev=18.0.1> " +
                       "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=extra Vendor=android [The Android] Path=support FullRev=18.3.4>>>" +
                "]",
                result.getUpdatedPkgs().toString().replace(", ", "\n"));
        assertEquals(
                "[" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=extra Vendor=android [The Android] Path=whatever FullRev=19.7.8>>" +
                 "]",
                result.getNewPkgs().toString().replace(", ", "\n"));
    }

    public final void testComputeUpdates_Platforms() throws Exception {
        final AndroidVersion api18 = new AndroidVersion("18");
        final AndroidVersion api19 = new AndroidVersion("19");

        final IdDisplay tagDefault = new IdDisplay("default", "Default");
        final IdDisplay tag1       = new IdDisplay("tag-1", "Tag 1");
        final IdDisplay tag2       = new IdDisplay("tag-2", "Tag 2");

        addLocalPlatform (api18, "2", "22.1.2");
        addLocalSource   (api18, "3");
        addLocalSample   (api18, "4", "22.1.2");
        addLocalSysImg   (api18, "5", null, "eabi");
        addLocalSysImg   (api18, "6", tag1, "eabi");
        addRemotePlatform(api18, new MajorRevision(12), new FullRevision(22));
        addRemoteSource  (api18, new MajorRevision(13));
        addRemoteSample  (api18, new MajorRevision(14), new FullRevision(22));
        addRemoteSysImg  (api18, new MajorRevision(15), tagDefault, "eabi");
        addRemoteSysImg  (api18, new MajorRevision(16), tag1,       "eabi");

        addRemotePlatform(api19, new MajorRevision(22), new FullRevision(23));
        addRemoteSource  (api19, new MajorRevision(23));
        addRemoteSample  (api19, new MajorRevision(24), new FullRevision(23));
        addRemoteSysImg  (api19, new MajorRevision(25), tagDefault, "eabi");
        addRemoteSysImg  (api19, new MajorRevision(26), tag1, "eabi");
        addRemoteSysImg  (api19, new MajorRevision(27), tag2, "eabi");

        LocalPkgInfo[] allLocalPkgs = mLS.getPkgsInfos(PkgType.PKG_ALL);

        UpdateResult result = Update.computeUpdates(allLocalPkgs, mRemotePkgs);

        assertNotNull(result);
        TestCase.assertEquals("[" +
                              "<LocalPlatformPkgInfo <PkgDesc Type=platform Android=API 18 Path=android-18 MajorRev=2 MinToolsRev=22.1.2> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=platform Android=API 18 Path=android-18 MajorRev=12 MinToolsRev=22.0.0>>>\n" +

                              "<LocalSysImgPkgInfo <PkgDesc Type=sys_image Android=API 18 Tag=default [Default] Path=eabi MajorRev=5> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=sys_image Android=API 18 Tag=default [Default] Path=eabi MajorRev=15>>>\n" +

                              "<LocalSysImgPkgInfo <PkgDesc Type=sys_image Android=API 18 Tag=tag-1 [Tag 1] Path=eabi MajorRev=6> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=sys_image Android=API 18 Tag=tag-1 [Tag 1] Path=eabi MajorRev=16>>>\n" +

                              "<LocalSamplePkgInfo <PkgDesc Type=sample Android=API 18 MajorRev=4 MinToolsRev=22.1.2> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=sample Android=API 18 MajorRev=14 MinToolsRev=22.0.0>>>\n" +

                              "<LocalSourcePkgInfo <PkgDesc Type=source Android=API 18 MajorRev=3> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=source Android=API 18 MajorRev=13>>>" +
                              "]", Arrays.toString(allLocalPkgs).replace(", ", "\n"));
        assertEquals(
                "[" +
                "<LocalPlatformPkgInfo <PkgDesc Type=platform Android=API 18 Path=android-18 MajorRev=2 MinToolsRev=22.1.2> " +
                          "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=platform Android=API 18 Path=android-18 MajorRev=12 MinToolsRev=22.0.0>>>\n" +

                "<LocalSysImgPkgInfo <PkgDesc Type=sys_image Android=API 18 Tag=default [Default] Path=eabi MajorRev=5> " +
                        "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=sys_image Android=API 18 Tag=default [Default] Path=eabi MajorRev=15>>>\n" +

                "<LocalSysImgPkgInfo <PkgDesc Type=sys_image Android=API 18 Tag=tag-1 [Tag 1] Path=eabi MajorRev=6> " +
                        "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=sys_image Android=API 18 Tag=tag-1 [Tag 1] Path=eabi MajorRev=16>>>\n" +

                "<LocalSamplePkgInfo <PkgDesc Type=sample Android=API 18 MajorRev=4 MinToolsRev=22.1.2> " +
                        "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=sample Android=API 18 MajorRev=14 MinToolsRev=22.0.0>>>\n" +

                "<LocalSourcePkgInfo <PkgDesc Type=source Android=API 18 MajorRev=3> " +
                        "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=source Android=API 18 MajorRev=13>>>" +
                "]",
                result.getUpdatedPkgs().toString().replace(", ", "\n"));
        assertEquals(
                "[" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=platform Android=API 19 Path=android-19 MajorRev=22 MinToolsRev=23.0.0>>\n" +

                 "<RemotePkgInfo Source:source <PkgDesc Type=sys_image Android=API 19 Tag=default [Default] Path=eabi MajorRev=25>>\n" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=sys_image Android=API 19 Tag=tag-1 [Tag 1] Path=eabi MajorRev=26>>\n" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=sys_image Android=API 19 Tag=tag-2 [Tag 2] Path=eabi MajorRev=27>>\n" +

                 "<RemotePkgInfo Source:source <PkgDesc Type=sample Android=API 19 MajorRev=24 MinToolsRev=23.0.0>>\n" +

                 "<RemotePkgInfo Source:source <PkgDesc Type=source Android=API 19 MajorRev=23>>" +
                 "]",
                result.getNewPkgs().toString().replace(", ", "\n"));
    }

    public final void testComputeUpdates_Addons() throws Exception {
        final AndroidVersion api18 = new AndroidVersion("18");
        final AndroidVersion api19 = new AndroidVersion("19");

        final IdDisplay vendor1 = new IdDisplay("android", "The Android");
        final IdDisplay name1   = new IdDisplay("cool_addon", "The Add-on");

        final IdDisplay vendor2 = new IdDisplay("vendor2", "Vendor Too");
        final IdDisplay name2   = new IdDisplay("addon-2", "Add-on Too");

        addLocalAddOn       (api18, "7", vendor1, name1);
        addLocalAddonSysImg (api18, "8", vendor1, name1, "abi32");
        addLocalAddonSysImg (api18, "9", vendor1, name1, "abi64");  // no update
        addRemoteAddOn      (api18, new MajorRevision(17), vendor1, name1);
        addRemoteAddonSysImg(api18, new MajorRevision(18), vendor1, name1, "abi32");
        addRemoteAddonSysImg(api18, new MajorRevision(19), vendor1, name1, "abi96"); //wrong abi
        // these remote sys-img do not match the right vendor/name.
        addRemoteAddonSysImg(api18, new MajorRevision(18), vendor2, name1, "abi64");
        addRemoteAddonSysImg(api18, new MajorRevision(18), vendor1, name2, "abi64");
        addRemoteAddonSysImg(api18, new MajorRevision(18), vendor2, name2, "abi64");

        addRemoteAddOn      (api19, new MajorRevision(27), vendor1, name1);
        addRemoteAddonSysImg(api19, new MajorRevision(28), vendor1, name1, "abi32");
        addRemoteAddonSysImg(api19, new MajorRevision(29), vendor1, name1, "abi64");

        LocalPkgInfo[] allLocalPkgs = mLS.getPkgsInfos(PkgType.PKG_ALL);

        UpdateResult result = Update.computeUpdates(allLocalPkgs, mRemotePkgs);

        assertNotNull(result);
        TestCase.assertEquals("[" +
                              "<LocalAddonPkgInfo <PkgDesc Type=addon Android=API 18 Vendor=android [The Android] Path=The Android:The Add-on:18 MajorRev=7> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=addon Android=API 18 Vendor=android [The Android] Path=The Android:The Add-on:18 MajorRev=17>>>\n" +
                              "<LocalAddonSysImgPkgInfo <PkgDesc Type=addon_sys_image Android=API 18 Vendor=android [The Android] Tag=cool_addon [The Add-on] Path=abi32 MajorRev=8> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=addon_sys_image Android=API 18 Vendor=android [The Android] Tag=cool_addon [The Add-on] Path=abi32 MajorRev=18>>>\n" +
                              "<LocalAddonSysImgPkgInfo <PkgDesc Type=addon_sys_image Android=API 18 Vendor=android [The Android] Tag=cool_addon [The Add-on] Path=abi64 MajorRev=9>>" +
                              "]", Arrays.toString(allLocalPkgs).replace(", ", "\n"));
        assertEquals(
                "[" +
                "<LocalAddonPkgInfo <PkgDesc Type=addon Android=API 18 Vendor=android [The Android] Path=The Android:The Add-on:18 MajorRev=7> " +
                        "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=addon Android=API 18 Vendor=android [The Android] Path=The Android:The Add-on:18 MajorRev=17>>>\n" +
                "<LocalAddonSysImgPkgInfo <PkgDesc Type=addon_sys_image Android=API 18 Vendor=android [The Android] Tag=cool_addon [The Add-on] Path=abi32 MajorRev=8> " +
                             "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=addon_sys_image Android=API 18 Vendor=android [The Android] Tag=cool_addon [The Add-on] Path=abi32 MajorRev=18>>>" +
                "]",
                result.getUpdatedPkgs().toString().replace(", ", "\n"));
        assertEquals(
                "[" +
                "<RemotePkgInfo Source:source <PkgDesc Type=addon Android=API 19 Vendor=android [The Android] Path=The Android:The Add-on:19 MajorRev=27>>\n" +
                "<RemotePkgInfo Source:source <PkgDesc Type=addon_sys_image Android=API 18 Vendor=android [The Android] Tag=addon-2 [Add-on Too] Path=abi64 MajorRev=18>>\n" +
                "<RemotePkgInfo Source:source <PkgDesc Type=addon_sys_image Android=API 18 Vendor=android [The Android] Tag=cool_addon [The Add-on] Path=abi96 MajorRev=19>>\n" +
                "<RemotePkgInfo Source:source <PkgDesc Type=addon_sys_image Android=API 18 Vendor=vendor2 [Vendor Too] Tag=addon-2 [Add-on Too] Path=abi64 MajorRev=18>>\n" +
                "<RemotePkgInfo Source:source <PkgDesc Type=addon_sys_image Android=API 18 Vendor=vendor2 [Vendor Too] Tag=cool_addon [The Add-on] Path=abi64 MajorRev=18>>\n" +
                "<RemotePkgInfo Source:source <PkgDesc Type=addon_sys_image Android=API 19 Vendor=android [The Android] Tag=cool_addon [The Add-on] Path=abi32 MajorRev=28>>\n" +
                "<RemotePkgInfo Source:source <PkgDesc Type=addon_sys_image Android=API 19 Vendor=android [The Android] Tag=cool_addon [The Add-on] Path=abi64 MajorRev=29>>" +
                "]",
                result.getNewPkgs().toString().replace(", ", "\n"));

    }

    // --- all of the above at the same time

    public final void testComputeUpdates_All() throws Exception {
        final AndroidVersion api18 = new AndroidVersion("18");
        final AndroidVersion api19 = new AndroidVersion("19");

        final IdDisplay tagDefault = new IdDisplay("default", "Default");
        final IdDisplay tag1       = new IdDisplay("tag-1", "Tag 1");
        final IdDisplay tag2       = new IdDisplay("tag-2", "Tag 2");

        final IdDisplay vendor = new IdDisplay("android", "The Android");
        final IdDisplay vendor1 = new IdDisplay("android", "The Android");
        final IdDisplay name1   = new IdDisplay("cool_addon", "The Add-on");

        final IdDisplay vendor2 = new IdDisplay("vendor2", "Vendor Too");
        final IdDisplay name2   = new IdDisplay("addon-2", "Add-on Too");

        //---
        addLocalTool("22.3.4", "18");
        addRemoteTool(new FullRevision(23), new FullRevision(19));
        addRemoteTool(new FullRevision(23, 0, 1, 2), new FullRevision(19));

        addLocalPlatformTool("1.0.2");
        addRemotePlatformTool(new FullRevision(1, 0, 3));
        addRemotePlatformTool(new FullRevision(2, 0, 4, 5));

        addLocalBuildTool("18.0.0");
        addLocalBuildTool("19.0.0");
        addRemoteBuildTool(new FullRevision(18, 0, 1));
        addRemoteBuildTool(new FullRevision(19, 1, 2));

        //---
        addLocalDoc (api18, "1");
        addRemoteDoc(api18, new MajorRevision(2));
        addRemoteDoc(api19, new MajorRevision(3));

        addLocalExtra("18.0.1", vendor, "support");
        addLocalExtra("18.0.2", vendor, "compat");
        addRemoteExtra(new NoPreviewRevision(18, 3, 4), vendor, "support",  "The Support Lib");
        addRemoteExtra(new NoPreviewRevision(18, 5, 6), vendor, "compat",   "The Compat Lib");
        addRemoteExtra(new NoPreviewRevision(19, 7, 8), vendor, "whatever", "The Whatever Lib");

        //---

        addLocalPlatform (api18, "2", "22.1.2");
        addLocalSource   (api18, "3");
        addLocalSample   (api18, "4", "22.1.2");
        addLocalSysImg   (api18, "5", null, "eabi");
        addLocalSysImg   (api18, "6", tag1, "eabi");
        addRemotePlatform(api18, new MajorRevision(12), new FullRevision(22));
        addRemoteSource  (api18, new MajorRevision(13));
        addRemoteSample  (api18, new MajorRevision(14), new FullRevision(22));
        addRemoteSysImg  (api18, new MajorRevision(15), tagDefault, "eabi");
        addRemoteSysImg  (api18, new MajorRevision(16), tag1,       "eabi");

        addRemotePlatform(api19, new MajorRevision(22), new FullRevision(23));
        addRemoteSource  (api19, new MajorRevision(23));
        addRemoteSample  (api19, new MajorRevision(24), new FullRevision(23));
        addRemoteSysImg  (api19, new MajorRevision(25), tagDefault, "eabi");
        addRemoteSysImg  (api19, new MajorRevision(26), tag1, "eabi");
        addRemoteSysImg  (api19, new MajorRevision(27), tag2, "eabi");

        //---
        addLocalAddOn       (api18, "7", vendor1, name1);
        addLocalAddonSysImg (api18, "8", vendor1, name1, "abi32");
        addLocalAddonSysImg (api18, "9", vendor1, name1, "abi64");  // no update
        addRemoteAddOn      (api18, new MajorRevision(17), vendor1, name1);
        addRemoteAddonSysImg(api18, new MajorRevision(18), vendor1, name1, "abi32");
        addRemoteAddonSysImg(api18, new MajorRevision(19), vendor1, name1, "abi96"); //wrong abi
        // these remote sys-img do not match the right vendor/name.
        addRemoteAddonSysImg(api18, new MajorRevision(18), vendor2, name1, "abi64");
        addRemoteAddonSysImg(api18, new MajorRevision(18), vendor1, name2, "abi64");
        addRemoteAddonSysImg(api18, new MajorRevision(18), vendor2, name2, "abi64");

        addRemoteAddOn      (api19, new MajorRevision(27), vendor1, name1);
        addRemoteAddonSysImg(api19, new MajorRevision(28), vendor1, name1, "abi32");
        addRemoteAddonSysImg(api19, new MajorRevision(29), vendor1, name1, "abi64");


        //---

        LocalPkgInfo[] allLocalPkgs = mLS.getPkgsInfos(PkgType.PKG_ALL);

        UpdateResult result = Update.computeUpdates(allLocalPkgs, mRemotePkgs);

        assertNotNull(result);
        TestCase.assertEquals("[" +
                              "<LocalToolPkgInfo <PkgDesc Type=tools FullRev=22.3.4 MinPlatToolsRev=18.0.0> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=tools FullRev=23.0.0 MinPlatToolsRev=19.0.0>>>\n" +

                              "<LocalPlatformToolPkgInfo <PkgDesc Type=platform_tools FullRev=1.0.2> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=platform_tools FullRev=1.0.3>>>\n" +

                              "<LocalBuildToolPkgInfo <PkgDesc Type=build_tools FullRev=18.0.0>>\n" +
                              "<LocalBuildToolPkgInfo <PkgDesc Type=build_tools FullRev=19.0.0>>\n" +
                              //---
                              "<LocalDocPkgInfo <PkgDesc Type=doc Android=API 18 MajorRev=1> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=doc Android=API 19 MajorRev=3>>>\n" +
                              //---
                              "<LocalPlatformPkgInfo <PkgDesc Type=platform Android=API 18 Path=android-18 MajorRev=2 MinToolsRev=22.1.2> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=platform Android=API 18 Path=android-18 MajorRev=12 MinToolsRev=22.0.0>>>\n" +
                              "<LocalSysImgPkgInfo <PkgDesc Type=sys_image Android=API 18 Tag=default [Default] Path=eabi MajorRev=5> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=sys_image Android=API 18 Tag=default [Default] Path=eabi MajorRev=15>>>\n" +
                              "<LocalSysImgPkgInfo <PkgDesc Type=sys_image Android=API 18 Tag=tag-1 [Tag 1] Path=eabi MajorRev=6> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=sys_image Android=API 18 Tag=tag-1 [Tag 1] Path=eabi MajorRev=16>>>\n" +
                              //---
                              "<LocalAddonPkgInfo <PkgDesc Type=addon Android=API 18 Vendor=android [The Android] Path=The Android:The Add-on:18 MajorRev=7> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=addon Android=API 18 Vendor=android [The Android] Path=The Android:The Add-on:18 MajorRev=17>>>\n" +
                              "<LocalAddonSysImgPkgInfo <PkgDesc Type=addon_sys_image Android=API 18 Vendor=android [The Android] Tag=cool_addon [The Add-on] Path=abi32 MajorRev=8> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=addon_sys_image Android=API 18 Vendor=android [The Android] Tag=cool_addon [The Add-on] Path=abi32 MajorRev=18>>>\n" +
                              "<LocalAddonSysImgPkgInfo <PkgDesc Type=addon_sys_image Android=API 18 Vendor=android [The Android] Tag=cool_addon [The Add-on] Path=abi64 MajorRev=9>>\n" +
                              //---
                              "<LocalSamplePkgInfo <PkgDesc Type=sample Android=API 18 MajorRev=4 MinToolsRev=22.1.2> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=sample Android=API 18 MajorRev=14 MinToolsRev=22.0.0>>>\n" +

                              "<LocalSourcePkgInfo <PkgDesc Type=source Android=API 18 MajorRev=3> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=source Android=API 18 MajorRev=13>>>\n" +
                              //---
                              "<LocalExtraPkgInfo <PkgDesc Type=extra Vendor=android [The Android] Path=compat FullRev=18.0.2> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=extra Vendor=android [The Android] Path=compat FullRev=18.5.6>>>\n" +

                              "<LocalExtraPkgInfo <PkgDesc Type=extra Vendor=android [The Android] Path=support FullRev=18.0.1> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=extra Vendor=android [The Android] Path=support FullRev=18.3.4>>>" +
                              "]", Arrays.toString(allLocalPkgs).replace(", ", "\n"));
        assertEquals(
                "[" +
                "<LocalToolPkgInfo <PkgDesc Type=tools FullRev=22.3.4 MinPlatToolsRev=18.0.0> " +
                      "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=tools FullRev=23.0.0 MinPlatToolsRev=19.0.0>>>\n" +

                "<LocalPlatformToolPkgInfo <PkgDesc Type=platform_tools FullRev=1.0.2> " +
                              "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=platform_tools FullRev=1.0.3>>>\n" +
                //---
                "<LocalDocPkgInfo <PkgDesc Type=doc Android=API 18 MajorRev=1> " +
                     "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=doc Android=API 19 MajorRev=3>>>\n" +
                //---
                "<LocalPlatformPkgInfo <PkgDesc Type=platform Android=API 18 Path=android-18 MajorRev=2 MinToolsRev=22.1.2> " +
                          "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=platform Android=API 18 Path=android-18 MajorRev=12 MinToolsRev=22.0.0>>>\n" +

                "<LocalSysImgPkgInfo <PkgDesc Type=sys_image Android=API 18 Tag=default [Default] Path=eabi MajorRev=5> " +
                        "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=sys_image Android=API 18 Tag=default [Default] Path=eabi MajorRev=15>>>\n" +

                "<LocalSysImgPkgInfo <PkgDesc Type=sys_image Android=API 18 Tag=tag-1 [Tag 1] Path=eabi MajorRev=6> " +
                        "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=sys_image Android=API 18 Tag=tag-1 [Tag 1] Path=eabi MajorRev=16>>>\n" +
                //---
                "<LocalAddonPkgInfo <PkgDesc Type=addon Android=API 18 Vendor=android [The Android] Path=The Android:The Add-on:18 MajorRev=7> " +
                       "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=addon Android=API 18 Vendor=android [The Android] Path=The Android:The Add-on:18 MajorRev=17>>>\n" +
                "<LocalAddonSysImgPkgInfo <PkgDesc Type=addon_sys_image Android=API 18 Vendor=android [The Android] Tag=cool_addon [The Add-on] Path=abi32 MajorRev=8> " +
                             "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=addon_sys_image Android=API 18 Vendor=android [The Android] Tag=cool_addon [The Add-on] Path=abi32 MajorRev=18>>>\n" +
                //---
                "<LocalSamplePkgInfo <PkgDesc Type=sample Android=API 18 MajorRev=4 MinToolsRev=22.1.2> " +
                        "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=sample Android=API 18 MajorRev=14 MinToolsRev=22.0.0>>>\n" +

                "<LocalSourcePkgInfo <PkgDesc Type=source Android=API 18 MajorRev=3> " +
                        "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=source Android=API 18 MajorRev=13>>>\n" +
                //---

                "<LocalExtraPkgInfo <PkgDesc Type=extra Vendor=android [The Android] Path=compat FullRev=18.0.2> " +
                       "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=extra Vendor=android [The Android] Path=compat FullRev=18.5.6>>>\n" +
                "<LocalExtraPkgInfo <PkgDesc Type=extra Vendor=android [The Android] Path=support FullRev=18.0.1> " +
                       "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=extra Vendor=android [The Android] Path=support FullRev=18.3.4>>>" +
                  "]",
                result.getUpdatedPkgs().toString().replace(", ", "\n"));
        assertEquals(
                "[" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=tools FullRev=23.0.1 rc2 MinPlatToolsRev=19.0.0>>\n" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=platform_tools FullRev=2.0.4 rc5>>\n" +

                 "<RemotePkgInfo Source:source <PkgDesc Type=build_tools FullRev=18.0.1>>\n" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=build_tools FullRev=19.1.2>>\n" +
                 //---
                 "<RemotePkgInfo Source:source <PkgDesc Type=platform Android=API 19 Path=android-19 MajorRev=22 MinToolsRev=23.0.0>>\n" +

                 "<RemotePkgInfo Source:source <PkgDesc Type=sys_image Android=API 19 Tag=default [Default] Path=eabi MajorRev=25>>\n" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=sys_image Android=API 19 Tag=tag-1 [Tag 1] Path=eabi MajorRev=26>>\n" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=sys_image Android=API 19 Tag=tag-2 [Tag 2] Path=eabi MajorRev=27>>\n" +
                 //---
                 "<RemotePkgInfo Source:source <PkgDesc Type=addon Android=API 19 Vendor=android [The Android] Path=The Android:The Add-on:19 MajorRev=27>>\n" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=addon_sys_image Android=API 18 Vendor=android [The Android] Tag=addon-2 [Add-on Too] Path=abi64 MajorRev=18>>\n" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=addon_sys_image Android=API 18 Vendor=android [The Android] Tag=cool_addon [The Add-on] Path=abi96 MajorRev=19>>\n" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=addon_sys_image Android=API 18 Vendor=vendor2 [Vendor Too] Tag=addon-2 [Add-on Too] Path=abi64 MajorRev=18>>\n" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=addon_sys_image Android=API 18 Vendor=vendor2 [Vendor Too] Tag=cool_addon [The Add-on] Path=abi64 MajorRev=18>>\n" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=addon_sys_image Android=API 19 Vendor=android [The Android] Tag=cool_addon [The Add-on] Path=abi32 MajorRev=28>>\n" +
                 "<RemotePkgInfo Source:source <PkgDesc Type=addon_sys_image Android=API 19 Vendor=android [The Android] Tag=cool_addon [The Add-on] Path=abi64 MajorRev=29>>\n" +
                 //---
                 "<RemotePkgInfo Source:source <PkgDesc Type=sample Android=API 19 MajorRev=24 MinToolsRev=23.0.0>>\n" +

                 "<RemotePkgInfo Source:source <PkgDesc Type=source Android=API 19 MajorRev=23>>\n" +
                 //---
                 "<RemotePkgInfo Source:source <PkgDesc Type=extra Vendor=android [The Android] Path=whatever FullRev=19.7.8>>" +
                 "]",
                result.getNewPkgs().toString().replace(", ", "\n"));

    }

    //---

    private void addLocalTool(String fullRev, String minPlatToolsRev) {
        mFOp.recordExistingFolder("/sdk/tools");
        mFOp.recordExistingFile("/sdk/tools/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                "Archive.Os=WINDOWS\n" +
                "Pkg.Revision=" + fullRev + "\n" +
                "Platform.MinPlatformToolsRev=" + minPlatToolsRev + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n" +
                "Pkg.SourceUrl=https\\://example.com/repository-8.xml");
        mFOp.recordExistingFile("/sdk/tools/" + SdkConstants.androidCmdName(), "placeholder");
        mFOp.recordExistingFile("/sdk/tools/" + SdkConstants.FN_EMULATOR, "placeholder");
    }

    private void addLocalPlatformTool(String fullRev) {
        mFOp.recordExistingFolder("/sdk/platform-tools");
        mFOp.recordExistingFile("/sdk/platform-tools/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                "Archive.Os=WINDOWS\n" +
                "Pkg.Revision=" + fullRev + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n" +
                "Pkg.SourceUrl=https\\://example.com/repository-8.xml");
    }

    private void addLocalDoc(AndroidVersion version, String majorRev) {
        mFOp.recordExistingFolder("/sdk/docs");
        mFOp.recordExistingFile("/sdk/docs/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                "Archive.Os=ANY\n" +
                "AndroidVersion.ApiLevel=" + version.getApiString() + "\n" +
                "Pkg.Revision=" + majorRev + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n" +
                "Pkg.SourceUrl=https\\://example.com/repository-8.xml");
        mFOp.recordExistingFile("/sdk/docs/index.html", "placeholder");
    }

    private void addLocalBuildTool(String fullRev) {
        mFOp.recordExistingFolder("/sdk/build-tools");
        mFOp.recordExistingFolder("/sdk/build-tools/" + fullRev);
        mFOp.recordExistingFile("/sdk/build-tools/" + fullRev + "/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                "Archive.Os=WINDOWS\n" +
                "Pkg.Revision=" + fullRev + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n" +
                "Pkg.SourceUrl=https\\://example.com/repository-8.xml");
    }

    private void addLocalExtra(String fullRev, IdDisplay vendor, String path) {
        mFOp.recordExistingFolder("/sdk/extras");
        mFOp.recordExistingFolder("/sdk/extras/" + vendor.getId());
        mFOp.recordExistingFolder("/sdk/extras/" + vendor.getId() + "/" + path);
        mFOp.recordExistingFile("/sdk/extras/" + vendor.getId() + "/" + path + "/source.properties",
                "Extra.NameDisplay=Android Support Library\n" +
                "Extra.VendorDisplay=" + vendor.getDisplay() + "\n" +
                "Extra.VendorId=" + vendor.getId() + "\n" +
                "Extra.Path=" + path +  "\n" +
                "Extra.OldPaths=compatibility\n" +
                "Archive.Os=ANY\n" +
                "Pkg.Revision=" + fullRev + "\n" +
                "Archive.Arch=ANY\n");
    }

    private void addLocalSource(AndroidVersion version, String majorRev) {
        String api = version.getApiString();
        mFOp.recordExistingFolder("/sdk/sources");
        mFOp.recordExistingFolder("/sdk/sources/android-" + api);
        mFOp.recordExistingFile("/sdk/sources/android-" + api + "/source.properties",
                "Archive.Os=ANY\n" +
                "AndroidVersion.ApiLevel=" + api + "\n" +
                "AndroidVersion.CodeName=\n" +
                "Pkg.Revision=" + majorRev + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n");
    }

    private void addLocalSample(AndroidVersion version, String majorRev, String minToolsRev) {
        String api = version.getApiString();
        mFOp.recordExistingFolder("/sdk/samples");
        mFOp.recordExistingFolder("/sdk/samples/android-" + api);
        mFOp.recordExistingFile("/sdk/samples/android-" + api + "/source.properties",
                "Archive.Os=ANY\n" +
                "AndroidVersion.ApiLevel=" + api + "\n" +
                "AndroidVersion.CodeName=\n" +
                "Pkg.Revision=" + majorRev + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Platform.MinToolsRev=" + minToolsRev + "\n" +
                "Archive.Arch=ANY\n");
    }

    private void addLocalSysImg(AndroidVersion version, String majorRev, IdDisplay tag, String abi) {
        String api = version.getApiString();
        String tagDir = (tag == null ? "" : "/" + tag.getId());
        mFOp.recordExistingFolder("/sdk/system-images");
        mFOp.recordExistingFolder("/sdk/system-images/android-" + api + tagDir);
        mFOp.recordExistingFolder("/sdk/system-images/android-" + api + tagDir + "/" + abi);
        mFOp.recordExistingFile  ("/sdk/system-images/android-" + api + tagDir + "/" + abi +"/source.properties",
                "SystemImage.Abi=" + abi + "\n" +
                (tag == null ? "" : ("SystemImage.TagId=" + tag.getId())) + "\n" +
                (tag == null ? "" : ("SystemImage.TagDisplay=" + tag.getDisplay())) + "\n" +
                "Pkg.Revision=" + majorRev + "\n" +
                "AndroidVersion.ApiLevel=" + api + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Os=ANY\n" +
                "Archive.Arch=ANY\n");
    }

    private void addLocalAddonSysImg(AndroidVersion version, String majorRev, IdDisplay vendor, IdDisplay tag, String abi) {
        String api = version.getApiString();
        String addon_dir = "addon-" + vendor.getId() + "-" + tag.getId();
        mFOp.recordExistingFolder("/sdk/system-images");
        mFOp.recordExistingFolder("/sdk/system-images/" + addon_dir);
        mFOp.recordExistingFolder("/sdk/system-images/" + addon_dir + "/" + abi);
        mFOp.recordExistingFile  ("/sdk/system-images/" + addon_dir + "/" + abi + "/source.properties",
                "SystemImage.Abi=" + abi + "\n" +
                "SystemImage.TagId=" + tag.getId() + "\n" +
                "SystemImage.TagDisplay=" + tag.getDisplay() + "\n" +
                "Addon.VendorId=" + vendor.getId() + "\n" +
                "Addon.VendorDisplay=" + vendor.getDisplay() + "\n" +
                "Pkg.Revision=" + majorRev + "\n" +
                "AndroidVersion.ApiLevel=" + api + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Os=ANY\n" +
                "Archive.Arch=ANY\n");
    }

    private void addLocalPlatform(AndroidVersion version, String majorRev, String minToolsRev) {
        String api = version.getApiString();
        mFOp.recordExistingFolder("/sdk/platforms");
        mFOp.recordExistingFolder("/sdk/platforms/android-" + api);
        mFOp.recordExistingFile("/sdk/platforms/android-" + api + "/android.jar");
        mFOp.recordExistingFile("/sdk/platforms/android-" + api + "/framework.aidl");
        mFOp.recordExistingFile("/sdk/platforms/android-" + api + "/source.properties",
                "Pkg.Revision=" + majorRev + "\n" +
                "AndroidVersion.ApiLevel=" + api + "\n" +
                "AndroidVersion.ApiLevel=18\n" +
                "Layoutlib.Api=10\n" +
                "Layoutlib.Revision=1\n" +
                "Platform.MinToolsRev=" + minToolsRev + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Os=ANY\n" +
                "Archive.Arch=ANY\n");
        mFOp.recordExistingFile("/sdk/platforms/android-" + api + "/sdk.properties",
                "sdk.ant.templates.revision=1\n" +
                "sdk.skin.default=WVGA800\n");
        mFOp.recordExistingFile("/sdk/platforms/android-" + api + "/build.prop",
                "ro.build.id=JB_MR2\n" +
                "ro.build.display.id=sdk-eng 4.3 JB_MR2 819563 test-keys\n" +
                "ro.build.version.incremental=819563\n" +
                "ro.build.version.sdk=" + api + "\n" +
                "ro.build.version.codename=REL\n" +
                "ro.build.version.release=4.3\n" +
                "ro.build.date=Tue Sep 10 18:43:31 UTC 2013\n" +
                "ro.build.date.utc=1378838611\n" +
                "ro.build.type=eng\n" +
                "ro.build.tags=test-keys\n" +
                "ro.product.model=sdk\n" +
                "ro.product.name=sdk\n" +
                "ro.product.board=\n" +
                "ro.product.cpu.abi=armeabi-v7a\n" +
                "ro.product.cpu.abi2=armeabi\n" +
                "ro.product.locale.language=en\n" +
                "ro.product.locale.region=US\n" +
                "ro.wifi.channels=\n" +
                "ro.board.platform=\n" +
                "# ro.build.product is obsolete; use ro.product.device\n" +
                "# Do not try to parse ro.build.description or .fingerprint\n" +
                "ro.build.description=sdk-eng 4.3 JB_MR2 819563 test-keys\n" +
                "ro.build.fingerprint=generic/sdk/generic:4.3/JB_MR2/819563:eng/test-keys\n" +
                "ro.build.characteristics=default\n" +
                "rild.libpath=/system/lib/libreference-ril.so\n" +
                "rild.libargs=-d /dev/ttyS0\n" +
                "ro.config.notification_sound=OnTheHunt.ogg\n" +
                "ro.config.alarm_alert=Alarm_Classic.ogg\n" +
                "ro.kernel.android.checkjni=1\n" +
                "xmpp.auto-presence=true\n" +
                "ro.config.nocheckin=yes\n" +
                "net.bt.name=Android\n" +
                "dalvik.vm.stack-trace-file=/data/anr/traces.txt\n" +
                "ro.build.user=generic\n" +
                "ro.build.host=generic\n" +
                "ro.product.brand=generic\n" +
                "ro.product.manufacturer=generic\n" +
                "ro.product.device=generic\n" +
                "ro.build.product=generic\n");
    }

    private void addLocalAddOn(AndroidVersion version, String majorRev, IdDisplay vendor, IdDisplay name) {
        String api = version.getApiString();
        String addon_dir = "addon-" + vendor.getId() + "-" + name;
        mFOp.recordExistingFolder("/sdk/add-ons");
        mFOp.recordExistingFolder("/sdk/add-ons/" + addon_dir);
        mFOp.recordExistingFile("/sdk/add-ons/" + addon_dir + "/source.properties",
                "Pkg.Revision=" + majorRev + "\n" +
                "Addon.VendorId=" + vendor.getId() + "\n" +
                "Addon.VendorDisplay=" + vendor.getDisplay() + "\n" +
                "Addon.NameId=" + name.getId() + "\n" +
                "Addon.NameDisplay=" + name.getDisplay() + "\n" +
                "AndroidVersion.ApiLevel=" + api + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Os=ANY\n" +
                "Archive.Arch=ANY\n");
        mFOp.recordExistingFile("/sdk/add-ons/" + addon_dir + "/manifest.ini",
                "Pkg.Revision=" + majorRev + "\n" +
                "name=" + name.getDisplay() + "\n" +
                "name-id=" + name.getId() + "\n" +
                "vendor=" + vendor.getDisplay() + "\n" +
                "vendor-id=" + vendor.getId() + "\n" +
                "api=" + api + "\n" +
                "libraries=com.foo.lib1;com.blah.lib2\n" +
                "com.foo.lib1=foo.jar;API for Foo\n" +
                "com.blah.lib2=blah.jar;API for Blah\n");
    }

    //---

    private void addRemoteTool(FullRevision revision, FullRevision minPlatformToolsRev) {
        IPkgDesc d = PkgDesc.Builder.newTool(revision, minPlatformToolsRev).create();
        RemotePkgInfo r = new RemotePkgInfo(d, mSource, 0);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemotePlatformTool(FullRevision revision) {
        IPkgDesc d = PkgDesc.Builder.newPlatformTool(revision).create();
        RemotePkgInfo r = new RemotePkgInfo(d, mSource, 0);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemoteDoc(AndroidVersion version, MajorRevision revision) {
        IPkgDesc d = PkgDesc.Builder.newDoc(version, revision).create();
        RemotePkgInfo r = new RemotePkgInfo(d, mSource, 0);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemoteBuildTool(FullRevision revision) {
        IPkgDesc d = PkgDesc.Builder.newBuildTool(revision).create();
        RemotePkgInfo r = new RemotePkgInfo(d, mSource, 0);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemoteExtra(NoPreviewRevision revision,
                                IdDisplay vendor,
                                String path,
                                String name) {
        IPkgDesc d = PkgDesc.Builder.newExtra(vendor, path, name, null, revision).create();
        RemotePkgInfo r = new RemotePkgInfo(d, mSource, 0);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemoteSource(AndroidVersion version, MajorRevision revision) {
        IPkgDesc d = PkgDesc.Builder.newSource(version, revision).create();
        RemotePkgInfo r = new RemotePkgInfo(d, mSource, 0);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemoteSample(AndroidVersion version,
                                 MajorRevision revision,
                                 FullRevision minToolsRev) {
        IPkgDesc d = PkgDesc.Builder.newSample(version, revision, minToolsRev).create();
        RemotePkgInfo r = new RemotePkgInfo(d, mSource, 0);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemoteSysImg(AndroidVersion version,
                                 MajorRevision revision,
                                 IdDisplay tag,
                                 String abi) {
        IPkgDesc d = PkgDesc.Builder.newSysImg(version, tag, abi, revision).create();
        RemotePkgInfo r = new RemotePkgInfo(d, mSource, 0);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemoteAddonSysImg(AndroidVersion version,
                                      MajorRevision revision,
                                      IdDisplay vendor,
                                      IdDisplay tag,
                                      String abi) {
        IPkgDesc d = PkgDesc.Builder.newAddonSysImg(version, vendor, tag, abi, revision).create();
        RemotePkgInfo r = new RemotePkgInfo(d, mSource, 0);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemotePlatform(AndroidVersion version,
                                   MajorRevision revision,
                                   FullRevision minToolsRev) {
        IPkgDesc d = PkgDesc.Builder.newPlatform(version, revision, minToolsRev).create();
        RemotePkgInfo r = new RemotePkgInfo(d, mSource, 0);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemoteAddOn(AndroidVersion version,
                                MajorRevision revision,
                                IdDisplay vendor,
                                IdDisplay name) {
        IPkgDesc d = PkgDesc.Builder.newAddon(version, revision, vendor, name).create();
        RemotePkgInfo r = new RemotePkgInfo(d, mSource, 0);
        mRemotePkgs.put(d.getType(), r);
    }

}
