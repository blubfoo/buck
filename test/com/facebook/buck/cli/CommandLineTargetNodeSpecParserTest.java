/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.cli;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.core.cell.Cell;
import com.facebook.buck.core.cell.CellPathResolver;
import com.facebook.buck.core.cell.TestCellBuilder;
import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.config.FakeBuckConfig;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.UnconfiguredBuildTargetFactoryForTests;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.parser.BuildFileSpec;
import com.facebook.buck.parser.BuildTargetMatcherTargetNodeParser;
import com.facebook.buck.parser.BuildTargetSpec;
import com.facebook.buck.parser.TargetNodeSpec;
import com.facebook.buck.testutil.TemporaryPaths;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class CommandLineTargetNodeSpecParserTest {

  private CommandLineTargetNodeSpecParser parser;

  @Rule public ExpectedException exception = ExpectedException.none();
  @Rule public TemporaryPaths tmp = new TemporaryPaths();
  private ProjectFilesystem filesystem;
  private Cell rootCell;

  CommandLineTargetNodeSpecParser setupParser(
      Path relativeWorkingDir, ImmutableMap<String, ImmutableMap<String, String>> rawConfig) {
    ImmutableMap.Builder<String, ImmutableMap<String, String>> configBuilder =
        ImmutableMap.builder();
    configBuilder.putAll(rawConfig);
    configBuilder.putAll(
        ImmutableMap.of(
            "alias",
            ImmutableMap.of("foo", "//some:thing", "bar", "//some:thing //some/other:thing")));
    BuckConfig config = FakeBuckConfig.builder().setSections(configBuilder.build()).build();
    filesystem = TestProjectFilesystems.createProjectFilesystem(tmp.getRoot(), config.getConfig());
    rootCell = new TestCellBuilder().setFilesystem(filesystem).setBuckConfig(config).build();
    return new CommandLineTargetNodeSpecParser(
        rootCell,
        filesystem.getRootPath().resolve(relativeWorkingDir).normalize(),
        config,
        new BuildTargetMatcherTargetNodeParser());
  }

  CommandLineTargetNodeSpecParser setupParser() {
    return setupParser(Paths.get(""), ImmutableMap.of());
  }

  @Before
  public void setUp() {
    this.parser = setupParser();
  }

  @Test
  public void trailingDotDotDot() throws Exception {
    ProjectFilesystem root = FakeProjectFilesystem.createJavaOnlyFilesystem();
    Path directory = root.getPathForRelativePath("hello");
    Files.createDirectories(directory);
    assertEquals(
        BuildFileSpec.fromRecursivePath(directory.toAbsolutePath(), root.getRootPath()),
        parseOne(createCell(root), "//hello/...").getBuildFileSpec());
    assertEquals(
        BuildFileSpec.fromRecursivePath(root.getRootPath(), root.getRootPath()),
        parseOne(createCell(root), "//...").getBuildFileSpec());
    assertEquals(
        BuildFileSpec.fromRecursivePath(root.getRootPath(), root.getRootPath()),
        parseOne(createCell(root), "...").getBuildFileSpec());
    assertEquals(
        BuildTargetSpec.from(
            UnconfiguredBuildTargetFactoryForTests.newInstance(root.getRootPath(), "//hello:...")),
        parseOne(createCell(root), "//hello:..."));
  }

  @Test
  public void aliasExpansion() throws Exception {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    Cell cell = new TestCellBuilder().setFilesystem(filesystem).build();
    filesystem.mkdirs(Paths.get("some/other"));
    assertEquals(
        ImmutableSet.of(
            BuildTargetSpec.from(
                UnconfiguredBuildTargetFactoryForTests.newInstance("//some:thing"))),
        parser.parse(cell, "foo"));
    assertEquals(
        ImmutableSet.of(
            BuildTargetSpec.from(
                UnconfiguredBuildTargetFactoryForTests.newInstance("//some:thing")),
            BuildTargetSpec.from(
                UnconfiguredBuildTargetFactoryForTests.newInstance("//some/other:thing"))),
        parser.parse(cell, "bar"));
    assertEquals(
        ImmutableSet.of(
            BuildTargetSpec.from(
                UnconfiguredBuildTargetFactoryForTests.newInstance("//some:thing#fl")),
            BuildTargetSpec.from(
                UnconfiguredBuildTargetFactoryForTests.newInstance("//some/other:thing#fl"))),
        parser.parse(cell, "bar#fl"));
  }

  @Test
  public void tailingColon() throws Exception {
    ProjectFilesystem filesystem = FakeProjectFilesystem.createJavaOnlyFilesystem();
    Path packageDirectory = filesystem.getPathForRelativePath("hello");
    Files.createDirectories(packageDirectory);
    assertEquals(
        BuildFileSpec.fromPath(packageDirectory, filesystem.getRootPath()),
        parseOne(createCell(filesystem), "//hello:").getBuildFileSpec());
  }

  private TargetNodeSpec parseOne(Cell cell, String arg) {
    return Iterables.getOnlyElement(parser.parse(cell, arg));
  }

  @Test
  public void normalizeBuildTargets() {
    assertEquals("//:", parser.normalizeBuildTargetString("//:"));
    assertEquals("//:", parser.normalizeBuildTargetString(":"));
    assertEquals("//...", parser.normalizeBuildTargetString("//..."));
    assertEquals("//...", parser.normalizeBuildTargetString("..."));
  }

  @Test
  public void crossCellTargets() {
    assertEquals("@other//:", parser.normalizeBuildTargetString("@other//:"));
    assertEquals("+other//...", parser.normalizeBuildTargetString("+other//..."));
    assertEquals("other//:", parser.normalizeBuildTargetString("other//"));
  }

  @Test
  public void cannotReferenceNonExistentDirectoryInARecursivelyWildcard() {
    Cell cell = createCell(null);
    CellPathResolver cellRoots = cell.getCellPathResolver();
    Path cellPath = cellRoots.getCellPathOrThrow(Optional.empty());
    exception.expectMessage(
        "does_not_exist/... references non-existent directory "
            + cellPath.resolve("does_not_exist"));
    exception.expect(HumanReadableException.class);
    parser.parse(cell, "does_not_exist/...");
  }

  @Test
  public void cannotReferenceNonExistentDirectoryWithPackageTargetNames() {
    Cell cell = createCell(null);
    CellPathResolver cellRoots = cell.getCellPathResolver();
    Path cellPath = cellRoots.getCellPathOrThrow(Optional.empty());
    exception.expectMessage(
        "does_not_exist: references non-existent directory " + cellPath.resolve("does_not_exist"));
    exception.expect(HumanReadableException.class);
    parser.parse(cell, "does_not_exist:");
  }

  @Test
  public void cannotReferenceNonExistentDirectoryWithImplicitTargetName() {
    exception.expectMessage("does_not_exist references non-existent directory does_not_exist");
    exception.expect(HumanReadableException.class);
    parser.parse(createCell(null), "does_not_exist");
  }

  private Cell createCell(@Nullable ProjectFilesystem filesystem) {
    TestCellBuilder builder = new TestCellBuilder();
    if (filesystem != null) {
      builder.setFilesystem(filesystem);
    }
    return builder.build();
  }

  @Test
  public void handlesRelativeTargets() throws IOException {
    ImmutableMap<String, ImmutableMap<String, String>> config =
        ImmutableMap.of("ui", ImmutableMap.of("relativize_targets_to_working_directory", "true"));
    parser = setupParser(Paths.get("subdir"), config);
    filesystem.mkdirs(Paths.get("subdir", "foo", "bar"));
    filesystem.mkdirs(Paths.get("foo", "bar"));

    assertEquals("//...", parser.normalizeBuildTargetString("//..."));
    assertEquals("//foo/...", parser.normalizeBuildTargetString("//foo/..."));
    assertEquals("//foo/bar:baz", parser.normalizeBuildTargetString("//foo/bar:baz"));
    assertEquals("//foo/bar:", parser.normalizeBuildTargetString("//foo/bar:"));
    assertEquals("//foo/bar:bar", parser.normalizeBuildTargetString("//foo/bar"));
    assertEquals("//foo:bar", parser.normalizeBuildTargetString("//foo:bar"));
    assertEquals("//foo:", parser.normalizeBuildTargetString("//foo:"));
    assertEquals("//foo:foo", parser.normalizeBuildTargetString("//foo"));
    assertEquals("//:baz", parser.normalizeBuildTargetString("//:baz"));
    assertEquals("//:", parser.normalizeBuildTargetString("//:"));

    assertEquals("//subdir/...", parser.normalizeBuildTargetString("..."));
    assertEquals("//subdir/foo/...", parser.normalizeBuildTargetString("foo/..."));
    assertEquals("//subdir/foo/bar:baz", parser.normalizeBuildTargetString("foo/bar:baz"));
    assertEquals("//subdir/foo/bar:", parser.normalizeBuildTargetString("foo/bar:"));
    assertEquals("//subdir/foo/bar:bar", parser.normalizeBuildTargetString("foo/bar"));
    assertEquals("//subdir/foo:bar", parser.normalizeBuildTargetString("foo:bar"));
    assertEquals("//subdir/foo:", parser.normalizeBuildTargetString("foo:"));
    assertEquals("//subdir/foo:foo", parser.normalizeBuildTargetString("foo"));
    assertEquals("//subdir:baz", parser.normalizeBuildTargetString(":baz"));
    assertEquals("//subdir:", parser.normalizeBuildTargetString(":"));

    // Absolute targets
    assertEquals(
        BuildFileSpec.fromRecursivePath(rootCell.getRoot().toAbsolutePath(), rootCell.getRoot()),
        parseOne(rootCell, "//...").getBuildFileSpec());

    assertEquals(
        BuildFileSpec.fromRecursivePath(
            rootCell.getRoot().resolve("foo").toAbsolutePath(), rootCell.getRoot()),
        parseOne(rootCell, "//foo/...").getBuildFileSpec());

    assertEquals(
        BuildTargetSpec.from(
            UnconfiguredBuildTargetFactoryForTests.newInstance(
                rootCell.getRoot(), "//foo/bar:baz")),
        parseOne(rootCell, "//foo/bar:baz"));

    assertEquals(
        BuildFileSpec.fromPath(
            rootCell.getRoot().resolve(Paths.get("foo", "bar")), filesystem.getRootPath()),
        parseOne(rootCell, "//foo/bar:").getBuildFileSpec());

    assertEquals(
        BuildTargetSpec.from(
            UnconfiguredBuildTargetFactoryForTests.newInstance(
                rootCell.getRoot(), "//foo/bar:bar")),
        parseOne(rootCell, "//foo/bar"));

    assertEquals(
        BuildTargetSpec.from(
            UnconfiguredBuildTargetFactoryForTests.newInstance(rootCell.getRoot(), "//foo:bar")),
        parseOne(rootCell, "//foo:bar"));

    assertEquals(
        BuildFileSpec.fromPath(
            rootCell.getRoot().resolve(Paths.get("foo")), filesystem.getRootPath()),
        parseOne(rootCell, "//foo:").getBuildFileSpec());

    assertEquals(
        BuildTargetSpec.from(
            UnconfiguredBuildTargetFactoryForTests.newInstance(rootCell.getRoot(), "//foo:foo")),
        parseOne(rootCell, "//foo:foo"));

    assertEquals(
        BuildTargetSpec.from(
            UnconfiguredBuildTargetFactoryForTests.newInstance(rootCell.getRoot(), "//:baz")),
        parseOne(rootCell, "//:baz"));

    assertEquals(
        BuildFileSpec.fromPath(rootCell.getRoot(), filesystem.getRootPath()),
        parseOne(rootCell, "//:").getBuildFileSpec());

    // Relative targets
    assertEquals(
        BuildFileSpec.fromRecursivePath(
            rootCell.getRoot().resolve("subdir").toAbsolutePath(), rootCell.getRoot()),
        parseOne(rootCell, "...").getBuildFileSpec());

    assertEquals(
        BuildFileSpec.fromRecursivePath(
            rootCell.getRoot().resolve(Paths.get("subdir", "foo")).toAbsolutePath(),
            rootCell.getRoot()),
        parseOne(rootCell, "foo/...").getBuildFileSpec());

    assertEquals(
        BuildTargetSpec.from(
            UnconfiguredBuildTargetFactoryForTests.newInstance(
                rootCell.getRoot(), "//subdir/foo/bar:baz")),
        parseOne(rootCell, "foo/bar:baz"));

    assertEquals(
        BuildFileSpec.fromPath(
            rootCell.getRoot().resolve(Paths.get("subdir", "foo", "bar")),
            filesystem.getRootPath()),
        parseOne(rootCell, "foo/bar:").getBuildFileSpec());

    assertEquals(
        BuildTargetSpec.from(
            UnconfiguredBuildTargetFactoryForTests.newInstance(
                rootCell.getRoot(), "//subdir/foo/bar:bar")),
        parseOne(rootCell, "foo/bar"));

    assertEquals(
        BuildTargetSpec.from(
            UnconfiguredBuildTargetFactoryForTests.newInstance(
                rootCell.getRoot(), "//subdir/foo:bar")),
        parseOne(rootCell, "foo:bar"));

    assertEquals(
        BuildFileSpec.fromPath(
            rootCell.getRoot().resolve(Paths.get("subdir", "foo")), filesystem.getRootPath()),
        parseOne(rootCell, "foo:").getBuildFileSpec());

    assertEquals(
        BuildTargetSpec.from(
            UnconfiguredBuildTargetFactoryForTests.newInstance(
                rootCell.getRoot(), "//subdir/foo:foo")),
        parseOne(rootCell, "foo:foo"));

    assertEquals(
        BuildTargetSpec.from(
            UnconfiguredBuildTargetFactoryForTests.newInstance(rootCell.getRoot(), "//subdir:baz")),
        parseOne(rootCell, ":baz"));

    assertEquals(
        BuildFileSpec.fromPath(
            rootCell.getRoot().resolve(Paths.get("subdir")), filesystem.getRootPath()),
        parseOne(rootCell, ":").getBuildFileSpec());
  }

  @Test
  public void doesNotRelativizeTargetsIfDisabled() throws IOException {
    ImmutableMap<String, ImmutableMap<String, String>> config =
        ImmutableMap.of("ui", ImmutableMap.of("relativize_targets_to_working_directory", "false"));
    parser = setupParser(Paths.get("subdir"), config);
    filesystem.mkdirs(Paths.get("subdir/foo/bar"));

    assertEquals("//foo/bar:baz", parser.normalizeBuildTargetString("foo/bar:baz"));
    assertEquals("//foo/bar:", parser.normalizeBuildTargetString("foo/bar:"));
    assertEquals("//:baz", parser.normalizeBuildTargetString(":baz"));
  }
}
