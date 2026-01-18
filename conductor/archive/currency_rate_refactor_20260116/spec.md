# Track Specification: Refactor Currency Rate Downloaders

## Overview
The goal of this track is to modernize the currency rate downloading logic in Financisto. Currently, some downloaders may use legacy networking libraries or lack robust error handling and comprehensive unit tests.

## Objectives
1. **Migration to Ktor:** Ensure all currency rate downloaders use Ktor with the CIO engine, consistent with the project's tech stack.
2. **Robust Error Handling:** Improve how network failures and malformed API responses are handled and reported.
3. **Comprehensive Testing:** Achieve >80% test coverage for all refactored downloaders, covering both success and failure scenarios.
4. **Maintainability:** Refactor code to be more idiomatic Kotlin and follow the project's style guidelines.

## Targeted Components
- `ru.orangesoftware.financisto.rates.OpenExchangeRatesDownloader`
- `ru.orangesoftware.financisto.rates.WebserviceXConversionRateDownloader`
- `ru.orangesoftware.financisto.rates.FreeCurrencyRateDownloader`

## Success Criteria
- All targeted downloaders are migrated to Ktor.
- Automated test suite passes for all downloaders.
- Code coverage for the `rates` package exceeds 80%.
- No regressions in currency rate fetching functionality.
