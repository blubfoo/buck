/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.rules.coercer;

import static com.facebook.buck.core.cell.TestCellBuilder.createCellRoots;
import static org.junit.Assert.assertThat;

import com.facebook.buck.core.model.UnconfiguredTargetConfiguration;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.rules.macros.Macro;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.hamcrest.Matchers;
import org.junit.Test;

public class ZeroArgMacroTypeCoercerTest {

  @Test
  public void test() throws CoerceFailedException {
    Path basePath = Paths.get("java/com/facebook/buck/example");
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    ZeroArgMacro macro = new ZeroArgMacro();
    ZeroArgMacroTypeCoercer<ZeroArgMacro> coercer =
        new ZeroArgMacroTypeCoercer<>(ZeroArgMacro.class, macro);
    ZeroArgMacro result =
        coercer.coerce(
            createCellRoots(filesystem),
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            ImmutableList.of());
    assertThat(result, Matchers.is(macro));
  }

  @Test(expected = CoerceFailedException.class)
  public void testIncorrectArgs() throws CoerceFailedException {
    Path basePath = Paths.get("java/com/facebook/buck/example");
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    ZeroArgMacroTypeCoercer<ZeroArgMacro> coercer =
        new ZeroArgMacroTypeCoercer<>(ZeroArgMacro.class, new ZeroArgMacro());
    coercer.coerce(
        createCellRoots(filesystem),
        filesystem,
        basePath,
        UnconfiguredTargetConfiguration.INSTANCE,
        ImmutableList.of("arg"));
  }

  private static class ZeroArgMacro implements Macro {}
}
