/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.microbean.scopelet;

import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;

import java.util.List;
import java.util.Optional;

import org.microbean.bean.Creation;
import org.microbean.bean.Dependents;
import org.microbean.bean.Destruction;
import org.microbean.bean.Factory;
import org.microbean.bean.Id;

import static java.lang.constant.ConstantDescs.BSM_INVOKE;

import static org.microbean.bean.Qualifiers.anyQualifier;

import static org.microbean.lang.Lang.declaredType;
import static org.microbean.lang.Lang.typeElement;

import static org.microbean.scope.Scope.NONE_ID;
import static org.microbean.scope.Scope.SINGLETON_ID;

public final class NoneScopelet extends Scopelet<NoneScopelet> implements Constable {

  private static final ClassDesc CD_NoneScopelet = ClassDesc.of(NoneScopelet.class.getName());

  public static final Id ID =
    new Id(List.of(declaredType(NoneScopelet.class),
                   declaredType(null,
                                typeElement(Scopelet.class),
                                declaredType(NoneScopelet.class))),
           List.of(NONE_ID, anyQualifier()), // qualifiers
           SINGLETON_ID); // the scope we belong to

  public NoneScopelet() {
    super(NONE_ID); // the scope we implement
  }

  @Override // Scopelet<NoneScopelet>
  public final Id id() {
    return ID;
  }

  @Override // Scopelet<NoneScopelet>
  public final <I> I get(final Object beanId) {
    if (!this.active()) {
      throw new InactiveScopeletException();
    }
    return null;
  }

  @Override // Scopelet<NoneScopelet>
  public final <I> I supply(final Object beanId, final Factory<I> factory, final Creation<I> c) {
    if (!this.active()) {
      throw new InactiveScopeletException();
    } else if (factory == null) {
      return null;
    }
    final I returnValue = factory.create(c);
    if (returnValue != null && c != null && factory.destroys()) {
      final Destruction d = c.destruction();
      if (d instanceof Dependents deps) {
        deps.add(new Instance<>(returnValue, factory::destroy, d));
      }
    }
    return returnValue;
  }

  @Override // Scopelet<NoneScopelet>
  public final void remove(final Object id) {
    if (!this.active()) {
      throw new InactiveScopeletException();
    }
  }

  @Override // Constable
  public final Optional<DynamicConstantDesc<NoneScopelet>> describeConstable() {
    return Optional.of(DynamicConstantDesc.of(BSM_INVOKE, MethodHandleDesc.ofConstructor(CD_NoneScopelet)));
  }

}
