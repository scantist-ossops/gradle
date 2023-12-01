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

package org.gradle.api.internal.initialization.transform;

import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.cache.GlobalCacheLocations;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.classpath.InPlaceClasspathBuilder;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactory;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactoryForAgent;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactoryForLegacy;
import org.gradle.internal.file.Stat;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;

import static org.gradle.api.internal.initialization.transform.BaseInstrumentingArtifactTransform.InstrumentArtifactTransformParameters;

/**
 * Base artifact transform that instruments plugins with Gradle instrumentation, e.g. for configuration cache detection or property upgrades.
 */
@DisableCachingByDefault(because = "Instrumented jars are too big to cache")
public abstract class BaseInstrumentingArtifactTransform implements TransformAction<InstrumentArtifactTransformParameters> {

    public interface InstrumentArtifactTransformParameters extends TransformParameters {
        @Input
        Property<Boolean> getAgentSupported();

        @Input
        Property<Integer> getMaxSupportedJavaVersion();

        @Input
        @Optional
        Property<String> getUpgradedPropertiesHash();
    }


    static class InstrumentationServices {

        private final ClasspathElementTransformFactoryForAgent transformFactory;
        private final ClasspathElementTransformFactoryForLegacy legacyTransformFactory;
        private final GlobalCacheLocations globalCacheLocations;

        @Inject
        public InstrumentationServices(Stat stat, GlobalCacheLocations globalCacheLocations) {
            this.transformFactory = new ClasspathElementTransformFactoryForAgent(new InPlaceClasspathBuilder(), new ClasspathWalker(stat));
            this.legacyTransformFactory = new ClasspathElementTransformFactoryForLegacy(new InPlaceClasspathBuilder(), new ClasspathWalker(stat));
            this.globalCacheLocations = globalCacheLocations;
        }

        public ClasspathElementTransformFactory getTransformFactory(boolean isAgentSupported) {
            return isAgentSupported ? transformFactory : legacyTransformFactory;
        }

        public GlobalCacheLocations getGlobalCacheLocations() {
            return globalCacheLocations;
        }
    }
}
