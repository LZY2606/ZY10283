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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;

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
     * Read-only contribution to a dry-run shade plan. Called instead of
     * {@link #processResource(String, InputStream, List)} when the shader only computes the plan of
     * the shading. Implementations must not mutate any state, so that a subsequent real shading with
     * the same transformer instance is not affected. The returned detail is recorded in the plan
     * entry; the key {@code "output"} is reserved for the path of the output entry this transformer
     * will produce for the given resource when it finishes the output stream.
     *
     * @param resource the resource name after relocation
     * @param relocators the relocators in effect
     * @return detail describing what this transformer would do with the resource, never {@code null}
     * @since 3.6.3
     */
    default Map<String, String> describePlanContribution(String resource, List<Relocator> relocators) {
        return Collections.emptyMap();
    }
}
