/*
 * Copyright 2016-present Facebook, Inc.
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

package com.facebook.buck.doctor;

import static com.facebook.buck.doctor.DoctorTestUtils.createDefectReport;
import static com.facebook.buck.doctor.DoctorTestUtils.createDoctorConfig;
import static com.facebook.buck.doctor.DoctorTestUtils.createDoctorHelper;
import static org.hamcrest.junit.MatcherAssume.assumeThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.core.config.FakeBuckConfig;
import com.facebook.buck.doctor.config.BuildLogEntry;
import com.facebook.buck.doctor.config.DoctorConfig;
import com.facebook.buck.doctor.config.DoctorEndpointRequest;
import com.facebook.buck.doctor.config.DoctorEndpointResponse;
import com.facebook.buck.doctor.config.DoctorJsonResponse;
import com.facebook.buck.doctor.config.DoctorProtocolVersion;
import com.facebook.buck.doctor.config.DoctorSuggestion;
import com.facebook.buck.doctor.config.ImmutableDoctorConfig;
import com.facebook.buck.doctor.config.ImmutableDoctorEndpointResponse;
import com.facebook.buck.doctor.config.ImmutableDoctorJsonResponse;
import com.facebook.buck.doctor.config.ImmutableDoctorSuggestion;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.TestBuildEnvironmentDescription;
import com.facebook.buck.testutil.TestConsole;
import com.facebook.buck.testutil.integration.HttpdForTests;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.testutil.integration.ZipInspector;
import com.facebook.buck.util.BuckConstant;
import com.facebook.buck.util.Console;
import com.facebook.buck.util.DefaultProcessExecutor;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.json.ObjectMappers;
import com.facebook.buck.util.versioncontrol.NoOpCmdLineInterface;
import com.facebook.buck.util.versioncontrol.VersionControlStatsGenerator;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DoctorCommandIntegrationTest {

  @Rule public TemporaryPaths tempFolder = new TemporaryPaths();
  private ProjectWorkspace workspace;
  private ProjectFilesystem filesystem;
  private HttpdForTests httpd;

  private UserInputFixture userInputFixture;
  private DoctorEndpointResponse doctorResponse;
  private DefectSubmitResult reportResult;

  private final AtomicReference<String> requestMethod = new AtomicReference<>();
  private final AtomicReference<String> requestPath = new AtomicReference<>();
  private final AtomicReference<byte[]> requestBody = new AtomicReference<>();

  private static final String LOG_PATH =
      BuckConstant.getBuckOutputPath()
          .resolve("log")
          .resolve("2016-06-21_16h16m24s_buildcommand_ac8bd626-6137-4747-84dd-5d4f215c876c")
          .resolve(BuckConstant.BUCK_LOG_FILE_NAME)
          .toString();
  private static final String BUILD_COMMAND_DIR_PATH =
      "buck-out/log/" + "2016-06-21_16h16m24s_buildcommand_ac8bd626-6137-4747-84dd-5d4f215c876c/";

  @Before
  public void setUp() throws Exception {
    userInputFixture = new UserInputFixture("0");

    filesystem = TestProjectFilesystems.createProjectFilesystem(tempFolder.getRoot());
    workspace = TestDataHelper.createProjectWorkspaceForScenario(this, "report", tempFolder);
    workspace.setUp();

    reportResult =
        DefectSubmitResult.builder()
            .setIsRequestSuccessful(true)
            .setReportSubmitErrorMessage(Optional.empty())
            .setReportSubmitMessage("This is a json/string generated by the remote server.")
            .setRequestProtocol(DoctorProtocolVersion.JSON)
            .build();

    doctorResponse =
        new ImmutableDoctorEndpointResponse(
            Optional.empty(),
            ImmutableList.of(
                new ImmutableDoctorSuggestion(
                    DoctorSuggestion.StepStatus.ERROR, Optional.empty(), "Suggestion no1"),
                new ImmutableDoctorSuggestion(
                    DoctorSuggestion.StepStatus.WARNING, Optional.of("Area"), "Suggestion no2")));

    httpd = new HttpdForTests();
    httpd.addHandler(
        createEndpointHttpdHandler(
            "POST",
            "{\"buildId\":\"ac8bd626-6137-4747-84dd-5d4f215c876c\",\"logDirPath\":\""
                + LOG_PATH
                + "\",\"machineReadableLog\""));
    httpd.start();
  }

  @After
  public void cleanUp() throws Exception {
    httpd.close();
  }

  @Test
  public void testEndpointUrl() throws Exception {
    DoctorReportHelper helper =
        createDoctorHelper(
            workspace,
            userInputFixture.getUserInput(),
            new ImmutableDoctorConfig(FakeBuckConfig.builder().build()));
    BuildLogHelper buildLogHelper = new BuildLogHelper(filesystem);
    Optional<BuildLogEntry> entry =
        helper.promptForBuild(new ArrayList<>(buildLogHelper.getBuildLogs()));

    DoctorEndpointRequest request = helper.generateEndpointRequest(entry.get(), reportResult);
    DoctorEndpointResponse response = helper.uploadRequest(request);
    assertEquals(
        "Please define URL",
        response.getErrorMessage().get(),
        "Doctor endpoint URL is not set. Please set [doctor] endpoint_url on your .buckconfig");
  }

  @Test
  public void testPromptWithoutReport() throws Exception {
    assumeThat(Platform.detect(), Matchers.not(Matchers.is(Platform.WINDOWS)));

    DoctorReportHelper helper =
        createDoctorHelper(
            workspace,
            userInputFixture.getUserInput(),
            createDoctorConfig(httpd.getRootUri().getPort(), "", DoctorProtocolVersion.JSON));

    BuildLogHelper buildLogHelper = new BuildLogHelper(filesystem);
    Optional<BuildLogEntry> entry =
        helper.promptForBuild(new ArrayList<>(buildLogHelper.getBuildLogs()));

    DoctorEndpointRequest request = helper.generateEndpointRequest(entry.get(), reportResult);
    DoctorEndpointResponse response = helper.uploadRequest(request);
    helper.presentResponse(response);

    assertEquals(response, doctorResponse);
    assertEquals(
        String.format(
            "%n:: Suggestions%n- [Error] Suggestion no1%n- [Warning][Area] Suggestion no2%n%n"),
        ((TestConsole) helper.getConsole()).getTextWrittenToStdOut());
  }

  @Test
  public void testReportSuccessfulUpload() throws Exception {
    // Set the last-modified time of the build command first so our user input will select it
    Path buildCommandLogDir = filesystem.resolve(LOG_PATH).getParent();
    filesystem.setLastModifiedTime(buildCommandLogDir, FileTime.from(Instant.now()));
    for (Path path : filesystem.getDirectoryContents(buildCommandLogDir)) {
      filesystem.setLastModifiedTime(path, FileTime.from(Instant.now()));
    }

    AtomicReference<String> requestMethod = new AtomicReference<>();
    AtomicReference<String> requestPath = new AtomicReference<>();
    AtomicReference<byte[]> requestBody = new AtomicReference<>();
    String successMessage = "Upload successful";
    try (HttpdForTests httpd = new HttpdForTests()) {
      httpd.addHandler(
          new AbstractHandler() {
            @Override
            public void handle(
                String s,
                Request request,
                HttpServletRequest httpServletRequest,
                HttpServletResponse httpServletResponse)
                throws IOException {
              httpServletResponse.setStatus(200);
              request.setHandled(true);

              if (request.getHttpURI().getPath().equals("/status.php")) {
                return;
              }
              requestPath.set(request.getHttpURI().getPath());
              requestMethod.set(request.getMethod());
              requestBody.set(ByteStreams.toByteArray(httpServletRequest.getInputStream()));
              try (DataOutputStream out =
                  new DataOutputStream(httpServletResponse.getOutputStream())) {
                out.writeBytes(successMessage);
              }
            }
          });
      httpd.start();

      DoctorConfig doctorConfig =
          createDoctorConfig(httpd.getRootUri().getPort(), "", DoctorProtocolVersion.SIMPLE);
      DoctorReportHelper helper =
          createDoctorHelper(workspace, userInputFixture.getUserInput(), doctorConfig);
      BuildLogHelper buildLogHelper = new BuildLogHelper(filesystem);
      Optional<BuildLogEntry> entry =
          helper.promptForBuild(new ArrayList<>(buildLogHelper.getBuildLogs()));
      DefectSubmitResult report =
          createDefectReport(
              workspace,
              ImmutableSet.of(entry.get()),
              userInputFixture.getUserInput(),
              doctorConfig);

      assertThat(report.getReportSubmitMessage(), Matchers.equalTo(Optional.of(successMessage)));
      assertThat(requestPath.get(), Matchers.equalTo(DoctorConfig.DEFAULT_REPORT_UPLOAD_PATH));
      assertThat(requestMethod.get(), Matchers.equalTo("POST"));

      filesystem.mkdirs(filesystem.getBuckPaths().getBuckOut());
      Path reportPath =
          filesystem.createTempFile(filesystem.getBuckPaths().getBuckOut(), "report", "zip");
      filesystem.writeBytesToPath(requestBody.get(), reportPath);
      ZipInspector zipInspector = new ZipInspector(filesystem.resolve(reportPath));
      zipInspector.assertFileExists("report.json");
      zipInspector.assertFileExists("buckconfig.local");
      zipInspector.assertFileExists("bucklogging.local.properties");
      zipInspector.assertFileExists(BUILD_COMMAND_DIR_PATH + BuckConstant.BUCK_LOG_FILE_NAME);
      zipInspector.assertFileExists(
          BUILD_COMMAND_DIR_PATH + BuckConstant.BUCK_MACHINE_LOG_FILE_NAME);
      zipInspector.assertFileExists(
          BUILD_COMMAND_DIR_PATH + BuckConstant.RULE_KEY_DIAG_KEYS_FILE_NAME);
      zipInspector.assertFileExists(
          BUILD_COMMAND_DIR_PATH + BuckConstant.RULE_KEY_DIAG_GRAPH_FILE_NAME);
    }
  }

  @Test
  public void testJsonUpload() throws Exception {

    String reportId = "123456789";
    String rageUrl = "https://www.webpage.com/buck/rage/" + reportId;
    String rageMsg = "This is supposed to be JSON.";

    try (HttpdForTests httpd = new HttpdForTests()) {
      httpd.addHandler(
          new AbstractHandler() {
            @Override
            public void handle(
                String s,
                Request request,
                HttpServletRequest httpServletRequest,
                HttpServletResponse httpResponse)
                throws IOException {
              httpResponse.setStatus(200);
              request.setHandled(true);

              if (request.getHttpURI().getPath().equals("/status.php")) {
                return;
              }

              DoctorJsonResponse json =
                  new ImmutableDoctorJsonResponse(
                      /* isRequestSuccessful */ true,
                      /* errorMessage */ Optional.empty(),
                      /* rageUrl */ Optional.of(rageUrl),
                      /* message */ Optional.of(rageMsg));
              try (DataOutputStream out = new DataOutputStream(httpResponse.getOutputStream())) {
                ObjectMappers.WRITER.writeValue((DataOutput) out, json);
              }
            }
          });
      httpd.start();

      DoctorConfig doctorConfig =
          createDoctorConfig(httpd.getRootUri().getPort(), "", DoctorProtocolVersion.JSON);
      DoctorReportHelper helper =
          createDoctorHelper(workspace, userInputFixture.getUserInput(), doctorConfig);
      BuildLogHelper buildLogHelper = new BuildLogHelper(filesystem);
      Optional<BuildLogEntry> entry =
          helper.promptForBuild(new ArrayList<>(buildLogHelper.getBuildLogs()));
      DefectSubmitResult report =
          createDefectReport(
              workspace,
              ImmutableSet.of(entry.get()),
              userInputFixture.getUserInput(),
              doctorConfig);

      assertTrue(report.getIsRequestSuccessful().get());
      assertEquals(rageUrl, report.getReportSubmitLocation().get());
      assertEquals(rageMsg, report.getReportSubmitMessage().get());
      assertEquals(reportId, report.getReportId().get());
    }
  }

  @Test
  public void testExtraInfo() throws Exception {
    DoctorConfig doctorConfig =
        createDoctorConfig(0, "python, extra.py", DoctorProtocolVersion.SIMPLE);
    DoctorReportHelper helper =
        createDoctorHelper(workspace, userInputFixture.getUserInput(), doctorConfig);
    BuildLogHelper buildLogHelper = new BuildLogHelper(filesystem);
    Optional<BuildLogEntry> entry =
        helper.promptForBuild(new ArrayList<>(buildLogHelper.getBuildLogs()));

    Console console = new TestConsole();
    DoctorTestUtils.CapturingDefectReporter reporter =
        new DoctorTestUtils.CapturingDefectReporter();
    DoctorInteractiveReport report =
        new DoctorInteractiveReport(
            reporter,
            filesystem,
            console,
            userInputFixture.getUserInput(),
            Optional.empty(),
            TestBuildEnvironmentDescription.INSTANCE,
            new VersionControlStatsGenerator(new NoOpCmdLineInterface(), Optional.empty()),
            doctorConfig,
            new DefaultExtraInfoCollector(
                doctorConfig, filesystem, new DefaultProcessExecutor(console)),
            ImmutableSet.of(entry.get()),
            Optional.empty());
    report.collectAndSubmitResult();

    assertThat(
        reporter.getDefectReport().getExtraInfo(),
        Matchers.equalTo(Optional.of("Extra" + System.lineSeparator())));
    assertThat(
        reporter.getDefectReport().getIncludedPaths().stream()
            .map(Object::toString)
            .collect(Collectors.toList()),
        Matchers.hasItem(Matchers.endsWith("extra.txt")));
    assertThat(
        reporter
            .getDefectReport()
            .getUserLocalConfiguration()
            .get()
            .getConfigOverrides()
            .get("foo.bar"),
        Matchers.equalTo("baz"));
  }

  @Test
  public void testReportUploadFailure() throws Exception {
    try (HttpdForTests httpd = new HttpdForTests()) {
      httpd.addHandler(
          new AbstractHandler() {
            @Override
            public void handle(
                String s,
                Request request,
                HttpServletRequest httpServletRequest,
                HttpServletResponse httpServletResponse) {
              httpServletResponse.setStatus(500);
              request.setHandled(true);
            }
          });
      httpd.start();

      DoctorConfig doctorConfig =
          createDoctorConfig(httpd.getRootUri().getPort(), "", DoctorProtocolVersion.SIMPLE);
      DoctorReportHelper helper =
          createDoctorHelper(workspace, userInputFixture.getUserInput(), doctorConfig);
      BuildLogHelper buildLogHelper = new BuildLogHelper(filesystem);
      Optional<BuildLogEntry> entry =
          helper.promptForBuild(new ArrayList<>(buildLogHelper.getBuildLogs()));
      DefectSubmitResult report =
          createDefectReport(
              workspace,
              ImmutableSet.of(entry.get()),
              userInputFixture.getUserInput(),
              doctorConfig);

      // If upload fails it should store the zip locally and inform the user.
      assertFalse(report.getReportSubmitErrorMessage().get().isEmpty());
      ZipInspector zipInspector =
          new ZipInspector(filesystem.resolve(report.getReportSubmitLocation().get()));
      zipInspector.assertFileExists("report.json");
      zipInspector.assertFileExists("buckconfig.local");
      zipInspector.assertFileExists("bucklogging.local.properties");
      zipInspector.assertFileExists(BUILD_COMMAND_DIR_PATH + BuckConstant.BUCK_LOG_FILE_NAME);
      zipInspector.assertFileExists(
          BUILD_COMMAND_DIR_PATH + BuckConstant.BUCK_MACHINE_LOG_FILE_NAME);
      zipInspector.assertFileExists(
          BUILD_COMMAND_DIR_PATH + BuckConstant.RULE_KEY_DIAG_KEYS_FILE_NAME);
      zipInspector.assertFileExists(
          BUILD_COMMAND_DIR_PATH + BuckConstant.RULE_KEY_DIAG_GRAPH_FILE_NAME);
    }
  }

  private AbstractHandler createEndpointHttpdHandler(String expectedMethod, String expectedBody) {
    return new AbstractHandler() {
      @Override
      public void handle(
          String s,
          Request request,
          HttpServletRequest httpRequest,
          HttpServletResponse httpResponse)
          throws IOException {
        httpResponse.setStatus(200);
        request.setHandled(true);

        if (request.getHttpURI().getPath().equals("/status.php")) {
          return;
        }

        httpResponse.setContentType("application/json");
        httpResponse.setCharacterEncoding("utf-8");

        requestPath.set(request.getHttpURI().getPath());
        requestMethod.set(request.getMethod());
        requestBody.set(ByteStreams.toByteArray(httpRequest.getInputStream()));

        assertTrue(requestMethod.get().equalsIgnoreCase(expectedMethod));
        assertThat(
            "Request should contain the uuid.",
            new String(requestBody.get(), Charsets.UTF_8),
            Matchers.containsString(expectedBody));

        try (DataOutputStream out = new DataOutputStream(httpResponse.getOutputStream())) {
          ObjectMappers.WRITER.writeValue((DataOutput) out, doctorResponse);
        }
      }
    };
  }

  @Test
  public void testRedundantConfigArgs() throws Exception {
    DoctorConfig doctorConfig =
        createDoctorConfig(httpd.getRootUri().getPort(), "", DoctorProtocolVersion.SIMPLE);
    DoctorReportHelper helper =
        createDoctorHelper(workspace, userInputFixture.getUserInput(), doctorConfig);
    BuildLogHelper buildLogHelper = new BuildLogHelper(filesystem);

    Path machineLog =
        filesystem.resolve(BUILD_COMMAND_DIR_PATH + BuckConstant.BUCK_MACHINE_LOG_FILE_NAME);
    Path logDir = machineLog.getParent();

    filesystem.deleteFileAtPathIfExists(machineLog);
    filesystem.move(logDir.resolve("buck-machine-log_duplicate_cmd_args"), machineLog);

    Optional<BuildLogEntry> entry =
        helper.promptForBuild(new ArrayList<>(buildLogHelper.getBuildLogs()));

    Console console = new TestConsole();
    DoctorTestUtils.CapturingDefectReporter reporter =
        new DoctorTestUtils.CapturingDefectReporter();
    DoctorInteractiveReport report =
        new DoctorInteractiveReport(
            reporter,
            filesystem,
            console,
            userInputFixture.getUserInput(),
            Optional.empty(),
            TestBuildEnvironmentDescription.INSTANCE,
            new VersionControlStatsGenerator(new NoOpCmdLineInterface(), Optional.empty()),
            doctorConfig,
            new DefaultExtraInfoCollector(
                doctorConfig, filesystem, new DefaultProcessExecutor(console)),
            ImmutableSet.of(entry.get()),
            Optional.empty());
    report.collectAndSubmitResult();

    assertThat(
        reporter
            .getDefectReport()
            .getUserLocalConfiguration()
            .get()
            .getConfigOverrides()
            .get("foo.bar"),
        Matchers.equalTo("baz2"));
  }
}
