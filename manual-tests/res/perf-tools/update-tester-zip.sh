rm ProfilerTester.zip
pushd ../../../../../base/profiler/integration-tests/
zip -9 -r  ProfilerTester.zip ProfilerTester/
popd
mv ../../../../../base/profiler/integration-tests/ProfilerTester.zip .