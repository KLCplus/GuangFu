name: report-generation
description: Gather verified station data before requesting a report generation operation.
allowed_tools:
  - station.detail
  - weather.current
  - pv.history
  - prediction.list
  - prediction.detail
  - report.generate
output_sections:
  - Conclusion
  - Data evidence
  - Report status
