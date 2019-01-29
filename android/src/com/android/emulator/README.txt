The 'snapshot.proto' file in this directory must be synchronized
with the Emulator's file:
    external/qemu/android/android-emu/android/snapshot/proto/snapshot.proto
    on branch aosp/emu-master-dev

The snapshot.proto file here must be MANUALLY compiled to Java using 'aprotoc'.
'aprotoc' can be found in the System image, e.g.
<aosp-master>/prebuilts/misc/linux-x86/protobuf/aprotoc

Compile using the command
    aprotoc --java_out=../../../../gen snapshot.proto
which will create SnapshotOuterClass.java.


If you make a change to the protobuf, you must check in both
    tools/adt/idea/android/src/com/android/emulator/snapshot.proto
and                        !!!
    tools/adt/idea/android/gen/com/android/emulator/SnapshotOuterClass.java
