# Currency, price estimate and filament density

Issues: #72 (estimate, density), #75 (currency), building on [Spoolman filaments](spoolman.md) (#11).

Every request gets an **estimated price** until the admin sets the final price. It comes from the chosen filaments' prices in Spoolman, with a price per 100 g as the default for everything else, all times a markup. Estimated grams use the chosen filament's **density** instead of assuming PLA.

## Settings

**Settings → Spoolman → Use prices from Spoolman** (default off) decides whether chosen filaments use their Spoolman price.

- It is a Spoolman switch like colour selection: it can only be on while the [Spoolman integration](spoolman.md) is.
- Switching the integration off switches it off too.

**Settings → Pricing** (`/admin/settings?tab=pricing`) holds the currency and the numbers, with a line saying whether Spoolman prices are on and a link to the Spoolman tab:
- **Currency** (default EUR, #75): see [Currency](#currency).
- **Markup**, e.g. `1,5` (0,01–100, at most 2 decimals, default 1): multiplies every price, in both modes.
- **Price per 100 g** in the currency (0–1000, at most 2 decimals, default 0): the default price.
  - With Spoolman prices on, it is used for colours without a chosen filament, or with a chosen filament that has no price in Spoolman.
  - With them off, it is used for everything.
  - **0 = no default price** (and so no automatic price at all while Spoolman prices are off).
- Both fields are always shown. Decimals can be typed the Dutch or English way (`1,5` or `1.5`); the fields show them in the viewer's format.

## Currency

Issue #75. Every price (final prices, estimates, the price per 100 g) is in one currency, chosen in Settings → Pricing.

- **Choice:** a dropdown with every ISO 4217 code the browser knows, with its name ("EUR – Euro", "USD – US Dollar"), sorted by code. The saved one is always listed. Default EUR.
- **Not converted:** amounts are stored without a currency. Changing it relabels every amount, old requests included; the dropdown says so.
- **Spoolman:** Spoolman's prices are taken to be in the same currency; the "Use prices from Spoolman" switch says so and asks to set the same currency in Spoolman. Spoolman's own currency setting isn't read.
- **Formatting:** in the viewer's browser locale, not a fixed one. USD shows as `$6,900.42` in an English (US) browser and `US$ 6.900,42` in a Dutch one. Always 2 decimals, also for currencies like JPY (`¥1,234.00`), because that is what is stored.
- **Input fields** (final price, price per 100 g, markup) show numbers in the viewer's format without thousands separators, with the currency's symbol in front. Typing accepts both decimal styles, apostrophe grouping (`1'234.50`) and a currency symbol or code (`$ 12`, `CHF 5`). See [admin review](admin-review.md#prices).
- **Validation:** the backend accepts any code `java.util.Currency` knows, in any case, and stores it upper case.

## How the estimate is made

A `PricingStrategy` per mode, over every file and link of the request times its quantity, all times the markup. Declined items don't count.

- **Spoolman prices** (`SpoolmanPrices`): each colour's price per gram is its chosen filament's (spool price ÷ net spool weight). Without a choice, or for a filament without price or weight, it is the default (price per 100 g ÷ 100).
  - **A plate whose colours all cost the same** (all on the default, or one filament for all) counts with the plate's grams, so unsliced multi-colour plates can be priced.
  - **Otherwise each colour counts with its own grams,** the same as for the [not-enough check](spoolman.md#not-enough-filament): the colour's sliced grams, else the plate's when it is the plate's only colour.
  - **A file without colours** uses its total grams with its whole-file choice (or the default).
  - **Prices:** the **filament's** price and weight in Spoolman are used; spool prices are ignored. Spoolman's prices are taken to be in PrintBridge's [currency](#currency). While Spoolman can't be read, every colour uses the default.
- **Price per 100 g** (`PricePerHundredGrams`): the total grams of all items ÷ 100 × the price × the markup.
- **Partial:** whatever is known is added up. When something can't be priced, the estimate is marked *partial*. That covers:
  - a colour with no price and no default;
  - a colour whose grams aren't known;
  - a link;
  - a file that isn't analysed.

  When nothing at all can be priced, there is no estimate.
- **Rounding:** to cents (half up).
- **No estimate** once the admin set a final price, for a declined request, or when nothing can be priced (e.g. Spoolman prices off and a price per 100 g of 0).

## Density

Estimated plate grams (`volume × density × 0.35`, see [model info](model-info.md)) use the chosen filament's Spoolman density:

- **Which plates:** a plate whose only colour has a chosen filament, and every plate of a file (STL, plain 3MF) with a whole-file choice.
- **What stays:**
  - plates with several colours, filaments without a density, and files without choices use PLA's 1,24;
  - sliced (exact) grams never change;
  - the fill factor stays 0,35.
- **Only with Spoolman readable:** densities are used while the Spoolman integration is on and Spoolman can be read.
- **Where the grams flow:** into the plate, file and request totals, the not-enough-filament check and the estimate.

## Where it shows

- **Request page and both request lists**, for the requester and the admin, in place of "Price pending": `~ €12.34` (in the viewer's format) with a small *(estimate)* or *(partial estimate)*. The final price replaces it.
- **Price & payment panel** (admin):
  - the final price field's placeholder is the estimate (`12.34`) instead of the example `69.42`;
  - **Save with an empty field saves the estimate** as the final price ("Price set to the estimate, €12.34.");
  - without an estimate, an empty field still asks to enter a price.

## API

- `GET /requests/{id}` and the request lists carry `estimatedPrice: {amount, partial}` or `null`, and `currency` (#75), so requesters, who can't read the settings, can format prices.
- `GET /settings` has `pricing: {currency, markup, pricePer100Grams}` and `spoolman.prices`.
- `PUT /settings/pricing` saves the currency and the numbers (`400` unknown currency, out of range or more than 2 decimals).
- `PUT /settings/spoolman` saves `prices` with the other Spoolman switches (`400` without the integration).

## Data

`app_settings` (V15): `spoolman_prices` (needs `spoolman_enabled`, like colour selection), `pricing_markup` (`NUMERIC(5,2)`, 0.01–100, default 1), `price_per_100_grams` (`NUMERIC(6,2)`, 0–1000, default 0). `currency` (V18, `VARCHAR(3)`, three capitals, default `EUR`). Estimates aren't stored; they are worked out when a request or list is shown.

## Not included

- A full cost model (electricity, machine time, fixed fees, minimum price).
- Converting amounts between currencies, or a currency per request.
- Reading Spoolman's own currency setting.
- Currency-specific decimals (JPY without cents).
- Spool-level prices.
- A markup in the price-per-100 g mode.
- Prices per material.
- Changing the fill factor.
