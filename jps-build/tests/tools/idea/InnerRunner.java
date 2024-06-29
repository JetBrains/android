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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.junit.internal.MethodSorter;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.*;
import org.junit.runner.notification.RunNotifier;

import java.lang.reflect.Method;
import java.util.*;

import java.lang.reflect.Modifier;

/**
 * This is a test runner that has the same flaws as IntelliJ's JUnit3RunnerWithInners, when
 * run in a sharded environment. JUnit3RunnerWithInners creates nested suites that do not
 * filter well with JUnit38ClassRunner, and it uses a global static variable to know
 * what tests it found. This will not work in a sharded environment. This replicates
 * part of the issues with that runner so it can be used in tests.
 */
public class InnerRunner extends Runner implements Filterable, Sortable {
    private static final Set<Class> requestedRunners = new HashSet<>();

    private JUnit38ClassRunner delegateRunner;
    private final Class<?> klass;

    public InnerRunner(Class<?> klass) {
        this.klass = klass;
        requestedRunners.add(klass);
    }

    @Override
    public void run(RunNotifier notifier) {
        initialize();
        delegateRunner.run(notifier);
    }

    @Override
    public Description getDescription() {
        initialize();
        return delegateRunner.getDescription();
    }

    @Override
    public void filter(Filter filter) throws NoTestsRemainException {
        initialize();
        delegateRunner.filter(filter);
    }

    @Override
    public void sort(Sorter sorter) {
        initialize();
        delegateRunner.sort(sorter);
    }

    protected void initialize() {
        if (delegateRunner != null) return;
        delegateRunner = new JUnit38ClassRunner(getCollectedTests());
    }

    private Test getCollectedTests() {
        return createTreeTestSuite(klass);
    }

    private static Test createTreeTestSuite(Class root) {
        Set<Class> classes = new LinkedHashSet<>(collectDeclaredClasses(root, true));
        Map<Class, TestSuite> classSuites = new HashMap<>();

        for (Class aClass : classes) {
            classSuites.put(aClass, hasTestMethods(aClass) ? new TestSuite(aClass) : new TestSuite(aClass.getCanonicalName()));
        }

        for (Class aClass : classes) {
            if (aClass.getEnclosingClass() != null && classes.contains(aClass.getEnclosingClass())) {
                classSuites.get(aClass.getEnclosingClass()).addTest(classSuites.get(aClass));
            }
        }

        return classSuites.get(root);
    }

    private static List<Class> collectDeclaredClasses(Class klass, boolean withItself) {
        List<Class> result = new ArrayList<>();
        if (withItself) {
            result.add(klass);
        }

        for (Class aClass : klass.getDeclaredClasses()) {
            result.addAll(collectDeclaredClasses(aClass, true));
        }

        return result;
    }

    private static boolean hasTestMethods(Class klass) {
        for (Class currentClass = klass; Test.class.isAssignableFrom(currentClass); currentClass = currentClass.getSuperclass()) {
            for (Method each : MethodSorter.getDeclaredMethods(currentClass)) {
                if (isTestMethod(each)) return true;
            }
        }

        return false;
    }

    static boolean isTestMethod(Method method) {
        return method.getParameterTypes().length == 0 &&
               method.getName().startsWith("test") &&
               method.getReturnType().equals(Void.TYPE) &&
               Modifier.isPublic(method.getModifiers());
    }
}
