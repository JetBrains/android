/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.agent;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.ProtectionDomain;

public final class Agent {
    public static void premain(String agentArgs, Instrumentation inst) throws IOException {
      Path jar = Files.createTempFile("inject", ".jar");
      try (InputStream is = Agent.class.getResourceAsStream("/tools/adt/idea/as-driver/as_driver_inject_deploy.jar")) {
        Files.copy(is, jar, StandardCopyOption.REPLACE_EXISTING);
      }

      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        try {
          Files.delete(jar);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }));
      inst.addTransformer(new ClassFileTransformer() {
        public byte[] transform(ClassLoader loader,
                                String className,
                                Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain,
                                byte[] classfileBuffer) {
          if (className.equals("com/intellij/openapi/project/ProjectManager")) {
            try {
              URLClassLoader child = new URLClassLoader(new URL[] {jar.toUri().toURL()}, loader);
              Class<?> service = Class.forName("com.android.tools.asdriver.inject.AndroidStudioService", true, child);
              service.getMethod("start").invoke(null);
            } catch (Throwable t) {
              t.printStackTrace();
            }
          }
          return classfileBuffer;
        }
      });
    }
}
