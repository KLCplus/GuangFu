name: station-inspection
description: Analyze the current operating status, weather impact, power and predictions for one photovoltaic station.
allowed_tools:
  - station.list
  - station.detail
  - weather.current
  - weather.forecast
  - pv.realtime
  - pv.history
  - prediction.list
  - prediction.detail
output_sections:
  - Current status
  - Data evidence
  - Risk assessment
  - Recommendations
