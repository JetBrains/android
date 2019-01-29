rm ProfilerTester.zip

# Create a clone of the project that we'll run cleanup steps on
mkdir ProfilerTester
cp -r ../../../../../base/profiler/integration-tests/ProfilerTester/ ProfilerTester/

cd ProfilerTester
git init
git clean -dfX # remove .gitignore files
rm -rf .git
cd ..

zip -9 -r  ProfilerTester.zip ProfilerTester/

rm -rf ProfilerTester
