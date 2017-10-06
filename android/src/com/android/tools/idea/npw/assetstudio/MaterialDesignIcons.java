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
package com.android.tools.idea.npw.assetstudio;

import static com.android.SdkConstants.DOT_XML;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class MaterialDesignIcons {

    public static final String PATH = "images/material_design_icons/";
    private static final Pattern CATEGORY = Pattern.compile(PATH + "(\\w+)/");

    private MaterialDesignIcons() {
    }

    @Nullable
    public static String getPathForBasename(@NonNull String basename) {
        return getBasenameToPathMap(path -> GraphicGenerator.getResourcesNames(path, DOT_XML))
                .get(basename);
    }

    @NonNull
    @VisibleForTesting
    static Map<String, String> getBasenameToPathMap(
            @NonNull Function<String, Iterator<String>> generator) {
        ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
        int dotXmlLength = DOT_XML.length();

        for (String category : getCategories()) {
            String path = PATH + category + '/';

            for (Iterator<String> i = generator.apply(path); i.hasNext(); ) {
                String name = i.next();
                builder.put(name.substring(0, name.length() - dotXmlLength), path + name);
            }
        }

        return builder.build();
    }

    @NonNull
    public static Collection<String> getCategories() {
        return getCategories(GraphicGenerator.class.getClassLoader().getResource(PATH));
    }

    @VisibleForTesting
    static Collection<String> getCategories(@Nullable URL url) {
        if (url == null) {
            return Collections.emptyList();
        }

        switch (url.getProtocol()) {
            case "file":
                return getCategoriesFromFile(new File(url.getPath()));
            case "jar":
                try {
                    JarURLConnection connection = (JarURLConnection) url.openConnection();
                    return getCategoriesFromJar(connection.getJarFile());
                } catch (IOException exception) {
                    return Collections.emptyList();
                }
            default:
                return Collections.emptyList();
        }
    }

    @NonNull
    @VisibleForTesting
    static Collection<String> getCategoriesFromFile(@NonNull File file) {
        String[] array = file.list();

        if (array == null) {
            return Collections.emptyList();
        }

        List<String> list = Arrays.asList(array);
        list.sort(String::compareTo);

        return list;
    }

    @NonNull
    @VisibleForTesting
    static Collection<String> getCategoriesFromJar(@NonNull ZipFile jar) {
        return jar.stream()
                .map(MaterialDesignIcons::getCategory)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
    }

    @Nullable
    private static String getCategory(@NonNull ZipEntry entry) {
        Matcher matcher = CATEGORY.matcher(entry.getName());
        return matcher.matches() ? matcher.group(1) : null;
    }
}
