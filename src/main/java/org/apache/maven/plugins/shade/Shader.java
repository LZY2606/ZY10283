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
package org.apache.maven.plugins.shade;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * Interface that defines the process of shading.
 */
public interface Shader {
    /**
     * Perform a shading operation.
     *
     * @param shadeRequest            holds the many parameters to this method
     * @throws IOException            for IO errors reading the thing
     * @throws MojoExecutionException for anything else that goes wrong
     */
    void shade(ShadeRequest shadeRequest) throws IOException, MojoExecutionException;

    /**
     * Perform a dry-run of a shading operation: computes where every entry of every input JAR would end up
     * after filters, relocations and resource transformers are applied, and which occurrence of overlapping
     * entries would win, without writing the output JAR and without letting resource transformers mutate
     * their state or finish into the output stream.
     *
     * @param shadeRequest holds the many parameters to this method
     * @return the plan; when an entry cannot be planned the returned plan is incomplete and carries the
     *         failure location
     * @since 3.6.3
     */
    ShadePlan plan(ShadeRequest shadeRequest);
}
