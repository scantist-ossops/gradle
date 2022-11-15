/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.properties;

import org.gradle.api.internal.tasks.TaskDependencyContainer;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

/**
 * A supplier of a property value.
 */
public interface PropertyValue extends Callable<Object> {
    /**
     * The value of the underlying property.
     */
    @Nullable
    @Override
    Object call();

    /**
     * Returns the dependencies of the property value, if supported by the value implementation. Returns an empty collection if not supported or the value has no producer tasks.
     */
    TaskDependencyContainer getTaskDependencies();

    /**
     * Finalizes the property value, if possible. This makes the value final, so that it no longer changes, but not necessarily immutable.
     */
    void maybeFinalizeValue();

    PropertyValue ABSENT = new PropertyValue() {
        @Nullable
        @Override
        public Object call() {
            return null;
        }

        @Override
        public TaskDependencyContainer getTaskDependencies() {
            // Ignore
            return TaskDependencyContainer.EMPTY;
        }

        @Override
        public void maybeFinalizeValue() {
            // Ignore
        }
    };
}