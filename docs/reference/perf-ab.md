# GenTV performance workflow

Use a dedicated emulator for the controlled comparison. Do not use the benchmark identity on a real user's TV or clear a production application's data.

The baseline project is supplied separately under `work/performance-baseline` or with `--baseline-source`. It is not part of the distributed product. Both variants are built with the reserved `com.gentv.perf` identity, identical configuration and comparable environment.

```sh
python3 tools/emulator_benchmark.py --serial emulator-5554 --baseline-source work/performance-baseline
```

Run at least two alternating rounds with five samples per variant. Report medians, navigation frame statistics and process memory with the actual environment, settings and sample counts. A test process does not become the default Home and cannot be used to claim real-TV compatibility.

Historical results and their limitations are recorded in [emulator-performance.md](../emulator-performance.md). Build commands and source identity are documented in [README.md](../../README.md). No new performance claim is made by the branding cleanup.
