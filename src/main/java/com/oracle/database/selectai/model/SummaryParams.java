/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License Version 1.0 as shown at
 * https://oss.oracle.com/licenses/upl/
 */
package com.oracle.database.selectai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

/**
 * Typed parameters payload for {@code DBMS_CLOUD_AI.SUMMARIZE}.
 * <p>
 * This model lets Java callers configure summarization without manually
 * constructing JSON. It serializes to the snake_case names expected by
 * {@code DBMS_CLOUD_AI.SUMMARIZE}.
 * <p>
 * This model represents the JSON payload supplied to the {@code params}
 * argument of {@code DBMS_CLOUD_AI.SUMMARIZE}. It serializes Java fields to
 * the snake_case names expected by the database, such as {@code min_words},
 * {@code max_words}, {@code summary_style},
 * {@code chunk_processing_method}, and {@code extractiveness_level}.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/dbms-cloud-ai-package.html#GUID-90D2E0E6-1EF1-4275-9927-8E0613FD305D">
 *      DBMS_CLOUD_AI summarize parameters</a>
 */
public final class SummaryParams {

    /** Approximate minimum summary word count. */
    private final Integer minWords;
    /** Approximate maximum summary word count. */
    private final Integer maxWords;
    /** Output formatting style for the generated summary. */
    private final Style summaryStyle;
    /** Chunk processing method for content that exceeds model token limits. */
    private final ChunkProcessingMethod chunkProcessingMethod;
    /** Degree to which generated text follows original wording. */
    private final ExtractivenessLevel extractivenessLevel;

    private SummaryParams(Builder builder) {
        this.minWords = builder.minWords;
        this.maxWords = builder.maxWords;
        this.summaryStyle = builder.summaryStyle;
        this.chunkProcessingMethod = builder.chunkProcessingMethod;
        this.extractivenessLevel = builder.extractivenessLevel;
    }

    /**
     * Creates a builder for summarize parameters.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the approximate minimum word count.
     *
     * @return minimum word count, or {@code null} when unset
     */
    public Integer getMinWords() {
        return minWords;
    }

    /**
     * Returns the approximate maximum word count.
     *
     * @return maximum word count, or {@code null} when unset
     */
    public Integer getMaxWords() {
        return maxWords;
    }

    /**
     * Returns the requested summary style.
     *
     * @return summary style, or {@code null} when unset
     */
    public String getSummaryStyle() {
        return summaryStyle == null ? null : summaryStyle.getValue();
    }

    /**
     * Returns the requested summary style enum.
     *
     * @return summary style enum, or {@code null} when unset
     */
    @JsonIgnore
    public Style getSummaryStyleEnum() {
        return summaryStyle;
    }

    /**
     * Returns the chunk processing method.
     *
     * @return chunk processing method, or {@code null} when unset
     */
    public String getChunkProcessingMethod() {
        return chunkProcessingMethod == null ? null : chunkProcessingMethod.getValue();
    }

    /**
     * Returns the chunk processing method enum.
     *
     * @return chunk processing method enum, or {@code null} when unset
     */
    @JsonIgnore
    public ChunkProcessingMethod getChunkProcessingMethodEnum() {
        return chunkProcessingMethod;
    }

    /**
     * Returns the extractiveness level.
     *
     * @return extractiveness level, or {@code null} when unset
     */
    public String getExtractivenessLevel() {
        return extractivenessLevel == null ? null : extractivenessLevel.getValue();
    }

    /**
     * Returns the extractiveness level enum.
     *
     * @return extractiveness level enum, or {@code null} when unset
     */
    @JsonIgnore
    public ExtractivenessLevel getExtractivenessLevelEnum() {
        return extractivenessLevel;
    }

    /**
     * Serializes the parameters using DBMS_CLOUD_AI snake_case JSON names.
     *
     * @return JSON representation of this SummaryParams instance
     */
    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize SummaryParams", e);
        }
    }

    /**
     * Output format style for the generated summary.
     */
    public enum Style {
        /** One or more paragraphs. */
        PARAGRAPH("paragraph"),
        /** List of key points. */
        LIST("list");

        private final String value;

        Style(String value) {
            this.value = value;
        }

        /**
         * Returns the database JSON value.
         *
         * @return database JSON value
         */
        public String getValue() {
            return value;
        }
    }

    /**
     * Method used when the source content must be split into chunks.
     */
    public enum ChunkProcessingMethod {
        /** Iteratively refines the summary across chunks. */
        ITERATIVE_REFINEMENT("iterative_refinement"),
        /** Summarizes chunks and reduces them into a final summary. */
        MAP_REDUCE("map_reduce");

        private final String value;

        ChunkProcessingMethod(String value) {
            this.value = value;
        }

        /**
         * Returns the database JSON value.
         *
         * @return database JSON value
         */
        public String getValue() {
            return value;
        }
    }

    /**
     * Degree to which the generated summary follows source wording.
     */
    public enum ExtractivenessLevel {
        /** Stays close to original phrasing. */
        HIGH("high"),
        /** Balances extraction and paraphrasing. */
        MEDIUM("medium"),
        /** Allows more freedom to reword and restructure. */
        LOW("low");

        private final String value;

        ExtractivenessLevel(String value) {
            this.value = value;
        }

        /**
         * Returns the database JSON value.
         *
         * @return database JSON value
         */
        public String getValue() {
            return value;
        }
    }

    /**
     * Builder for {@link SummaryParams}.
     */
    public static final class Builder {
        /** Minimum word count being assembled. */
        private Integer minWords;
        /** Maximum word count being assembled. */
        private Integer maxWords;
        /** Summary style being assembled. */
        private Style summaryStyle;
        /** Chunk processing method being assembled. */
        private ChunkProcessingMethod chunkProcessingMethod;
        /** Extractiveness level being assembled. */
        private ExtractivenessLevel extractivenessLevel;

        private Builder() {
        }

        /**
         * Sets the approximate minimum number of words.
         *
         * @param minWords minimum word count, greater than or equal to 0
         * @return this builder instance
         */
        public Builder minWords(Integer minWords) {
            if (minWords != null && minWords < 0) {
                throw new IllegalArgumentException("minWords must be greater than or equal to 0");
            }
            this.minWords = minWords;
            return this;
        }

        /**
         * Sets the approximate maximum number of words.
         *
         * @param maxWords maximum word count, greater than or equal to 1
         * @return this builder instance
         */
        public Builder maxWords(Integer maxWords) {
            if (maxWords != null && maxWords < 1) {
                throw new IllegalArgumentException("maxWords must be greater than or equal to 1");
            }
            this.maxWords = maxWords;
            return this;
        }

        /**
         * Sets the output summary style.
         *
         * @param summaryStyle summary style
         * @return this builder instance
         */
        public Builder summaryStyle(Style summaryStyle) {
            this.summaryStyle = summaryStyle;
            return this;
        }

        /**
         * Sets the chunk processing method.
         *
         * @param chunkProcessingMethod chunk processing method
         * @return this builder instance
         */
        public Builder chunkProcessingMethod(ChunkProcessingMethod chunkProcessingMethod) {
            this.chunkProcessingMethod = chunkProcessingMethod;
            return this;
        }

        /**
         * Sets the extractiveness level.
         *
         * @param extractivenessLevel extractiveness level
         * @return this builder instance
         */
        public Builder extractivenessLevel(ExtractivenessLevel extractivenessLevel) {
            this.extractivenessLevel = extractivenessLevel;
            return this;
        }

        /**
         * Builds immutable summarize parameters.
         *
         * @return immutable SummaryParams built from current builder state
         */
        public SummaryParams build() {
            if (minWords != null && maxWords != null && minWords > maxWords) {
                throw new IllegalArgumentException("minWords must be less than or equal to maxWords");
            }
            if (minWords == null && maxWords == null && summaryStyle == null
                    && chunkProcessingMethod == null && extractivenessLevel == null) {
                throw new IllegalArgumentException("at least one summarize parameter must be set");
            }
            return new SummaryParams(this);
        }
    }
}
