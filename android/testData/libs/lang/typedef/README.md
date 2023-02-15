To regenerate Library.jar run the following, assuming your `SRC` environment
variable points to the root of the source tree.

```shell
THIS_DIRECTORY="tools/adt/idea/android/testData/libs/lang/typedef"
BAZEL_OUTPUT_DIR="$SRC/bazel-bin/$THIS_DIRECTORY/jar"
TARGET_NAME="TypeDefCompletionContributorTestLibrary"
OUTPUT_FILE_NAME="Library.jar"
echo "Building source JAR" && \
bazel build //$THIS_DIRECTORY/jar:$TARGET_NAME-src.jar &> /dev/null && \
echo "Building class JAR" && \
bazel build //$THIS_DIRECTORY/jar:lib$TARGET_NAME.jar &> /dev/null && \
mkdir -p /tmp/jarmerge/unzipped && \
cp $BAZEL_OUTPUT_DIR/$TARGET_NAME-src.jar /tmp/jarmerge/ && \
cp $BAZEL_OUTPUT_DIR/lib$TARGET_NAME.jar /tmp/jarmerge/ && \
cd /tmp/jarmerge/unzipped && \
jar -xf ../$TARGET_NAME-src.jar && \
jar -xf ../lib$TARGET_NAME.jar && \
echo "Merging JARs" && \
jar -cf ../$OUTPUT_FILE_NAME . && \
cd - &> /dev/null && \
echo "Copying merged JAR to $SRC/$THIS_DIRECTORY/$OUTPUT_FILE_NAME" && \
cp /tmp/jarmerge/$OUTPUT_FILE_NAME $SRC/$THIS_DIRECTORY/ ;\
echo "Cleaning up"; \
rm /tmp/jarmerge -rf
```
