/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.shade.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.jar.JarOutputStream;

import org.apache.maven.plugins.shade.ShadePlanEntry;
import org.apache.maven.plugins.shade.relocation.Relocator;

/**
 * @author Jason van Zyl
 */
public interface ResourceTransformer {
    boolean canTransformResource(String resource);

    /**
     * Transform an individual resource.
     *
     * @param resource the resource name
     * @param is an input stream for the resource, the implementation should *not* close this stream
     * @param relocators  a list of relocators
     * @throws IOException when the IO blows up
     * @deprecated prefer ReproducibleResourceTransformer
     */
    void processResource(String resource, InputStream is, List<Relocator> relocators) throws IOException;

    boolean hasTransformedResource();

    void modifyOutputStream(JarOutputStream os) throws IOException;

    /**
     * Contributes read-only information about what this transformer would do with the given resource to a
     * dry-run shade plan entry. Implementations must not mutate their state here: a dry-run must not pollute
     * a subsequent real shading run using the same transformer instance.
     *
     * @param entry the plan entry being built for a resource this transformer can transform
     * @since 3.6.3
     */
    default void contributeToPlan(ShadePlanEntry.Builder entry) {
        // no plan contribution by default
    }
}
