"""Private, bounded-lifetime audio workspace; never sweep shared /tmp files."""
from __future__ import annotations

import logging
import shutil
import tempfile
import time
from contextlib import contextmanager
from pathlib import Path

import config

logger = logging.getLogger("vg.cleanup")
_active: set[Path] = set()


def root() -> Path:
    directory = config.AUDIO_TEMP_DIR
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    return directory


@contextmanager
def workspace(prefix: str = "request-"):
    with tempfile.TemporaryDirectory(prefix=prefix, dir=root()) as name:
        path = Path(name)
        _active.add(path)
        try:
            yield path
        finally:
            _active.discard(path)


def cleanup_stale(now: float | None = None) -> int:
    now = time.time() if now is None else now
    removed = 0
    for path in root().iterdir():
        if path in _active or path.is_symlink() or not path.is_dir():
            continue
        if not path.name.startswith(("request-", "decode-")):
            continue
        try:
            if now - path.stat().st_mtime < config.AUDIO_TEMP_TTL_S:
                continue
            shutil.rmtree(path)
            removed += 1
        except FileNotFoundError:
            pass
        except OSError:
            logger.warning("Unable to clean an expired audio workspace")
    if removed:
        logger.info("Removed %d expired audio workspaces", removed)
    return removed
