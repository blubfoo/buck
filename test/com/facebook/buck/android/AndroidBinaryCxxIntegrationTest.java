/*
 * Copyright 2017-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.android;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.jvm.java.testutil.AbiCompilationModeTest;
import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.testutil.integration.ZipInspector;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AndroidBinaryCxxIntegrationTest extends AbiCompilationModeTest {
  @Rule public TemporaryPaths tmpFolder = new TemporaryPaths();

  private ProjectWorkspace workspace;

  private ProjectFilesystem filesystem;

  @Before
  public void setUp() throws IOException {
    workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            new AndroidBinaryCxxIntegrationTest(), "android_project", tmpFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();
    AssumeAndroidPlatform.get(workspace).assumeNdkIsAvailable();
    setWorkspaceCompilationMode(workspace);
    filesystem = TestProjectFilesystems.createProjectFilesystem(workspace.getDestPath());
  }

  @Test
  public void testCxxLibraryAsAsset() throws IOException {
    String target = "//apps/sample:app_cxx_lib_asset";
    workspace.runBuckCommand("build", target).assertSuccess();
    ZipInspector zipInspector =
        new ZipInspector(
            workspace.getPath(
                BuildTargetPaths.getGenPath(
                    filesystem, BuildTargetFactory.newInstance(target), "%s.apk")));
    zipInspector.assertFileExists("assets/lib/x86/libnative_cxx_libasset.so");
    zipInspector.assertFileDoesNotExist("lib/x86/libnative_cxx_libasset.so");
    zipInspector.assertFileExists("lib/x86/libnative_cxx_foo1.so");
    zipInspector.assertFileExists("lib/x86/libnative_cxx_foo2.so");
    zipInspector.assertFileDoesNotExist("assets/lib/x86/libnative_cxx_foo1.so");
    zipInspector.assertFileDoesNotExist("assets/lib/x86/libnative_cxx_foo2.so");
  }

  @Test
  public void testCxxLibraryAsAssetWithoutPackaging() throws IOException {
    String target = "//apps/sample:app_cxx_lib_asset_no_package";
    workspace.runBuckCommand("build", target).assertSuccess();
    ZipInspector zipInspector =
        new ZipInspector(
            workspace.getPath(
                BuildTargetPaths.getGenPath(
                    filesystem, BuildTargetFactory.newInstance(target), "%s.apk")));
    zipInspector.assertFileDoesNotExist("assets/lib/x86/libnative_cxx_libasset.so");
    zipInspector.assertFileExists("lib/x86/libnative_cxx_libasset.so");
  }

  @Test
  public void testCxxLibraryDep() throws IOException {
    String target = "//apps/sample:app_cxx_lib_dep";
    workspace.runBuckCommand("build", target).assertSuccess();

    ZipInspector zipInspector =
        new ZipInspector(
            workspace.getPath(
                BuildTargetPaths.getGenPath(
                    filesystem, BuildTargetFactory.newInstance(target), "%s.apk")));
    zipInspector.assertFileExists("lib/armeabi-v7a/libnative_cxx_lib.so");
    zipInspector.assertFileExists("lib/x86/libnative_cxx_lib.so");
    if (AssumeAndroidPlatform.get(workspace).isGnuStlAvailable()) {
      zipInspector.assertFileExists("lib/armeabi-v7a/libgnustl_shared.so");
      zipInspector.assertFileExists("lib/x86/libgnustl_shared.so");
    } else {
      zipInspector.assertFileExists("lib/armeabi-v7a/libc++_shared.so");
      zipInspector.assertFileExists("lib/x86/libc++_shared.so");
    }
  }

  @Test
  public void testCxxLibraryDepStaticRuntime() throws IOException {
    String target = "//apps/sample:app_cxx_lib_dep";
    workspace.runBuckCommand("build", "-c", "ndk.cxx_runtime_type=static", target).assertSuccess();

    ZipInspector zipInspector =
        new ZipInspector(
            workspace.getPath(
                BuildTargetPaths.getGenPath(
                    filesystem, BuildTargetFactory.newInstance(target), "%s.apk")));
    zipInspector.assertFileExists("lib/armeabi-v7a/libnative_cxx_lib.so");
    zipInspector.assertFileExists("lib/x86/libnative_cxx_lib.so");
    if (AssumeAndroidPlatform.get(workspace).isGnuStlAvailable()) {
      zipInspector.assertFileDoesNotExist("lib/armeabi-v7a/libgnustl_shared.so");
      zipInspector.assertFileDoesNotExist("lib/x86/libgnustl_shared.so");
    } else {
      zipInspector.assertFileDoesNotExist("lib/armeabi-v7a/libc++_shared.so");
      zipInspector.assertFileDoesNotExist("lib/x86/libc++_shared.so");
    }
  }

  @Test
  public void testCxxLibraryDepModular() throws IOException {
    String target = "//apps/sample:app_cxx_lib_dep_modular";
    workspace.runBuckCommand("build", target).assertSuccess();

    ZipInspector zipInspector =
        new ZipInspector(
            workspace.getPath(
                BuildTargetPaths.getGenPath(
                    filesystem, BuildTargetFactory.newInstance(target), "%s.apk")));
    zipInspector.assertFileDoesNotExist("lib/armeabi-v7a/libnative_cxx_lib.so");
    zipInspector.assertFileDoesNotExist("lib/x86/libnative_cxx_lib.so");
    if (AssumeAndroidPlatform.get(workspace).isGnuStlAvailable()) {
      zipInspector.assertFileExists("lib/armeabi-v7a/libgnustl_shared.so");
      zipInspector.assertFileExists("lib/x86/libgnustl_shared.so");
    } else {
      zipInspector.assertFileExists("lib/armeabi-v7a/libc++_shared.so");
      zipInspector.assertFileExists("lib/x86/libc++_shared.so");
    }
    zipInspector.assertFileExists("assets/native.cxx.lib/libs.txt");
    zipInspector.assertFileExists("assets/native.cxx.lib/libs.xzs");
  }

  @Test
  public void testCxxLibraryDepClang() throws IOException {
    String target = "//apps/sample:app_cxx_lib_dep";
    ProcessResult result =
        workspace.runBuckCommand(
            "build", "-c", "ndk.compiler=clang", "-c", "ndk.cxx_runtime=libcxx", target);
    result.assertSuccess();

    ZipInspector zipInspector =
        new ZipInspector(
            workspace.getPath(
                BuildTargetPaths.getGenPath(
                    filesystem, BuildTargetFactory.newInstance(target), "%s.apk")));
    zipInspector.assertFileExists("lib/armeabi-v7a/libnative_cxx_lib.so");
    zipInspector.assertFileExists("lib/armeabi-v7a/libc++_shared.so");
    zipInspector.assertFileExists("lib/x86/libnative_cxx_lib.so");
    zipInspector.assertFileExists("lib/x86/libc++_shared.so");
  }

  @Test
  public void testCxxLibraryDepWithNoFilters() throws IOException {
    String target = "//apps/sample:app_cxx_lib_dep_no_filters";
    workspace.runBuckCommand("build", target).assertSuccess();

    ZipInspector zipInspector =
        new ZipInspector(
            workspace.getPath(
                BuildTargetPaths.getGenPath(
                    filesystem, BuildTargetFactory.newInstance(target), "%s.apk")));
    if (AssumeAndroidPlatform.get(workspace).isArmAvailable()) {
      zipInspector.assertFileExists("lib/armeabi/libnative_cxx_lib.so");
    }
    zipInspector.assertFileExists("lib/armeabi-v7a/libnative_cxx_lib.so");
    zipInspector.assertFileExists("lib/x86/libnative_cxx_lib.so");
  }

  @Test
  public void testNoCxxDepsDoesNotIncludeNdkRuntime() throws IOException {
    String target = "//apps/sample:app_no_cxx_deps";
    workspace.runBuckCommand("build", target).assertSuccess();

    ZipInspector zipInspector =
        new ZipInspector(
            workspace.getPath(
                BuildTargetPaths.getGenPath(
                    filesystem, BuildTargetFactory.newInstance(target), "%s.apk")));
    if (AssumeAndroidPlatform.get(workspace).isGnuStlAvailable()) {
      zipInspector.assertFileDoesNotExist("lib/armeabi-v7a/libgnustl_shared.so");
      zipInspector.assertFileDoesNotExist("lib/x86/libgnustl_shared.so");
    } else {
      zipInspector.assertFileDoesNotExist("lib/armeabi-v7a/libc++_shared.so");
      zipInspector.assertFileDoesNotExist("lib/x86/libc++_shared.so");
    }
  }

  @Test
  public void testStaticCxxLibraryDep() throws IOException {
    String target = "//apps/sample:app_static_cxx_lib_dep";
    workspace.runBuckCommand("build", target).assertSuccess();

    ZipInspector zipInspector =
        new ZipInspector(
            workspace.getPath(
                BuildTargetPaths.getGenPath(
                    filesystem, BuildTargetFactory.newInstance(target), "%s.apk")));
    zipInspector.assertFileExists("lib/x86/libnative_cxx_foo1.so");
    zipInspector.assertFileExists("lib/x86/libnative_cxx_foo2.so");
    zipInspector.assertFileDoesNotExist("lib/x86/libnative_cxx_bar.so");
  }

  @Test
  public void testHeaderOnlyCxxLibrary() throws IOException {
    String target = "//apps/sample:app_header_only_cxx_lib_dep";
    workspace.runBuckCommand("build", target).assertSuccess();
    ZipInspector zipInspector =
        new ZipInspector(
            workspace.getPath(
                BuildTargetPaths.getGenPath(
                    filesystem, BuildTargetFactory.newInstance(target), "%s.apk")));
    zipInspector.assertFileDoesNotExist("lib/x86/libnative_cxx_headeronly.so");
  }

  @Test
  public void testX86OnlyCxxLibrary() throws IOException {
    String target = "//apps/sample:app_with_x86_lib";
    workspace.runBuckCommand("build", target).assertSuccess();
    ZipInspector zipInspector =
        new ZipInspector(
            workspace.getPath(
                BuildTargetPaths.getGenPath(
                    filesystem, BuildTargetFactory.newInstance(target), "%s.apk")));
    zipInspector.assertFileDoesNotExist("lib/armeabi-v7a/libnative_cxx_x86-only.so");
    zipInspector.assertFileDoesNotExist("lib/armeabi/libnative_cxx_x86-only.so");
    zipInspector.assertFileExists("lib/x86/libnative_cxx_x86-only.so");
    if (AssumeAndroidPlatform.get(workspace).isGnuStlAvailable()) {
      zipInspector.assertFileDoesNotExist("lib/armeabi-v7a/libgnustl_shared.so");
      zipInspector.assertFileDoesNotExist("lib/armeabi/libgnustl_shared.so");
      zipInspector.assertFileExists("lib/x86/libgnustl_shared.so");
    } else {
      zipInspector.assertFileDoesNotExist("lib/armeabi-v7a/libc++_shared.so");
      zipInspector.assertFileDoesNotExist("lib/armeabi/libc++_shared.so");
      zipInspector.assertFileExists("lib/x86/libc++_shared.so");
    }
  }

  @Test
  public void testCxxLibraryDepWithUnifiedHeaders() throws IOException {
    AssumeAndroidPlatform.get(workspace).assumeUnifiedHeadersAvailable();

    String target = "//apps/sample:app_cxx_lib_dep";
    workspace.addBuckConfigLocalOption("ndk", "use_unified_headers", "true");
    workspace.runBuckCommand("build", target).assertSuccess();
  }

  @Test
  public void testCxxLibraryDepWithConstraints() throws IOException {
    String target = "//apps/sample:app_cxx_lib_dep_with_constraints_without_cpu_map";
    workspace
        .runBuckCommand("build", target, "--target-platforms", "//:android-x86_32-armv7")
        .assertSuccess();

    ZipInspector zipInspector =
        new ZipInspector(
            workspace.getPath(
                BuildTargetPaths.getGenPath(
                    filesystem, BuildTargetFactory.newInstance(target), "%s.apk")));
    zipInspector.assertFileExists("lib/armeabi-v7a/libnative_cxx_lib-with-platform-deps.so");
    zipInspector.assertFileDoesNotExist("lib/armeabi-v7a/libnative_cxx_x86-only-2.so");
    zipInspector.assertFileExists("lib/x86/libnative_cxx_lib-with-platform-deps.so");
    zipInspector.assertFileExists("lib/x86/libnative_cxx_x86-only-2.so");
    if (AssumeAndroidPlatform.get(workspace).isGnuStlAvailable()) {
      zipInspector.assertFileExists("lib/armeabi-v7a/libgnustl_shared.so");
      zipInspector.assertFileExists("lib/x86/libgnustl_shared.so");
    } else {
      zipInspector.assertFileExists("lib/armeabi-v7a/libc++_shared.so");
      zipInspector.assertFileExists("lib/x86/libc++_shared.so");
    }
  }

  @Test
  public void cannotBuildBinaryWithAndroidPlatformAndWithoutCpuMap() {
    String target = "//apps/sample:app_cxx_lib_dep_with_constraints_without_cpu_map";
    ProcessResult result =
        workspace.runBuckCommand("build", target, "--target-platforms", "//:android-x86_64-arm");

    result.assertFailure();
    assertThat(
        result.getStderr(),
        containsString(
            "//apps/sample:app_cxx_lib_dep_with_constraints_without_cpu_map: "
                + "nested platform is not found for ARMV7 CPU type "
                + "in platform //:android-x86_64-arm"));
  }
}
