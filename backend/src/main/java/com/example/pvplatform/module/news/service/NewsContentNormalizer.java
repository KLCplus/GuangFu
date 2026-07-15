package com.example.pvplatform.module.news.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared cleanup for newly fetched articles and legacy records returned by the API. */
@Component
public class NewsContentNormalizer {
    private static final String REMOVABLE = "script,style,iframe,nav,footer,form,button,input,select,textarea,"
        + ".share,.share-box,.bdsharebuttonbox,.related,.recommend,.advertisement,.ads,.copyright,"
        + ".toolbar,.article-tools,.print,.qr-code";
    private static final Set<String> METADATA_LABELS = Set.of(
        "目录项的基本信息", "公开事项名称", "索引号", "主办单位", "制发日期", "发文字号", "文号"
    );
    private static final Safelist ARTICLE_SAFELIST = new Safelist()
        .addTags("p", "br", "h1", "h2", "h3", "h4", "ul", "ol", "li", "strong", "em", "blockquote",
            "table", "thead", "tbody", "tr", "th", "td", "a")
        .addAttributes("a", "href", "title")
        .addAttributes("th", "rowspan", "colspan")
        .addAttributes("td", "rowspan", "colspan")
        .addProtocols("a", "href", "http", "https")
        .addEnforcedAttribute("a", "target", "_blank")
        .addEnforcedAttribute("a", "rel", "noopener noreferrer");

    public String cleanContent(String content, String title, boolean external) {
        if (content == null || content.isBlank()) return "";
        Document document = Jsoup.parseBodyFragment(content);
        document.select(REMOVABLE).remove();
        if (external) {
            removeMetadataBlocks(document);
            removeRepeatedTitle(document, title);
            flattenLayoutTables(document);
        }
        return Jsoup.clean(document.body().html(), "", ARTICLE_SAFELIST).trim();
    }

    public String visibleSummary(String summary, String content) {
        String normalizedSummary = normalizeText(summary);
        if (normalizedSummary.isBlank()) return null;
        if (metadataScore(summary) >= 2) return null;
        String normalizedBody = normalizeText(Jsoup.parseBodyFragment(content == null ? "" : content).text());
        if (normalizedBody.isBlank()) return summary.trim();
        String bodyStart = normalizedBody.substring(0, Math.min(normalizedBody.length(), Math.max(180, normalizedSummary.length() * 2)));
        if (bodyStart.startsWith(normalizedSummary) || normalizedSummary.startsWith(bodyStart)) return null;
        int comparisonLength = Math.min(normalizedSummary.length(), bodyStart.length());
        if (comparisonLength >= 24 && similarity(normalizedSummary.substring(0, comparisonLength), bodyStart.substring(0, comparisonLength)) >= 0.82) {
            return null;
        }
        return summary.trim();
    }

    private void removeMetadataBlocks(Document document) {
        for (Element table : document.select("table")) {
            if (metadataScore(table.text()) >= 2) table.remove();
        }
        for (Element element : List.copyOf(document.body().children())) {
            String text = normalizeText(element.text());
            if (text.isBlank()) continue;
            if (METADATA_LABELS.stream().anyMatch(text::contains) && (metadataScore(text) >= 2 || text.length() < 40)) {
                element.remove();
                continue;
            }
            // Metadata is a leading website chrome block; stop once genuine prose begins.
            if (text.length() >= 80) break;
        }
    }

    private void removeRepeatedTitle(Document document, String title) {
        String normalizedTitle = normalizeText(title);
        if (normalizedTitle.isBlank()) return;
        for (Element element : List.copyOf(document.body().children()).subList(0, Math.min(4, document.body().childrenSize()))) {
            String text = normalizeText(element.text());
            if (text.equals(normalizedTitle) || (text.length() >= 12 && normalizedTitle.equals(text.replace("标题", "")))) {
                element.remove();
            }
        }
        for (Element element : List.copyOf(document.select("h1,h2,h3,h4,p"))) {
            if (normalizeText(element.text()).equals(normalizedTitle)) element.remove();
        }
    }

    private void flattenLayoutTables(Document document) {
        for (Element table : List.copyOf(document.select("table"))) {
            if (normalizeText(table.text()).isBlank()) {
                table.remove();
                continue;
            }
            Element section = table.childrenSize() == 1 ? table.child(0) : null;
            Element row = section != null && Set.of("tbody", "thead", "tfoot").contains(section.tagName())
                && section.childrenSize() == 1 ? section.child(0) : null;
            Element cell = row != null && "tr".equals(row.tagName()) && row.childrenSize() == 1 ? row.child(0) : null;
            if (cell != null && Set.of("td", "th").contains(cell.tagName())) {
                table.before(cell.html());
                table.remove();
            }
        }
    }

    private int metadataScore(String value) {
        String text = normalizeText(value);
        return (int) METADATA_LABELS.stream().filter(text::contains).count();
    }

    private String normalizeText(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
            .replaceAll("[\\p{P}\\p{S}\\p{Z}\\s]+", "")
            .trim();
    }

    private double similarity(String left, String right) {
        int distance = levenshtein(left, right);
        return 1.0 - (double) distance / Math.max(left.length(), right.length());
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous; previous = current; current = swap;
        }
        return previous[right.length()];
    }
}
