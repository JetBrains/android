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

import junit.framework.TestCase;
import org.junit.runner.RunWith;

@RunWith(InnerRunner.class)
public class Test3 extends TestCase {

    @RunWith(InnerRunner.class)
    public static class Nested1 extends TestCase {

        public void testNested1() {
            System.out.println("Test3::testNested1");
        }

        public void testNested3() {
            System.out.println("Test3::testNested3");
        }

        public void testNested5() {
            System.out.println("Test3::testNested5");
        }

        public void testNested7() {
            System.out.println("Test3::testNested7");
        }

        public void testNested9() {
            System.out.println("Test3::testNested9");
        }
    }

    @RunWith(InnerRunner.class)
    public static class Nested2 extends TestCase {

        public void testNested2() {
            System.out.println("Test3::testNested2");
        }

        public void testNested4() {
            System.out.println("Test3::testNested4");
        }

        public void testNested6() {
            System.out.println("Test3::testNested6");
        }

        public void testNested8() {
            System.out.println("Test3::testNested8");
        }

        public void testNested10() {
            System.out.println("Test3::testNested10");
        }
    }    
}