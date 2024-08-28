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