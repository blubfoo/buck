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
package com.facebook.buck.query;

import com.facebook.buck.core.model.QueryTarget;
import com.facebook.buck.query.QueryEnvironment.Argument;
import com.facebook.buck.query.QueryEnvironment.ArgumentType;
import com.facebook.buck.query.QueryEnvironment.QueryFunction;
import com.google.common.collect.ImmutableList;
import java.util.Set;

/**
 * A "testsof" query expression, which computes the tests of the given targets.
 *
 * <p>This operator behavior is documented at docs/command/query.soy
 *
 * <pre>expr ::= TESTSOF '(' expr ')'</pre>
 */
public class TestsOfFunction<T extends QueryTarget> implements QueryFunction<T, T> {

  private static final ImmutableList<ArgumentType> ARGUMENT_TYPES =
      ImmutableList.of(ArgumentType.EXPRESSION);

  public TestsOfFunction() {}

  @Override
  public String getName() {
    return "testsof";
  }

  @Override
  public int getMandatoryArguments() {
    return 1;
  }

  @Override
  public ImmutableList<ArgumentType> getArgumentTypes() {
    return ARGUMENT_TYPES;
  }

  @Override
  public Set<T> eval(
      QueryEvaluator<T> evaluator, QueryEnvironment<T> env, ImmutableList<Argument<T>> args)
      throws QueryException {
    Set<T> targets = evaluator.eval(args.get(0).getExpression(), env);
    return Unions.of((T target) -> env.getTestsForTarget(target), targets);
  }
}
