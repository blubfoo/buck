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
package com.facebook.buck.core.description.impl;

import com.facebook.buck.core.description.BaseDescription;
import com.facebook.buck.core.description.RuleDescription;
import com.facebook.buck.core.description.arg.BuildRuleArg;
import com.facebook.buck.core.description.arg.ConstructorArg;
import com.facebook.buck.core.model.AbstractRuleType;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.RuleType;
import com.facebook.buck.core.rules.actions.ActionCreationException;
import com.facebook.buck.core.rules.analysis.RuleAnalysisContext;
import com.facebook.buck.core.rules.analysis.RuleAnalysisException;
import com.facebook.buck.core.rules.config.ConfigurationRule;
import com.facebook.buck.core.rules.config.ConfigurationRuleArg;
import com.facebook.buck.core.rules.config.ConfigurationRuleDescription;
import com.facebook.buck.core.rules.config.ConfigurationRuleResolver;
import com.facebook.buck.core.rules.providers.collect.ProviderInfoCollection;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RuleTypeFactoryTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  private static class SampleBuildDescription implements RuleDescription<BuildRuleArg> {

    @Override
    public ProviderInfoCollection ruleImpl(
        RuleAnalysisContext context, BuildTarget target, BuildRuleArg args)
        throws ActionCreationException, RuleAnalysisException {
      throw new AssertionError();
    }

    @Override
    public Class<BuildRuleArg> getConstructorArgType() {
      return BuildRuleArg.class;
    }
  }

  @Test
  public void buildRule() {
    RuleType ruleType = RuleTypeFactory.create(SampleBuildDescription.class);
    Assert.assertEquals(RuleType.of("sample_build", AbstractRuleType.Kind.BUILD), ruleType);
  }

  private static class SampleConfigurationDescription
      implements ConfigurationRuleDescription<ConfigurationRuleArg, ConfigurationRule> {
    @Override
    public Class<ConfigurationRule> getRuleClass() {
      return ConfigurationRule.class;
    }

    @Override
    public ConfigurationRule createConfigurationRule(
        ConfigurationRuleResolver configurationRuleResolver,
        BuildTarget buildTarget,
        ConfigurationRuleArg arg) {
      throw new AssertionError();
    }

    @Override
    public Class<ConfigurationRuleArg> getConstructorArgType() {
      return ConfigurationRuleArg.class;
    }
  }

  @Test
  public void configurationRule() {
    RuleType ruleType = RuleTypeFactory.create(SampleConfigurationDescription.class);
    Assert.assertEquals(
        RuleType.of("sample_configuration", AbstractRuleType.Kind.CONFIGURATION), ruleType);
  }

  private static class UnknownRuleTypeDescription implements BaseDescription<ConstructorArg> {
    @Override
    public Class<ConstructorArg> getConstructorArgType() {
      return ConstructorArg.class;
    }
  }

  @Test
  public void unknownRule() {
    thrown.expectMessage("cannot determine rule kind");
    RuleTypeFactory.create(UnknownRuleTypeDescription.class);
  }

  private interface BothArg extends ConfigurationRuleArg, BuildRuleArg {}

  private static class BothDescription
      implements ConfigurationRuleDescription<BothArg, ConfigurationRule>,
          RuleDescription<BothArg> {

    @Override
    public ProviderInfoCollection ruleImpl(
        RuleAnalysisContext context, BuildTarget target, BothArg args)
        throws ActionCreationException, RuleAnalysisException {
      throw new AssertionError();
    }

    @Override
    public Class<ConfigurationRule> getRuleClass() {
      return ConfigurationRule.class;
    }

    @Override
    public ConfigurationRule createConfigurationRule(
        ConfigurationRuleResolver configurationRuleResolver, BuildTarget buildTarget, BothArg arg) {
      throw new AssertionError();
    }

    @Override
    public Class<BothArg> getConstructorArgType() {
      return BothArg.class;
    }
  }

  @Test
  public void both() {
    thrown.expectMessage("rule cannot be both build and configuration");
    RuleTypeFactory.create(BothDescription.class);
  }
}
