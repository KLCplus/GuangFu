package com.example.pvplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

import java.util.List;

/** Idempotent platform-authored operational notes for fresh and existing demo databases. */
@Component
@Order(100)
@ConditionalOnProperty(prefix = "news.platform-seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PlatformNewsSeedMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PlatformNewsSeedMigration.class);
    private final JdbcTemplate jdbc;

    public PlatformNewsSeedMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("news") || !columnExists("news", "category") || !columnExists("news", "external_id")) {
            log.warn("Skip platform news seed: initialize and migrate the news table first");
            return;
        }
        int inserted = 0;
        for (Article article : articles()) {
            inserted += jdbc.update("""
                INSERT INTO news
                  (title, summary, content, news_type, category, content_type, source_type, source_name,
                   external_id, external_content, target_role, status, published_at, created_at, updated_at, deleted)
                SELECT ?, ?, ?, 'NEWS', 'PLATFORM', 'PLATFORM_ARTICLE', 'PLATFORM', '光伏智云平台',
                       ?, 0, 'ALL', 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                WHERE NOT EXISTS (
                  SELECT 1 FROM news WHERE source_type = 'PLATFORM' AND external_id = ?
                )
                """, article.title(), article.summary(), article.content(), article.externalId(), article.externalId());
        }
        log.info("平台原创资讯初始化完成 inserted={} existing={}", inserted, articles().size() - inserted);
    }

    private List<Article> articles() {
        return List.of(
            new Article("暴雨过后，光伏电站应先检查什么",
                "从积水、支架、电缆和监控数据四个方面完成雨后基础巡检。",
                "暴雨停止并确认现场安全后，建议先检查站区排水和设备基础是否积水，再查看支架、围栏及组件是否出现松动或破损。重点排查汇流箱、逆变器、电缆接头和穿管位置的进水痕迹，不要在积水未排除时带电操作。最后结合监控曲线核对组串电流、绝缘告警和通信状态，对异常点位做好记录并交由具备资质的人员处理。本文为光伏智云平台整理的通用巡检建议，具体操作应遵循设备说明书和电站安全制度。",
                "platform-guide-rain-inspection-v1"),
            new Article("高温天气下的组件效率与运行检查",
                "高温会影响组件输出，巡检时应同时关注散热、遮挡和电气连接。",
                "组件温度升高时，实际输出功率通常会受到温度系数影响。运维人员可对照环境温度、组件背板温度和同组串功率，识别异常温升或输出偏差。检查时应保持逆变器通风通道畅通，及时清理影响散热的杂物，并关注接头、线缆和配电设备是否存在过热迹象。不要仅凭瞬时功率判断设备故障，应结合辐照度、历史曲线和同类设备表现综合分析。",
                "platform-guide-high-temperature-v1"),
            new Article("强对流天气前后的电站安全检查要点",
                "针对大风、雷电和短时强降雨，提前核对固定、排水与防雷设施。",
                "强对流天气来临前，应确认组件压块、支架连接、屋面附着物和临时设施是否牢固，检查排水沟、落水口及站区通道是否畅通。雷雨期间避免开展露天电气作业。天气结束后，优先检查组件破损、支架位移、防雷接地、箱体密封和通信中断情况；发现异响、焦味、明显变形或绝缘告警时，应按照电站应急流程隔离风险区域并安排专业人员复核。",
                "platform-guide-convective-weather-v1"),
            new Article("地质灾害风险地区的光伏运维注意事项",
                "山地电站应把边坡、排水、道路和基础变形纳入日常风险巡查。",
                "位于山地、沟谷或地质灾害易发区域的电站，应持续关注降雨预报和属地灾害预警。巡检重点包括边坡裂缝、落石、排水冲刷、道路沉降、基础外露和支架倾斜。连续降雨或收到地质灾害预警时，应减少非必要现场作业，避免人员进入陡坡、沟口和可能发生滑坡泥石流的区域。异常情况应及时留存位置和影像记录，并联系属地管理部门或专业单位评估。",
                "platform-guide-geological-risk-v1")
        );
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?", Integer.class, table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?", Integer.class, table, column);
        return count != null && count > 0;
    }

    private record Article(String title, String summary, String content, String externalId) {}
}
