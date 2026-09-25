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
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

/**
 * Serializes a {@link ShadePlan} to JSON with a fixed schema and deterministic ordering:
 * object keys always appear in declaration order, entries are sorted by input artifact and
 * original entry path, and all lists derived from sets are sorted lexicographically.
 *
 * @since 3.6.3
 */
public final class ShadePlanJsonWriter {

    private ShadePlanJsonWriter() {
        // utility
    }

    /**
     * Serializes the plan to a JSON string.
     *
     * @param plan the plan
     * @return the JSON document
     */
    public static String toJson(ShadePlan plan) {
        StringWriter writer = new StringWriter();
        try {
            write(plan, writer);
        } catch (IOException e) {
            // StringWriter does not throw
            throw new IllegalStateException(e);
        }
        return writer.toString();
    }

    /**
     * Serializes the plan to the given writer.
     *
     * @param plan the plan
     * @param writer the target
     * @throws IOException when writing fails
     */
    public static void write(ShadePlan plan, Writer writer) throws IOException {
        writer.write("{\n");
        field(writer, "  ", "schemaVersion", Integer.toString(ShadePlan.SCHEMA_VERSION), true);
        field(writer, "  ", "status", quote(plan.getStatus().name()), true);
        writeFailure(plan, writer);
        writeJars(plan, writer);
        writeEntries(plan, writer);
        writeStrings(writer, "outputDirectories", plan.getOutputDirectories(), true);
        writeStrings(writer, "transformerOutputs", plan.getTransformerOutputs(), false);
        writer.write("}\n");
        writer.flush();
    }

    private static void writeFailure(ShadePlan plan, Writer writer) throws IOException {
        writer.write("  \"failure\": ");
        if (plan.getFailure() == null) {
            writer.write("null");
        } else {
            writer.write("{\n");
            String nested = "    ";
            field(writer, nested, "jar", quote(plan.getFailure().getJar()), true);
            field(writer, nested, "entry", quote(plan.getFailure().getEntry()), true);
            field(writer, nested, "error", quote(plan.getFailure().getError()), false);
            writer.write("  }");
        }
        writer.write(",\n");
    }

    private static void writeJars(ShadePlan plan, Writer writer) throws IOException {
        writeStrings(writer, "jars", plan.getJars(), true);
    }

    private static void writeEntries(ShadePlan plan, Writer writer) throws IOException {
        writer.write("  \"entries\": [");
        if (!plan.getEntries().isEmpty()) {
            writer.write('\n');
            for (int i = 0; i < plan.getEntries().size(); i++) {
                ShadePlanEntry entry = plan.getEntries().get(i);
                writer.write("    {\n");
                String nested = "      ";
                field(writer, nested, "jar", quote(entry.getJar()), true);
                field(writer, nested, "source", quote(entry.getSource()), true);
                field(writer, nested, "kind", quote(entry.getKind().name()), true);
                field(
                        writer,
                        nested,
                        "disposition",
                        quote(entry.getDisposition().name()),
                        true);
                field(writer, nested, "filteredBy", quote(entry.getFilteredBy()), true);
                field(writer, nested, "excludedReason", quote(entry.getExcludedReason()), true);
                field(writer, nested, "output", quote(entry.getOutput()), true);
                field(writer, nested, "transformer", quote(entry.getTransformer()), true);
                writeDetail(writer, nested, entry);
                field(writer, nested, "duplicateGroup", quote(entry.getDuplicateGroup()), true);
                field(
                        writer,
                        nested,
                        "winner",
                        entry.getWinner() == null ? "null" : entry.getWinner().toString(),
                        true);
                field(
                        writer,
                        nested,
                        "compression",
                        entry.getCompression() == null
                                ? "null"
                                : quote(entry.getCompression().name()),
                        true);
                field(
                        writer,
                        nested,
                        "timestampPolicy",
                        entry.getTimestampPolicy() == null
                                ? "null"
                                : quote(entry.getTimestampPolicy().name()),
                        false);
                writer.write(i + 1 < plan.getEntries().size() ? "    },\n" : "    }\n");
            }
            writer.write("  ],\n");
        } else {
            writer.write("],\n");
        }
    }

    private static void writeDetail(Writer writer, String nested, ShadePlanEntry entry) throws IOException {
        writer.write(nested);
        writer.write("\"transformerDetail\": {");
        if (!entry.getTransformerDetail().isEmpty()) {
            writer.write('\n');
            int i = 0;
            for (Map.Entry<String, String> detail : entry.getTransformerDetail().entrySet()) {
                writer.write(nested);
                writer.write("  ");
                writer.write(quote(detail.getKey()));
                writer.write(": ");
                writer.write(quote(detail.getValue()));
                writer.write(++i < entry.getTransformerDetail().size() ? ",\n" : "\n");
            }
            writer.write(nested);
            writer.write('}');
        } else {
            writer.write('}');
        }
        writer.write(",\n");
    }

    private static void writeStrings(Writer writer, String name, Iterable<String> values, boolean comma)
            throws IOException {
        writer.write("  \"");
        writer.write(name);
        writer.write("\": [");
        java.util.Iterator<String> it = values.iterator();
        if (it.hasNext()) {
            writer.write('\n');
            while (it.hasNext()) {
                writer.write("    ");
                writer.write(quote(it.next()));
                writer.write(it.hasNext() ? ",\n" : "\n");
            }
            writer.write("  ]");
        } else {
            writer.write(']');
        }
        writer.write(comma ? ",\n" : "\n");
    }

    private static void field(Writer writer, String indent, String name, String value, boolean comma)
            throws IOException {
        writer.write(indent);
        writer.write('"');
        writer.write(name);
        writer.write("\": ");
        writer.write(value);
        writer.write(comma ? ",\n" : "\n");
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
        return out.toString();
    }
}
