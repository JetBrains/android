#include <sys/stat.h>
#include <stdio.h>

int main(int argc, char** args) {
    struct stat st;
    char* filename = args[1];
    
    if (stat(filename, &st) < 0) {
        perror(filename);
        return 1;
    }
    printf("%lu\n", st.st_mtime);
    return 0;
}

