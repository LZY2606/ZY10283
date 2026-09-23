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

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * A single entry of a dry-run {@link ShadePlan}. Describes what would happen to one entry of one input JAR
 * (or to a directory synthesized while writing such an entry) when the shading would actually be executed.
 *
 * @since 3.6.3
 */
public final class ShadePlanEntry {

    /**
     * Classification of an input entry.
     */
    public enum Kind {
        /** A directory entry, either present in an input JAR or synthesized while writing a nested entry. */
        DIRECTORY,
        /** A {@code .class} file. */
        CLASS,
        /** A {@code .java} source file. */
        JAVA_SOURCE,
        /** A service descriptor below {@code META-INF/services/}. */
        SERVICE,
        /** A signature file below {@code META-INF/} ({@code *.SF}, {@code *.RSA}, {@code *.DSA}, {@code *.EC}, {@code SIG-*}). */
        SIGNATURE,
        /** The {@code module-info.class} of the module path. */
        MODULE_INFO,
        /** An entry of a multi-release JAR below {@code META-INF/versions/}. */
        MULTI_RELEASE,
        /** The {@code META-INF/MANIFEST.MF} manifest. */
        MANIFEST,
        /** Any other resource. */
        RESOURCE
    }

    /**
     * What would happen to the entry when the shading would actually be executed.
     */
    public enum Outcome {
        /** The entry would be written to the output JAR. */
        WRITTEN,
        /** The entry would be dropped because a filter matched it. */
        FILTERED,
        /** The entry would be dropped because shading never copies it (e.g. {@code META-INF/INDEX.LIST}). */
        EXCLUDED,
        /** The entry would be consumed by a resource transformer. */
        TRANSFORMED,
        /** The entry would lose against an earlier entry with the same final path. */
        DUPLICATE,
        /** The entry would be skipped (input directory entries are synthesized on demand instead). */
        SKIPPED
    }

    private final String jar;

    private final String path;

    private final Kind kind;

    private final Outcome outcome;

    private final String filter;

    private final String excluded;

    private final String relocatedPath;

    private final String finalPath;

    private final String transformer;

    private final Map<String, String> transformerPlan;

    private final String duplicateGroup;

    private final boolean winner;

    private final String compression;

    private final String timestampPolicy;

    private final long time;

    private ShadePlanEntry(Builder builder) {
        this.jar = builder.jar;
        this.path = builder.path;
        this.kind = builder.kind;
        this.outcome = builder.outcome;
        this.filter = builder.filter;
        this.excluded = builder.excluded;
        this.relocatedPath = builder.relocatedPath;
        this.finalPath = builder.finalPath;
        this.transformer = builder.transformer;
        this.transformerPlan = Collections.unmodifiableMap(new TreeMap<>(builder.transformerPlan));
        this.duplicateGroup = builder.duplicateGroup;
        this.winner = builder.winner;
        this.compression = builder.compression;
        this.timestampPolicy = builder.timestampPolicy;
        this.time = builder.time;
    }

    /**
     * @return path of the input JAR (or directory) this entry was read from
     */
    public String getJar() {
        return jar;
    }

    /**
     * @return original path of the entry inside its input JAR
     */
    public String getPath() {
        return path;
    }

    /**
     * @return classification of the entry
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * @return what would happen to the entry during a real shading run
     */
    public Outcome getOutcome() {
        return outcome;
    }

    /**
     * @return description of the filter that dropped the entry, or {@code null}
     */
    public String getFilter() {
        return filter;
    }

    /**
     * @return reason why the entry is never copied to the output JAR, or {@code null}
     */
    public String getExcluded() {
        return excluded;
    }

    /**
     * @return the relocated path of the entry, or {@code null} when relocation does not change it
     */
    public String getRelocatedPath() {
        return relocatedPath;
    }

    /**
     * @return the path the entry would have in the output JAR, or {@code null} when it is not written
     */
    public String getFinalPath() {
        return finalPath;
    }

    /**
     * @return class name of the resource transformer that would consume the entry, or {@code null}
     */
    public String getTransformer() {
        return transformer;
    }

    /**
     * @return read-only plan contribution of the consuming transformer, never {@code null}
     */
    public Map<String, String> getTransformerPlan() {
        return transformerPlan;
    }

    /**
     * @return original path shared with entries of other input JARs, or {@code null} when there is no overlap
     */
    public String getDuplicateGroup() {
        return duplicateGroup;
    }

    /**
     * @return whether this occurrence of the entry is the one that would be written to the output JAR
     */
    public boolean isWinner() {
        return winner;
    }

    /**
     * @return {@code STORED} or {@code DEFLATED} for written entries, {@code null} otherwise
     */
    public String getCompression() {
        return compression;
    }

    /**
     * @return {@code PRESERVE} when the input timestamp is kept, {@code UNSET} when there is none, {@code null}
     *         for entries that are not written
     */
    public String getTimestampPolicy() {
        return timestampPolicy;
    }

    /**
     * @return the timestamp that would be written, or {@code -1} when there is none
     */
    public long getTime() {
        return time;
    }

    ShadePlanEntry withDuplicateGroup(String group) {
        Builder builder = toBuilder();
        builder.duplicateGroup = group;
        return builder.build();
    }

    private Builder toBuilder() {
        Builder builder = new Builder(jar, path, kind, outcome);
        builder.filter = filter;
        builder.excluded = excluded;
        builder.relocatedPath = relocatedPath;
        builder.finalPath = finalPath;
        builder.transformer = transformer;
        builder.transformerPlan.putAll(transformerPlan);
        builder.duplicateGroup = duplicateGroup;
        builder.winner = winner;
        builder.compression = compression;
        builder.timestampPolicy = timestampPolicy;
        builder.time = time;
        return builder;
    }

    /**
     * Builder for {@link ShadePlanEntry}.
     */
    public static final class Builder {
        private final String jar;

        private final String path;

        private final Kind kind;

        private final Outcome outcome;

        private String filter;

        private String excluded;

        private String relocatedPath;

        private String finalPath;

        private String transformer;

        private final Map<String, String> transformerPlan = new TreeMap<>();

        private String duplicateGroup;

        private boolean winner;

        private String compression;

        private String timestampPolicy;

        private long time = -1;

        public Builder(String jar, String path, Kind kind, Outcome outcome) {
            this.jar = jar;
            this.path = path;
            this.kind = kind;
            this.outcome = outcome;
        }

        public Builder filter(String filter) {
            this.filter = filter;
            return this;
        }

        public Builder excluded(String excluded) {
            this.excluded = excluded;
            return this;
        }

        public Builder relocatedPath(String relocatedPath) {
            this.relocatedPath = relocatedPath;
            return this;
        }

        public Builder finalPath(String finalPath) {
            this.finalPath = finalPath;
            return this;
        }

        public Builder transformer(String transformer) {
            this.transformer = transformer;
            return this;
        }

        /**
         * Adds a read-only plan contribution of the consuming transformer. Intended to be called from
         * {@link org.apache.maven.plugins.shade.resource.ResourceTransformer#contributeToPlan(Builder)}.
         *
         * @param key contribution key
         * @param value contribution value
         * @return this builder
         */
        public Builder putTransformerPlanAttribute(String key, String value) {
            this.transformerPlan.put(key, value);
            return this;
        }

        public Builder duplicateGroup(String duplicateGroup) {
            this.duplicateGroup = duplicateGroup;
            return this;
        }

        public Builder winner(boolean winner) {
            this.winner = winner;
            return this;
        }

        public Builder compression(String compression) {
            this.compression = compression;
            return this;
        }

        public Builder timestampPolicy(String timestampPolicy) {
            this.timestampPolicy = timestampPolicy;
            return this;
        }

        public Builder time(long time) {
            this.time = time;
            return this;
        }

        public ShadePlanEntry build() {
            return new ShadePlanEntry(this);
        }
    }
}
