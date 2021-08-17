/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.tools.idea.gradle.model.IdeAndroidArtifactOutput;
import com.android.tools.idea.gradle.model.IdeFilterData;
import com.android.tools.idea.gradle.model.impl.IdeFilterDataImpl;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import junit.framework.TestCase;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SplitOutputMatcherTest extends TestCase {

    /** Helper to run InstallHelper.computeMatchingOutput with variable ABI list. */
    private static List<File> computeBestOutput(
            @NonNull List<IdeAndroidArtifactOutput> outputs, @NonNull String... deviceAbis) {
        return SplitOutputMatcher.computeBestOutput(outputs,
                                                    null /* variantAbiFilters */,
                                                    Arrays.asList(deviceAbis))
                .stream()
                .map(IdeAndroidArtifactOutput::getOutputFile)
                .collect(Collectors.toList());
    }

    private static List<File> computeBestOutput(
            @NonNull List<IdeAndroidArtifactOutput> outputs,
            @NonNull Set<String> deviceAbis,
            @NonNull String... variantAbiFilters) {
        return SplitOutputMatcher.computeBestOutput(outputs,
                                                    Arrays.asList(variantAbiFilters),
                                                    new ArrayList<>(deviceAbis))
                .stream()
                .map(IdeAndroidArtifactOutput::getOutputFile)
                .collect(Collectors.toList());
    }

    /** Fake implementation of FilteredOutput */
    private static final class FakeSplitOutput implements IdeAndroidArtifactOutput {

        private final String abiFilter;

        private final File file;

        private final int versionCode;

        FakeSplitOutput(String abiFilter, int versionCode) {

            this.abiFilter = abiFilter;
            file = new File(abiFilter);
            this.versionCode = versionCode;
        }

        FakeSplitOutput(String abiFilter, File file, int versionCode) {
            this.abiFilter = abiFilter;
            this.file = file;
            this.versionCode = versionCode;
        }

        @Override
        public int getVersionCode() {
            return versionCode;
        }

        @NonNull
        @Override
        public Collection<IdeFilterData> getFilters() {
            ImmutableList.Builder<IdeFilterData> filters = ImmutableList.builder();
            if (abiFilter != null) {
                filters.add(new IdeFilterDataImpl(abiFilter, OutputFile.ABI));
            }
            return filters.build();
        }

        @NonNull
        @Override
        public File getOutputFile() {
            return file;
        }

        @Override
        public String toString() {
            return "FilteredOutput{" + abiFilter + '}';
        }
    }

    public void testSingleOutput() {
        IdeAndroidArtifactOutput match;
        List<IdeAndroidArtifactOutput> list = new ArrayList<>();

        list.add(match = getUniversalOutput(1));

        List<File> result = computeBestOutput(list, "foo");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testAbiOnlyWithMatch() {
        IdeAndroidArtifactOutput match;
        List<IdeAndroidArtifactOutput> list = new ArrayList<>();

        list.add(getUniversalOutput(1));
        list.add(match = getAbiOutput("foo", 2));
        list.add(getAbiOutput("bar", 3));

        List<File> result = computeBestOutput(list, "foo");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testAbiOnlyWithMultiMatch() {
        IdeAndroidArtifactOutput match;
        List<IdeAndroidArtifactOutput> list = new ArrayList<>();

        // test where the versionCode match the abi order
        list.add(getUniversalOutput(1));
        list.add(getAbiOutput("foo", 2));
        list.add(match = getAbiOutput("bar", 3));

        // bar is preferred over foo
        List<File> result = computeBestOutput(list, "bar", "foo");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testAbiPreference() {
        IdeAndroidArtifactOutput match;
        List<IdeAndroidArtifactOutput> list = new ArrayList<>();

        // test where the versionCode match the abi order
        list.add(getUniversalOutput(1));
        list.add(getAbiOutput("foo", 1));
        list.add(match = getAbiOutput("bar", 1, "bar1"));
        list.add(getAbiOutput("bar", 1, "bar2"));

        // bar is preferred over foo
        List<File> result = computeBestOutput(list, "bar", "foo");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testAbiPreferenceForUniveralApk() {
        IdeAndroidArtifactOutput match;
        List<IdeAndroidArtifactOutput> list = new ArrayList<>();

        // test where the versionCode match the abi order
        list.add(match = getUniversalOutput(1));
        list.add(getAbiOutput("foo", 1));
        list.add(getAbiOutput("foo", 1));
        list.add(getAbiOutput("foo", 1));

        // bar is preferred over foo
        List<File> result = computeBestOutput(list, "bar", "foo");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testAbiOnlyWithMultiMatch2() {
        IdeAndroidArtifactOutput match;
        List<IdeAndroidArtifactOutput> list = new ArrayList<>();

        // test where the versionCode does not match the abi order
        list.add(getUniversalOutput(1));
        list.add(getAbiOutput("foo", 2));
        list.add(match = getAbiOutput("bar", 3));

        // bar is preferred over foo
        List<File> result = computeBestOutput(list, "foo", "bar");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testAbiOnlyWithUniversalMatch() {
        IdeAndroidArtifactOutput match;
        List<IdeAndroidArtifactOutput> list = new ArrayList<>();

        list.add(match = getUniversalOutput(1));
        list.add(getAbiOutput("foo", 2));
        list.add(getAbiOutput("bar", 3));

        List<File> result = computeBestOutput(list, "zzz");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testAbiOnlyWithNoMatch() {
        List<IdeAndroidArtifactOutput> list = new ArrayList<>();

        list.add(getAbiOutput("foo", 1));
        list.add(getAbiOutput("bar", 2));

        List<File> result = computeBestOutput(list, "zzz");

        assertEquals(0, result.size());
    }

    public void testMultiFilterWithMatch() {
        IdeAndroidArtifactOutput match;
        List<IdeAndroidArtifactOutput> list = new ArrayList<>();

        list.add(getUniversalOutput(1));
        list.add(getOutput("zzz", 2));
        list.add(match = getOutput("foo", 4));
        list.add(getOutput("foo", 3));

        List<File> result = computeBestOutput(list, "foo");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testMultiFilterWithUniversalMatch() {
        IdeAndroidArtifactOutput match;
        List<IdeAndroidArtifactOutput> list = new ArrayList<>();

        list.add(match = getUniversalOutput(4));
        list.add(getOutput("zzz", 3));
        list.add(getOutput("bar", 2));
        list.add(getOutput("foo", 1));

        List<File> result = computeBestOutput(list, "zzz");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testMultiFilterWithNoMatch() {
        IdeAndroidArtifactOutput match;
        List<IdeAndroidArtifactOutput> list = new ArrayList<>();

        list.add(match = getOutput("zzz", 1));
        list.add(getOutput("bar", 2));
        list.add(getOutput("foo", 3));

        List<File> result = computeBestOutput(list, "zzz");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testVariantLevelAbiFilter() {
        IdeAndroidArtifactOutput match;
        List<IdeAndroidArtifactOutput> list = new ArrayList<>();

        list.add(match = getUniversalOutput(1));
        List<File> result = computeBestOutput(list, Sets.newHashSet("bar", "foo"), "foo", "zzz");

        assertEquals(1, result.size());
        assertEquals(match.getOutputFile(), result.get(0));
    }

    public void testWrongVariantLevelAbiFilter() {
        List<IdeAndroidArtifactOutput> list = new ArrayList<>();

        list.add(getUniversalOutput(1));

        List<File> result = computeBestOutput(list, Sets.newHashSet("bar", "foo"), "zzz");

        assertEquals(0, result.size());
    }

    private static IdeAndroidArtifactOutput getUniversalOutput(int versionCode) {
        return new FakeSplitOutput(null, null, versionCode);
    }

    private static IdeAndroidArtifactOutput getAbiOutput(String filter, int versionCode) {
        return new FakeSplitOutput(filter, versionCode);
    }

    private static IdeAndroidArtifactOutput getAbiOutput(
            String filter, int versionCode, String file) {
        return new FakeSplitOutput(filter, new File(file), versionCode);
    }

    private static IdeAndroidArtifactOutput getOutput(String abiFilter, int versionCode) {
        return new FakeSplitOutput(abiFilter, versionCode);
    }
}
