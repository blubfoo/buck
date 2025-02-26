/*
 * Copyright 2016-present Facebook, Inc.
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

import com.facebook.buck.core.cell.CellPathResolver;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;

public class OptionalTypeCoercer<T> implements TypeCoercer<Optional<T>> {

  private final TypeCoercer<T> coercer;

  public OptionalTypeCoercer(TypeCoercer<T> coercer) {
    Preconditions.checkArgument(
        !coercer.getOutputClass().isAssignableFrom(Optional.class),
        "Nested optional fields are ambiguous.");
    this.coercer = coercer;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Class<Optional<T>> getOutputClass() {
    return (Class<Optional<T>>) (Class<?>) Optional.class;
  }

  @Override
  public boolean hasElementClass(Class<?>... types) {
    return coercer.hasElementClass(types);
  }

  @Override
  public void traverse(CellPathResolver cellRoots, Optional<T> object, Traversal traversal) {
    if (object.isPresent()) {
      coercer.traverse(cellRoots, object.get(), traversal);
    }
  }

  @Override
  public Optional<T> coerce(
      CellPathResolver cellRoots,
      ProjectFilesystem filesystem,
      Path pathRelativeToProjectRoot,
      TargetConfiguration targetConfiguration,
      Object object)
      throws CoerceFailedException {
    if (object == null || (object instanceof Optional<?> && !((Optional<?>) object).isPresent())) {
      return Optional.empty();
    }
    return Optional.of(
        coercer.coerce(
            cellRoots, filesystem, pathRelativeToProjectRoot, targetConfiguration, object));
  }

  @Nullable
  @Override
  public Optional<T> concat(Iterable<Optional<T>> elements) {
    Iterable<Optional<T>> presentElements = Iterables.filter(elements, Optional::isPresent);

    if (Iterables.isEmpty(presentElements)) {
      return Optional.empty();
    }

    T result = coercer.concat(Iterables.transform(presentElements, Optional::get));

    return result == null ? null : Optional.of(result);
  }
}
