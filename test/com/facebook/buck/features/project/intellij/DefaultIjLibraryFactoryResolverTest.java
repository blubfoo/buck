/*
 * Copyright 2019-present Facebook, Inc.
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
package com.facebook.buck.features.project.intellij;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.targetgraph.TargetGraph;
import com.facebook.buck.core.model.targetgraph.TargetGraphFactory;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.jvm.java.JavaLibraryBuilder;
import com.facebook.buck.jvm.java.JavaLibraryDescriptionArg;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class DefaultIjLibraryFactoryResolverTest {

  private TargetNode<JavaLibraryDescriptionArg> noSrc;
  private TargetNode<JavaLibraryDescriptionArg> withSrc;
  private DefaultIjLibraryFactoryResolver libraryFactoryResolver;
  private Set<BuildTarget> targetsToBuild;

  @Before
  public void setUp() throws Exception {
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    noSrc = JavaLibraryBuilder.createBuilder("//java:foo").build(filesystem);
    withSrc =
        JavaLibraryBuilder.createBuilder("//java:foo")
            .addSrc(Paths.get("Foo.java"))
            .build(filesystem);
    TargetGraph graph = TargetGraphFactory.newInstance(noSrc, withSrc);
    IjProjectSourcePathResolver sourcePathResolver = new IjProjectSourcePathResolver(graph);
    targetsToBuild = new HashSet<>();
    libraryFactoryResolver =
        new DefaultIjLibraryFactoryResolver(
            filesystem, sourcePathResolver, Optional.of(targetsToBuild));
  }

  @Test
  public void getPathIfJavaLibraryForNoSrc() {
    assertEquals(Optional.empty(), libraryFactoryResolver.getPathIfJavaLibrary(noSrc));
    assertEquals(0, targetsToBuild.size());
  }

  @Test
  public void getPathIfJavaLibraryWithSrc() {
    assertEquals(
        Optional.of(
            ExplicitBuildTargetSourcePath.of(
                BuildTargetFactory.newInstance("//java:foo"),
                Paths.get("buck-out", "gen", "java", "lib__foo__output", "foo.jar"))),
        libraryFactoryResolver.getPathIfJavaLibrary(withSrc));
    assertEquals(1, targetsToBuild.size());
  }
}
