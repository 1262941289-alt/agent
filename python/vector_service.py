"""向量化推理边车服务（Python / FastAPI）。

为 Java 侧 ExperienceRetriever 提供文本语义相似度打分，用于把经验召回从
字符 bigram 重叠升级为向量检索。

两种运行模式（自动降级）：
  * semantic —— 安装了 sentence-transformers 且模型可加载时启用，真正的语义 embedding。
  * hashing  —— 纯 Python 的 char-ngram 特征哈希向量（零额外依赖），进程内稳定。

启动：
    pip install -r requirements.txt
    uvicorn vector_service:app --host 127.0.0.1 --port 8000

可选环境变量：
    VECTOR_MODEL  默认 "BAAI/bge-small-zh-v1.5"（中文效果好，需联网首次下载模型）。
"""
import hashlib
import math
import os
import re
import threading
from typing import List

from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="agent-vector-service")

MODEL_NAME = os.getenv("VECTOR_MODEL", "BAAI/bge-small-zh-v1.5")

_model = None
_mode = "hashing"
_HASH_DIM = 256


def _deterministic_hash(token: str) -> int:
    """跨进程稳定的哈希，避免 PYTHONHASHSEED 随机化影响向量持久化。"""
    return int.from_bytes(hashlib.md5(token.encode("utf-8")).digest()[:8], "big")


def _char_trigrams(text: str) -> List[str]:
    return [text[i:i + 3] for i in range(len(text) - 2)]


def _tokenize(text: str) -> List[str]:
    if not text:
        return []
    lower = text.lower()
    # 中文按单字、英文/数字按连续片段，再补 char-trigram 作为子词信息
    tokens = re.findall(r"[a-z0-9]+|[\u4e00-\u9fff]", lower)
    tokens += _char_trigrams(lower)
    return tokens


def _hash_vec(text: str) -> List[float]:
    vec = [0.0] * _HASH_DIM
    tokens = _tokenize(text)
    for t in tokens:
        vec[_deterministic_hash(t) % _HASH_DIM] += 1.0
    norm = math.sqrt(sum(x * x for x in vec))
    if norm > 0:
        vec = [x / norm for x in vec]
    return vec


def _cosine(a: List[float], b: List[float]) -> float:
    return float(sum(x * y for x, y in zip(a, b)))


def _try_load_semantic() -> None:
    global _model, _mode
    try:
        from sentence_transformers import SentenceTransformer
        print(f"[vector-service] 正在加载语义模型 {MODEL_NAME}（首次会联网下载，请稍候）...")
        _model = SentenceTransformer(MODEL_NAME)
        _mode = "semantic"
        print("[vector-service] 语义模型加载完成，mode=semantic")
    except Exception as e:  # noqa: BLE001
        _model = None
        _mode = "hashing"
        print(f"[vector-service] 语义模型不可用，回退 hashing 向量：{e}")


def _similarity_scores(query: str, candidates: List[str]) -> List[float]:
    if _mode == "semantic" and _model is not None:
        embeddings = _model.encode([query] + candidates, normalize_embeddings=True)
        q = embeddings[0]
        return [float(q @ c) for c in embeddings[1:]]
    q = _hash_vec(query)
    return [_cosine(q, _hash_vec(c)) for c in candidates]


class SimilarRequest(BaseModel):
    query: str
    candidates: List[str]


@app.get("/health")
def health():
    return {"status": "ok", "mode": _mode, "model": MODEL_NAME}


@app.post("/similar")
def similar(req: SimilarRequest):
    candidates = [c or "" for c in req.candidates]
    scores = _similarity_scores(req.query or "", candidates)
    return {"scores": scores, "mode": _mode, "model": MODEL_NAME}


threading.Thread(target=_try_load_semantic, daemon=True).start()