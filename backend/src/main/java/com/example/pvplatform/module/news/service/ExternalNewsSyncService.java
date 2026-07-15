package com.example.pvplatform.module.news.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.pvplatform.module.news.config.NewsSyncProperties;
import com.example.pvplatform.persistence.entity.NewsDO;
import com.example.pvplatform.persistence.mapper.NewsMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExternalNewsSyncService {
    private static final Logger log = LoggerFactory.getLogger(ExternalNewsSyncService.class);
    private static final Pattern DATE = Pattern.compile("(20\\d{2})[-./年](\\d{1,2})[-./月](\\d{1,2})");
    private static final Pattern DATE_TIME = Pattern.compile("(20\\d{2})[-./年](\\d{1,2})[-./月](\\d{1,2})(?:日)?(?:\\s+(\\d{1,2}):(\\d{2}))?");
    private static final Set<String> DISASTER_WORDS = Set.of("灾害", "地质", "滑坡", "泥石流", "山洪", "洪涝", "暴雨", "强降雨", "台风", "大风", "强对流", "高温", "干旱", "防汛", "抗旱", "应急响应", "风险");
    private static final Set<String> ENERGY_WORDS = Set.of("光伏", "太阳能", "新能源", "可再生能源", "并网", "消纳", "储能", "绿电", "绿证", "电站");
    private static final Set<String> ENTERPRISE_WORDS = Set.of("光伏", "组件", "电站", "储能", "运维", "数字化", "数字能源", "逆变器");
    private static final String REMOVABLE_CONTENT = "script,style,iframe,nav,footer,form,button,input,select,textarea,.share,.related,.recommend,.advertisement,.ads,.copyright";
    private static final String ATTACHMENT_SELECTOR = "a[href$='.pdf'],a[href*='.pdf?'],a[href$='.doc'],a[href*='.doc?'],a[href$='.docx'],a[href*='.docx?'],a[href$='.xls'],a[href*='.xls?'],a[href$='.xlsx'],a[href*='.xlsx?']";
    private static final Safelist ARTICLE_SAFELIST = new Safelist()
        .addTags("p", "br", "h1", "h2", "h3", "h4", "ul", "ol", "li", "strong", "em", "blockquote",
            "table", "thead", "tbody", "tr", "th", "td", "a")
        .addAttributes("a", "href", "title")
        .addAttributes("th", "rowspan", "colspan")
        .addAttributes("td", "rowspan", "colspan")
        .addProtocols("a", "href", "http", "https")
        .addEnforcedAttribute("a", "target", "_blank")
        .addEnforcedAttribute("a", "rel", "noopener noreferrer");

    private final NewsMapper mapper;
    private final NewsSyncProperties properties;
    private final NewsTlsSupport tlsSupport;
    private final NewsContentNormalizer contentNormalizer;

    public ExternalNewsSyncService(NewsMapper mapper, NewsSyncProperties properties, NewsTlsSupport tlsSupport,
                                   NewsContentNormalizer contentNormalizer) {
        this.mapper = mapper;
        this.properties = properties;
        this.tlsSupport = tlsSupport;
        this.contentNormalizer = contentNormalizer;
    }

    public Map<String, SyncStats> syncConfiguredSources() {
        Map<String, SyncStats> result = new LinkedHashMap<>();
        run(result, "MEM", properties.isMemEnabled(), this::syncMem);
        run(result, "NEA", properties.isNeaEnabled(), this::syncNea);
        run(result, "LONGI", properties.isLongiEnabled(), this::syncLongi);
        return result;
    }

    public SyncStats syncSource(String source) {
        return switch (source == null ? "" : source.trim().toUpperCase(Locale.ROOT)) {
            case "MEM" -> syncMem();
            case "NEA" -> syncNea();
            case "LONGI" -> syncLongi();
            default -> throw new IllegalArgumentException("不支持的新闻来源");
        };
    }

    @CacheEvict(cacheNames = {"news:public-list", "news:detail"}, allEntries = true)
    public SyncStats syncMem() {
        return syncWeb("MEM", "GOVERNMENT_SITE", "中华人民共和国应急管理部",
            "https://www.mem.gov.cn/xw/yjglbgzdt/", "DISASTER", DISASTER_WORDS,
            "a[href$='.shtml']", List.of("#UCAP-CONTENT", ".TRS_Editor", ".article-content", ".content"));
    }

    @CacheEvict(cacheNames = {"news:public-list", "news:detail"}, allEntries = true)
    public SyncStats syncNea() {
        return syncWeb("NEA", "GOVERNMENT_SITE", "国家能源局",
            "https://www.nea.gov.cn/sjzz/xny/index.htm", "INDUSTRY", ENERGY_WORDS,
            "a[href$=/c.html], a[href*='/c.html']", List.of("#zoom", ".article-content", ".TRS_Editor", ".content"));
    }

    @CacheEvict(cacheNames = {"news:public-list", "news:detail"}, allEntries = true)
    public SyncStats syncLongi() {
        return syncWeb("LONGI", "ENTERPRISE_SITE", "隆基绿能",
            "https://www.longi.com/cn/news/", "ENTERPRISE", ENTERPRISE_WORDS,
            "a[href*=/cn/news/]", List.of("main", ".news-detail", ".detail-content", ".rich-text"));
    }

    private SyncStats syncWeb(String code, String sourceType, String sourceName, String listUrl,
                              String defaultCategory, Set<String> keywords, String linkSelector,
                              List<String> bodySelectors) {
        MutableStats stats = new MutableStats();
        try {
            repairLegacyLongTitles(stats, sourceType, sourceName);
            Document list = fetch(listUrl);
            Map<String, Candidate> candidates = new LinkedHashMap<>();
            for (Element link : list.select(linkSelector)) {
                String title = extractTitle(link);
                String url = normalizeUrl(link.absUrl("href"));
                if (title.length() < 4 || url == null || url.equals(listUrl) || !containsAny(title, keywords)) {
                    stats.skipped++;
                    continue;
                }
                String context = nearestDatedContext(link);
                candidates.putIfAbsent(url, new Candidate(title, url, parsePublishedAt(context)));
                if (candidates.size() >= Math.max(1, properties.getMaxItemsPerSource())) break;
            }
            stats.parsed = candidates.size();
            for (Candidate candidate : candidates.values()) {
                try {
                    Document detail = fetch(candidate.url());
                    ExtractedContent extracted = extractContent(detail, bodySelectors);
                    if (extracted.body().isBlank()) { stats.skipped++; continue; }
                    Candidate enriched = enrichCandidate(candidate, detail);
                    String category = "NEA".equals(code) && isPolicy(enriched.title()) ? "POLICY" : defaultCategory;
                    upsert(stats, code, sourceType, sourceName, category, enriched, extracted);
                } catch (Exception exception) {
                    stats.failed++;
                    log.warn("新闻详情同步失败 source={} url={} reason={}", code, candidate.url(), exception.getMessage());
                }
            }
        } catch (Exception exception) {
            stats.failed++;
            log.warn("新闻列表同步失败 source={} url={} reason={}", code, listUrl, exception.getMessage());
        }
        SyncStats result = stats.freeze();
        log.info("新闻来源同步完成 source={} parsed={} inserted={} updated={} duplicate={} skipped={} failed={}",
            code, result.parsed(), result.inserted(), result.updated(), result.duplicate(), result.skipped(), result.failed());
        return result;
    }

    private void upsert(MutableStats stats, String code, String sourceType, String sourceName,
                        String category, Candidate candidate, ExtractedContent extracted) {
        String externalId = sha256(candidate.url());
        NewsDO existing = mapper.selectOne(Wrappers.<NewsDO>lambdaQuery()
            .eq(NewsDO::getSourceType, sourceType).eq(NewsDO::getExternalId, externalId).last("LIMIT 1"));
        LocalDateTime published = candidate.publishedAt() == null ? LocalDateTime.now() : candidate.publishedAt();
        String content = contentNormalizer.cleanContent(extracted.body(), candidate.title(), true);
        String summary = contentNormalizer.visibleSummary(abbreviate(Jsoup.parseBodyFragment(content).text(), 260), content);
        Attachment attachment = extracted.attachment();
        if (existing != null) {
            boolean samePublishedAt = candidate.publishedAt() == null || published.equals(existing.getSourcePublishedAt());
            boolean sameAttachment = java.util.Objects.equals(value(attachment, Attachment::name), existing.getAttachmentName())
                && java.util.Objects.equals(value(attachment, Attachment::type), existing.getAttachmentType())
                && java.util.Objects.equals(value(attachment, Attachment::url), existing.getAttachmentUrl());
            if (candidate.title().equals(existing.getTitle()) && content.equals(existing.getContent()) && samePublishedAt && sameAttachment) {
                existing.setFetchedAt(LocalDateTime.now());
                mapper.updateById(existing);
                stats.duplicate++;
                return;
            }
            existing.setTitle(candidate.title()); existing.setSummary(summary); existing.setContent(content);
            existing.setAttachmentName(value(attachment, Attachment::name));
            existing.setAttachmentType(value(attachment, Attachment::type));
            existing.setAttachmentUrl(value(attachment, Attachment::url));
            existing.setCategory(category); existing.setSourcePublishedAt(published); existing.setPublishedAt(published);
            existing.setFetchedAt(LocalDateTime.now()); existing.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(existing); stats.updated++; return;
        }
        NewsDO row = new NewsDO();
        row.setTitle(candidate.title()); row.setSummary(summary); row.setContent(content);
        row.setAttachmentName(value(attachment, Attachment::name));
        row.setAttachmentType(value(attachment, Attachment::type));
        row.setAttachmentUrl(value(attachment, Attachment::url));
        row.setNewsType("NEWS"); row.setCategory(category); row.setContentType("EXTERNAL_NEWS");
        row.setSourceType(sourceType); row.setSourceName(sourceName); row.setSourceUrl(candidate.url());
        row.setExternalId(externalId); row.setSourcePublishedAt(published); row.setFetchedAt(LocalDateTime.now());
        row.setExternalContent(1); row.setTargetRole("ALL"); row.setStatus("PUBLISHED");
        row.setPublishedAt(published); row.setCreatedAt(LocalDateTime.now()); row.setUpdatedAt(LocalDateTime.now()); row.setDeleted(0);
        mapper.insert(row); stats.inserted++;
    }

    private Document fetch(String url) throws java.io.IOException {
        return tlsSupport.apply(Jsoup.connect(url), url)
            .userAgent(properties.getUserAgent())
            .timeout(Math.max(properties.getConnectTimeoutMs(), properties.getReadTimeoutMs()))
            .followRedirects(true).maxBodySize(2_000_000).get();
    }

    private ExtractedContent extractContent(Document document, List<String> selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            if (element != null) {
                Element article = element.clone();
                article.select(REMOVABLE_CONTENT).remove();
                Attachment attachment = findAttachment(article, document);
                article.select(ATTACHMENT_SELECTOR).remove();
                String html = Jsoup.clean(article.html(), document.baseUri(), ARTICLE_SAFELIST);
                if (Jsoup.parseBodyFragment(html).text().length() >= 30) return new ExtractedContent(html, attachment);
            }
        }
        return new ExtractedContent("", findAttachment(document, document));
    }

    private Attachment findAttachment(Element primary, Document document) {
        Element link = primary.selectFirst(ATTACHMENT_SELECTOR);
        if (link == null && primary != document) link = document.selectFirst(ATTACHMENT_SELECTOR);
        if (link == null) return null;
        String url = normalizeUrl(link.absUrl("href"));
        if (url == null) return null;
        String type = attachmentType(url, link.text());
        if (type == null) return null;
        String name = clean(link.text());
        if (name.isBlank()) {
            String path = URI.create(url).getPath();
            String file = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
            name = URLDecoder.decode(file, StandardCharsets.UTF_8);
        }
        if (name.isBlank()) name = "文章附件." + type.toLowerCase(Locale.ROOT);
        return new Attachment(abbreviate(name, 255), type, url);
    }

    private String attachmentType(String url, String label) {
        String value = (url + " " + (label == null ? "" : label)).toLowerCase(Locale.ROOT);
        for (String extension : List.of("PDF", "DOCX", "DOC", "XLSX", "XLS")) {
            if (value.matches(".*\\." + extension.toLowerCase(Locale.ROOT) + "(?:[?#].*)?(?:\\s.*)?$")) return extension;
        }
        return null;
    }

    private <T> String value(T value, java.util.function.Function<T, String> getter) {
        return value == null ? null : getter.apply(value);
    }

    private Candidate enrichCandidate(Candidate candidate, Document detail) {
        Element heading = detail.selectFirst("h1");
        String title = heading == null ? candidate.title() : clean(heading.text());
        LocalDateTime publishedAt = candidate.publishedAt();
        if (heading != null && heading.parent() != null) {
            LocalDateTime detailTime = parsePublishedAt(heading.parent().text());
            if (detailTime != null) publishedAt = detailTime;
        }
        Element time = detail.selectFirst("time,.date,.time,.publish-time,.article-date");
        if (time != null) {
            LocalDateTime detailTime = parsePublishedAt(time.text());
            if (detailTime != null) publishedAt = detailTime;
        }
        return new Candidate(title.isBlank() ? candidate.title() : title, candidate.url(), publishedAt);
    }

    private void repairLegacyLongTitles(MutableStats stats, String sourceType, String sourceName) {
        List<NewsDO> rows = mapper.selectList(Wrappers.<NewsDO>lambdaQuery()
            .eq(NewsDO::getSourceType, sourceType).eq(NewsDO::getSourceName, sourceName).isNotNull(NewsDO::getSourceUrl));
        for (NewsDO row : rows) {
            if (row.getTitle() == null || (row.getTitle().length() <= 100
                && !row.getTitle().matches(".*隆基新闻\\s+\\d{4}.*"))) continue;
            try {
                Document detail = fetch(row.getSourceUrl());
                Element h1 = detail.selectFirst("h1");
                String title = h1 == null ? clean(detail.title().replaceAll("[-—_]\\s*(隆基绿能|国家能源局|中华人民共和国应急管理部).*$", "")) : clean(h1.text());
                if (!title.isBlank() && !title.equals(row.getTitle())) {
                    row.setTitle(title); row.setUpdatedAt(LocalDateTime.now()); mapper.updateById(row); stats.updated++;
                }
            } catch (Exception exception) {
                stats.failed++;
                log.warn("历史外部标题清洗失败 source={} url={} reason={}", sourceName, row.getSourceUrl(), exception.getMessage());
            }
        }
    }

    private void run(Map<String, SyncStats> result, String source, boolean enabled, SyncCall call) {
        if (!enabled) return;
        try { result.put(source, call.run()); }
        catch (Exception exception) { result.put(source, new SyncStats(0,0,0,0,0,1)); log.warn("新闻来源隔离失败 source={}", source, exception); }
    }

    private boolean containsAny(String value, Set<String> words) { return words.stream().anyMatch(value::contains); }
    private String nearestDatedContext(Element link) {
        Element current = link;
        String fallback = link.text();
        for (int depth = 0; depth < 5 && current != null; depth++, current = current.parent()) {
            String text = clean(current.text());
            if (!text.isBlank()) fallback = text;
            if (parsePublishedAt(text) != null) return text;
        }
        return fallback;
    }
    private String extractTitle(Element link) {
        String title = clean(link.attr("title"));
        if (!title.isBlank()) return title;
        Element heading = link.selectFirst("h1,h2,h3,h4,h5,.title,.news-title");
        if (heading != null && !clean(heading.text()).isBlank()) return clean(heading.text());
        String own = clean(link.ownText());
        if (!own.isBlank()) return own;
        String text = clean(link.text());
        Matcher date = DATE.matcher(text);
        if (date.find()) text = text.substring(0, date.start()).trim();
        return text.length() <= 180 ? text : text.substring(0, 180).trim();
    }
    private boolean isPolicy(String value) { return containsAny(value, Set.of("政策", "标准", "办法", "意见", "通知", "公告", "规范", "规定")); }
    private String clean(String value) { return value == null ? "" : value.replaceAll("\\s+", " ").trim(); }
    private String abbreviate(String value, int max) { return value.length() <= max ? value : value.substring(0, max) + "…"; }
    private String normalizeUrl(String value) {
        try { if (value == null || value.isBlank()) return null; URI uri = URI.create(value).normalize(); return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) ? uri.toString() : null; }
        catch (Exception ignored) { return null; }
    }
    private LocalDateTime parsePublishedAt(String value) {
        Matcher matcher = DATE_TIME.matcher(value == null ? "" : value);
        if (!matcher.find()) return null;
        try {
            int hour = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));
            int minute = matcher.group(5) == null ? 0 : Integer.parseInt(matcher.group(5));
            return LocalDateTime.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)), hour, minute);
        } catch (Exception ignored) { return null; }
    }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private record Candidate(String title, String url, LocalDateTime publishedAt) {}
    private record Attachment(String name, String type, String url) {}
    private record ExtractedContent(String body, Attachment attachment) {}
    public record SyncStats(int parsed, int inserted, int updated, int duplicate, int skipped, int failed) {}
    private interface SyncCall { SyncStats run(); }
    private static final class MutableStats {
        int parsed, inserted, updated, duplicate, skipped, failed;
        SyncStats freeze() { return new SyncStats(parsed, inserted, updated, duplicate, skipped, failed); }
    }
}
