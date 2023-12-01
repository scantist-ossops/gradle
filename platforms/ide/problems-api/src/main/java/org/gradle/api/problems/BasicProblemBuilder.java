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

import javax.annotation.Nullable;

/**
 * {@link Problem} instance builder that is capable of creating new Problem instances.
 *
 * An example of how to use the builder:
 * <pre>{@code
 *  <problemReporter>.report(configurator -> configurator
 *          .label("test problem")
 *          .category("category", "subcategory")
 *          .severity(Severity.ERROR)
 *          .details("this is a test")
 *          .build()
 *  }</pre>
 *
 * @since 8.6
 */
@Incubating
public interface BasicProblemBuilder extends ProblemBuilder { // TODO (donat) should be renamed to ProblemBuilder after the base type rename is done.

    /**
     * Creates the new problem. Calling this method won't report the problem via build operations, it can be done separately by calling {@link org.gradle.api.problems.internal.InternalProblemReporter#report(Problem)}.
     *
     * @return the new problem
     */
    Problem build();

    /**
     * {@inheritDoc}
     */
    @Override
    BasicProblemBuilder label(String label, Object... args);

    /**
     * {@inheritDoc}
     */
    @Override
    BasicProblemBuilder category(String category, String... details);

    /**
     * {@inheritDoc}
     */
    @Override
    BasicProblemBuilder documentedAt(DocLink doc);

    /**
     * {@inheritDoc}
     */
    @Override
    BasicProblemBuilder documentedAt(String url);

    /**
     * {@inheritDoc}
     */
    @Override
    BasicProblemBuilder fileLocation(String path, @Nullable Integer line, @Nullable Integer column, @Nullable Integer length);

    /**
     * {@inheritDoc}
     */
    @Override
    BasicProblemBuilder pluginLocation(String pluginId);

    /**
     * {@inheritDoc}
     */
    @Override
    BasicProblemBuilder stackLocation();

    /**
     * {@inheritDoc}
     */
    @Override
    BasicProblemBuilder details(String details);

    /**
     * {@inheritDoc}
     */
    @Override
    BasicProblemBuilder solution(String solution);

    /**
     * {@inheritDoc}
     */
    @Override
    BasicProblemBuilder additionalData(String key, Object value);

    /**
     * {@inheritDoc}
     */
    @Override
    BasicProblemBuilder withException(RuntimeException e);

    /**
     * {@inheritDoc}
     */
    @Override
    BasicProblemBuilder severity(Severity severity);
}
