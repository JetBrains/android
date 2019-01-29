cd arm
nm -C --line-numbers --demangle --defined-only libnative-lib.so | egrep "\sT\s((Test)|(Java_com_eugene_sum_MainActivity_add)|(Swap)|(JNI_OnLoad))" > symbols.txt
cd ..
cd arm64
nm -C --line-numbers --demangle --defined-only libnative-lib.so | egrep "\sT\s((Test)|(Java_com_eugene_sum_MainActivity_add)|(Swap)|(JNI_OnLoad))" > symbols.txt
cd ..
cd x86
nm -C --line-numbers --demangle --defined-only libnative-lib.so | egrep "\sT\s((Test)|(Java_com_eugene_sum_MainActivity_add)|(Swap)|(JNI_OnLoad))" > symbols.txt
cd ..
cd x86_64
nm -C --line-numbers --demangle --defined-only libnative-lib.so | egrep "\sT\s((Test)|(Java_com_eugene_sum_MainActivity_add)|(Swap)|(JNI_OnLoad))" > symbols.txt
cd ..