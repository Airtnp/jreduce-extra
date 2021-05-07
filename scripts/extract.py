import os
import sys
import csv
import subprocess
import itertools
from shutil import copyfile, rmtree
from pathlib import Path
from collections import defaultdict


run_sh = "run2.sh"
workfolder_old = "workfolder"
workfolder_new = "workfolder2"
reduce_old = "reduced"
reduce_new = "reduced2"
output_csv_name = "reduced_results.csv"
output_csv_indiv_name = "reduced_results_indiv.csv"
method_name = "items+logic+cls"


def run_jreduce(subfolder_path: Path, f:str, d: str):
    os.makedirs(subfolder_path / "utils", exist_ok=True)
    run_path = subfolder_path / run_sh
    copyfile("scripts/{}".format(run_sh), run_path)
    copyfile("extract.py/compile.sh", subfolder_path / "utils" / "compile.sh")
    copyfile("extract.py/predicate2_{}.sh".format(d),
        subfolder_path / "predicate2")
    os.chmod(subfolder_path / run_sh, 0o777)
    os.chmod(subfolder_path / "predicate2", 0o777)

    rmtree(subfolder_path / workfolder_new, ignore_errors=True)
    rmtree(subfolder_path / reduce_new, ignore_errors=True)

    print(f"running {f}:{d}")

    proc = subprocess.Popen(["sh", subfolder_path / run_sh], shell=False)
    proc.communicate()
    status = proc.wait()
    return status

def read_benchmark(workfolder: Path):
    result = defaultdict(lambda: "")
    result['status'] = "unknown"

    with open(str(workfolder / "metrics.csv")) as p:
        rows = list(csv.DictReader(p))

    for final in reversed(rows):
        if final['judgment'] == 'success': 
            result['status'] = 'success'
            break
    else:
        result['status'] = "bad"

    result["scc"] = int(final["count"])
    result["initial-scc"] = int(rows[0]["count"])
    result["bytes"] = int(final["bytes"])
    result["initial-bytes"] = int(rows[0]["bytes"])
    result["classes"] = int(final["classes"])
    result["initial-classes"] = int(rows[0]["classes"])
    result["iters"] = int(final["folder"])
    result["setup-time"] = float(rows[0]["time"])
    result["flaky"] = rows[0]["judgment"] != "success"
    
    hits = set()
    for x in rows:
        hits.add(int(x["count"]))

    for i in itertools.count():
        if not i in hits: break
    result["searches"] = i - 1

    if result['status'] != "timeout":
        result["time"] = float(final["time"]) + float(final["run time"]) + float(final["setup time"])
    else:
        result["time"] = "NaN"

    bugs = list((workfolder / "initial" / "stdout").read_text().splitlines())
    result["bugs"] = len(bugs)
    bugs = set(bugs)
    
    result["verify"] = "success"
    for path in sorted(workfolder.glob("*/*/stdout")):
        found_bugs = set(path.read_text().splitlines())
        if bugs < found_bugs:
            result["verify"] = str(path.parent.name)
            break
    
    return result


def run_all():
    decompilers = ["cfr", "fernflower", "procyon"]
    path = "/Users/liranxiao/result/full/"

    output = open(output_csv_name, "a+")
    wr = csv.DictWriter(output, 
        ["name", "predicate", "strategy", 
            "bugs", "initial-scc", "scc", "initial-classes", "classes", 
            "initial-bytes", "bytes", 
            "iters", "searches", "setup-time", "time", 
            "status", "verify", "flaky", "exitcode", "ratio"]
        )
    wr.writeheader()

    for f in os.listdir(path):
        full_path = os.path.join(path, f)
        if os.path.isdir(full_path):
            for d in decompilers:
                subfolder_path = Path(os.path.join(full_path, d, "items+logic"))
                if not subfolder_path.exists():
                    continue
                reduced_path = subfolder_path / reduce_old
                if not reduced_path.exists():
                    continue

                status = run_jreduce(subfolder_path, f, d)

                workfolder = Path(subfolder_path) / workfolder_old
                workfolder2 = Path(subfolder_path) / workfolder_new

                try:
                    result = read_benchmark(workfolder)
                    result2 = read_benchmark(workfolder2)

                    result.update(
                        predicate=d,
                        name=f,
                        strategy="items+logic",
                        exitcode=str(status),
                        ratio="N/A",
                    )

                    result2.update(
                        predicate=d,
                        name=f,
                        strategy=method_name,
                        exitcode=str(status),
                        ratio=str(int(result2['bytes']) / int(result['bytes']))
                    )
                    wr.writerow(result)
                    wr.writerow(result2)
                except Exception as e:
                    wr.writerow({
                        "name": f,
                        "predicate": d,
                        "strategy": "items+logic",
                        "status": str(e)
                    })

                output.flush()


def run_with(l: list):
    path = "/Users/liranxiao/result/full/"
    
    output = open(output_csv_indiv_name, "a+")
    wr = csv.DictWriter(output, 
        ["name", "predicate", "strategy", 
            "bugs", "initial-scc", "scc", "initial-classes", "classes", 
            "initial-bytes", "bytes", 
            "iters", "searches", "setup-time", "time", 
            "status", "verify", "flaky", "exitcode", "ratio"]
        )
    wr.writeheader()
    
    for (f, d) in l:
        subfolder_path = Path(os.path.join(path, f, d, "items+logic"))

        status = run_jreduce(subfolder_path, f, d)

        workfolder = Path(subfolder_path) / workfolder_old
        workfolder2 = Path(subfolder_path) / workfolder_new

        try:
            result = read_benchmark(workfolder)
            result2 = read_benchmark(workfolder2)

            result.update(
                predicate=d,
                name=f,
                strategy="items+logic",
                exitcode=str(status),
                ratio="N/A",
            )

            result2.update(
                predicate=d,
                name=f,
                strategy=method_name,
                exitcode=str(status),
                ratio=str(int(result2['bytes']) / int(result['bytes']))
            )
            wr.writerow(result)
            wr.writerow(result2)
        except Exception as e:
            wr.writerow({
                "name": f,
                "predicate": d,
                "strategy": "items+logic",
                "status": str(e)
            })

        output.flush()


if __name__ == "__main__":
    run_all()