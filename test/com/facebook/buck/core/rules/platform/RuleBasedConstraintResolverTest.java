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
package com.facebook.buck.core.rules.platform;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.core.exceptions.DependencyStack;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.ConfigurationBuildTargetFactoryForTests;
import com.facebook.buck.core.model.platform.ConstraintSetting;
import com.facebook.buck.core.model.platform.ConstraintValue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RuleBasedConstraintResolverTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testGettingConstraintSettingThrowsWithWrongRuleType() {
    RuleBasedConstraintResolver ruleBasedConstraintResolver =
        new RuleBasedConstraintResolver(
            (buildTarget, dependencyStack) -> DummyConfigurationRule.of(buildTarget));

    thrown.expectMessage("//dummy:target is used as constraint_setting, but has wrong type");

    ruleBasedConstraintResolver.getConstraintSetting(
        BuildTargetFactory.newInstance("//dummy:target"), DependencyStack.root());
  }

  @Test
  public void testGettingConstraintValueThrowsWithWrongRuleType() {
    RuleBasedConstraintResolver ruleBasedConstraintResolver =
        new RuleBasedConstraintResolver(
            (buildTarget, dependencyStack) -> DummyConfigurationRule.of(buildTarget));

    thrown.expectMessage("//dummy:target is used as constraint_value, but has wrong type");

    ruleBasedConstraintResolver.getConstraintValue(
        BuildTargetFactory.newInstance("//dummy:target"), DependencyStack.root());
  }

  @Test
  public void testGettingConstraintValueThrowsWithWrongConstraintSettingRuleType() {
    BuildTarget constraintSettingTarget =
        ConfigurationBuildTargetFactoryForTests.newInstance("//:setting");
    BuildTarget constraintValueTarget =
        ConfigurationBuildTargetFactoryForTests.newInstance("//:value");

    RuleBasedConstraintResolver ruleBasedConstraintResolver =
        new RuleBasedConstraintResolver(
            (buildTarget, dependencyStack) -> {
              if (buildTarget.equals(constraintSettingTarget)) {
                return DummyConfigurationRule.of(buildTarget);
              } else {
                return new ConstraintValueRule(
                    buildTarget, buildTarget.getShortName(), constraintSettingTarget);
              }
            });

    thrown.expectMessage("//:setting is used as constraint_setting, but has wrong type");

    ruleBasedConstraintResolver.getConstraintValue(constraintValueTarget, DependencyStack.root());
  }

  @Test
  public void testGettingConstraintsReturnCorrectObject() {
    BuildTarget constraintSettingTarget =
        ConfigurationBuildTargetFactoryForTests.newInstance("//:setting");
    BuildTarget constraintValueTarget =
        ConfigurationBuildTargetFactoryForTests.newInstance("//:value");

    RuleBasedConstraintResolver ruleBasedConstraintResolver =
        new RuleBasedConstraintResolver(
            (buildTarget, dependencyStack) -> {
              if (buildTarget.equals(constraintSettingTarget)) {
                return new ConstraintSettingRule(buildTarget, buildTarget.getShortName());
              } else {
                return new ConstraintValueRule(
                    buildTarget, buildTarget.getShortName(), constraintSettingTarget);
              }
            });

    ConstraintValue constraintValue =
        ruleBasedConstraintResolver.getConstraintValue(
            constraintValueTarget, DependencyStack.root());
    ConstraintSetting constraintSetting =
        ruleBasedConstraintResolver.getConstraintSetting(
            constraintSettingTarget, DependencyStack.root());

    assertEquals(constraintSetting, constraintValue.getConstraintSetting());
    assertEquals(constraintSettingTarget, constraintSetting.getBuildTarget());
    assertEquals(constraintValueTarget, constraintValue.getBuildTarget());
  }
}
