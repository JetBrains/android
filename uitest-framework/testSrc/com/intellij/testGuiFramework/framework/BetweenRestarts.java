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
package com.intellij.testGuiFramework.framework;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** When a test uses RestartUtils.restartIdeBetween(), methods in that test class annotated with @BetweenRestarts will be run on
 * the server after closing the first client IDE and before starting the second. This provides a place to do things like run the patcher.
 * <p>
 * Note:
 * <ol>
 *   <li> If there are multiple test methods in the same class that do an IDE restart, they will share @BetweenRestarts code.
 *   <li> If a test restarts the IDE multiple times, the @BetweenRestarts code will be run every time the IDE is restarted.
 *   <li> If there are multiple methods in a test class annotated with @BetweenRestarts, their execution order is undefined.
 * </ol>
*/
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BetweenRestarts {
}
