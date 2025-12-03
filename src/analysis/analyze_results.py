import os
import re
import csv
from collections import defaultdict
import matplotlib.pyplot as plt

# --------------------------
# Configuration
# --------------------------
RESULTS_DIR = "../results"   # adjust if using src/results
OUTPUT_ROOT = "../final_analysis_output"

CSV_DIR = os.path.join(OUTPUT_ROOT, "csv")
PLOTS_DIR = os.path.join(OUTPUT_ROOT, "plots")

# Only analyze thread counts that are meaningful on your machine
VALID_THREAD_COUNTS = {4, 8, 16}

# sub-folders for plots
PLOT_SUBDIRS = {
    "runtime_ms": "runtime",
    "throughput_reqs_per_sec": "throughput",
    "latency_ms": "latency",
    "avg_enqueue_ms": "enqueue",
    "avg_dequeue_ms": "dequeue",
}

# Ensure directory structure exists
for d in [CSV_DIR, PLOTS_DIR] + [os.path.join(PLOTS_DIR, sub) for sub in PLOT_SUBDIRS.values()]:
    os.makedirs(d, exist_ok=True)

# --------------------------
# Regex patterns
# --------------------------
DIR_RE = re.compile(r"threads_(\d+)_(\d+)_reqPerClient")


# --------------------------
# Parsing helpers
# --------------------------
def parse_time_to_ms(value_with_unit: str) -> float:
    value_with_unit = value_with_unit.strip()
    value, unit = value_with_unit.split()

    value = float(value)

    if unit == "ms":
        return value
    elif unit == "µs":
        return value / 1000.0
    elif unit == "ns":
        return value / 1_000_000.0
    else:
        raise ValueError(f"Unknown time unit: '{unit}'")


def parse_threads_and_reqs(dirname: str):
    m = DIR_RE.fullmatch(dirname)
    if not m:
        return None, None
    return int(m.group(1)), int(m.group(2))


def parse_queue_name(filename: str) -> str:
    return filename.split("_results_")[0]


def parse_result_file(filepath: str):
    metrics = {
        "runtime_ms": None,
        "throughput_reqs_per_sec": None,
        "latency_ms": None,
        "avg_enqueue_ms": None,
        "avg_dequeue_ms": None,
    }

    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()

            if line.startswith("Total runtime:"):
                metrics["runtime_ms"] = float(line.split()[2])

            elif line.startswith("Avg enqueue time:"):
                metrics["avg_enqueue_ms"] = parse_time_to_ms(" ".join(line.split()[3:]))

            elif line.startswith("Avg dequeue time:"):
                metrics["avg_dequeue_ms"] = parse_time_to_ms(" ".join(line.split()[3:]))

            elif line.startswith("Avg end-to-end request latency:"):
                metrics["latency_ms"] = parse_time_to_ms(" ".join(line.split()[4:]))

            elif line.startswith("Throughput:"):
                metrics["throughput_reqs_per_sec"] = float(line.split()[1])

    if None in metrics.values():
        raise ValueError(f"Missing metrics in file: {filepath}")

    return metrics


# --------------------------
# Data aggregation
# --------------------------
def gather_results():
    rows = []

    for root, dirs, files in os.walk(RESULTS_DIR):
        dirname = os.path.basename(root)
        threads, reqs = parse_threads_and_reqs(dirname)
        if threads is None:
            continue

        # *** FILTER OUT 32-thread (invalid for your CPU) ***
        if threads not in VALID_THREAD_COUNTS:
            print(f"[Skip] Ignoring threads={threads} (beyond hardware capacity)")
            continue

        for fname in files:
            if "_results_" not in fname:
                continue

            queue_name = parse_queue_name(fname)
            metrics = parse_result_file(os.path.join(root, fname))

            rows.append({
                "threads": threads,
                "requests_per_client": reqs,
                "queue": queue_name,
                **metrics,
            })

    return rows


def write_csv(rows, filename):
    outpath = os.path.join(CSV_DIR, filename)

    fieldnames = [
        "threads", "requests_per_client", "queue",
        "runtime_ms", "throughput_reqs_per_sec", "latency_ms",
        "avg_enqueue_ms", "avg_dequeue_ms"
    ]

    with open(outpath, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()

        for row in sorted(rows, key=lambda r: (r["requests_per_client"], r["threads"], r["queue"])):
            writer.writerow(row)

    print(f"[CSV] Wrote {outpath}")


def write_per_workload_csvs(rows):
    grouped = defaultdict(list)
    for r in rows:
        grouped[r["requests_per_client"]].append(r)

    for reqs, subset in grouped.items():
        fname = f"aggregate_{reqs}_reqPerClient.csv"
        write_csv(subset, fname)


# --------------------------
# Plotting
# --------------------------
def plot_metric(rows, metric_key, ylabel, reqs):
    subdir = PLOT_SUBDIRS[metric_key]
    outpath = os.path.join(PLOTS_DIR, subdir, f"{metric_key}_{reqs}.png")

    grouped = defaultdict(list)
    for r in rows:
        grouped[r["queue"]].append(r)

    plt.figure(figsize=(7, 5))

    for qname, qrows in grouped.items():
        qrows = sorted(qrows, key=lambda r: r["threads"])
        x = [r["threads"] for r in qrows]
        y = [r[metric_key] for r in qrows]
        plt.plot(x, y, marker="o", label=qname)

    plt.xlabel("Threads")
    plt.ylabel(ylabel)
    plt.title(f"{ylabel} vs Threads ({reqs} req/client)")
    plt.grid(True)
    plt.legend()
    plt.tight_layout()
    plt.savefig(outpath)
    plt.close()

    print(f"[Plot] Wrote {outpath}")


def generate_all_plots(rows):
    grouped = defaultdict(list)
    for r in rows:
        grouped[r["requests_per_client"]].append(r)

    for reqs, subset in grouped.items():
        plot_metric(subset, "runtime_ms", "Runtime (ms)", reqs)
        plot_metric(subset, "throughput_reqs_per_sec", "Throughput (req/s)", reqs)
        plot_metric(subset, "latency_ms", "Latency (ms)", reqs)
        plot_metric(subset, "avg_enqueue_ms", "Avg Enqueue Time (ms)", reqs)
        plot_metric(subset, "avg_dequeue_ms", "Avg Dequeue Time (ms)", reqs)


# --------------------------
# Main
# --------------------------
def main():
    rows = gather_results()
    if not rows:
        print("No rows found — check RESULTS_DIR path.")
        return

    write_csv(rows, "all_results_filtered.csv")
    write_per_workload_csvs(rows)
    generate_all_plots(rows)

    print("\nAll CSVs and plots generated in final_analysis_output/")


if __name__ == "__main__":
    main()
