# lab

Scripts used to measure the plugin. They expect a test server in `../lab-server` (not in the repo)
with `paper.jar` (Paper 1.21.4), `UltimateUI.jar` and `UUIOptimizer.jar` in `plugins/`, listening on
port 22222 in offline mode. UltimateUI is not redistributable, so it is not included.

```bash
npm install
node console.mjs ../lab-server commands.txt     # starts the server, forwards lines of commands.txt to its console
node stress.mjs 15 35 hub on                    # bots, seconds, UltimateUI page, tag
echo "uuiopt toggle" >> commands.txt            # pause the filter
node stress.mjs 15 35 hub off
node compare.mjs on off off off2                # final entity state, with a noise baseline
```

`stress.mjs` writes `stress-<tag>.txt` (packet counts) and `stress-<tag>-state.json` (final state of
the fake entities seen by the first three bots). Set `JAVA_HOME` if `java` is not on the path.
