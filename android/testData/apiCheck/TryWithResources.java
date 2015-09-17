<error descr="The SDK platform-tools version ((16.0.2)) is too old  to check APIs compiled with API 17; please update">package p1.p2;</error>

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;


public class Class {
    static String readFirstLineFromFile(String path) throws IOException {
        try <error descr="Try-with-resources requires API level 19 (current min is 1)">(BufferedReader br = new BufferedReader(new FileReader(path)))</error> {
            return br.readLine();
        }
    }
}
