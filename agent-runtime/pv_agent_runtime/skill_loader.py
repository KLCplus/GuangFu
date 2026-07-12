from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import re


@dataclass(frozen=True)
class Skill:
    name: str
    description: str
    path: Path
    body: str
    allowed_tools: list[str]


class SkillLoader:
    """Loads SKILL.md files using the frontmatter convention from agent_skills."""

    def __init__(self, skills_dir: str | Path):
        self.skills_dir = Path(skills_dir)

    def load_all(self) -> dict[str, Skill]:
        skills: dict[str, Skill] = {}
        for skill_md in sorted(self.skills_dir.glob("*/SKILL.md")):
            skill = self.load(skill_md)
            skills[skill.name] = skill
        return skills

    def load(self, skill_md: str | Path) -> Skill:
        path = Path(skill_md)
        text = path.read_text(encoding="utf-8")
        frontmatter, body = self._split_frontmatter(text)
        name = frontmatter.get("name", path.parent.name)
        description = frontmatter.get("description", "")
        allowed_tools = self._extract_allowed_tools(body)
        if not name or not description:
            raise ValueError(f"Skill is missing name or description: {path}")
        return Skill(name=name, description=description, path=path, body=body, allowed_tools=allowed_tools)

    def select(self, user_task: str, skills: dict[str, Skill]) -> Skill:
        text = user_task.lower()
        if any(word in user_task for word in ("运行情况", "巡检", "电站", "分析")):
            return skills["station-inspection"]
        if any(word in user_task for word in ("天气", "云", "降雨", "温度")):
            return skills["weather-impact-analysis"]
        if any(word in user_task for word in ("预测", "趋势", "任务")):
            return skills["power-prediction-analysis"]
        if any(word in user_task for word in ("异常", "故障", "波动", "下降")) or "anomaly" in text:
            return skills["anomaly-diagnosis"]
        if any(word in user_task for word in ("报告", "生成")):
            return skills["comprehensive-report"]
        if "api" in text:
            return skills["api-usage-analysis"]
        return skills["station-inspection"]

    def _split_frontmatter(self, text: str) -> tuple[dict[str, str], str]:
        match = re.match(r"^---\n(.*?)\n---\n?(.*)$", text, re.S)
        if not match:
            raise ValueError("SKILL.md must start with YAML frontmatter")
        data: dict[str, str] = {}
        for line in match.group(1).splitlines():
            if ":" not in line or line.startswith(" "):
                continue
            key, value = line.split(":", 1)
            data[key.strip()] = value.strip().strip('"').strip("'")
        return data, match.group(2)

    def _extract_allowed_tools(self, body: str) -> list[str]:
        tools: list[str] = []
        in_section = False
        for line in body.splitlines():
            if line.lower().startswith("## allowed tools"):
                in_section = True
                continue
            if in_section and line.startswith("## "):
                break
            if in_section:
                match = re.search(r"`([^`]+\.[^`]+)`", line)
                if match:
                    tools.append(match.group(1))
        return tools

