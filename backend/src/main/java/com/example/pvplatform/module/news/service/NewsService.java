package com.example.pvplatform.module.news.service;

import com.example.pvplatform.module.news.entity.News;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NewsService {
    public List<News> list() {
        return List.of(
            new News(1L, "系统模型更新通知", "平台新增 Transformer 光伏预测模型。",
                "MODEL_UPDATE", LocalDateTime.now().toString()),
            new News(2L, "平台骨架版本发布", "Web、后端和模型服务主链路已建立。",
                "NOTICE", LocalDateTime.now().toString())
        );
    }

    public News detail(Long newsId) {
        return list().stream().filter(item -> item.newsId().equals(newsId)).findFirst().orElse(list().get(0));
    }
}
