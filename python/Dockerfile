# 向量化推理边车服务（Python / FastAPI）。
# 默认 hashing 模式零重依赖（已能提供稳定的文本相似度打分，fail-open 兜底）。
# 如需语义 embedding：取消 python/requirements.txt 里 sentence-transformers 的注释即可，
# 但会增加镜像体积并需首次联网下载模型。
FROM python:3.11-slim

WORKDIR /app

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY vector_service.py ./

EXPOSE 8000

# 绑定 0.0.0.0 以便 compose 内部按服务名访问；端口沿用 8000
CMD ["uvicorn", "vector_service:app", "--host", "0.0.0.0", "--port", "8000"]