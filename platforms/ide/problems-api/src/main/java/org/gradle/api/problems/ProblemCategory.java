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

import java.util.List;

/**
 * A category is a component of a {@link Problem} that helps group related problems together.
 * <p>
 * A category defines the following hierarchical elements to distinguish instances:
 * <ul>
 *     <li>namespace</li>
 *     <li>category</li>
 *     <li>subcategories</li>
 * </ul>
 * <p>
 * The namespace denotes which component emitted the problem. It can be either Gradle core or a third-party plugin. The values should use the following pattern:
 * <ul>
 *     <li>Gradle core: {@code gradle}</li>
 *     <li>Plugins: {@code gradle-plugin:<plugin-id>}</li>
 * </ul>
 * <p>
 * A category should contain the most broad term describing the problem.
 * A few examples are: {@code compilation}, {@code deprecation}, {@code task-validation}.
 * <p>
 * The problem category can be refined with an optional hierarchy of subcategories.
 * For example, a problem covering a java compilation warning can be denoted with the following subcategories: {@code [java, unused-variable]}.
 * <p>
 * The categorization depends on the domain and don't have any constraints. Clients (i.e. IDEs) receiving problems should use the category information for
 * properly group and sort the received instances.
 * }</pre>
 *
 * @since 8.5
 */
@Incubating
public interface ProblemCategory {

    /**
     * The namespace.
     *
     * @return The namespace.
     */
    String getNamespace();

    /**
     * The main category.
     *
     * @return The main categories.
     */
    String getCategory();

    /**
     * The optional subcategories.
     *
     * @return The subcategories.
     */
    List<String> getSubCategories();
}
