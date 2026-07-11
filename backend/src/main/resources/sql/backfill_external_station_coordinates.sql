-- =============================================================================
-- 回填 external_pv_station 经纬度数据
-- 说明: PVOutput 公开实时页 (live.jsp) 不提供经纬度信息,
--       导致通过 live page sync 创建的电站经纬度为 NULL.
--       本脚本根据电站所在地区 (postcode) 回填近似经纬度,
--       用于天气服务查询。精确坐标可通过 PVOutput Search API 获取后更新。
-- 使用方法: 在 MySQL 中执行本脚本即可
-- =============================================================================

-- 澳大利亚 QLD 昆士兰州
UPDATE external_pv_station SET latitude = -27.614000, longitude = 152.973000 WHERE external_system_id = 85414;   -- RPM Building 37 (4110 Ipswich)
UPDATE external_pv_station SET latitude = -27.590000, longitude = 153.060000 WHERE external_system_id = 76824;   -- Symbio Laboratories - 52 (4113)
UPDATE external_pv_station SET latitude = -27.590000, longitude = 153.060000 WHERE external_system_id = 77009;   -- Cook Medical 61/1 (4113)
UPDATE external_pv_station SET latitude = -27.590000, longitude = 153.060000 WHERE external_system_id = 76363;   -- Symbio Laboratories - 44 (4113)
UPDATE external_pv_station SET latitude = -27.590000, longitude = 153.060000 WHERE external_system_id = 73718;   -- Cook Medical 61/2 (4113)
UPDATE external_pv_station SET latitude = -27.590000, longitude = 153.060000 WHERE external_system_id = 34257;   -- Cook Medical Australia (4113)
UPDATE external_pv_station SET latitude = -27.250000, longitude = 153.020000 WHERE external_system_id = 77260;   -- Qtank (4500)
UPDATE external_pv_station SET latitude = -27.580000, longitude = 152.940000 WHERE external_system_id = 83299;   -- Pillow Talk Northolt (4076)
UPDATE external_pv_station SET latitude = -27.580000, longitude = 152.940000 WHERE external_system_id = 85307;   -- Pillow Talk Limestone (4076)
UPDATE external_pv_station SET latitude = -25.540000, longitude = 152.700000 WHERE external_system_id = 61555;   -- Victory Church Maryborough (4650)
UPDATE external_pv_station SET latitude = -27.380000, longitude = 153.030000 WHERE external_system_id = 60081;   -- Estilo on Kittyhawk (4032)
UPDATE external_pv_station SET latitude = -27.380000, longitude = 153.030000 WHERE external_system_id = 112897;  -- Pechey Sigenergy (4032)
UPDATE external_pv_station SET latitude = -27.610000, longitude = 152.850000 WHERE external_system_id = 78034;   -- Refresh Waters (4300)
UPDATE external_pv_station SET latitude = -23.380000, longitude = 150.510000 WHERE external_system_id = 78569;   -- Rockhampton Baptist Tabernacle (4701)
UPDATE external_pv_station SET latitude = -16.920000, longitude = 145.770000 WHERE external_system_id = 101178;  -- Autobarn Cairns (4870)
UPDATE external_pv_station SET latitude = -27.070000, longitude = 152.960000 WHERE external_system_id = 47759;   -- Beecham_Holden (4510)
UPDATE external_pv_station SET latitude = -27.640000, longitude = 153.130000 WHERE external_system_id = 63656;   -- Highpoint Business Centre (4127)
UPDATE external_pv_station SET latitude = -27.430000, longitude = 153.000000 WHERE external_system_id = 57262;   -- Enoggera Self Storage (4051)
UPDATE external_pv_station SET latitude = -27.430000, longitude = 153.000000 WHERE external_system_id = 59226;   -- IGear (4051)
UPDATE external_pv_station SET latitude = -27.510000, longitude = 153.010000 WHERE external_system_id = 82727;   -- Renovare Yeronga (4104)
UPDATE external_pv_station SET latitude = -26.720000, longitude = 153.120000 WHERE external_system_id = 108252;  -- Zinc Bokarina Enphase (4575)
UPDATE external_pv_station SET latitude = -26.720000, longitude = 153.120000 WHERE external_system_id = 107468;  -- Revive Birtinya (4575)
UPDATE external_pv_station SET latitude = -27.380000, longitude = 153.050000 WHERE external_system_id = 88347;   -- Village Central Nundah (4012)
UPDATE external_pv_station SET latitude = -20.010000, longitude = 148.250000 WHERE external_system_id = 107648;  -- CE Big 4 (4802)
UPDATE external_pv_station SET latitude = -27.320000, longitude = 153.040000 WHERE external_system_id = 66682;   -- Bracken Ridge Baptist Church (4017)

-- 澳大利亚 NSW 新南威尔士州
UPDATE external_pv_station SET latitude = -31.460000, longitude = 152.730000 WHERE external_system_id = 81697;   -- Woodrose (2446)
UPDATE external_pv_station SET latitude = -31.100000, longitude = 150.930000 WHERE external_system_id = 56089;   -- Careys Belmore St (2340)
UPDATE external_pv_station SET latitude = -31.100000, longitude = 150.930000 WHERE external_system_id = 56088;   -- Careys Main Office (2340)
UPDATE external_pv_station SET latitude = -32.910000, longitude = 151.750000 WHERE external_system_id = 83168;   -- Hunter H2O (2304)
UPDATE external_pv_station SET latitude = -28.810000, longitude = 153.280000 WHERE external_system_id = 64847;   -- UCRHSolar (2480)

-- 澳大利亚 QLD Gold Coast
UPDATE external_pv_station SET latitude = -27.960000, longitude = 153.370000 WHERE external_system_id = 71111;   -- Arcare Parkwood (4214)
UPDATE external_pv_station SET latitude = -27.680000, longitude = 153.100000 WHERE external_system_id = 84975;   -- Pro Tech Distributions Unit 1 (4132)

-- 澳大利亚 VIC 维多利亚州
UPDATE external_pv_station SET latitude = -37.740000, longitude = 142.020000 WHERE external_system_id = 55018;   -- Wannon Water Hamilton WTP (3300)
UPDATE external_pv_station SET latitude = -38.370000, longitude = 142.470000 WHERE external_system_id = 51616;   -- Wannon Water Gateway HQ (3280)

-- 日本
UPDATE external_pv_station SET latitude = 35.680000, longitude = 139.760000 WHERE external_system_id = 75956;    -- HISA3 (Japan)

-- 马尔代夫
UPDATE external_pv_station SET latitude = 4.180000, longitude = 73.510000 WHERE external_system_id = 76845;      -- Alila Kothaifaru (Maldives)

-- 泰国
UPDATE external_pv_station SET latitude = 13.760000, longitude = 100.500000 WHERE external_system_id = 32225;    -- FRECON-APY_RoofTop#2 (Thailand)

-- =============================================================================
-- 验证: 执行以下查询确认所有启用电站均有经纬度
-- SELECT id, external_system_id, system_name, latitude, longitude
-- FROM external_pv_station WHERE enabled = 1 AND (latitude IS NULL OR longitude IS NULL);
-- 预期结果: 0 rows
-- =============================================================================
