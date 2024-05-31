
# Building tools/idea in bazel

This package contains the rules `jps_library` and `jps_test` that provide a sandboxed skeleton to run `tools/idea` (actually any arbitrary) builds via bazel. Here bazel is only used a a dispatcher of actions that have a well known set of inputs, and produce an output.

For `jps_build` the input is the
generally the source in `tools/idea` and the output is the compilation of a module and its transitive dependencies (equivalent to a `_deploy.jar`).

For `jps_test` the inputs are the source and the `jps_build` output, and it runs a test using the bazel runner, to obtain reporting, sharding and sandboxing.

## Network access

Both the build and the test execution require network access if run for the first time. These rules provide the ability to `bazel run` both the build and the test to capture the downloads. This downloads are stored in the repository as cache directories, that can be later used as inputs of the rules to avoid the network access and enable sandboxed execution.

# jps_library

```python
jps_library(
        name,
        module,
        deps,
        download_cache,
        cmd,
)
```

`jps_library` generates a `.zip` file containing the output of the compilation of a given module and its transitive dependencies. The `.zip` is not a default output of the rule, and it's named `${name}.zip`.

`jps_library` can be executed with `bazel run`. In this mode it will run the build without a sandbox and copy all donwloaded data to the `download_cache` location.

<table class="table table-condensed table-bordered table-params">
  <colgroup>
    <col class="col-param" />
    <col class="param-description" />
  </colgroup>
  <thead>
    <tr>
      <th colspan="2">Attributes</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>name</code></td>
      <td>
        <p><code>Name, required</code></p>
        <p>A unique name for this target</p>
      </td>
    <tr>
      <td><code>module</code></td>
      <td>
        <p><code>String, required</code></p>
        <p>The name of the module to compile</p>
        <p>
          The tests and the production code of this module are compiled.
        </p>
      </td>
    </tr>
    </tr>
      <td><code>deps</code></td>
      <td>
        <p><code>List of labels, required</code></p>
        <p>List of targets to set up the workspace with. They can be zip files that are expanded in the temporary workspace, or <code>jps_*</code> rules.</p>
      </td>
    </tr>
    <tr>
      <td><code>download_cache</code></td>
      <td>
        <p><code>String, optional</code></p>
        <p>The workspace directory to save the downloaded artifacts to</p>
        <p>
          This is only used when <code>bazel run</code> on this target is invoked.
        </p>
      </td>
    </tr>
    <tr>
      <td><code>cmd</code></td>
      <td>
        <p><code>String, optional</code></p>
        <p>The command to execute in the repo. Useful for tests. Default is <code>platform/jps-bootstrap/jps-bootstrap.sh</code>. The working directory for this command will be <code>tools/idea</code> in the temporary workspace.</p>
      </td>
    </tr>
  </tbody>
</table>

# jps_test

```python
jps_test(
        name,
        module,
        deps,
        download_cache,
        test_suite,
)
```

`jps_test` runs the given `test_suite` on a test with the class path of the given `module`. This would usually depend on the `jps_library` that builds the module in question.

`jps_test` can be executed with `bazel run`. In this mode it will run the build without a sandbox and copy all donwloaded data to the `download_cache` location.

<table class="table table-condensed table-bordered table-params">
  <colgroup>
    <col class="col-param" />
    <col class="param-description" />
  </colgroup>
  <thead>
    <tr>
      <th colspan="2">Attributes</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>name</code></td>
      <td>
        <p><code>Name, required</code></p>
        <p>A unique name for this target</p>
      </td>
    </tr>
      <td><code>deps</code></td>
      <td>
        <p><code>List of labels, required</code></p>
        <p>List of targets to set up the workspace with. They can be zip files that are expanded in the temporary workspace, or <code>jps_*</code> rules.</p>
      </td>
    </tr>
    <tr>
      <td><code>module</code></td>
      <td>
        <p><code>String, required</code></p>
        <p>The name of the module to test with</p>
        <p>
          The module to use to configure the classpath of the test suite.
        </p>
      </td>
    </tr>
    <tr>
      <td><code>download_cache</code></td>
      <td>
        <p><code>String, optional</code></p>
        <p>The workspace directory to save the downloaded artifacts to</p>
        <p>
          This is only used when <code>bazel run</code> on this target is invoked.
        </p>
      </td>
    </tr>
    <tr>
      <td><code>test_suite</code></td>
      <td>
        <p><code>String, required</code></p>
        <p>The test suite to use</p>
      </td>
    </tr>
  </tbody>
</table>

# jps_cache

```python
jps_cache(
        name,
        srcs,
        strip_prefix,
)
```
`jps_cache` represents a group of files that can be used to set up a `jps_library` or `jps_test` workspace.

<table class="table table-condensed table-bordered table-params">
  <colgroup>
    <col class="col-param" />
    <col class="param-description" />
  </colgroup>
  <thead>
    <tr>
      <th colspan="2">Attributes</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>name</code></td>
      <td>
        <p><code>Name, required</code></p>
        <p>A unique name for this target</p>
      </td>
    </tr>
      <td><code>srcs</code></td>
      <td>
        <p><code>List of labels, required</code></p>
        <p>The list of files to use</p>
      </td>
    </tr>
    <tr>
      <td><code>strip_prefix</code></td>
      <td>
        <p><code>String, required</code></p>
        <p>The path prefix to be removed from the given list of files</p>
      </td>
    </tr>
  </tbody>
</table>
