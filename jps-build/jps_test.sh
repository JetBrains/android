#!/bin/sh
mkdir -p .test

cat $JVM_ARGS_FILE > .test/args.txt

echo '-ea' >> .test/args.txt
echo "-Djava.util.prefs.userRoot=$PWD/.test"  >> .test/args.txt
echo "-Djava.util.prefs.systemRoot=$PWD/.test"  >> .test/args.txt
echo '-Didea.classpath.index.enabled=true' >> .test/args.txt
echo "-Didea.system.path=$JPS_WORKSPACE/system" >> .test/args.txt
echo "-Duser.home=$JPS_WORKSPACE/home" >> .test/args.txt
echo "-Didea.test.workspace=$JPS_WORKSPACE" >> .test/args.txt
echo "-Didea.config.path=$JPS_WORKSPACE/config" >> .test/args.txt
echo '-Didea.test.cyclic.buffer.size=1048576' >> .test/args.txt
echo "-Dbazel.test_suite=$TEST_SUITE" >> .test/args.txt
echo "-Didea.root=$PWD" >> .test/args.txt
echo "-Djava.security.manager=allow" >> .test/args.txt
echo "-Dmaven.repo.for.testing=file://$JPS_WORKSPACE/maven" >> .test/args.txt
echo "--add-opens=java.base/sun.net.www.protocol.https=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/java.io=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/java.lang=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/java.lang.ref=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/java.net=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/java.nio=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/java.nio.charset=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/java.text=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/java.time=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/java.util=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/sun.net.dns=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.base/sun.security.util=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/com.sun.java.swing=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/com.sun.java.swing.plaf.gtk=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/java.awt=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/java.awt.image=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/javax.swing=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/sun.awt=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/sun.font=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.desktop/sun.swing=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=java.management/sun.management=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED" >> .test/args.txt
echo "--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED"
echo -n "-cp $RUNTIME_DEPS:" >> .test/args.txt
cat out/studio/artifacts/module-tests/$TEST_MODULE.classpath.txt | sed "s#^#../../#" | tr '\n' ':' >> .test/args.txt
echo '' >> .test/args.txt

if [ -n "$TEST_EXCLUDE_FILTER"  ]; then
  _test_exclude_filter="--test_exclude_filter=$TEST_EXCLUDE_FILTER"
fi

if [ -n "$TEST_FILTER"  ]; then
  _test_filter="--test_filter=$TEST_FILTER"
fi

$JAVA_BIN @.test/args.txt com.google.testing.junit.runner.BazelTestRunner $_test_filter $_test_exclude_filter