package com.splice.domain;

/** collected=false 表示该样本未采集：不允许存在任何 junction 计数行。 */
public record SampleInfo(String id, String group, boolean collected) {
}
