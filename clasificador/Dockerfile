FROM python:3.13.16-slim-trixie
COPY --from=ghcr.io/astral-sh/uv:0.13.1 /uv /uvx /bin/
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy UV_PYTHON_DOWNLOADS=never
WORKDIR /app
COPY pyproject.toml uv.lock .python-version README.md ./
RUN uv sync --locked --no-dev --no-install-project
COPY src ./src
RUN uv sync --locked --no-dev
RUN useradd --system --uid 10001 clamon
USER 10001
ENV PATH="/app/.venv/bin:$PATH" PORT=8080
CMD ["clasificador"]
