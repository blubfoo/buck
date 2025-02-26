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

package com.facebook.buck.android.toolchain.platform;

import com.facebook.buck.android.toolchain.ndk.TargetCpuType;
import com.facebook.buck.core.description.arg.Hint;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.ConfigurationBuildTargets;
import com.facebook.buck.core.model.UnconfiguredBuildTargetView;
import com.facebook.buck.core.rules.config.ConfigurationRuleArg;
import com.facebook.buck.core.rules.config.ConfigurationRuleDescription;
import com.facebook.buck.core.rules.config.ConfigurationRuleResolver;
import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import org.immutables.value.Value;

/**
 * A description for {@code android_platform}.
 *
 * <p>For example:
 *
 * <pre>
 *   android_platform(
 *      name = "platform",
 *      base_platform = "//config/platform:android",
 *      native_platforms = [
 *          "//config/platform:cpu-x86_64",
 *          "//config/platform:cpu-armv7",
 *      ]
 *   )
 * </pre>
 */
public class AndroidPlatformDescription
    implements ConfigurationRuleDescription<AndroidPlatformArg, AndroidMultiPlatformRule> {

  @Override
  public Class<AndroidPlatformArg> getConstructorArgType() {
    return AndroidPlatformArg.class;
  }

  @Override
  public Class<AndroidMultiPlatformRule> getRuleClass() {
    return AndroidMultiPlatformRule.class;
  }

  @Override
  public AndroidMultiPlatformRule createConfigurationRule(
      ConfigurationRuleResolver configurationRuleResolver,
      BuildTarget buildTarget,
      AndroidPlatformArg arg) {
    return new AndroidMultiPlatformRule(
        buildTarget,
        ConfigurationBuildTargets.convert(arg.getBasePlatform()),
        ConfigurationBuildTargets.convertValues(arg.getNativePlatforms()));
  }

  @Override
  public ImmutableSet<BuildTarget> getConfigurationDeps(AndroidPlatformArg arg) {
    return ImmutableSet.<BuildTarget>builder()
        .add(ConfigurationBuildTargets.convert(arg.getBasePlatform()))
        .addAll(
            ConfigurationBuildTargets.convert(
                ImmutableSet.copyOf(arg.getNativePlatforms().values())))
        .build();
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractAndroidPlatformArg extends ConfigurationRuleArg {
    @Hint(isConfigurable = false)
    UnconfiguredBuildTargetView getBasePlatform();

    @Value.NaturalOrder
    @Hint(isConfigurable = false)
    ImmutableSortedMap<TargetCpuType, UnconfiguredBuildTargetView> getNativePlatforms();
  }
}
