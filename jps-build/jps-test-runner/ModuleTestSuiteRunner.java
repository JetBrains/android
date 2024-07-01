/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.test;

import java.io.IOException;
import java.lang.Class;
import java.lang.String;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

public class ModuleTestSuiteRunner extends Suite {

    public ModuleTestSuiteRunner(Class<?> suiteClass, RunnerBuilder builder)
            throws InitializationError, ClassNotFoundException, IOException {
        super(new ModuleRunnerBuilder(builder), getTestClasses(suiteClass));
    }



    private static Class<?>[] getTestClasses(Class<?> suiteClass) throws IOException {
        String toolsIdea = System.getProperty("idea.root");

        Set<String> ignore = new HashSet();
        String env = System.getenv("IGNORE_SUITES");
        if (env != null) {
            ignore.addAll(Arrays.asList(env.split(":")));
        }

        String module = System.getenv("TEST_MODULE");
        Path dir = Paths.get(toolsIdea + "/out/studio/classes/test/" + module);
        List<Path> classes = Files.walk(dir)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".class"))
            .collect(Collectors.toList());
        ArrayList<Class<?>> suites = new ArrayList<>();
        for (Path cl : classes) {
            String name = dir.relativize(cl).toString();
            name = name.replaceAll("/", ".");
            name = name.replaceFirst("\\.class$", "");
            if (ignore.contains(name)) {
                continue;
            }
            try {
                Class<?> aClass = Class.forName(name);
                if (aClass.getAnnotation(RunWith.class) != null) {
                    suites.add(aClass);
                }
            } catch (ClassNotFoundException e) {
                // Ignore
            }
        }
        return suites.toArray(new Class[]{});
    }
}
