/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

/**
 * A function that can be used to configure a {@link Problem} via {@link ProblemBuilder}.
 * <p>
 * Usage example:
 *
 * <pre>
 * throw getProblemService().forPlugin("org.example.plugin").throwing(spec -&gt;
 *        spec.label("Task 'foo' does not exist")
 *            .category("task-selection")
 *            .severity(Severity.ERROR)
 *            .withException(new TaskSelectionException()));
 * </pre>
 *
 * @since 8.4
 */
@Incubating
public interface ProblemBuilderSpec {

    // TODO (donat) consider changing all Problem API @since annotation to 8.6
    // TODO (donat) replace it with Action<ProblemBuilder>

    /**
     * Function applying the configuration for the provided object.
     *
     * @param builder The target object
     * @since 8.6
     */
    void apply(ProblemBuilder builder);
}
