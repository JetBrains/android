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
package com.android.templates;

public class GeneralTemplates {
    private static interface UnaryMethodCall {
        Object $method(Object o);
    }
    private static class $Type {}
    private static interface $Interface {}
    private static void getFragment(String fragmentName, Object controller) {}

    public static void call(UnaryMethodCall $target, Void $method, Object $argument) {
        $target.$method($argument);
    }

    public static void defineAssignment(String $fragmentName, Void $messageController) {
        getFragment($fragmentName, $messageController);
    }

    public static void defineInnerClass(Class $Interface, Void $method, Class $Type, Object $arg, final Statement $f) {
        new $Interface() {
            public void $method($Type $arg) {
                $f.$();
            }
        };
    }



}
