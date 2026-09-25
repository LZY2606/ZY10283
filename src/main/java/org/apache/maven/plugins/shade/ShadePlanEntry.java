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
 * A single entry of a {@link ShadePlan}: the dry-run decision for one entry of one input artifact.
 *
 * @since 3.6.3
 */
public final class ShadePlanEntry {

    /**
     * Classification of the original entry.
     */
    public enum Kind {
        DIRECTORY,
        MANIFEST,
        SERVICE,
        SIGNATURE,
        MODULE_INFO,
        MULTI_RELEASE,
        CLASS,
        JAVA_SOURCE,
        RESOURCE
    }

    /**
     * What the shading process decided to do with the entry.
     */
    public enum Disposition {
        /** The entry is written to the output JAR. */
        WRITTEN,
        /** The entry loses against an earlier entry claiming the same output path. */
        DUPLICATE,
        /** The entry is consumed by a resource transformer. */
        TRANSFORMED,
        /** The entry is removed by a filter. */
        FILTERED,
        /** The entry is dropped by a built-in shader rule. */
        EXCLUDED,
        /** The entry is skipped (directory entries are synthesized on demand). */
        SKIPPED
    }

    /**
     * Compression of the output entry.
     */
    public enum Compression {
        STORED,
        DEFLATED,
        /** The compression is decided by the consuming transformer when it finishes the output. */
        TRANSFORMER_DEFINED
    }

    /**
     * Timestamp policy of the output entry.
     */
    public enum TimestampPolicy {
        /** The timestamp of the source entry is preserved. */
        SOURCE_ENTRY_TIME,
        /** The timestamp is decided by the consuming transformer when it finishes the output. */
        TRANSFORMER_DEFINED
    }

    private final String jar;

    private final String source;

    private final Kind kind;

    private final Disposition disposition;

    private final String filteredBy;

    private final String excludedReason;

    private final String output;

    private final String transformer;

    private final Map<String, String> transformerDetail;

    private final String duplicateGroup;

    private final Boolean winner;

    private final Compression compression;

    private final TimestampPolicy timestampPolicy;

    private ShadePlanEntry(Builder builder) {
        this.jar = builder.jar;
        this.source = builder.source;
        this.kind = builder.kind;
        this.disposition = builder.disposition;
        this.filteredBy = builder.filteredBy;
        this.excludedReason = builder.excludedReason;
        this.output = builder.output;
        this.transformer = builder.transformer;
        this.transformerDetail = Collections.unmodifiableMap(new TreeMap<>(builder.transformerDetail));
        this.duplicateGroup = builder.duplicateGroup;
        this.winner = builder.winner;
        this.compression = builder.compression;
        this.timestampPolicy = builder.timestampPolicy;
    }

    /**
     * @return path of the input artifact (JAR or directory) this entry was read from
     */
    public String getJar() {
        return jar;
    }

    /**
     * @return original path of the entry inside the input artifact
     */
    public String getSource() {
        return source;
    }

    /**
     * @return classification of the original entry
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * @return the decision taken for this entry
     */
    public Disposition getDisposition() {
        return disposition;
    }

    /**
     * @return class name of the filter that removed this entry, or {@code null}
     */
    public String getFilteredBy() {
        return filteredBy;
    }

    /**
     * @return reason why the shader itself excluded this entry, or {@code null}
     */
    public String getExcludedReason() {
        return excludedReason;
    }

    /**
     * @return final path in the output JAR after relocation, or {@code null} if the entry produces no output
     */
    public String getOutput() {
        return output;
    }

    /**
     * @return class name of the resource transformer consuming this entry, or {@code null}
     */
    public String getTransformer() {
        return transformer;
    }

    /**
     * @return read-only contribution of the consuming transformer, never {@code null}
     */
    public Map<String, String> getTransformerDetail() {
        return transformerDetail;
    }

    /**
     * @return output path shared with other entries when more than one entry claims it, or {@code null}
     */
    public String getDuplicateGroup() {
        return duplicateGroup;
    }

    /**
     * @return whether this entry wins its duplicate group, or {@code null} when not part of one
     */
    public Boolean getWinner() {
        return winner;
    }

    /**
     * @return compression of the output entry, or {@code null} when the entry produces no direct output
     */
    public Compression getCompression() {
        return compression;
    }

    /**
     * @return timestamp policy of the output entry, or {@code null} when the entry produces no direct output
     */
    public TimestampPolicy getTimestampPolicy() {
        return timestampPolicy;
    }

    static Builder builder(String jar, String source, Kind kind) {
        return new Builder(jar, source, kind);
    }

    ShadePlanEntry withDuplicateMembership(String group, boolean groupWinner) {
        Builder copy = new Builder(jar, source, kind);
        copy.disposition = disposition;
        copy.filteredBy = filteredBy;
        copy.excludedReason = excludedReason;
        copy.output = output;
        copy.transformer = transformer;
        copy.transformerDetail = transformerDetail;
        copy.duplicateGroup = group;
        copy.winner = groupWinner;
        copy.compression = compression;
        copy.timestampPolicy = timestampPolicy;
        return copy.build();
    }

    static final class Builder {
        private final String jar;

        private final String source;

        private final Kind kind;

        private Disposition disposition;

        private String filteredBy;

        private String excludedReason;

        private String output;

        private String transformer;

        private Map<String, String> transformerDetail = Collections.emptyMap();

        private String duplicateGroup;

        private Boolean winner;

        private Compression compression;

        private TimestampPolicy timestampPolicy;

        private Builder(String jar, String source, Kind kind) {
            this.jar = jar;
            this.source = source;
            this.kind = kind;
        }

        Builder disposition(Disposition disposition) {
            this.disposition = disposition;
            return this;
        }

        Builder filteredBy(String filteredBy) {
            this.filteredBy = filteredBy;
            return this;
        }

        Builder excludedReason(String excludedReason) {
            this.excludedReason = excludedReason;
            return this;
        }

        Builder output(String output) {
            this.output = output;
            return this;
        }

        Builder transformer(String transformer) {
            this.transformer = transformer;
            return this;
        }

        Builder transformerDetail(Map<String, String> transformerDetail) {
            this.transformerDetail = transformerDetail;
            return this;
        }

        Builder duplicateGroup(String duplicateGroup) {
            this.duplicateGroup = duplicateGroup;
            return this;
        }

        Builder winner(Boolean winner) {
            this.winner = winner;
            return this;
        }

        Builder compression(Compression compression) {
            this.compression = compression;
            return this;
        }

        Builder timestampPolicy(TimestampPolicy timestampPolicy) {
            this.timestampPolicy = timestampPolicy;
            return this;
        }

        ShadePlanEntry build() {
            return new ShadePlanEntry(this);
        }
    }
}
