## UI

- Overview graphs: allow more than two data fields on one graph.
- Overview graphs: use the first field on the left axis and later fields on the right axis. Fields with the same
  unit as the first (IOB with absolute IOB, BGI with deviations) share the left axis, as on master.
- Overview graphs: zoom the graphs below the glucose graph with a two-finger pinch, the same as on the glucose graph itself.
- Overview graphs: show `%sens` and VSENS with different colors. `%sens` uses the new autosens colour (rose)
  and VSENS the ISF chip colour, the same as the overview chips.
- Overview graphs: remove the 100 dp minimum and 250 dp maximum height limits.
- Overview screen: hide the top controls until the content is scrolled back to the top; keep bottom controls visible.
- Overview screen: hide the top controls after 3 seconds of inactivity and keep them hidden when returning from another screen.
- Overview screen: show the top search controls when pulling down while already at the top.
- Overview screen: show and hide the top search controls with a slower, smoother slide-and-fade animation.
- Overview screen: move the content with the search bar's slide instead of snapping it into place when the animation starts.
- Overview screen: center the disabled SMB cross on the SMB icon.
- Quick Launch: remove the count badges from the Treatments and Scenes bottom-bar buttons.
- Quick Launch: make the settings action optional and configurable instead of always adding it.
- Quick Launch: draw the divider before Scenes across the full menu width.
- Overview screen: the screens read the scroll position only to know if they are at the top, so they are no longer
  redrawn on every scroll frame. Removed an always-visible AnimatedVisibility around the bottom bar.
- Overview graphs: three new data fields with the values the wizard and the algorithm used, drawn dashed:
  - Wizard ISF: the ISF for carbs the bolus wizard uses (the bold wizard ISF), dashed in the ISF colour beside VSENS.
  - Autosens used by the algorithm: the ratio of each loop run, dashed in the autosens colour beside SENS%.
  - Target used by the algorithm: the target of each loop run, dashed in the "adjusted" target colour.
  Wizard ISF comes from the COB buckets. Autosens and target come from a light query that reads only these two
  fields of the stored loop results with json_extract, like the VSENS query (#5248). Same-unit pairs share an axis.
- Tests: ApsResultAlgorithmValuesTest (real SQLite).
