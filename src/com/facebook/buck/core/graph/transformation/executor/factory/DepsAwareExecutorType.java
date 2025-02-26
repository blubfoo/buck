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
package com.facebook.buck.core.graph.transformation.executor.factory;

import com.facebook.buck.util.randomizedtrial.WithProbability;

/**
 * An enumeration for identifying which implementation of the {@link
 * com.facebook.buck.core.graph.transformation.executor.DepsAwareExecutor} to use.
 */
public enum DepsAwareExecutorType implements WithProbability {
  /**
   * use {@link com.facebook.buck.core.graph.transformation.executor.impl.DefaultDepsAwareExecutor}
   */
  DEFAULT(1),
  /**
   * use {@link
   * com.facebook.buck.core.graph.transformation.executor.impl.DefaultDepsAwareExecutorWithLocalStack}
   */
  DEFAULT_WITH_LS(0),
  /**
   * use {@link
   * com.facebook.buck.core.graph.transformation.executor.impl.JavaExecutorBackedDefaultDepsAwareExecutor}
   */
  JAVA_BASED(0),
  /**
   * use {@link
   * com.facebook.buck.core.graph.transformation.executor.impl.ToposortBasedDepsAwareExecutor}
   */
  TOPOSORT_BASED(0),
  ;

  private final double probability;

  DepsAwareExecutorType(double probability) {
    this.probability = probability;
  }

  @Override
  public double getProbability() {
    return probability;
  }
}
