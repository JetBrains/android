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

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Test;

@RunWith(JUnit4.class)
public class Test1 {

    @Test
    public void testDownloadFile() {
        System.out.println("Test1::testDownloadFile");
        Path down = Path.of("test_download");
        down.toFile().mkdirs();
        Path artifact = down.resolve("artifact.txt");
        boolean isBazelRun = System.getenv("BUILD_WORKSPACE_DIRECTORY") != null;
        if (isBazelRun) {
            try {
                new FileOutputStream(artifact.toFile()).close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            if (!Files.exists(artifact)) {
                throw new RuntimeException("Downloaded artifact does not exist.");
            }
        }
    }

    @Test
    public void testOther() {
        System.out.println("Test1::testOther");
    }

    @Test
    public void testFilterIndividualTest() {
        throw new RuntimeException("I always fail");
    }
}