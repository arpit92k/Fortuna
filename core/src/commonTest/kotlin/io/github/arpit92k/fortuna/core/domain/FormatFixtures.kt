package io.github.arpit92k.fortuna.core.domain

/**
 * Saved datasets in each released format version.
 *
 * Never edit one: each is what a real app wrote, and every later version of
 * the app must still read it. A new format version adds a new fixture here.
 *
 * Version 1 was frozen for the first release (plan step 26); FrozenFormatsTest
 * fails if this fixture changes.
 */
object FormatFixtures {
    /**
     * Format version 1. Pounds as the base currency, a bank account, the
     * architecture's US brokerage example, a yen account whose rate was typed
     * from the pound side, a mortgage, and a closed account that was archived.
     */
    const val DATASET_V1: String = """
{
  "formatVersion": 1,
  "baseCurrency": "GBP",
  "nextId": 11,
  "categories": [
    { "id": 1, "name": "Cash", "kind": "asset", "status": "active" },
    { "id": 2, "name": "Investments", "kind": "asset", "status": "active" },
    { "id": 3, "name": "Property", "kind": "asset", "status": "active" },
    { "id": 4, "name": "Pension", "kind": "asset", "status": "active" },
    { "id": 5, "name": "Debt", "kind": "liability", "status": "active" }
  ],
  "sources": [
    {
      "id": 6, "name": "Current account", "category": 1, "currency": "GBP",
      "updateFrequency": "monthly", "status": "active",
      "snapshots": [
        { "date": "2026-01-31", "value": "2500.00", "amountAdded": "0.00" },
        { "date": "2026-04-30", "value": "3100.50", "amountAdded": "500.00", "note": "Bonus paid in" }
      ]
    },
    {
      "id": 7, "name": "US brokerage", "category": 2, "currency": "USD",
      "updateFrequency": "quarterly", "status": "active", "note": "Index funds",
      "snapshots": [
        { "date": "2026-01-31", "value": "10000.00", "amountAdded": "0.00" },
        { "date": "2026-04-30", "value": "11500.00", "amountAdded": "1000.00" }
      ]
    },
    {
      "id": 8, "name": "Tokyo savings", "category": 1, "currency": "JPY",
      "updateFrequency": "yearly", "status": "active",
      "snapshots": [
        { "date": "2026-02-15", "value": "480000", "amountAdded": "0" }
      ]
    },
    {
      "id": 9, "name": "Mortgage", "category": 5, "currency": "GBP",
      "updateFrequency": "half-yearly", "status": "active",
      "snapshots": [
        { "date": "2026-01-31", "value": "180000.00", "amountAdded": "0.00" },
        { "date": "2026-04-30", "value": "178950.25", "amountAdded": "-1500.00" }
      ]
    },
    {
      "id": 10, "name": "Old savings account", "category": 1, "currency": "GBP",
      "updateFrequency": "monthly", "status": "archived", "note": "Closed in March",
      "snapshots": [
        { "date": "2026-01-31", "value": "800.00", "amountAdded": "0.00" },
        { "date": "2026-03-10", "value": "0.00", "amountAdded": "-805.12" }
      ]
    }
  ],
  "rates": [
    {
      "currency": "JPY",
      "history": [
        { "date": "2026-02-15", "unit": "GBP", "quote": "JPY", "rate": "191.5" }
      ]
    },
    {
      "currency": "USD",
      "history": [
        { "date": "2026-01-31", "unit": "USD", "quote": "GBP", "rate": "0.80" },
        { "date": "2026-04-30", "unit": "USD", "quote": "GBP", "rate": "0.75" }
      ]
    }
  ],
  "settings": {
    "defaultUpdateFrequency": "monthly",
    "revision": 12,
    "lastBackupRevision": 11,
    "lastBackup": "2026-04-30"
  }
}
"""

    /**
     * Format version 1, with an account added with a gain it had already
     * made: £12,000, of which £10,000 was deposited. The opening principal is
     * optional, and absent from [DATASET_V1].
     */
    const val DATASET_V1_OPENING_PRINCIPAL: String = """
{
  "formatVersion": 1,
  "baseCurrency": "GBP",
  "nextId": 7,
  "categories": [
    { "id": 1, "name": "Cash", "kind": "asset", "status": "active" },
    { "id": 2, "name": "Investments", "kind": "asset", "status": "active" },
    { "id": 3, "name": "Property", "kind": "asset", "status": "active" },
    { "id": 4, "name": "Pension", "kind": "asset", "status": "active" },
    { "id": 5, "name": "Debt", "kind": "liability", "status": "active" }
  ],
  "sources": [
    {
      "id": 6, "name": "ISA", "category": 2, "currency": "GBP",
      "updateFrequency": "monthly", "status": "active", "openingPrincipal": "10000.00",
      "snapshots": [
        { "date": "2026-01-31", "value": "12000.00", "amountAdded": "0.00" }
      ]
    }
  ],
  "rates": [],
  "settings": {
    "defaultUpdateFrequency": "monthly",
    "revision": 1
  }
}
"""
}
