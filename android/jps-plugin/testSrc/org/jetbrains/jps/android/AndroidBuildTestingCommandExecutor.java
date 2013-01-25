package org.jetbrains.jps.android;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.util.AndroidBuildTestingManager;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
abstract class AndroidBuildTestingCommandExecutor implements AndroidBuildTestingManager.MyCommandExecutor {

  private volatile StringWriter myStringWriter = new StringWriter();
  private final Map<String, Pattern> myPathPrefixes = new HashMap<String, Pattern>();

  public void addPathPrefix(@NotNull String id, @NotNull String prefix) {
    myPathPrefixes.put(id, Pattern.compile("(" + FileUtil.toSystemIndependentName(prefix) + ").*"));
  }

  public void addRegexPathPrefix(@NotNull String id, @NotNull String regex) {
    myPathPrefixes.put(id, Pattern.compile("(" + regex + ").*"));
  }

  @NotNull
  @Override
  public Process createProcess(@NotNull String[] args, @NotNull Map<? extends String, ? extends String> environment) {
    final String[] argsToLog = processArgs(args);
    logString(StringUtil.join(argsToLog, "\n"));

    if (environment.size() > 0) {
      logString("\nenv: " + environment.toString());
    }
    logString("\n\n");
    try {
      return doCreateProcess(args, environment);
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void log(@NotNull String s) {
    final String[] args = s.split("\\n");
    logString(StringUtil.join(processArgs(args), "\n"));
    logString("\n\n");
  }

  private synchronized void logString(String s) {
    myStringWriter.write(s);
  }

  private String[] processArgs(String[] args) {
    final String[] result = new String[args.length];

    for (int i = 0; i < result.length; i++) {
      String s = FileUtil.toSystemIndependentName(args[i]);

      for (Map.Entry<String, Pattern> entry : myPathPrefixes.entrySet()) {
        final Pattern prefixPattern = entry.getValue();
        final String id = entry.getKey();
        final Matcher matcher = prefixPattern.matcher(s);

        if (matcher.matches()) {
          s = "$" + id + "$" + s.substring(matcher.group(1).length());
        }
      }
      result[i] = s;
    }
    return result;
  }

  @NotNull
  protected abstract Process doCreateProcess(@NotNull String[] args, @NotNull Map<? extends String, ? extends String> environment)
    throws Exception;

  @NotNull
  public synchronized String getLog() {
    return myStringWriter.toString();
  }

  public synchronized void clear() {
    myStringWriter = new StringWriter();
  }

  protected static class MyProcess extends Process {

    private final int myExitValue;
    private final String myOutputText;
    private final String myErrorText;

    protected MyProcess(int exitValue, @NotNull String outputText, @NotNull String errorText) {
      myExitValue = exitValue;
      myOutputText = outputText;
      myErrorText = errorText;
    }

    @Override
    public OutputStream getOutputStream() {
      throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getInputStream() {
      return stringToInputStream(myOutputText);
    }

    @Override
    public InputStream getErrorStream() {
      return stringToInputStream(myErrorText);
    }

    private static ByteArrayInputStream stringToInputStream(String s) {
      return new ByteArrayInputStream(s.getBytes(Charset.defaultCharset()));
    }

    @Override
    public int waitFor() throws InterruptedException {
      return exitValue();
    }

    @Override
    public int exitValue() {
      return myExitValue;
    }

    @Override
    public void destroy() {
    }
  }
}
