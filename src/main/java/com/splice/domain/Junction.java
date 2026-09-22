package com.splice.domain;

/**
 * splice junction 边。fromOrd/toOrd 是 fixture 声明链转录顺序上的 exon ord。
 */
public record Junction(String id, int fromOrd, int toOrd, boolean lowMappability, String note) {
}
