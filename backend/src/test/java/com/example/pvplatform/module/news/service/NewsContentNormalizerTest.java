package com.example.pvplatform.module.news.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsContentNormalizerTest {
    private final NewsContentNormalizer normalizer = new NewsContentNormalizer();

    @Test
    void removesGovernmentMetadataAndRepeatedTitle() {
        String title = "新能源发展取得新进展";
        String html = "<h1>新能源发展取得新进展</h1><table><tr><td>目录项的基本信息</td></tr>"
            + "<tr><td>索引号</td><td>123</td><td>主办单位</td><td>国家能源局</td></tr>"
            + "<tr><td>制发日期</td><td>2026-07-15</td></tr></table><p>真正正文从这里开始。</p>";

        String cleaned = normalizer.cleanContent(html, title, true);

        assertThat(cleaned).doesNotContain("目录项的基本信息", "索引号", "主办单位", "制发日期", "<h1>");
        assertThat(cleaned).contains("真正正文从这里开始");
    }

    @Test
    void flattensSingleCellWebsiteLayoutTable() {
        String html = "<table><tr><td><p><strong>文章标题</strong></p><p>真正正文。</p></td></tr></table>";
        String cleaned = normalizer.cleanContent(html, "文章标题", true);
        assertThat(cleaned).doesNotContain("<table", "文章标题").contains("真正正文");
    }

    @Test
    void hidesDerivedSummaryButKeepsIndependentSummary() {
        String content = "<p>光伏装机规模持续增长，新能源消纳能力进一步提升。</p><p>后续正文。</p>";
        assertThat(normalizer.visibleSummary("光伏装机规模持续增长，新能源消纳能力进一步提升。", content)).isNull();
        assertThat(normalizer.visibleSummary("目录项的基本信息 索引号：123 主办单位：国家能源局", content)).isNull();
        assertThat(normalizer.visibleSummary("本文概述行业最新进展及其长期影响。", content)).isEqualTo("本文概述行业最新进展及其长期影响。");
    }
}
