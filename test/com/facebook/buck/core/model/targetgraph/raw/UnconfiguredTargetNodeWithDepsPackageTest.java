/*
 * Copyright 2018-present Facebook, Inc.
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

package com.facebook.buck.core.model.targetgraph.raw;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.facebook.buck.core.model.CanonicalCellName;
import com.facebook.buck.core.model.ImmutableUnconfiguredBuildTarget;
import com.facebook.buck.core.model.RuleType;
import com.facebook.buck.core.model.UnconfiguredBuildTarget;
import com.facebook.buck.core.model.targetgraph.impl.ImmutableUnconfiguredTargetNode;
import com.facebook.buck.parser.exceptions.ImmutableParsingError;
import com.facebook.buck.parser.exceptions.ParsingError;
import com.facebook.buck.util.json.ObjectMappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.Test;

public class UnconfiguredTargetNodeWithDepsPackageTest {

  private UnconfiguredTargetNodeWithDepsPackage getData() {
    ImmutableMap<String, Object> rawAttributes1 =
        ImmutableMap.of(
            "name",
            "target1",
            "buck.type",
            "java_library",
            "buck.base_path",
            "base",
            "deps",
            ImmutableSet.of(":target2"));

    UnconfiguredBuildTarget unconfiguredBuildTarget1 =
        ImmutableUnconfiguredBuildTarget.of(
            CanonicalCellName.rootCell(), "//base", "target1", UnconfiguredBuildTarget.NO_FLAVORS);
    UnconfiguredTargetNode unconfiguredTargetNode1 =
        ImmutableUnconfiguredTargetNode.of(
            unconfiguredBuildTarget1,
            RuleType.of("java_library", RuleType.Kind.BUILD),
            rawAttributes1,
            ImmutableSet.of(),
            ImmutableSet.of());

    ImmutableMap<String, Object> rawAttributes2 =
        ImmutableMap.of("name", "target2", "buck.type", "java_library", "buck.base_path", "base");

    UnconfiguredBuildTarget unconfiguredBuildTarget2 =
        ImmutableUnconfiguredBuildTarget.of(
            CanonicalCellName.rootCell(), "//base", "target2", UnconfiguredBuildTarget.NO_FLAVORS);
    UnconfiguredTargetNode unconfiguredTargetNode2 =
        ImmutableUnconfiguredTargetNode.of(
            unconfiguredBuildTarget2,
            RuleType.of("java_library", RuleType.Kind.BUILD),
            rawAttributes2,
            ImmutableSet.of(),
            ImmutableSet.of());

    UnconfiguredTargetNodeWithDeps unconfiguredTargetNodeWithDeps1 =
        ImmutableUnconfiguredTargetNodeWithDeps.of(
            unconfiguredTargetNode1, ImmutableSet.of(unconfiguredBuildTarget2));
    UnconfiguredTargetNodeWithDeps unconfiguredTargetNodeWithDeps2 =
        ImmutableUnconfiguredTargetNodeWithDeps.of(unconfiguredTargetNode2, ImmutableSet.of());

    ParsingError error = ImmutableParsingError.of("error1", ImmutableList.of("stacktrace1"));

    return new ImmutableUnconfiguredTargetNodeWithDepsPackage(
        Paths.get("base"),
        ImmutableMap.of(
            "target1", unconfiguredTargetNodeWithDeps1, "target2", unconfiguredTargetNodeWithDeps2),
        ImmutableList.of(error),
        ImmutableSet.of(Paths.get("test1.bzl"), Paths.get("test2.bzl")));
  }

  @Test
  public void canSerializeAndDeserializeJson() throws IOException {
    UnconfiguredTargetNodeWithDepsPackage unconfiguredTargetNodeWithDepsPackage = getData();
    byte[] data =
        ObjectMappers.WRITER_WITH_TYPE.writeValueAsBytes(unconfiguredTargetNodeWithDepsPackage);
    UnconfiguredTargetNodeWithDepsPackage unconfiguredTargetNodeWithDepsPackageDeserialized =
        ObjectMappers.READER_WITH_TYPE
            .forType(ImmutableUnconfiguredTargetNodeWithDepsPackage.class)
            .readValue(data);

    assertEquals(
        unconfiguredTargetNodeWithDepsPackage, unconfiguredTargetNodeWithDepsPackageDeserialized);
  }

  @Test
  public void canSerializeWithoutTypeAndFlatten() throws IOException {
    UnconfiguredTargetNodeWithDepsPackage unconfiguredTargetNodeWithDepsPackage = getData();
    String data = ObjectMappers.WRITER.writeValueAsString(unconfiguredTargetNodeWithDepsPackage);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node = mapper.readTree(data);

    // Validate property from UnconfiguredTargetNode ("buildTarget") is flattened so it is at the
    // same level
    // as non-flattened property ("deps")
    assertNotNull(node.get("nodes").get("target1").get("buildTarget"));
    assertNotNull(node.get("nodes").get("target1").get("deps"));
  }
}
