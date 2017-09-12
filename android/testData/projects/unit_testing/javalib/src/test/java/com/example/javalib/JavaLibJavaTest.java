package com.example.javalib;

import org.junit.Test;

import java.io.InputStream;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class JavaLibJavaTest {
    @Test
    public void referenceJavaLibJavaClass() throws Exception {
        assertEquals("JavaLibJavaClass", new JavaLibJavaClass().getName());
    }

    @Test
    public void referenceJavaLibKotlinClass() throws Exception {
        assertEquals("JavaLibKotlinClass", new JavaLibKotlinClass().getName());
    }

    @Test
    public void prodJavaResourcesOnClasspath() throws Exception {
        URL url = getClass().getClassLoader().getResource("javalib_resource_file.txt");
        assertNotNull(url);

        InputStream stream = getClass().getClassLoader().getResourceAsStream("javalib_resource_file.txt");
        assertNotNull(stream);
        byte[] line = new byte[1024];
        assertTrue("Expected >0 bytes read from input stream", stream.read(line) > 0);
        String s = new String(line, "UTF-8").trim();
        assertEquals("javalib", s);
    }

    @Test
    public void javaResourcesOnClasspath() throws Exception {
        URL url = getClass().getClassLoader().getResource("javalib_test_resource_file.txt");
        assertNotNull(url);

        InputStream stream = getClass().getClassLoader().getResourceAsStream("javalib_test_resource_file.txt");
        assertNotNull(stream);
        byte[] line = new byte[1024];
        assertTrue("Expected >0 bytes read from input stream", stream.read(line) > 0);
        String s = new String(line, "UTF-8").trim();
        assertEquals("javalib test", s);
    }
}
