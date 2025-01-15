/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023–2024 microBean™.
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

import java.lang.invoke.VarHandle;

import java.util.Objects;

import java.util.function.Supplier;

import org.microbean.bean.Request;

import static java.lang.invoke.MethodHandles.lookup;

/**
 * An {@link AutoCloseable} pairing of a contextual instance that can be destroyed with a {@link Destructor} that can
 * destroy it, an {@link AutoCloseable} that can release its dependent objects when needed, and a {@link Request} that
 * caused it to be created.
 *
 * @param <I> the type of the instance
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 *
 * @see Instance.Destructor
 *
 * @see Request
 */
public final class Instance<I> implements AutoCloseable, Supplier<I> {

  private static final VarHandle CLOSED;

  static {
    try {
      CLOSED = lookup().findVarHandle(Instance.class, "closed", boolean.class);
    } catch (final NoSuchFieldException | IllegalAccessException reflectiveOperationException) {
      throw (Error)new ExceptionInInitializerError(reflectiveOperationException.getMessage()).initCause(reflectiveOperationException);
    }
  }

  private final I object;

  private final Destructor<I> destructor;

  private final AutoCloseable releaser;

  private final Request<I> creationRequest;

  private volatile boolean closed;

  /**
   * Creates a new {@link Instance}.
   *
   * @param contextualInstance a contextual instance that has just been created; may be {@code null}
   *
   * @param destructor a {@link Destructor} capable of (eventually) destroying the supplied {@code contextualInstance};
   * may be {@code null}
   *
   * @param creationRequest a {@link Request} that is the reason for this creation; may be {@code null}
   */
  public Instance(final I contextualInstance,
                  final Destructor<I> destructor,
                  final Request<I> creationRequest) {
    this(contextualInstance, destructor, creationRequest instanceof AutoCloseable ac ? ac : null, creationRequest);
  }

  private Instance(final I object,
                   final Destructor<I> destructor,
                   final AutoCloseable releaser, // often the same object as creationRequest
                   final Request<I> creationRequest) { // often the same object as releaser
    super();
    // All of these things are nullable on purpose.
    this.object = object;
    this.releaser = releaser == null ? Instance::sink : releaser;
    this.destructor = destructor == null ? Instance::sink : destructor;
    this.creationRequest = creationRequest;
  }

  /**
   * Returns the contextual instance this {@link Instance} holds, which may be {@code null}.
   *
   * @return the contextual instance this {@link Instance} holds, which may be {@code null}
   */
  @Override // Supplier<I>
  public final I get() {
    if (this.closed()) { // volatile read, effectively
      throw new IllegalStateException("closed");
    }
    return this.object;
  }

  @Override // AutoCloseable
  public final void close() {
    if (CLOSED.compareAndSet(this, false, true)) { // volatile read/write
      RuntimeException t = null;
      try {
        this.destructor.destroy(this.object, this.creationRequest);
      } catch (final RuntimeException e) {
        t = e;
      } finally {
        try {
          this.releaser.close();
        } catch (final RuntimeException e) {
          if (t == null) {
            throw e;
          }
          t.addSuppressed(e);
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          if (t == null) {
            throw new ScopeletException(e.getMessage(), e);
          }
          t.addSuppressed(e);
        } catch (final Exception e) {
          if (t == null) {
            throw new ScopeletException(e.getMessage(), e);
          }
          t.addSuppressed(e);
        }
      }
      if (t != null) {
        throw t;
      }
    }
  }

  /**
   * Returns {@code true} if and only if this {@link Instance} has been {@linkplain #close() closed}.
   *
   * @return {@code true} if and only if this {@link Instance} has been {@linkplain #close() closed}
   */
  public final boolean closed() {
    return this.closed; // volatile read
  }

  @Override
  public final int hashCode() {
    // We don't want "closedness" to factor in here because it isn't part of equals(). But we want to use the results of
    // get(). Fortunately, that method is final. So we can just use direct field access.
    return this.object.hashCode();
  }

  @Override
  public final boolean equals(final Object other) {
    if (other == this) {
      return true;
    } else if (other != null && this.getClass() == other.getClass()) {
      // We don't want "closedness" to factor in here because it isn't part of hashCode(). But we want to use the
      // results of get(). Fortunately, that method is final. So we can just use direct field access.
      return Objects.equals(this.object, ((Instance<?>)other).object);
    } else {
      return false;
    }
  }

  @Override
  public final String toString() {
    return String.valueOf(this.object);
  }

  private static final void sink() {

  }

  private static final <A, B> void sink(final A a, final B b) {

  }

  /**
   * An interface whose implementations can destroy contextual instances.
   *
   * @param <I> the type borne by instances
   *
   * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
   */
  @FunctionalInterface
  public static interface Destructor<I> {

    /**
     * Destroys the supplied contextual instance.
     *
     * @param i the contextual instance to destroy; may be {@code null}
     *
     * @param creationRequest the {@link Request} that caused the contextual instance to be created; may be {@code null}
     */
    public void destroy(final I i, final Request<I> creationRequest);

  }

}
