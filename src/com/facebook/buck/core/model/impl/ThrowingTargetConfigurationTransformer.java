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
package com.facebook.buck.core.model.impl;

import com.facebook.buck.core.exceptions.DependencyStack;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.TargetConfigurationTransformer;
import com.google.common.collect.ImmutableList;

/**
 * Fake target configuration transformer to be used when configuration transorming is not performed
 */
public class ThrowingTargetConfigurationTransformer implements TargetConfigurationTransformer {

  /** Throw unconditionally */
  @Override
  public ImmutableList<TargetConfiguration> transform(
      TargetConfiguration targetConfiguration, DependencyStack dependencyStack) {
    throw new IllegalStateException();
  }

  /** Throw unconditionally */
  @Override
  public boolean needsTransformation(
      TargetConfiguration targetConfiguration, DependencyStack dependencyStack) {
    throw new IllegalStateException();
  }
}
