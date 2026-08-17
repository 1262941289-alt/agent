#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Ollama CPU 推理参数优化脚本（适配单核/低内存环境）

对本地 Ollama 的 /api/generate 做基准测试，遍历不同的
num_thread / num_batch / num_ctx / num_predict / flash_attn 组合，测量：
  - 提示词处理速度 (prompt eval tokens/s)
  - 生成速度 (generation tokens/s)
  - 单次请求总耗时（取中位数，避免抖动）
  - 模型常驻内存 (RSS，来自 /api/ps)
并输出最快配置与 application.yml 建议。

实践要点（低内存/单核环境）：
  1. 内存不足 + swap 颠簸是最大瓶颈，脚本启动前会先探测内存/swap 并告警。
  2. num_predict（单次生成上限）能显著压短总耗时——模型生成冗长文本时尤其明显。
  3. 结果用中位数而不是最小值，避免偶发抖动误导。

用法：
  python3 scripts/optimize_ollama.py                      # 默认基准
  python3 scripts/optimize_ollama.py --model qwen2.5:1.5b
  python3 scripts/optimize_ollama.py --runs 3 --timeout 180

仅依赖 Python 标准库，无需 pip 安装。
"""
import argparse
import json
import statistics
import sys
import time
import urllib.request

DEFAULT_PROMPT = (
    "你是一个数据分层专家。请判断下面这条交易记录应属于哪个风险层。"
    "数据项 D002：客户李四，个体经营户，注册地高风险地区X国，"
    "2026年累计交易金额 800000 元，交易对手为制裁名单实体，交易记录异常。"
    "请只输出 JSON：{\"layerCode\":\"L1\",\"reason\":\"高风险地区且金额超标\"}"
)


def http_json(base_url, path, body, timeout):
    req = urllib.request.Request(
        base_url.rstrip("/") + path,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def gen(base_url, model, prompt, options, timeout):
    """调用 Ollama /api/generate，返回解析后的指标；失败返回 None。"""
    body = {
        "model": model,
        "prompt": prompt,
        "stream": False,
        "options": options,
        "keep_alive": "5m",
    }
    try:
        t0 = time.time()
        data = http_json(base_url, "/api/generate", body, timeout)
        wall = time.time() - t0
        p_eval = data.get("prompt_eval_count") or 0
        p_ms = data.get("prompt_eval_duration") or 0
        gen_n = data.get("eval_count") or 0
        gen_ms = data.get("eval_duration") or 0
        return {
            "wall_s": wall,
            "prompt_tps": p_eval / (p_ms / 1e9) if p_ms else 0.0,
            "gen_tps": gen_n / (gen_ms / 1e9) if gen_ms else 0.0,
            "gen_n": gen_n,
        }
    except Exception as e:
        return {"error": str(e)}


def bench(base_url, model, prompt, options, runs, timeout):
    """预热 1 次 + 跑 runs 次，返回结果列表（忽略失败）。"""
    gen(base_url, model, prompt, options, timeout)  # 预热（加载模型/缓存）
    results = []
    for _ in range(runs):
        r = gen(base_url, model, prompt, options, timeout)
        if r and "error" not in r and r["wall_s"] > 0:
            results.append(r)
    return results


def fmt_name(options):
    parts = []
    for k in ("num_thread", "num_batch", "num_ctx", "num_predict", "flash_attn"):
        if k in options:
            parts.append(f"{k}={options[k]}")
    return ", ".join(parts) if parts else "defaults"


def probe_env():
    """探测 CPU 核数 / 内存 / swap，返回提示信息。"""
    cores = 1
    try:
        with open("/sys/fs/cgroup/cpu.max") as f:
            quota, period = f.read().split()
            cores = round(int(quota) / int(period)) if int(quota) > 0 else 1
    except Exception:
        pass
    mem_gb = swap_kb = 0
    try:
        with open("/proc/meminfo") as f:
            for line in f:
                if line.startswith("MemTotal"):
                    mem_kb = int(line.split()[1])
                    mem_gb = mem_kb / 1024 / 1024
                elif line.startswith("SwapTotal"):
                    swap_kb = int(line.split()[1])
                elif line.startswith("SwapFree"):
                    swap_kb -= int(line.split()[1])
    except Exception:
        pass
    return cores, mem_gb, max(swap_kb, 0) / 1024 / 1024


def main():
    ap = argparse.ArgumentParser(description="Ollama CPU 推理参数优化")
    ap.add_argument("--base-url", default="http://127.0.0.1:11434")
    ap.add_argument("--model", default="qwen2.5:1.5b")
    ap.add_argument("--prompt", default=DEFAULT_PROMPT)
    ap.add_argument("--runs", type=int, default=3, help="每组有效测试次数（取中位数）")
    ap.add_argument("--timeout", type=int, default=180, help="单次请求超时(秒)")
    args = ap.parse_args()

    cores, mem_gb, swap_used_gb = probe_env()
    print("== 环境 ==")
    print(f"CPU 核数(约): {cores} | 内存(约): {mem_gb:.1f} GB | swap 已用: {swap_used_gb:.2f} GB")
    if swap_used_gb > 0.5:
        print("警告: swap 已用较大，可能存在内存颠簸，会显著拖慢推理速度。")
        print("建议: 关闭不必要的进程释放内存，或减小 num_ctx / 模型体积。")
    print(f"模型: {args.model} | 提示词长度: {len(args.prompt)} 字符")
    print()

    # 候选配置：单核/低内存下重点调 num_batch / num_ctx / num_predict / flash_attn
    configs = [
        {},                                   # 基线（Ollama 默认）
        {"num_batch": 64},
        {"num_batch": 256},
        {"num_ctx": 1024, "num_batch": 256, "flash_attn": True},
        {"num_ctx": 1024, "num_batch": 256, "num_predict": 128},
        {"num_ctx": 1024, "num_batch": 256, "num_predict": 256, "flash_attn": True},
    ]

    results = []
    for opts in configs:
        rs = bench(args.base_url, args.model, args.prompt, opts, args.runs, args.timeout)
        if not rs:
            print(f"  [{fmt_name(opts)}] 失败: 无有效结果")
            continue
        med = {k: statistics.median(r[k] for r in rs) for k in
               ("wall_s", "prompt_tps", "gen_tps", "gen_n")}
        results.append((opts, med))
        print(f"  [{fmt_name(opts)}] 耗时中位 {med['wall_s']:.1f}s | "
              f"提示词 {med['prompt_tps']:.1f} tok/s | 生成 {med['gen_tps']:.1f} tok/s | "
              f"输出 {med['gen_n']:.0f} tok")

    if not results:
        print("\n所有配置均失败，请检查 Ollama 是否运行、模型是否已拉取。")
        sys.exit(1)

    # 读取模型常驻内存
    rss_mb = 0
    try:
        ps = http_json(args.base_url, "/api/ps", {}, args.timeout)
        if ps.get("models"):
            rss_mb = ps["models"][0].get("size", 0) / 1024 / 1024
    except Exception:
        pass
    if rss_mb:
        print(f"\n当前已加载模型常驻内存: {rss_mb:.0f} MB")

    best_opts, best = min(results, key=lambda x: x[1]["wall_s"])
    print()
    print("== 推荐配置 ==")
    print(f"最快: {fmt_name(best_opts)}  (单次耗时中位 {best['wall_s']:.1f}s, "
          f"生成 {best['gen_tps']:.1f} tok/s)")
    print()
    print("application.yml 建议（num_thread/num_batch/num_predict 为请求级参数）：")
    print("spring:")
    print("  ai:")
    print("    ollama:")
    print(f"      base-url: {args.base_url}")
    print("      chat:")
    print("        options:")
    print(f"          model: {args.model}")
    print("          temperature: 0.1")
    print(f"          num-ctx: {best_opts.get('num_ctx', 2048)}")
    if "num_batch" in best_opts:
        print(f"          num-batch: {best_opts['num_batch']}")
    if "num_predict" in best_opts:
        print(f"          num-predict: {best_opts['num_predict']}")

    # 同时把完整结果写到 /tmp/optimize_result.txt 便于回溯
    try:
        with open("/tmp/optimize_result.txt", "w", encoding="utf-8") as f:
            f.write("== 环境 ==\n")
            f.write(f"CPU 核数(约): {cores} | 内存(约): {mem_gb:.1f} GB | swap 已用: {swap_used_gb:.2f} GB\n")
            if rss_mb:
                f.write(f"模型常驻内存: {rss_mb:.0f} MB\n")
            f.write("\n== 各配置结果（耗时中位） ==\n")
            for opts, med in results:
                f.write(f"  [{fmt_name(opts)}] {med['wall_s']:.1f}s gen={med['gen_tps']:.1f} tok/s\n")
            f.write(f"\n== 推荐 ==\n{fmt_name(best_opts)} ({best['wall_s']:.1f}s)\n")
        print("\n完整结果已保存到 /tmp/optimize_result.txt")
    except Exception as e:
        print(f"\n保存结果失败: {e}")


if __name__ == "__main__":
    main()
