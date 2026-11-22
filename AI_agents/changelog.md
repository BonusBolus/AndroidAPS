## UI

- Overview graphs: add a selectable Time in Range bar with Very Low, Low, In Range, High, and Very High ranges.
- TIR graphs: support the current day from midnight or a rolling 24-hour view.
- TIR graphs: keep TIR exclusive from other graph series and make it available in the history browser.
- TIR graphs: move the Today and 24h choice into the graph edit screen.
- TIR graphs: the legend shows only the name and the percentage of each range, no glucose numbers, so it is less cluttered.
- TIR graphs: the edit button sits at the top, level with the "Time in range" title, so it no longer overlaps the legend. The
  TIR graph shows no extra name row: the title already names it.
- TIR graphs: show the tight range (3.9–7.8 mmol/L, 70–140 mg/dL) as a darker green part of the In Range bar, with its own legend item.
- TIR graphs: autosens has its own colour (rose) in the theme; the autosens ratio line in the graphs uses it.
- TIR graphs: the Tight legend item is hidden when the low mark is above 7.8 mmol/L (140 mg/dL), so it never
  shows a reversed range. Spacing uses the theme values.
- Tests: TirCalculationTest (midnight and 24 h window, never past now, past days, invalid readings, tight part).
